package com.eaglelan.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.eaglelan.app.data.LoudMode
import com.eaglelan.app.data.Loudness
import com.eaglelan.app.data.LoudnessDto
import com.eaglelan.app.data.isVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** One 随机速览 segment: a random window of [lengthMs] starting at [startMs]. */
private data class Clip(val item: ItemEntity, val startMs: Long, val lengthMs: Long, val loud: LoudnessDto? = null)

/** Deals clips from the pool in shuffled order, reshuffling when it runs out. Trashed items are skipped. */
private class ClipDeck(private val pool: List<ItemEntity>, private val trashed: Set<String>) {
    private val queue = ArrayDeque<ItemEntity>()

    val isEmpty get() = pool.all { it.id in trashed }

    fun next(lengthMs: Long): Clip {
        var item: ItemEntity
        do {
            if (queue.isEmpty()) queue.addAll(pool.filter { it.id !in trashed }.shuffled())
            item = queue.removeFirstOrNull() ?: return Clip(pool.first(), 0, 1000)   // all trashed: caller closes
        } while (item.id in trashed)
        val dur = (item.duration * 1000).toLong()
        if (dur <= lengthMs + 1000) return Clip(item, 0, dur.coerceAtLeast(1000))   // short video: play it all
        // skip intros/outros: start somewhere in 5%..95% of the video
        val lo = (dur * 0.05).toLong()
        val hi = (dur * 0.95).toLong() - lengthMs
        return Clip(item, if (hi > lo) Random.nextLong(lo, hi) else 0, lengthMs)
    }
}

/** A short message over the preview; [undoId] adds 撤销 for a pending delete. */
private data class PreviewToast(val text: String, val ms: Long = 2000, val undoId: String? = null, val undoName: String = "")

private val CLIP_LENGTHS = listOf(5, 10, 15, 30)

/** Same as the desktop 随机速览 plugin: a delete waits this long so it can be undone. */
private const val UNDO_MS = 3000L

/**
 * 随机速览: plays a random [clip length] window of a random video from the current list, then the
 * next one, endlessly. Two players alternate — while one plays, the other is already buffered at
 * the next clip's position — so switching is nearly instant.
 *
 * Works like the desktop plugin: 看全片 keeps playing the current video to its end right here
 * (± 10 s, drag to seek), then previewing carries on; 管理员 tokens on the LAN can rate (1–5,
 * the same star again clears it) and move a video to the trash, which skips it at once and can be
 * undone for 3 seconds.
 */
