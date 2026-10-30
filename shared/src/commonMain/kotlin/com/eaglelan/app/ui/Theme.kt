package com.eaglelan.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eaglelan.app.resources.Res
import com.eaglelan.app.resources.noto_sans_sc
import org.jetbrains.compose.resources.Font

/**
 * The app's design system, identical on every platform (no wallpaper colours).
 * The full spec, with swatches: the "设计规范" board of the UI prototype.
 */
object AppColors {
    val Background = Color(0xFF0F1115)
    /** Top bar, sidebar. */
    val Surface1 = Color(0xFF16191F)
    /** Menus, inputs, placeholders. */
    val Surface2 = Color(0xFF1D2129)
    /** Snackbar. */
    val Surface3 = Color(0xFF262B34)
    val Divider = Color(0xFF323844)
    val Text1 = Color(0xFFECEEF2)
    val Text2 = Color(0xFFA9AFBA)
    val Text3 = Color(0xFF858C99)
    /** Filled buttons and the app icon; white text on it passes 4.5:1. */
    val Brand = Color(0xFF2A6FE0)
    /** Text buttons, icons and links on dark surfaces. */
    val Accent = Color(0xFF9DBBFF)
    val Selected = Color(0xFF1E2D4A)
    val OnSelected = Color(0xFFD3E0FF)
    val Favorite = Color(0xFFFF5470)
    val Online = Color(0xFF4CB867)
    val Offline = Color(0xFFF0645C)
    val Checking = Color(0xFF8B93A1)
    val ErrorText = Color(0xFFFF9A93)
}

/** Over video and thumbnails. */
object MediaColors {
    /** Buttons over the picture. */
    val ScrimWeak = Color(0x47000000)
    /** Badges, toasts, the paused icon. */
    val Scrim = Color(0x80000000)
    /** Top / bottom bar gradients start here. */
    val ScrimStrong = Color(0xB8000000)
    val Text = Color.White
    val Text2 = Color(0xBDFFFFFF)
    val Chip = Color(0x29FFFFFF)
    val Track = Color(0x40FFFFFF)
    val Buffered = Color(0x66FFFFFF)
}

/** Three corner radii: badges, cards / thumbnails / menus, and fully round. */
object AppShapes {
    val Small = RoundedCornerShape(4.dp)
    val Medium = RoundedCornerShape(10.dp)
    val Full = RoundedCornerShape(percent = 50)
}

object Motion {
    val Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    /** Thumbnail ⇄ viewer. */
    const val EMPHASIZED = 340
    /** Drawer, top bar, swipes. */
    const val STANDARD = 240
    /** Overlays fading in and out. */
    const val FADE = 200
}

private val scheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color(0xFF0A1A3A),
    primaryContainer = AppColors.Brand,
    onPrimaryContainer = Color.White,
    secondary = AppColors.Accent,
    onSecondary = Color(0xFF0A1A3A),
    secondaryContainer = AppColors.Selected,
    onSecondaryContainer = AppColors.OnSelected,
    tertiary = AppColors.Accent,
    background = AppColors.Background,
    onBackground = AppColors.Text1,
    surface = AppColors.Background,
    onSurface = AppColors.Text1,
    surfaceVariant = AppColors.Surface2,
    onSurfaceVariant = AppColors.Text2,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = AppColors.Background,
    surfaceContainerLow = AppColors.Surface1,
    surfaceContainer = AppColors.Surface1,
    surfaceContainerHigh = AppColors.Surface2,
    surfaceContainerHighest = AppColors.Surface3,
    inverseSurface = AppColors.Surface3,
    inverseOnSurface = AppColors.Text1,
    inversePrimary = AppColors.Accent,
    outline = Color(0xFF5A616E),
    outlineVariant = AppColors.Divider,
    error = AppColors.ErrorText,
    onError = Color(0xFF3A0B08),
    scrim = Color.Black,
)

@Composable
private fun appFontFamily(): FontFamily {
    // One variable font (Noto Sans SC = 思源黑体, subset to common characters; rarer ones fall back to the system font).
    val fonts = listOf(400, 500, 700).map { w ->
        Font(Res.font.noto_sans_sc, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
    return FontFamily(fonts)
}

private fun style(size: Int, line: Int, weight: FontWeight, family: FontFamily) =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val f = appFontFamily()
    val typography = Typography(
        headlineMedium = style(26, 34, FontWeight.Bold, f),
        headlineSmall = style(20, 28, FontWeight.Bold, f),
        titleLarge = style(18, 26, FontWeight.Medium, f),
        titleMedium = style(16, 24, FontWeight.Medium, f),
        titleSmall = style(14, 20, FontWeight.Medium, f),
        bodyLarge = style(15, 22, FontWeight.Normal, f),
        bodyMedium = style(14, 20, FontWeight.Normal, f),
        bodySmall = style(12, 18, FontWeight.Normal, f),
        labelLarge = style(14, 20, FontWeight.Medium, f),
        labelMedium = style(12, 16, FontWeight.Medium, f),
        labelSmall = style(11, 16, FontWeight.Medium, f),
    )
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Default text / icon colour for every screen. Without this, anything not inside a Surface or
        // Scaffold (the library, settings, the floating top bar) falls back to black.
        CompositionLocalProvider(
            LocalContentColor provides AppColors.Text1,
            LocalTextStyle provides typography.bodyLarge,
            content = content,
        )
    }
}

/** Filled buttons use the brand blue with white text (M3's default would be the light accent). */
@Composable
fun brandButtonColors() = ButtonDefaults.buttonColors(containerColor = AppColors.Brand, contentColor = Color.White)
