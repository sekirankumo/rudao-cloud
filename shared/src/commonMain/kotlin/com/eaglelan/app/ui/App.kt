package com.eaglelan.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.eaglelan.app.AppServices
import okio.Path.Companion.toPath

/** The whole app; each platform shell only creates [services] and hosts this. */
@Composable
fun App(services: AppServices) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { services.http })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(services.cacheDir.toPath() / "thumbs")
                    .maxSizeBytes(1024L * 1024 * 1024)
                    .build()
            }
            // Thumbnails are immutable per item; Coil 3 ignores Cache-Control by default, so the disk
            // cache is always used when present.
            .crossfade(true)
            .build()
    }
    AppTheme {
        val vm: MainViewModel = viewModel { MainViewModel(services) }
        val configured by vm.configured.collectAsStateWithLifecycle()
        var showSetup by rememberSaveable { mutableStateOf(false) }
        var viewerIndex by rememberSaveable { mutableIntStateOf(-1) }
        var previewing by rememberSaveable { mutableStateOf(false) }

        // F5 / Ctrl+R: the keyboard version of pull-to-refresh.
        KeyHandler { e ->
            val refresh = e.key == Key.F5 || (e.key == Key.R && e.isCtrlPressed)
            if (refresh && configured) vm.refresh()
            refresh
        }

        if (!configured || showSetup) {
            SetupScreen(
                vm = vm,
                canCancel = configured,
                onDone = { showSetup = false },
            )
        } else {
            LibraryScreen(
                vm = vm,
                onOpen = { viewerIndex = it },
                onSettings = { showSetup = true },
                onPreview = { previewing = true },
            )
            if (previewing) {
                PreviewScreen(
                    vm = vm,
                    onClose = { previewing = false },
                    onWatchFull = { item, positionMs ->
                        // open the normal player on that video, at the moment being previewed
                        PlaybackMemory.positions[item.id] = positionMs
                        previewing = false
                        viewerIndex = vm.items.value.indexOfFirst { it.id == item.id }
                    },
                )
            }
            if (viewerIndex >= 0) {
                ViewerScreen(
                    vm = vm,
                    startIndex = viewerIndex,
                    onClose = { viewerIndex = -1 },
                )
            }
        }
        UpdateDialog(vm)
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = platformColorScheme() ?: darkColorScheme(
        primary = Color(0xFF7FA7FF),
        background = Color(0xFF111214),
        surface = Color(0xFF111214),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
