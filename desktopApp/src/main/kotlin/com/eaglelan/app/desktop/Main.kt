package com.eaglelan.app.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.eaglelan.app.createDesktopServices
import com.eaglelan.app.shutdownDesktopServices
import com.eaglelan.app.ui.App
import com.eaglelan.app.ui.AppKeys
import java.awt.Dimension

fun main() {
    val services = createDesktopServices()
    val icon = BitmapPainter(
        Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")!!.use { loadImageBitmap(it) }
    )
    application {
        val state = rememberWindowState(width = 1280.dp, height = 820.dp)
        Window(
            onCloseRequest = {
                shutdownDesktopServices()
                exitApplication()
            },
            state = state,
            title = "入到云盘",
            icon = icon,
            onPreviewKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.F11) {
                    state.placement =
                        if (state.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
                        else WindowPlacement.Fullscreen
                    true
                } else {
                    AppKeys.dispatch(e)
                }
            },
        ) {
            window.minimumSize = Dimension(420, 560)
            // same as the Android window background, so nothing flashes white on start
            window.background = java.awt.Color(0x11, 0x12, 0x14)
            App(services)
        }
    }
}
