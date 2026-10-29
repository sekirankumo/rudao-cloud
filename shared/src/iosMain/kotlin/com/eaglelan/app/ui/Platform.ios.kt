package com.eaglelan.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS

actual val isDesktop = false

/** iOS 16+ (requestGeometryUpdate); on older systems the button does nothing. */
@Composable
actual fun LandscapeLock(enabled: Boolean) {
    DisposableEffect(enabled) {
        requestOrientation(if (enabled) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskPortrait)
        onDispose { if (enabled) requestOrientation(UIInterfaceOrientationMaskPortrait) }
    }
}

private fun requestOrientation(mask: UIInterfaceOrientationMask) {
    val scene = UIApplication.sharedApplication.connectedScenes.anyObject() as? UIWindowScene ?: return
    if (!scene.respondsToSelector(NSSelectorFromString("requestGeometryUpdateWithPreferences:errorHandler:"))) return
    scene.requestGeometryUpdateWithPreferences(UIWindowSceneGeometryPreferencesIOS(mask), null)
}

actual fun toggleFullscreen() {}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun screenWidthDp(): Int =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value.toInt() }

/** Read by the Swift host view controller (iosApp/Sources/iOSApp.swift), which owns the status bar. */
object SystemBars {
    var onChange: (() -> Unit)? = null
    var hidden: Boolean = false
        set(v) {
            field = v
            onChange?.invoke()
        }
}

@Composable
actual fun ImmersiveMode() {
    DisposableEffect(Unit) {
        SystemBars.hidden = true
        onDispose { SystemBars.hidden = false }
    }
}

/** iOS has no system back; the viewer and 随机速览 have their own close buttons. */
@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) {
}
