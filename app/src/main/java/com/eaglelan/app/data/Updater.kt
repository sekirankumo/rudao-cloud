package com.eaglelan.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Releases are published by .github/workflows/release.yml in this repository. */
const val UPDATE_REPO = "sekirankumo/rudao-cloud"

@Serializable
private data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

class Updater(private val context: Context, private val http: OkHttpClient) {

    val currentVersion: String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    /** The latest published release if it is newer than this build, else null. */
    suspend fun checkNewer(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$UPDATE_REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { resp ->
            if (resp.code == 404) return@withContext null      // nothing published yet
            if (!resp.isSuccessful) throw ApiException("GitHub 返回 HTTP ${resp.code}")
            val rel = AppJson.decodeFromString(GhRelease.serializer(), resp.body!!.string())
            val apk = rel.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext null
            val version = rel.tag.removePrefix("v")
            if (!isNewer(version, currentVersion)) return@withContext null
            Release(version, rel.body.orEmpty().trim(), apk.url, apk.size)
        }
    }

    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "update-${release.version}.apk")
        http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException("下载失败：HTTP ${resp.code}")
            val body = resp.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: release.size
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        file
    }

    /** Whether the system lets this app open the package installer (Android 8+ asks per app). */
    fun canInstall() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        /** Compares dotted versions numerically ("0.10.0" > "0.9.3"); suffixes like "-dev" are ignored. */
        fun isNewer(remote: String, local: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(remote)
            val b = parts(local)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
