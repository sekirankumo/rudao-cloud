package com.eaglelan.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.eaglelan.app.data.ItemEntity
import com.eaglelan.app.data.LoudMode
import com.eaglelan.app.data.Loudness
import com.eaglelan.app.data.LoudnessDto
import com.eaglelan.app.data.isVideo
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/**
 * One 随机速览 segment: a random window of [lengthMs] starting at [startMs]. [key] identifies the
 * page, so pages keep their player when others are removed (a trashed video).
 */
private data class Clip(
    val key: Long,
    val item: ItemEntity,
    val startMs: Long,
    val lengthMs: Long,
    val loud: LoudnessDto? = null,
)

/**
 * 按屏幕方向挑视频: when [enabled], only videos shaped like the screen are dealt. Square ones and
 * ones without a known size fit both ways.
 */
private class OrientationFilter(var enabled: Boolean, var landscape: Boolean) {
    fun fits(item: ItemEntity): Boolean {
        if (!enabled || item.width <= 0 || item.height <= 0) return true
        return if (landscape) item.width >= item.height else item.height >= item.width
    }
}

/**
 * Deals clips from the pool in shuffled order, reshuffling when it runs out. Trashed items are
 * skipped, and so are videos that don't fit the screen's orientation — unless none in the pool do.
 */
private class ClipDeck(
    private val pool: List<ItemEntity>,
    private val trashed: Set<String>,
    private val orientation: OrientationFilter,
) {
    private val queue = ArrayDeque<ItemEntity>()
    private var nextKey = 0L

    val isEmpty get() = pool.all { it.id in trashed }

    /** True when the orientation filter is on but nothing in the pool fits it (everything is dealt). */
    val noneFit get() = orientation.enabled && pool.none { it.id !in trashed && orientation.fits(it) }

    fun fits(item: ItemEntity) = noneFit || orientation.fits(item)

    fun next(lengthMs: Long): Clip {
        val key = nextKey++
        val all = noneFit
        val ok = { i: ItemEntity -> i.id !in trashed && (all || orientation.fits(i)) }
        var at = queue.indexOfFirst(ok)
        if (at < 0) {                               // nothing usable left in this round: reshuffle
            // Shuffle in every video, not only those fitting the screen now: after the phone is
            // turned, the round must still hold the other orientation's videos (not just squares).
            queue.clear()
            queue.addAll(pool.filter { it.id !in trashed }.shuffled())
            at = queue.indexOfFirst(ok)
        }
        if (at < 0) return Clip(key, pool.first(), 0, 1000)   // all trashed: caller closes
        val item = queue.removeAt(at)
        val dur = (item.duration * 1000).toLong()
        if (dur <= lengthMs + 1000) return Clip(key, item, 0, dur.coerceAtLeast(1000))   // short video: play it all
        // skip intros/outros: start somewhere in 5%..95% of the video
        val lo = (dur * 0.05).toLong()
        val hi = (dur * 0.95).toLong() - lengthMs
        return Clip(key, item, if (hi > lo) Random.nextLong(lo, hi) else 0, lengthMs)
    }
}

/** A short message over the preview; [undoId] adds 撤销 for a pending delete. */
private data class PreviewToast(val text: String, val ms: Long = 2000, val undoId: String? = null, val undoName: String = "")

private val CLIP_LENGTHS = listOf(5, 10, 15, 30)

/** Same as the desktop 随机速览 plugin: a delete waits this long so it can be undone. */
private const val UNDO_MS = 3000L

