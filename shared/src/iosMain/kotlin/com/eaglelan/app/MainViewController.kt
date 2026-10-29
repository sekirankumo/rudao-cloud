package com.eaglelan.app

import androidx.compose.ui.window.ComposeUIViewController
import androidx.room.Room
import com.eaglelan.app.data.AppDatabase
import com.eaglelan.app.data.Prefs
import com.eaglelan.app.data.Repository
import com.eaglelan.app.data.UpdateInstaller
import com.eaglelan.app.data.Updater
import com.eaglelan.app.data.createHttpClient
import com.eaglelan.app.ui.App
import com.eaglelan.app.ui.AvVideoPlayer
import com.russhwolf.settings.NSUserDefaultsSettings
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
private fun dir(which: NSSearchPathDirectory): String =
    NSFileManager.defaultManager.URLForDirectory(which, NSUserDomainMask, null, true, null)!!.path!!

private val services: AppServices by lazy {
    val http = createHttpClient(Darwin)
    val prefs = Prefs(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults))
    val db = AppDatabase.build(
        Room.databaseBuilder<AppDatabase>(dir(NSDocumentDirectory) + "/library.db"),
        Dispatchers.IO,
    )
    AppServices(
        http = http,
        repo = Repository(prefs, db, http),
        updater = Updater(http, IosInstaller),
        cacheDir = dir(NSCachesDirectory),
        newPlayer = { kind -> AvVideoPlayer(kind) },
    )
}

/** A sideloaded IPA can't replace itself: 更新 opens the release page to download the new IPA. */
private object IosInstaller : UpdateInstaller {
    override val assetSuffix = ".ipa"
    override val canSelfInstall = false

    override fun openPage(url: String) {
        NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
    }
}

/** Called from Swift (iosApp/Sources/iOSApp.swift). */
fun MainViewController(): UIViewController = ComposeUIViewController { App(services) }
