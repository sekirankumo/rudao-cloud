package com.eaglelan.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.eaglelan.app.EagleApp
import com.eaglelan.app.data.ItemEntity
import com.eaglelan.app.data.isImage
import com.eaglelan.app.data.isVideo
import com.eaglelan.app.data.tagList
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Remembers where each video was left off, for this app session. */
internal object PlaybackMemory {
    val positions = HashMap<String, Long>()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ViewerScreen(vm: MainViewModel, startIndex: Int, onClose: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }

    // Immersive: hide status/navigation bars while viewing.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Auto-hide the overlay on videos after a few seconds of no interaction.
    LaunchedEffect(chrome, interaction, pager.settledPage) {
        if (chrome && items.getOrNull(pager.settledPage)?.isVideo == true) {
            delay(3500)
            chrome = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Vertical swipe changes item; horizontal drag on a video seeks.
        VerticalPager(
            state = pager,
            beyondViewportPageCount = 1,
            key = { items[it].id },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            when {
                item.isVideo -> VideoPage(
                    item = item,
                    url = vm.fileUrl(item),
                    thumbUrl = vm.thumbUrl(item),
                    active = pager.settledPage == page,
                    chrome = chrome,
                    onToggleChrome = { chrome = !chrome },
                    onInteract = { interaction++ },
                )
                item.isImage -> ZoomableImage(
                    url = vm.fileUrl(item),
                    onTap = { chrome = !chrome },
                )
                else -> Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onTap = { chrome = !chrome }) },
                ) {
                    if (item.hasThumb) {
                        AsyncImage(vm.thumbUrl(item), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                    Text(
                        "不支持预览 .${item.ext} 文件",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        val current = items.getOrNull(pager.currentPage)
        AnimatedVisibility(
            visible = chrome && current != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            if (current == null) return@AnimatedVisibility
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(current.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append("${pager.currentPage + 1} / ${items.size}")
                                if (current.width > 0) append(" · ${current.width}×${current.height}")
                                append(" · ${formatSize(current.size)}")
                                append(" · ${current.ext.uppercase()}")
                            },
                            color = Color(0xB3FFFFFF),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    val fav = current.id in favorites
                    IconButton(onClick = { vm.toggleFavorite(current.id) }) {
                        Icon(
                            if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (fav) "取消收藏" else "收藏",
                            tint = if (fav) FAVORITE_RED else Color.White,
                        )
                    }
                }
                val tags = current.tagList
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    ) {
                        tags.forEach { tag ->
                            // Tapping a tag filters the library by it.
                            Text(
                                tag,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x33FFFFFF))
                                    .clickable {
                                        vm.selectedTags.value = setOf(tag)
                                        onClose()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                if (current.annotation.isNotBlank()) {
                    Text(
                        current.annotation,
                        color = Color(0xCCFFFFFF),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(
    item: ItemEntity,
    url: String,
    thumbUrl: String,
    active: Boolean,
    chrome: Boolean,
    onToggleChrome: () -> Unit,
    onInteract: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Poster frame, visible until the first video frame renders.
        if (item.hasThumb) {
            AsyncImage(thumbUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        if (active) {
            ActiveVideo(item, url, chrome, onToggleChrome, onInteract)
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }) },
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ActiveVideo(
    item: ItemEntity,
    url: String,
    chrome: Boolean,
    onToggleChrome: () -> Unit,
    onInteract: () -> Unit,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        val player = remember(item.id) {
            val http = (context.applicationContext as EagleApp).http
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(http)))
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(url))
                    repeatMode = Player.REPEAT_MODE_ONE
                    PlaybackMemory.positions[item.id]?.let { seekTo(it) }
                    playWhenReady = true
                    prepare()
                }
        }
        DisposableEffect(player) {
            onDispose {
                PlaybackMemory.positions[item.id] = player.currentPosition
                player.release()
            }
        }

        var position by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(0L) }
        var playing by remember { mutableStateOf(false) }
        var buffering by remember { mutableStateOf(false) }
        var bufferedPos by remember { mutableLongStateOf(0L) }
        var speed by remember { mutableFloatStateOf(1f) }
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        // Horizontal-swipe seeking state.
        var swiping by remember { mutableStateOf(false) }
        var swipeStart by remember { mutableLongStateOf(0L) }
        var swipeTarget by remember { mutableLongStateOf(0L) }
        var swipeDx by remember { mutableFloatStateOf(0f) }
        var boosting by remember { mutableStateOf(false) }

        LaunchedEffect(player) {
            while (isActive) {
                position = player.currentPosition
                duration = player.duration.coerceAtLeast(0L)
                playing = player.isPlaying
                buffering = player.playbackState == Player.STATE_BUFFERING
                bufferedPos = player.bufferedPosition
                delay(200)
            }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = {
                it.player = player
                it.keepScreenOn = playing
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Gesture layer: tap toggles overlay, double-tap toggles play/pause,
        // horizontal drag seeks (vertical drags fall through to the pager).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(player) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            if (boosting) {
                                boosting = false
                                player.setPlaybackSpeed(speed)
                            }
                        },
                        onLongPress = {
                            boosting = true
                            player.setPlaybackSpeed(2f)
                            if (!player.isPlaying) player.play()
                        },
                        onTap = { onToggleChrome() },
                        onDoubleTap = {
                            if (player.isPlaying) player.pause() else player.play()
                        },
                    )
                }
                // Re-keyed on boosting: while held at 2x no drag detector runs, so moving the
                // finger neither seeks nor cancels the long-press.
                .pointerInput(player, boosting) {
                    if (boosting) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            swipeStart = player.currentPosition
                            swipeTarget = swipeStart
                            swipeDx = 0f
                            swiping = true
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            swipeDx += dx
                            val span = seekSpanMs(duration)
                            val target = swipeStart + (swipeDx / size.width * span).toLong()
                            swipeTarget = if (duration > 0) target.coerceIn(0L, duration) else target.coerceAtLeast(0L)
                        },
                        onDragEnd = {
                            player.seekTo(swipeTarget)
                            swiping = false
                            onInteract()
                        },
                        onDragCancel = { swiping = false },
                    )
                },
        )