/**
 * 随机速览: random [clip length] windows of random videos from the current list, endlessly, in a
 * vertical pager — dragging shows the next (or previous) clip moving in, like a short-video feed.
 *
 * Players: a small pool covers the previous clip, the current one and the next [previewPreloadAhead]
 * (one ahead outside the LAN). Spare players sit paused at their clip's start with it buffered, so a
 * swipe starts playing at once; leaving the window frees a player for a clip further ahead.
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

    val ahead = remember { if (vm.serverIsLan) previewPreloadAhead else 1 }
    val poolSize = ahead + 2                                  // previous + current + ahead
    val players = remember { List(poolSize) { vm.newPlayer(PlayerKind.PREVIEW) } }
    DisposableEffect(Unit) { onDispose { players.forEach { it.release() } } }
    val slotClip = remember { mutableStateListOf<Long>().apply { repeat(poolSize) { add(-1L) } } }  // clip key per player
    val slotGen = remember { IntArray(poolSize) }             // invalidates a superseded loudness fetch

    val trashed = remember { HashSet<String>() }              // trashed (or pending) this session: never dealt again
    val matchOrientation by vm.previewMatchOrientation.collectAsStateWithLifecycle()
    val windowSize = LocalWindowInfo.current.containerSize
    val landscape = windowSize.width > windowSize.height
    val orientation = remember { OrientationFilter(matchOrientation, landscape) }
    val deck = remember { ClipDeck(pool, trashed, orientation) }
    val history = remember { mutableStateListOf<Clip>().apply { repeat(ahead + 1) { add(deck.next(clipSec * 1000L)) } } }
    val pager = rememberPagerState { history.size }
    val stars = remember { mutableStateMapOf<String, Int>() }   // ratings changed this session
    LaunchedEffect(Unit) { vm.checkConnection() }             // refresh 管理员 for where the phone is right now
    val scope = rememberCoroutineScope()

    var curSlot by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var buffering by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }             // re-reads players' hasFrame for the poster
    var whole by remember { mutableStateOf(false) }           // 看全片: play the current video to its end
    var wholePos by remember { mutableLongStateOf(0L) }
    var wholeDur by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf<Float?>(null) }  // slider position while dragging
    var failures by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<PreviewToast?>(null) }
    LaunchedEffect(toast) {
        val t = toast ?: return@LaunchedEffect
        delay(t.ms)
        if (toast === t) toast = null
    }

    fun newClip(): Clip = deck.next(clipSecNow * 1000L)

    fun gainFor(clip: Clip, wholeVideo: Boolean, mode: LoudMode) =
        Loudness.volume(
            if (wholeVideo) Loudness.gainDb(clip.loud, null, 0, mode)
            else Loudness.gainDb(clip.loud, clip.startMs, clip.lengthMs, mode),
            mode,
        )

    /** Loads [clip] into player [slot], paused at its start. Loudness follows when the server answers. */
    fun assign(slot: Int, clip: Clip) {
        val gen = ++slotGen[slot]
        val p = players[slot]
        p.playWhenReady = false
        p.volume = gainFor(clip, false, loudModeNow)
        p.setUrl(vm.fileUrl(clip.item))
        p.seekTo(clip.startMs)
        slotClip[slot] = clip.key
        if (clip.loud == null) scope.launch {
            val loud = vm.loudness(clip.item.id) ?: return@launch
            if (slotGen[slot] != gen) return@launch
            val i = history.indexOfFirst { it.key == clip.key }
            if (i >= 0) history[i] = history[i].copy(loud = loud)
            p.volume = gainFor(clip.copy(loud = loud), whole && slot == curSlot, loudModeNow)
        }
    }

    /** Makes sure the clips around [index] exist and have players: current first, then ahead, then behind. */
    fun ensureWindow(index: Int) {
        while (history.size < index + ahead + 1) history.add(newClip())
        val order = buildList {
            add(index)
            for (i in 1..ahead) add(index + i)
            if (index > 0) add(index - 1)
        }.filter { it < history.size }
        val wanted = order.map { history[it].key }.toSet()
        val free = ArrayDeque((0 until poolSize).filter { slotClip[it] !in wanted })
        for (i in order) {
            val c = history[i]
            if (c.key in slotClip) continue
            assign(free.removeFirstOrNull() ?: break, c)
        }
    }

    // Turned the phone (or the setting): keep the clip on screen, re-deal the upcoming ones that no
    // longer fit. The previous clip stays, so 上一段 still goes back to it.
    LaunchedEffect(matchOrientation, landscape) {
        orientation.enabled = matchOrientation
        orientation.landscape = landscape
        if (deck.noneFit) {
            toast = PreviewToast("当前列表里没有${if (landscape) "横向" else "竖向"}的视频，先放全部", 3000)
            return@LaunchedEffect
        }
        val cur = pager.currentPage
        var changed = false
        for (i in cur + 1 until history.size) {
            if (!deck.fits(history[i].item)) {
                history[i] = newClip()
                changed = true
            }
        }
        if (changed) ensureWindow(cur)
    }

    // Whole-video mode uses one gain from the video's integrated loudness, like the plugin.
    LaunchedEffect(loudMode, whole) {
        players.forEachIndexed { s, p ->
            val c = history.firstOrNull { it.key == slotClip[s] } ?: return@forEachIndexed
            p.volume = gainFor(c, whole && s == curSlot, loudMode)
        }
    }

    // ---- actions shared by buttons, gestures and keys ----------------------------------------
    fun go(delta: Int) {
        whole = false
        val target = pager.currentPage + delta
        if (target < 0) {
            toast = PreviewToast("已经是第一段")
            return
        }
        scope.launch {
            while (history.size <= target) history.add(newClip())
            pager.animateScrollToPage(target)
        }
    }

    fun setWhole(on: Boolean) {
        if (on == whole || history.getOrNull(pager.currentPage) == null) return
        if (on) {
            whole = true
            paused = false
            toast = PreviewToast("看全片：播完或点「返回速览」回到速览")
        } else {
            go(1)                                   // back to previewing with the next clip
        }
    }

    fun seekBy(sec: Int) {
        val p = players[curSlot]
        val dur = p.duration.takeIf { it > 0 } ?: wholeDur
        p.seekTo((p.currentPosition + sec * 1000L).coerceIn(0, (dur - 500).coerceAtLeast(0)))
    }

    fun rate(n: Int) {
        val item = history.getOrNull(pager.currentPage)?.item ?: return
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
        val index = pager.currentPage
        val item = history.getOrNull(index)?.item ?: return
        if (!canManage || item.id in trashed) return
        trashed += item.id
        vm.trashLater(item.id, UNDO_MS) { trashed -= item.id }
        toast = PreviewToast("已移入回收站：${item.name}", UNDO_MS, undoId = item.id, undoName = item.name)
        whole = false
        if (deck.isEmpty) {
            vm.message.value = "列表里的视频都已放入回收站"
            onClose()
            return
        }
        // Drop its pages; the next clip slides into this position (already buffered on its player).
        val before = (0 until index).count { history[it].item.id == item.id }
        history.removeAll { it.item.id == item.id }
        val newIndex = index - before
        while (history.size < newIndex + ahead + 1) history.add(newClip())
        if (before > 0) scope.launch { pager.scrollToPage(newIndex) }
    }

    fun undoTrash() {
        val t = toast?.takeIf { it.undoId != null } ?: return
        if (vm.undoTrash(t.undoId!!)) {
            trashed -= t.undoId                     // back in the deck; it can come up again later
            toast = PreviewToast("已撤销：${t.undoName}")
        }
    }

    // Back: leaves 看全片 first, then the preview (the plugin's Esc).
    PlatformBackHandler(onBack = { if (whole) setWhole(false) else onClose() })

    // ---- playback: whichever clip has settled on screen plays; the rest wait, buffered ----------
    LaunchedEffect(Unit) {
        snapshotFlow { history.getOrNull(pager.settledPage)?.key }
            .distinctUntilChanged()
            .collectLatest { key ->
                val index = history.indexOfFirst { it.key == key }
                if (key == null || index < 0) return@collectLatest
                ensureWindow(index)
                val slot = slotClip.indexOf(key)
                if (slot < 0) return@collectLatest
                curSlot = slot
                players.forEachIndexed { s, p -> if (s != slot) p.playWhenReady = false }
                val clip = history[index]
                val p = players[slot]
                if (abs(p.currentPosition - clip.startMs) > 1500) p.seekTo(clip.startMs)
                p.volume = gainFor(clip, false, loudModeNow)
                whole = false
                progress = 0f
                var startPos = -1L
                var failed = false
                try {
                    while (true) {
                        p.playWhenReady = !paused
                        buffering = p.isBuffering
                        tick++
                        if (p.hasError) {
                            failed = true
                            break
                        }
                        if (p.isEnded) break
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
                } finally {
                    p.playWhenReady = false
                    if (slotClip[slot] == key) p.seekTo(clip.startMs)   // ready again if the user swipes back
                }
                whole = false
                failures = if (failed) failures + 1 else 0
                if (failures >= 5) {
                    vm.message.value = "连续多个视频无法播放，请检查连接"
                    onClose()
                    return@collectLatest
                }
                go(1)                                   // clip done: slide to the next one
                awaitCancellation()                     // until the next clip settles
            }
    }

    val clip = history.getOrNull(pager.currentPage)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VerticalPager(
            state = pager,
            key = { history[it].key },
            beyondViewportPageCount = 1,                 // the next page's surface exists before it's dragged in
            userScrollEnabled = !whole,                  // 看全片: the slider owns drags
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val c = history.getOrNull(page) ?: return@VerticalPager
            val slot = slotClip.indexOf(c.key)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    // tap = overlay, double-tap = pause (swipes belong to the pager)
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
                                go(if (dy > 0) 1 else -1)
                            }
                        }
                    },
            ) {
                if (c.item.hasThumb) {
                    AsyncImage(vm.thumbUrl(c.item), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                if (slot >= 0) {
                    val p = players[slot]
                    PagerVideoSurface(p, keepScreenOn = true, modifier = Modifier.fillMaxSize())
                    tick
                    if (videoSurfaceCoversPoster && !p.hasFrame && c.item.hasThumb) {
                        AsyncImage(vm.thumbUrl(c.item), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
            }
        }

        // Keys, as in the desktop plugin: → ↓ next (看全片: +10 s), ← ↑ previous (−10 s), space pause,
        // Enter 看全片 / back, 1–5 rate (0 clears), Delete trash, Z undo.
        KeyHandler { e ->
            when (e.key) {
                Key.DirectionRight -> if (whole) seekBy(10) else go(1)
                Key.DirectionLeft -> if (whole) seekBy(-10) else go(-1)
                Key.DirectionDown, Key.PageDown -> go(1)
                Key.DirectionUp, Key.PageUp -> go(-1)
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
        } else if (buffering && !pager.isScrollInProgress) {
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
                        else "随机速览 · 第 ${pager.currentPage + 1} 段 · 从 ${formatMillis(clip.startMs)} 开始",
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
                                val d = players[curSlot].duration.takeIf { it > 0 } ?: wholeDur
                                players[curSlot].seekTo((f * d).toLong())
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
                    IconButton(onClick = { go(-1) }) {
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
                    IconButton(onClick = { go(1) }) {
                        Icon(Icons.Default.SkipNext, "下一段", tint = MediaColors.Text)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { setWhole(!whole) },
                        shape = AppShapes.Full,
                        colors = ButtonDefaults.textButtonColors(containerColor = MediaColors.Chip, contentColor = MediaColors.Text),
                    ) {
                        Text(if (whole) "返回速览" else "看全片")
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
