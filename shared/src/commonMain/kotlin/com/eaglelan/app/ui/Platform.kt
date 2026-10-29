package com.eaglelan.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/** Android 12+ wallpaper colours; null elsewhere (the fixed dark scheme is used). */
@Composable
expect fun platformColorScheme(): ColorScheme?

/** Window width in dp, as Android's Configuration.screenWidthDp. */
@Composable
expect fun screenWidthDp(): Int

/** Hides the status / navigation bars while in composition (viewer, 随机速览). */
@Composable
expect fun ImmersiveMode()

/** System back: Android back gesture, Esc on Windows; iOS has none (screens show a close button). */
@Composable
expect fun PlatformBackHandler(onBack: () -> Unit)

/**
 * Hardware-keyboard shortcuts (mainly Windows). Screens register handlers while visible; the window
 * offers each key-down to the newest handler first, and stops at the first one that returns true.
 */
object AppKeys {
    private val handlers = mutableListOf<(KeyEvent) -> Boolean>()

    fun dispatch(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return handlers.asReversed().toList().any { it(event) }
    }

    internal fun add(h: (KeyEvent) -> Boolean) = handlers.add(h)
    internal fun remove(h: (KeyEvent) -> Boolean) = handlers.remove(h)
}

@Composable
fun KeyHandler(onKey: (KeyEvent) -> Boolean) {
    val current by rememberUpdatedState(onKey)
    DisposableEffect(Unit) {
        val h: (KeyEvent) -> Boolean = { current(it) }
        AppKeys.add(h)
        onDispose { AppKeys.remove(h) }
    }
}
