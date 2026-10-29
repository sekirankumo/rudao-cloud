package com.eaglelan.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarAnimation

@Composable
actual fun platformColorScheme(): ColorScheme? = null

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun screenWidthDp(): Int =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value.toInt() }

/** Needs UIViewControllerBasedStatusBarAppearance = NO (iosApp/Info.plist). */
@Suppress("DEPRECATION")
@Composable
actual fun ImmersiveMode() {
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.setStatusBarHidden(true, UIStatusBarAnimation.UIStatusBarAnimationFade)
        onDispose {
            UIApplication.sharedApplication.setStatusBarHidden(false, UIStatusBarAnimation.UIStatusBarAnimationFade)
        }
    }
}

/** iOS has no system back; the viewer and 随机速览 have their own close buttons. */
@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) {
}
