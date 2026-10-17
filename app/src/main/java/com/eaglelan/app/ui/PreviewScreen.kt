package com.eaglelan.app.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.eaglelan.app.EagleApp
import com.eaglelan.app.data.ItemEntity
import com.eaglelan.app.data.isVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import kotlin.random.Random

/** One 随机速览 segment: a random window of [lengthMs] starting at [startMs]. */
private data class Clip(val item: ItemEntity, val startMs: Long, val lengthMs: Long)

/** Deals clips from the pool in shuffled order, reshuffling when it runs out. */
private class ClipDeck(private val pool: List<ItemEntity>) {
    private val queue = ArrayDeque<ItemEntity>()

    fun next(lengthMs: Long): Clip {
        if (queue.isEmpty()) queue.addAll(pool.shuffled())
        val item = queue.removeFirst()
        val dur = (item.duration * 1000).toLong()
        if (dur <= lengthMs + 1000) return Clip(item, 0, dur.coerceAtLeast(1000))   // short video: play it all
        // skip intros/outros: start somewhere in 5%..95% of the video
        val lo = (dur * 0.05).toLong()
        val hi = (dur * 0.95).toLong() - lengthMs
        return Clip(item, if (hi > lo) Random.nextLong(lo, hi) else 0, lengthMs)
    }
}

/** Small buffer (a clip only needs ~10s) and keyframe seeks so jumping into the middle is quick. */
@UnstableApi
private fun previewPlayer(context: Context, http: OkHttpClient): ExoPlayer =
    ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(http)))
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(5_000, 15_000, 1_000, 2_000).build())
        .build()
        .apply { setSeekParameters(SeekParameters.CLOSEST_SYNC) }

private val CLIP_LENGTHS = listOf(5, 10, 15, 30)

/**
 * 随机速览: plays a random [clip length] window of a random video from the current list, then the
 * next one, endlessly. Two players alternate — while one plays, the other is already buffered at
 * the next clip's position — so switching is nearly instant.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PreviewScreen(vm: MainViewModel, onClose: () -> Unit, onWatchFull: (ItemEntity, Long) -> Unit) {
    val context = LocalContext.current
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val clipSec by vm.previewSeconds.collectAsStateWithLifecycle()
    val clipSecNow by rememberUpdatedState(clipSec)
    BackHandler(onBack = onClose)

    // Pool = videos in the current list, so scope / tags / search all apply.
    val pool = remember { vm.items.value.filter { it.isVideo && it.duration > 0 } }
    if (pool.isEmpty()) {
        LaunchedEffect(Unit) {
            vm.message.value = "当前列表里没有视频"
            onClose()
        }
        return
    }

    // Immersive while previewing.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val http = (context.applicationContext as EagleApp).http
    val players = remember { List(2) { previewPlayer(context, http) } }
    DisposableEffect(Unit) { onDispose { players.forEach { it.release() } } }
    val deck = remember { ClipDeck(pool) }
    val slots = remember { mutableStateListOf<Clip?>(null, null) }

    var cur by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var skipRequests by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var buffering by remember { mutableStateOf(true) }
    var clipNo by remember { mutableIntStateOf(1) }

    fun load(slot: Int) {
        val clip = deck.next(clipSecNow * 1000L)
        slots[slot] = clip
        players[slot].apply {
            playWhenReady = false
            setMediaItem(MediaItem.fromUri(vm.fileUrl(clip.item)))
            prepare()
            seekTo(clip.startMs)
        }
    }

    LaunchedEffect(Unit) {
        load(0)
        load(1)
        var failures = 0
        while (isActive) {
            val p = players[cur]
            val clip = slots[cur] ?: break
            val skipAtStart = skipRequests
            var startPos = -1L
            var failed = false
            while (isActive) {
                p.playWhenReady = !paused
                buffering = p.playbackState == Player.STATE_BUFFERING
                if (p.playerError != null) {
                    failed = true
                    break
                }
                if (skipRequests != skipAtStart || p.playbackState == Player.STATE_ENDED) break
                // count from where playback really started (keyframe seeks land a little early)
                if (p.isPlaying && startPos < 0) startPos = p.currentPosition
                if (startPos >= 0) {
                    val played = p.currentPosition - startPos
                    progress = (played.toFloat() / clip.lengthMs).coerceIn(0f, 1f)
                    if (played >= clip.lengthMs) break
                }
                delay(100)
            }
            p.playWhenReady = false
            failures = if (failed) failures + 1 else 0
            if (failures >= 5) {
                vm.message.value = "连续多个视频无法播放，请检查连接"
                onClose()
                break
            }
            val finished = cur
            cur = 1 - cur
            clipNo++
            progress = 0f
            load(finished)      // the player that just finished prepares the clip after next
            if (failed) delay(300)
        }
    }

    val clip = slots[cur]
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (clip != null && clip.item.hasThumb) {
            AsyncImage(vm.thumbUrl(clip.item), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setKeepContentOnPlayerReset(true)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                }
            },
            update = { it.player = players[cur] },
            modifier = Modifier.fillMaxSize(),
        )

        // Gestures: tap = overlay, double-tap = pause, swipe up = next clip.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { chrome = !chrome },
                        onDoubleTap = { paused = !paused },
                    )
                }
                .pointerInput(Unit) {
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onVerticalDrag = { change, d ->
                            change.consume()
                            dy += d
                        },
                        onDragEnd = { if (dy < -80.dp.toPx()) skipRequests++ },
                    )
                },
        )

        if (paused) {
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
        } else if (buffering) {
            CircularProgressIndicator(
                Modifier
                    .align(Alignment.Center)
                    .size(40.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

        // Clip progress, always visible as a hairline at the bottom.
        LinearProgressIndicator(
            progress = { progress },
            color = Color.White,
            trackColor = Color(0x33FFFFFF),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp),
        )

        AnimatedVisibility(
            visible = chrome && clip != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            if (clip == null) return@AnimatedVisibility
            val fav = clip.item.id in favorites
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "退出速览", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(clip.item.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "随机速览 · 第 $clipNo 段 · 从 ${formatMillis(clip.startMs)} 开始",
                        color = Color(0xB3FFFFFF),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(onClick = { vm.toggleFavorite(clip.item.id) }) {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (fav) "取消收藏" else "收藏",
                        tint = if (fav) FAVORITE_RED else Color.White,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chrome && clip != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (clip == null) return@AnimatedVisibility
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .navigationBarsPadding()
                    .padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text("每段", color = Color(0xB3FFFFFF), style = MaterialTheme.typography.labelMedium)
                    CLIP_LENGTHS.forEach { sec ->
                        FilterChip(
                            selected = sec == clipSec,
                            onClick = { vm.setPreviewSeconds(sec) },
                            label = { Text("${sec}秒") },
                            colors = FilterChipDefaults.filterChipColors(labelColor = Color.White),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { paused = !paused }) {
                        Icon(
                            if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (paused) "继续" else "暂停",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = { skipRequests++ }) {
                        Icon(Icons.Default.SkipNext, "下一段", tint = Color.White)
                    }
                    Text(
                        "上滑也可以切到下一段",
                        color = Color(0x99FFFFFF),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onWatchFull(clip.item, players[cur].currentPosition) }) {
                        Text("看完整", color = Color.White)
                    }
                }
            }
        }
    }
}