        if (boosting) {
            Text(
                "▶▶ 2x 倍速中",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .background(Color(0x99000000), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        if (swiping) {
            val delta = swipeTarget - swipeStart
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x99000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    "${formatMillis(swipeTarget)} / ${formatMillis(duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                )
                Text(
                    (if (delta >= 0) "+" else "-") + formatMillis(kotlin.math.abs(delta)),
                    color = Color(0xB3FFFFFF),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                )
            }
        } else if (buffering) {
            CircularProgressIndicator(
                Modifier
                    .align(Alignment.Center)
                    .size(40.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        } else if (!playing && duration > 0) {
            // Paused indicator; double-tap anywhere resumes.
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xCCFFFFFF),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color(0x66000000), CircleShape)
                    .padding(12.dp),
            )
        }

        // Always show the progress bar while swiping so the target position is visible.
        AnimatedVisibility(
            visible = chrome || swiping,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val shown = when {
                dragging -> (dragValue * duration).toLong()
                swiping -> swipeTarget
                else -> position
            }
            val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
                    .navigationBarsPadding()
                    .padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 4.dp),
            ) {
                IconButton(onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                    onInteract()
                }) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "暂停" else "播放",
                        tint = Color.White,
                    )
                }
                Text(formatMillis(shown), color = Color.White, style = timeStyle)
                ThinSeekBar(
                    progress = if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    buffered = if (duration > 0) (bufferedPos.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    active = dragging || swiping,
                    onSeekStart = { dragging = true },
                    onSeek = {
                        dragValue = it
                        onInteract()
                    },
                    onSeekEnd = {
                        player.seekTo((dragValue * duration).toLong())
                        dragging = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Text(formatMillis(duration), color = Color(0xB3FFFFFF), style = timeStyle)
                TextButton(onClick = {
                    val speeds = listOf(1f, 1.5f, 2f, 0.5f)
                    speed = speeds[(speeds.indexOf(speed) + 1) % speeds.size]
                    player.setPlaybackSpeed(speed)
                    onInteract()
                }) {
                    Text(
                        if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/**
 * A slim progress bar: 2dp track that thickens while seeking, with a small thumb.
 * The touch target is much taller than the drawn track so it's easy to grab.
 */
@Composable
private fun ThinSeekBar(
    progress: Float,
    buffered: Float,
    active: Boolean,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track by animateDpAsState(if (active) 4.dp else 2.dp, label = "track")
    val thumb by animateDpAsState(if (active) 14.dp else 8.dp, label = "thumb")
    val start by rememberUpdatedState(onSeekStart)
    val seek by rememberUpdatedState(onSeek)
    val end by rememberUpdatedState(onSeekEnd)
    Canvas(
        modifier
            .height(32.dp)
            .pointerInput(Unit) {
                fun frac(x: Float) = (x / size.width).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    start()
                    seek(frac(down.position.x))
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { c ->
                            if (c.pressed) {
                                seek(frac(c.position.x))
                                c.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    end()
                }
            },
    ) {
        val y = size.height / 2
        val w = size.width
        val t = track.toPx()
        drawLine(Color(0x40FFFFFF), Offset(0f, y), Offset(w, y), t, StrokeCap.Round)
        drawLine(Color(0x66FFFFFF), Offset(0f, y), Offset(w * buffered, y), t, StrokeCap.Round)
        drawLine(Color.White, Offset(0f, y), Offset(w * progress, y), t, StrokeCap.Round)
        drawCircle(Color.White, thumb.toPx() / 2, Offset(w * progress, y))
    }
}

/**
 * How much time one full-screen-width swipe covers: a quarter of the video,
 * but at least 30s (or the whole clip if shorter) and at most 10 minutes.
 */
private fun seekSpanMs(durationMs: Long): Long =
    if (durationMs <= 0) 60_000L
    else (durationMs / 4).coerceIn(minOf(durationMs, 30_000L), 600_000L)

@Composable
private fun ZoomableImage(url: String, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = boxSize.width * (s - 1) / 2
        val maxY = boxSize.height * (s - 1) / 2
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                boxSize = size
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // Only take over the gesture when pinching or already zoomed,
                        // so single-finger swipes still page through items.
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else clamp(offset + event.calculatePan(), scale)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            },
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
