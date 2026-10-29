package com.eaglelan.app

import com.eaglelan.app.data.Repository
import com.eaglelan.app.data.Updater
import com.eaglelan.app.ui.PlayerKind
import com.eaglelan.app.ui.VideoPlayer
import io.ktor.client.HttpClient

/** Everything a platform shell builds once at startup and hands to [com.eaglelan.app.ui.App]. */
class AppServices(
    val http: HttpClient,
    val repo: Repository,
    val updater: Updater,
    /** Directory for the thumbnail disk cache. */
    val cacheDir: String,
    val newPlayer: (PlayerKind) -> VideoPlayer,
)
