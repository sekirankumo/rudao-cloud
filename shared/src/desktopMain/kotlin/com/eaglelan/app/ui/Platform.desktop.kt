package com.eaglelan.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
actual fun platformColorScheme(): ColorScheme? = null

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun screenWidthDp(): Int =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value.toInt() }

/** A window has no system bars to hide; F11 toggles full screen instead (see desktopApp). */
@Composable
actual fun ImmersiveMode() {
}

@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) = KeyHandler { e ->
    if (e.key == Key.Escape) {
        onBack()
        true
    } else {
        false
    }
}
