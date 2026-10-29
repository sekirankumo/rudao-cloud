package com.eaglelan.app.ui

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
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.eaglelan.app.data.ItemEntity
import com.eaglelan.app.data.Loudness
import com.eaglelan.app.data.LoudnessDto
import com.eaglelan.app.data.isVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** One 随机速览 segment: a random window of [lengthMs] starting at [startMs]. */
private data class Clip(val item: ItemEntity, val startMs: Long, val lengthMs: Long, val loud: LoudnessDto? = null)

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

private val CLIP_LENGTHS = listOf(5, 10, 15, 30)

/**
 * 随机速览: plays a random [clip length] window of a random video from the current list, then the
 * next one, endlessly. Two players alternate — while one plays, the other is already buffered at
 * the next clip's position — so switching is nearly instant.
 */
@Composable
fun PreviewScreen(vm: MainViewModel, onClose: () -> Unit, onWatchFull: (ItemEntity, Long) -> Unit) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val clipSec by vm.previewSeconds.collectAsStateWithLifecycle()
    val clipSecNow by rememberUpdatedState(clipSec)
    val loudMode by vm.loudMode.collectAsStateWithLifecycle()
    val loudModeNow by rememberUpdatedState(loudMode)
    PlatformBackHandler(onBack = onClose)

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
    ImmersiveMode()

    val players = remember { List(2) { vm.newPlayer(PlayerKind.PREVIEW) } }
    DisposableEffect(Unit) { onDispose { players.forEach { it.release() } } }
    val deck = remember { ClipDeck(pool) }
    val slots = remember { mutableStateListOf<Clip?>(null, null) }
    val scope = rememberCoroutineScope()

    var cur by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var buffering by remember { mutableStateOf(true) }
    var clipNo by remember { mutableIntStateOf(1) }
    var hasFrame by remember { mutableStateOf(false) }

    // Clips are appended to `history` as they are needed, so 上一段 can step back through them.
    val history = remember { mutableStateListOf<Clip>() }
    val slotIndex = remember { intArrayOf(-1, -1) }   // which history index each player holds
    val slotGen = remember { intArrayOf(0, 0) }       // invalidates a superseded prepare
    var pos by remember { mutableIntStateOf(0) }
    var pendingNav by remember { mutableIntStateOf(0) }   // +1 下一段, -1 上一段

    fun newClip(): Clip = deck.next(clipSecNow * 1000L)

    /** Load history[index] into a player. Loudness is fetched here, before the clip is needed. */
    suspend fun prepareInto(slot: Int, index: Int) {
        if (slotIndex[slot] == index) return
        val gen = ++slotGen[slot]
        val base = history.getOrNull(index) ?: return
        val loud = vm.loudness(base.item.id)
        if (slotGen[slot] != gen) return            // superseded by a newer prepare
        val clip = base.copy(loud = loud)
        history[index] = clip
        slots[slot] = clip
        val p = players[slot]
        p.volume = Loudness.volume(Loudness.gainDb(clip.loud, clip.startMs, clip.lengthMs, loudModeNow), loudModeNow)
        p.playWhenReady = false
        p.setUrl(vm.fileUrl(clip.item))
        p.seekTo(clip.startMs)
        slotIndex[slot] = index
    }

    LaunchedEffect(loudMode) {
        slots.forEachIndexed { i, c ->
            if (c != null) players[i].volume = Loudness.volume(Loudness.gainDb(c.loud, c.startMs, c.lengthMs, loudMode), loudMode)
        }
    }

    LaunchedEffect(Unit) {
        history.add(newClip())
        prepareInto(0, 0)
        cur = 0
        history.add(newClip())          // preloaded on the spare player while the first clip plays
        prepareInto(1, 1)

        var failures = 0
        while (isActive) {
            val index = pos
            val clip = history.getOrNull(index) ?: break
            if (slotIndex[cur] != index) {       // came back to a clip the other player may already hold
                val other = 1 - cur
                if (slotIndex[other] != index) prepareInto(other, index)
                cur = other
            }
            val p = players[cur]
            // Prepare the following clip on the idle player while this one plays, so 下一段 is instant.
            if (history.size <= index + 1) history.add(newClip())
            val spare = 1 - cur
            if (slotIndex[spare] != index + 1) scope.launch { prepareInto(spare, index + 1) }
            p.seekTo(clip.startMs)
            progress = 0f
            var startPos = -1L
            var failed = false
            p.playWhenReady = !paused
            while (isActive) {
                p.playWhenReady = !paused
                buffering = p.isBuffering
                hasFrame = p.hasFrame
                if (p.hasError) {
                    failed = true
                    break
                }
                if (pendingNav != 0 || p.isEnded) break
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

            val nav = pendingNav
            pendingNav = 0
            val target = if (nav != 0) pos + nav else pos + 1
            if (target < 0) {
                vm.message.value = "已经是第一段"
                continue                        // replay the current clip from its start
            }
            pos = target
            while (history.size <= pos) history.add(newClip())
            clipNo = pos + 1
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
        VideoSurface(
            players[cur],
            keepScreenOn = true,
            keepContentOnPlayerReset = true,
            modifier = Modifier.fillMaxSize(),
        )
        if (videoSurfaceCoversPoster && !hasFrame && clip != null && clip.item.hasThumb) {
            AsyncImage(vm.thumbUrl(clip.item), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }

        // Keyboard: ↓ / → / PageDown = 下一段, ↑ / ← / PageUp = 上一段, space = pause.
        KeyHandler { e ->
            when (e.key) {
                Key.DirectionDown, Key.DirectionRight, Key.PageDown -> pendingNav = 1
                Key.DirectionUp, Key.DirectionLeft, Key.PageUp -> pendingNav = -1
                Key.Spacebar, Key.K -> paused = !paused
                else -> return@KeyHandler false
            }
            true
        }

        // Gestures: tap = overlay, double-tap = pause, swipe up = next clip, swipe down = previous.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { chrome = !chrome },
                        onDoubleTap = { paused = !paused },
                    )
                }
                // Mouse wheel: one notch down = next clip, up = previous.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var last = 0L
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val dy = event.changes.first().scrollDelta.y
                            event.changes.forEach { it.consume() }
                            val now = nowMs()
                            if (dy == 0f || now - last < 400) continue
                            last = now
                            pendingNav = if (dy > 0) 1 else -1
                        }
                    }
                }
                .pointerInput(Unit) {
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onVerticalDrag = { change, d ->
                            change.consume()
                            dy += d
                        },
                        onDragEnd = {
                            val threshold = 80.dp.toPx()
                            if (dy < -threshold) pendingNav = 1
                            else if (dy > threshold) pendingNav = -1
                        },
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
                    IconButton(onClick = { pendingNav = -1 }) {
                        Icon(Icons.Default.SkipPrevious, "上一段", tint = Color.White)
                    }
                    IconButton(onClick = { paused = !paused }) {
                        Icon(
                            if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (paused) "继续" else "暂停",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = { pendingNav = 1 }) {
                        Icon(Icons.Default.SkipNext, "下一段", tint = Color.White)
                    }
                    Text(
                        "上滑下一段 / 下滑上一段",
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
