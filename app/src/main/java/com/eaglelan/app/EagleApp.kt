package com.eaglelan.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.eaglelan.app.data.AppDatabase
import com.eaglelan.app.data.Prefs
import com.eaglelan.app.data.Repository
import com.eaglelan.app.data.Updater
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class EagleApp : Application(), ImageLoaderFactory {

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val repo: Repository by lazy {
        Repository(Prefs(this), AppDatabase.create(this), http)
    }

    val updater: Updater by lazy { Updater(this, http) }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(http)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("thumbs"))
                    .maxSizeBytes(1024L * 1024 * 1024)
                    .build()
            }
            // Thumbnails are immutable per item; always serve from disk cache when present.
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
}
