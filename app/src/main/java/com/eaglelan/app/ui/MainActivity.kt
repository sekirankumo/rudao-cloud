package com.eaglelan.app.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so force light system-bar icons regardless of system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            AppTheme {
                val vm: MainViewModel = viewModel()
                val configured by vm.configured.collectAsStateWithLifecycle()
                var showSetup by rememberSaveable { mutableStateOf(false) }
                var viewerIndex by rememberSaveable { mutableIntStateOf(-1) }
                var previewing by rememberSaveable { mutableStateOf(false) }

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
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        darkColorScheme(
            primary = Color(0xFF7FA7FF),
            background = Color(0xFF111214),
            surface = Color(0xFF111214),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
