package com.eaglelan.app.data

import com.eaglelan.app.APP_VERSION
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("html_url") val page: String = "",
    val body: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

/** [assetUrl] is this platform's package (.apk / -windows.exe / .ipa) in the release. */
data class Release(val version: String, val notes: String, val assetUrl: String, val size: Long, val pageUrl: String)

/** The platform half of self-update: which release asset to use, and how to install it. */
interface UpdateInstaller {
    /** Suffix of this platform's asset name, e.g. ".apk". */
    val assetSuffix: String
    /** False on iOS: an IPA can't install itself, so 更新 opens the release page instead. */
    val canSelfInstall: Boolean get() = true
    /** Whether the system lets this app open its installer (Android 8+ asks per app). */
    fun canInstall(): Boolean = true
    fun openInstallPermissionSettings() {}
    /** Downloads the package and returns its local path. */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): String = error("not supported")
    fun exists(path: String): Boolean = false
    fun install(path: String) {}
    fun openPage(url: String)
}

class Updater(private val http: HttpClient, val installer: UpdateInstaller) {

    val currentVersion: String = APP_VERSION

    /** The latest published release if it is newer than this build, else null. */
    suspend fun checkNewer(): Release? {
        val resp = http.get("https://api.github.com/repos/$UPDATE_REPO/releases/latest") {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", "rudao-cloud")
        }
        if (resp.status.value == 404) return null      // nothing published yet
        if (!resp.status.isSuccess()) throw ApiException("GitHub 返回 HTTP ${resp.status.value}")
        val rel = AppJson.decodeFromString(GhRelease.serializer(), resp.text())
        val asset = rel.assets.firstOrNull { it.name.endsWith(installer.assetSuffix) } ?: return null
        val version = rel.tag.removePrefix("v")
        if (!isNewer(version, currentVersion)) return null
        return Release(version, rel.body.orEmpty().trim(), asset.url, asset.size, rel.page)
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
