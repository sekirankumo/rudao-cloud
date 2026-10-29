package com.eaglelan.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.room.Room
import com.eaglelan.app.data.AppDatabase
import com.eaglelan.app.data.Prefs
import com.eaglelan.app.data.Release
import com.eaglelan.app.data.Repository
import com.eaglelan.app.data.UpdateInstaller
import com.eaglelan.app.data.Updater
import com.eaglelan.app.data.createHttpClient
import com.eaglelan.app.ui.ExoVideoPlayer
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Built once by the Application; the activity hands it to App(). */
fun createAndroidServices(context: Context): AppServices {
    val app = context.applicationContext
    // ExoPlayer streams through the same OkHttp client that backs Ktor (API, thumbnails, updates).
    val okHttp = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val http = createHttpClient(OkHttp) { preconfigured = okHttp }
    val prefs = Prefs(SharedPreferencesSettings(app.getSharedPreferences("settings", Context.MODE_PRIVATE)))
    val db = AppDatabase.build(
        Room.databaseBuilder<AppDatabase>(app, app.getDatabasePath("library.db").absolutePath),
        Dispatchers.IO,
    )
    return AppServices(
        http = http,
        repo = Repository(prefs, db, http),
        updater = Updater(http, AndroidInstaller(app, http)),
        cacheDir = app.cacheDir.absolutePath,
        newPlayer = { kind -> ExoVideoPlayer(app, okHttp, kind) },
    )
}

private class AndroidInstaller(private val context: Context, private val http: HttpClient) : UpdateInstaller {
    override val assetSuffix = ".apk"

    override fun canInstall() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override suspend fun download(release: Release, onProgress: (Float) -> Unit): String {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "update-${release.version}.apk")
        return http.downloadTo(release.assetUrl, file, release.size, onProgress).absolutePath
    }

    override fun exists(path: String) = File(path).exists()

    override fun install(path: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", File(path))
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun openPage(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