@Composable
fun PreviewScreen(vm: MainViewModel, onClose: () -> Unit) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val clipSec by vm.previewSeconds.collectAsStateWithLifecycle()
    val clipSecNow by rememberUpdatedState(clipSec)
    val loudMode by vm.loudMode.collectAsStateWithLifecycle()
    val loudModeNow by rememberUpdatedState(loudMode)
    val canManage by vm.canManage.collectAsStateWithLifecycle()

    // Pool = videos in the current list, so scope / tags / ratings / search all apply.
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
    val trashed = remember { HashSet<String>() }        // trashed (or pending) this session: never dealt again
    val deck = remember { ClipDeck(pool, trashed) }
    val stars = remember { mutableStateMapOf<String, Int>() }   // ratings changed this session
    LaunchedEffect(Unit) { vm.checkConnection() }       // refresh 管理员 for where the phone is right now
    val slots = remember { mutableStateListOf<Clip?>(null, null) }
    val scope = rememberCoroutineScope()

    var cur by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var buffering by remember { mutableStateOf(true) }
    var clipNo by remember { mutableIntStateOf(1) }
    var hasFrame by remember { mutableStateOf(false) }
    var whole by remember { mutableStateOf(false) }          // 看全片: play the current video to its end
    var wholePos by remember { mutableLongStateOf(0L) }
    var wholeDur by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf<Float?>(null) } // slider position while dragging
    var toast by remember { mutableStateOf<PreviewToast?>(null) }
    LaunchedEffect(toast) {
        val t = toast ?: return@LaunchedEffect
        delay(t.ms)
        if (toast === t) toast = null
    }

    // Clips are appended to `history` as they are needed, so 上一段 can step back through them.
    val history = remember { mutableStateListOf<Clip>() }
    val slotIndex = remember { intArrayOf(-1, -1) }   // which history index each player holds
    val slotGen = remember { intArrayOf(0, 0) }       // invalidates a superseded prepare
    var pos by remember { mutableIntStateOf(0) }
    var pendingNav by remember { mutableIntStateOf(0) }   // +1 下一段, -1 上一段

    fun newClip(): Clip = deck.next(clipSecNow * 1000L)

    fun gainFor(clip: Clip, wholeVideo: Boolean, mode: LoudMode) =
        Loudness.volume(
            if (wholeVideo) Loudness.gainDb(clip.loud, null, 0, mode)
            else Loudness.gainDb(clip.loud, clip.startMs, clip.lengthMs, mode),
            mode,
        )

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
        p.volume = gainFor(clip, false, loudModeNow)
        p.playWhenReady = false
        p.setUrl(vm.fileUrl(clip.item))
        p.seekTo(clip.startMs)
        slotIndex[slot] = index
    }

    // Whole-video mode uses one gain from the video's integrated loudness, like the plugin.
    LaunchedEffect(loudMode, whole) {
        slots.forEachIndexed { i, c ->
            if (c != null) players[i].volume = gainFor(c, whole && i == cur, loudMode)
        }
    }

    // ---- actions shared by buttons, gestures and keys ----------------------------------------
    fun setWhole(on: Boolean) {
        if (on == whole || slots[cur] == null) return
        whole = on
        if (on) {
            paused = false
            toast = PreviewToast("看全片：播完或点「返回速览」回到速览")
        } else {
            pendingNav = 1                          // back to previewing with the next clip
        }
    }

    fun seekBy(sec: Int) {
        val p = players[cur]
        val dur = p.duration.takeIf { it > 0 } ?: wholeDur
        p.seekTo((p.currentPosition + sec * 1000L).coerceIn(0, (dur - 500).coerceAtLeast(0)))
    }

    fun rate(n: Int) {
        val item = slots[cur]?.item ?: return
        if (!canManage) return
        val old = stars[item.id] ?: item.star
        val new = if (n == old) 0 else n            // the same number again clears it, like the plugin
        stars[item.id] = new
        toast = PreviewToast(if (new == 0) "已清除评分" else "评分：" + "★".repeat(new))
        scope.launch {
            vm.setStar(item.id, new)?.let { err ->
                stars[item.id] = old
                toast = PreviewToast("评分保存失败：$err", 3000)
            }
        }
    }

    fun trashCurrent() {
        val item = slots[cur]?.item ?: return
        if (!canManage || item.id in trashed) return
        trashed += item.id
        vm.trashLater(item.id, UNDO_MS) { trashed -= item.id }
        toast = PreviewToast("已移入回收站：${item.name}", UNDO_MS, undoId = item.id, undoName = item.name)
        whole = false
        pendingNav = 1
    }

    fun undoTrash() {
        val t = toast?.takeIf { it.undoId != null } ?: return
        if (vm.undoTrash(t.undoId!!)) {
            trashed -= t.undoId
            toast = PreviewToast("已撤销：${t.undoName}")
        }
    }

    // Back: leaves 看全片 first, then the preview (the plugin's Esc).
    PlatformBackHandler(onBack = { if (whole) setWhole(false) else onClose() })

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
                if (whole) {
                    val d = p.duration.takeIf { it > 0 } ?: (clip.item.duration * 1000).toLong()
                    wholeDur = d
                    wholePos = p.currentPosition
                    progress = if (d > 0) (wholePos.toFloat() / d).coerceIn(0f, 1f) else 0f
                } else {
                    // count from where playback really started (keyframe seeks land a little early)
                    if (p.isPlaying && startPos < 0) startPos = p.currentPosition
                    if (startPos >= 0) {
                        val played = p.currentPosition - startPos
                        progress = (played.toFloat() / clip.lengthMs).coerceIn(0f, 1f)
                        if (played >= clip.lengthMs) break
                    }
                }
                delay(100)
            }
            p.playWhenReady = false
            whole = false

            failures = if (failed) failures + 1 else 0
            if (failures >= 5) {
                vm.message.value = "连续多个视频无法播放，请检查连接"
                onClose()
                break
            }
            if (deck.isEmpty) {
                vm.message.value = "列表里的视频都已放入回收站"
                onClose()
                break
            }

            val nav = pendingNav
            pendingNav = 0
            val step = if (nav < 0) -1 else 1
            var target = pos + step
            // Going back skips clips trashed meanwhile; going forward replaces them with fresh ones.
            if (step < 0) while (target >= 0 && history[target].item.id in trashed) target--
            if (target < 0) {
                toast = PreviewToast("已经是第一段")
                continue                        // replay the current clip from its start
            }
            pos = target
            while (history.size <= pos) history.add(newClip())
            if (history[pos].item.id in trashed) {
                history[pos] = newClip()
                for (i in 0..1) if (slotIndex[i] == pos) slotIndex[i] = -1   // that player holds the old clip
            }
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

        // Keys, as in the desktop plugin: → ↓ next (看全片: +10 s), ← ↑ previous (−10 s), space pause,
        // Enter 看全片 / back, 1–5 rate (0 clears), Delete trash, Z undo.
        KeyHandler { e ->
            when (e.key) {
                Key.DirectionRight -> if (whole) seekBy(10) else pendingNav = 1
                Key.DirectionLeft -> if (whole) seekBy(-10) else pendingNav = -1
                Key.DirectionDown, Key.PageDown -> pendingNav = 1
                Key.DirectionUp, Key.PageUp -> pendingNav = -1
                Key.Spacebar, Key.K -> paused = !paused
                Key.Enter, Key.NumPadEnter -> setWhole(!whole)
                Key.Escape -> if (whole) setWhole(false) else onClose()
                Key.Delete, Key.Backspace -> trashCurrent()
                Key.Z -> undoTrash()
                Key.One, Key.NumPad1 -> rate(1)
                Key.Two, Key.NumPad2 -> rate(2)
                Key.Three, Key.NumPad3 -> rate(3)
                Key.Four, Key.NumPad4 -> rate(4)
                Key.Five, Key.NumPad5 -> rate(5)
                Key.Zero, Key.NumPad0 -> rate(0)
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
                tint = MediaColors.Text2,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(MediaColors.Scrim, CircleShape)
                    .padding(12.dp),
            )
        } else if (buffering) {
            CircularProgressIndicator(
                Modifier
                    .align(Alignment.Center)
                    .size(40.dp),
                color = MediaColors.Text,
                strokeWidth = 3.dp,
            )
        }

        // Clip progress, always visible as a hairline at the bottom.
        LinearProgressIndicator(
            progress = { progress },
            color = MediaColors.Text,
            trackColor = MediaColors.Track,
            drawStopIndicator = {},
            gapSize = 0.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp),
        )

        AnimatedVisibility(
            visible = chrome && clip != null,
            enter = fadeIn(tween(Motion.FADE, easing = Motion.Easing)),
            exit = fadeOut(tween(Motion.FADE, easing = Motion.Easing)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            if (clip == null) return@AnimatedVisibility
            val fav = clip.item.id in favorites
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(MediaColors.ScrimStrong, Color.Transparent)))
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 28.dp),
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "退出速览", tint = MediaColors.Text) }
                Column(Modifier.weight(1f)) {
                    Text(
                        clip.item.name,
                        color = MediaColors.Text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (whole) "全片 · ${formatMillis(wholePos)} / ${formatMillis(wholeDur)}"
                        else "随机速览 · 第 $clipNo 段 · 从 ${formatMillis(clip.startMs)} 开始",
                        color = MediaColors.Text2,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (canManage) {
                    IconButton(onClick = ::trashCurrent) {
                        Icon(Icons.Default.Delete, contentDescription = "移入回收站", tint = MediaColors.Text)
                    }
                }
                IconButton(onClick = { vm.toggleFavorite(clip.item.id) }) {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (fav) "取消收藏" else "收藏",
                        tint = if (fav) AppColors.Favorite else MediaColors.Text,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chrome && clip != null,
            enter = fadeIn(tween(Motion.FADE, easing = Motion.Easing)),
            exit = fadeOut(tween(Motion.FADE, easing = Motion.Easing)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (clip == null) return@AnimatedVisibility
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MediaColors.ScrimStrong)))
                    .navigationBarsPadding()
                    .padding(start = 8.dp, end = 8.dp, top = 32.dp, bottom = 12.dp),
            ) {
                if (canManage) {
                    val rating = stars[clip.item.id] ?: clip.item.star
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { n ->
                            IconButton(onClick = { rate(n) }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    if (n <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "$n 星",
                                    tint = if (n <= rating) AppColors.Star else MediaColors.Text2,
                                )
                            }
                        }
                    }
                }
                if (whole) {
                    Slider(
                        value = seeking ?: progress,
                        onValueChange = { seeking = it },
                        onValueChangeFinished = {
                            seeking?.let { f ->
                                val d = players[cur].duration.takeIf { it > 0 } ?: wholeDur
                                players[cur].seekTo((f * d).toLong())
                            }
                            seeking = null
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MediaColors.Text,
                            activeTrackColor = MediaColors.Text,
                            inactiveTrackColor = MediaColors.Track,
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text("每段", color = MediaColors.Text2, style = MaterialTheme.typography.labelMedium)
                        CLIP_LENGTHS.forEach { sec ->
                            FilterChip(
                                selected = sec == clipSec,
                                onClick = { vm.setPreviewSeconds(sec) },
                                label = { Text("${sec}秒") },
                                shape = AppShapes.Full,
                                colors = FilterChipDefaults.filterChipColors(labelColor = MediaColors.Text),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = sec == clipSec,
                                    borderColor = MediaColors.Track,
                                    selectedBorderColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pendingNav = -1 }) {
                        Icon(Icons.Default.SkipPrevious, "上一段", tint = MediaColors.Text)
                    }
                    if (whole) {
                        IconButton(onClick = { seekBy(-10) }) {
                            Icon(Icons.Default.Replay10, "快退 10 秒", tint = MediaColors.Text)
                        }
                    }
                    IconButton(onClick = { paused = !paused }) {
                        Icon(
                            if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (paused) "继续" else "暂停",
                            tint = MediaColors.Text,
                        )
                    }
                    if (whole) {
                        IconButton(onClick = { seekBy(10) }) {
                            Icon(Icons.Default.Forward10, "快进 10 秒", tint = MediaColors.Text)
                        }
                    }
                    IconButton(onClick = { pendingNav = 1 }) {
                        Icon(Icons.Default.SkipNext, "下一段", tint = MediaColors.Text)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { setWhole(!whole) },
                        shape = AppShapes.Full,
                        colors = ButtonDefaults.textButtonColors(containerColor = MediaColors.Chip, contentColor = MediaColors.Text),
                    ) {
                        Text(if (whole) "↩ 返回速览" else "▶ 看全片")
                    }
                }
            }
        }

        // Toast (with 撤销 after a delete), above the bottom controls.
        toast?.let { t ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 160.dp)
                    .widthIn(max = 420.dp)
                    .background(MediaColors.ScrimStrong, AppShapes.Full)
                    .padding(start = 16.dp, end = if (t.undoId != null) 4.dp else 16.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    t.text,
                    color = MediaColors.Text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (t.undoId != null) {
                    TextButton(onClick = ::undoTrash) { Text("撤销", color = AppColors.Brand) }
                }
            }
        }
    }
}
