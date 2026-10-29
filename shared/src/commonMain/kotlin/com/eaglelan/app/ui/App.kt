package com.eaglelan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var viewerIndex by rememberSaveable { mutableIntStateOf(-1) }
        var previewing by rememberSaveable { mutableStateOf(false) }
        var hero by remember { mutableStateOf<Hero?>(null) }
        val bounds = remember { ThumbBounds() }

        // F5 / Ctrl+R: the keyboard version of pull-to-refresh.
        KeyHandler { e ->
            val refresh = e.key == Key.F5 || (e.key == Key.R && e.isCtrlPressed)
            if (refresh && configured) vm.refresh()
            refresh
        }

        Box(Modifier.fillMaxSize().background(AppColors.Background)) {
            if (!configured || showSetup) {
                SetupScreen(
                    vm = vm,
                    canCancel = configured,
                    onDone = { showSetup = false },
                )
            } else {
                LibraryScreen(
                    vm = vm,
                    bounds = bounds,
                    onOpen = { index ->
                        val item = vm.items.value.getOrNull(index) ?: return@LibraryScreen
                        if (hero != null) return@LibraryScreen
                        hero = Hero(item, vm.thumbUrl(item), bounds.map[item.id], opening = true, index = index)
                    },
                    onSettings = { showSettings = true },
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
                        onClose = { item ->
                            viewerIndex = -1
                            if (item != null) {
                                vm.locate.value = item.id
                                hero = Hero(item, vm.thumbUrl(item), null, opening = false)
                            }
                        },
                    )
                }
                if (showSettings) {
                    SettingsScreen(
                        vm = vm,
                        onBack = { showSettings = false },
                        onChangeServer = {
                            showSettings = false
                            showSetup = true
                        },
                    )
                }
                hero?.let { h ->
                    HeroOverlay(h, bounds) {
                        if (h.opening) viewerIndex = h.index
                        hero = null
                    }
                }
            }
            UpdateDialog(vm)
        }
    }
}
