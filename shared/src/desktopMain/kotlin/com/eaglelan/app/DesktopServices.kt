package com.eaglelan.app

import androidx.room.Room
import com.eaglelan.app.data.AppDatabase
import com.eaglelan.app.data.Prefs
import com.eaglelan.app.data.Release
import com.eaglelan.app.data.Repository
import com.eaglelan.app.data.UpdateInstaller
import com.eaglelan.app.data.Updater
import com.eaglelan.app.data.createHttpClient
import com.eaglelan.app.ui.Vlc
import com.eaglelan.app.ui.VlcVideoPlayer
import com.russhwolf.settings.PreferencesSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.prefs.Preferences
import kotlin.system.exitProcess

/** Data lives in %LOCALAPPDATA%\RudaoCloud (database + thumbnail cache); settings in the registry. */
fun createDesktopServices(): AppServices {
    val home = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "RudaoCloud").apply { mkdirs() }
    val http = createHttpClient(OkHttp)
    val prefs = Prefs(PreferencesSettings(Preferences.userRoot().node("com/eaglelan/rudao-cloud")))
    val db = AppDatabase.build(
        Room.databaseBuilder<AppDatabase>(File(home, "library.db").absolutePath),
        Dispatchers.IO,
    )
    return AppServices(
        http = http,
        repo = Repository(prefs, db, http),
        updater = Updater(http, WindowsInstaller(http)),
        cacheDir = File(home, "cache").absolutePath,
        newPlayer = { kind -> VlcVideoPlayer(kind) },
    )
}

/** Call when the window closes. */
fun shutdownDesktopServices() = Vlc.release()

private class WindowsInstaller(private val http: HttpClient) : UpdateInstaller {
    override val assetSuffix = "-windows.exe"

    override suspend fun download(release: Release, onProgress: (Float) -> Unit): String {
        val dir = File(System.getProperty("java.io.tmpdir"), "rudao-cloud-update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "rudao-cloud-${release.version}-setup.exe")
        return http.downloadTo(release.assetUrl, file, release.size, onProgress).absolutePath
    }

    override fun exists(path: String) = File(path).exists()

    /** The installer replaces this installation, so quit to let it overwrite our files. */
    override fun install(path: String) {
        ProcessBuilder(path).start()
        exitProcess(0)
    }

    override fun openPage(url: String) = Desktop.getDesktop().browse(URI(url))
}
