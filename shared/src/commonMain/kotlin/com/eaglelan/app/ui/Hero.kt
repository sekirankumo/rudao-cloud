package com.eaglelan.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import com.eaglelan.app.data.ItemEntity
import kotlin.math.min
import kotlin.math.roundToInt

/** The thumbnail flying between its grid cell ([from]) and full screen; [index] is where the viewer opens. */
data class Hero(val item: ItemEntity, val thumbUrl: String, val from: Rect?, val opening: Boolean, val index: Int = -1)

/**
 * Opening: grid cell → full screen over a fading-in black backdrop; the viewer appears when it lands.
 * Closing: full screen → the cell, which the grid has meanwhile scrolled into view ([ThumbBounds]).
 * Without a cell to fly to (filtered away) it just fades.
 */
@Composable
fun HeroOverlay(hero: Hero, bounds: ThumbBounds, onFinished: () -> Unit) {
    val anim = remember(hero) { Animatable(if (hero.opening) 0f else 1f) }
    var cell by remember(hero) { mutableStateOf(hero.from) }
    LaunchedEffect(hero) {
        val spec = tween<Float>(Motion.EMPHASIZED, easing = Motion.Easing)
        if (hero.opening) {
            anim.animateTo(1f, spec)
        } else {
            // let the grid scroll to the item and lay it out before reading where it is
            repeat(3) { withFrameNanos { } }
            cell = bounds.map[hero.item.id]
            anim.animateTo(0f, spec)
        }
        onFinished()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val ratio = if (hero.item.width > 0 && hero.item.height > 0) hero.item.width.toFloat() / hero.item.height else 1f
        val scale = min(w / ratio, h)
        val fw = scale * ratio
        val fh = scale
        val full = Rect((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2)

        val t = anim.value
        val start = cell
        val rect = if (start != null) lerp(start, full, t) else full
        val density = LocalDensity.current
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = t)))
        AsyncImage(
            model = hero.thumbUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .alpha(if (start == null) t else 1f)
                .clip(RoundedCornerShape(lerp(10.dp, 0.dp, t)))
                .background(AppColors.Surface2),
        )
    }
}
