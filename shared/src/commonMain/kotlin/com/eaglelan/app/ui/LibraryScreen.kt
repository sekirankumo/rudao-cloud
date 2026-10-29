package com.eaglelan.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.eaglelan.app.data.Filter
import com.eaglelan.app.data.FolderDto
import com.eaglelan.app.data.ItemEntity
import com.eaglelan.app.data.Sort
import com.eaglelan.app.data.aspectRatio
import com.eaglelan.app.data.isVideo
import com.eaglelan.app.data.tagValueOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Where each visible thumbnail is on screen (root coordinates), for the viewer's open / close animation. */
class ThumbBounds {
    val map = HashMap<String, Rect>()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: MainViewModel,
    bounds: ThumbBounds,
    onOpen: (Int) -> Unit,
    onSettings: () -> Unit,
    onPreview: () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val thumbWidth by vm.thumbWidth.collectAsStateWithLifecycle()
    val conn by vm.conn.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val favToast by vm.favToast.collectAsStateWithLifecycle()
    val selectedTags by vm.selectedTags.collectAsStateWithLifecycle()
    val seed by vm.shuffleSeed.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val locate by vm.locate.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyStaggeredGridState()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var pinching by remember { mutableStateOf(false) }

    // The top bar floats over the grid and slides away while scrolling down (back on scrolling up).
    var barHeight by remember { mutableIntStateOf(0) }
    var barOffset by remember { mutableFloatStateOf(0f) }
    val barScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                barOffset = (barOffset + available.y).coerceIn(-barHeight.toFloat(), 0f)
                return Offset.Zero
            }
        }
    }
    if (searching && barOffset != 0f) barOffset = 0f

    // Connection indicator: re-check every 30s while the screen is visible.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.checkConnection()
                delay(30_000)
            }
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }
    LaunchedEffect(favToast) {
        val t = favToast ?: return@LaunchedEffect
        snackbar.currentSnackbarData?.dismiss()
        val r = snackbar.showSnackbar(
            if (t.added) "已收藏" else "已取消收藏",
            actionLabel = "撤销",
            duration = SnackbarDuration.Short,
        )
        if (r == SnackbarResult.ActionPerformed) vm.undoFavorite(t)
        vm.favToast.value = null
    }
    LaunchedEffect(filter, sort, selectedTags, seed) {
        gridState.scrollToItem(0)
        barOffset = 0f
    }
    // Back from the viewer: bring the last item watched into view (the close animation flies to it).
    LaunchedEffect(locate) {
        val id = locate ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            val info = gridState.layoutInfo
            val cell = info.visibleItemsInfo.firstOrNull { it.index == index }
            val top = info.viewportStartOffset + barHeight
            val bottom = info.viewportEndOffset
            val fullyVisible = cell != null && cell.offset.y >= top && cell.offset.y + cell.size.height <= bottom
            if (!fullyVisible) {
                gridState.scrollToItem(index)
                gridState.scrollBy(-(info.viewportSize.height / 3f))
            }
            barOffset = 0f
        }
        vm.locate.value = null
    }

    // Tablets / wide windows: pinned sidebar, and more columns at the same thumbnail size.
    val screenWidth = screenWidthDp()
    val wide = screenWidth >= 840
    val gridWidth = if (wide) screenWidth - SIDEBAR_WIDTH else screenWidth
    // Stepless size: the user picks a target thumbnail width; fit as many columns as it allows.
    val gridColumns = (gridWidth / thumbWidth).roundToInt().coerceIn(1, 12)
    val barHeightDp = with(density) { barHeight.toDp() }

    val body: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .nestedScroll(barScroll),
        ) {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = syncing,
                onRefresh = vm::refresh,
                state = pullState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = syncing,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = barHeightDp),
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    // Two-finger pinch resizes thumbnails; single-finger scrolling is untouched.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var zoomed = false
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoom = event.calculateZoom()
                                    if (zoom != 1f) vm.setThumbWidth(vm.thumbWidth.value * zoom, persist = false)
                                    event.changes.forEach { it.consume() }
                                    zoomed = true
                                    pinching = true
                                }
                            } while (event.changes.any { it.pressed })
                            if (zoomed) vm.setThumbWidth(vm.thumbWidth.value)
                            pinching = false
                        }
                    }
                    // Mouse: Ctrl + wheel resizes thumbnails, like the pinch.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                                    val dy = event.changes.first().scrollDelta.y
                                    vm.setThumbWidth(vm.thumbWidth.value * 1.1f.pow(-dy))
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
            ) {
                val firstSync = syncing && totals.all == 0
                if (firstSync) {
                    FirstSync(top = barHeightDp)
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(gridColumns),
                        state = gridState,
                        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = barHeightDp + 6.dp, bottom = 96.dp),
                        verticalItemSpacing = 6.dp,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                            ThumbCell(
                                item = item,
                                thumbUrl = vm.thumbUrl(item),
                                showName = gridWidth / gridColumns >= 110,
                                favorite = item.id in favorites,
                                progress = progress[item.id],
                                bounds = bounds,
                                onClick = { onOpen(index) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.longPressFavorite(item.id)
                                },
                            )
                        }
                    }
                }
                if (pinching) {
                    Text(
                        "$gridColumns 列",
                        color = MediaColors.Text,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(MediaColors.Scrim, AppShapes.Medium)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (items.isEmpty() && !syncing) {
                    EmptyState(
                        vm = vm,
                        filtered = search.isNotBlank() || selectedTags.isNotEmpty(),
                        filter = filter,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            // ---- floating top bar ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = barOffset }
                    .onSizeChanged { barHeight = it.height }
                    .background(AppColors.Surface1.copy(alpha = 0.94f))
                    .statusBarsPadding(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(56.dp).padding(horizontal = 4.dp),
                ) {
                    if (!wide && !searching) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "菜单")
                        }
                    }
                    if (searching) {
                        TextField(
                            value = search,
                            onValueChange = { vm.search.value = it },
                            placeholder = { Text("搜索名称 / 标签 / 注释") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        IconButton(onClick = {
                            searching = false
                            vm.search.value = ""
                        }) { Icon(Icons.Default.Close, "关闭搜索") }
                    } else {
                        Column(Modifier.weight(1f).padding(start = if (wide) 16.dp else 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ConnectionDot(conn) {
                                    vm.recheckConnection()
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        snackbar.showSnackbar(
                                            when (vm.conn.value) {
                                                Conn.ONLINE -> "已连接服务器：${vm.connDetail.value}"
                                                Conn.OFFLINE -> "无法连接服务器：${vm.connDetail.value}"
                                                Conn.CHECKING -> "正在检查连接…"
                                            }
                                        )
                                    }
                                }
                                Text(
                                    filterTitle(filter),
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                if (syncing && totals.all == 0) "正在同步…" else "${formatCount(items.size)} 项",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Text2,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                        IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "搜索") }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.SwapVert, "排序与显示") }
                            DropdownMenu(
                                expanded = sortMenu,
                                onDismissRequest = { sortMenu = false },
                                shape = AppShapes.Medium,
                                containerColor = AppColors.Surface2,
                            ) {
                                Text(
                                    "排序",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppColors.Text2,
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                                )
                                Sort.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label) },
                                        leadingIcon = { RadioButton(selected = s == sort, onClick = null) },
                                        onClick = {
                                            if (s == Sort.RANDOM) vm.startRandom() else vm.setSort(s)
                                            sortMenu = false
                                        },
                                    )
                                }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                ThumbSizeSlider(thumbWidth, gridColumns) { vm.setThumbWidth(it) }
                            }
                        }
                        IconButton(onClick = onPreview) { Icon(Icons.Default.PlayCircleOutline, "随机速览") }
                        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置") }
                    }
                }
                if (selectedTags.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(selectedTags.toList(), key = { it }) { tag ->
                            InputChip(
                                selected = true,
                                onClick = { vm.toggleTag(tag) },
                                label = { Text(tag) },
                                shape = AppShapes.Full,
                                border = null,
                                trailingIcon = { Icon(Icons.Default.Close, "移除", Modifier.size(16.dp)) },
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { vm.selectedTags.value = emptySet() },
                                label = { Text("清除全部", color = AppColors.Text2) },
                                shape = AppShapes.Full,
                            )
                        }
                    }
                }
                if (syncing) {
                    LinearProgressIndicator(
                        color = AppColors.Brand,
                        trackColor = AppColors.Surface2,
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
            }

            // ---- random button + snackbar ----
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                if (sort == Sort.RANDOM) {
                    SmallFloatingActionButton(
                        onClick = vm::stopRandom,
                        containerColor = AppColors.Surface3,
                        contentColor = AppColors.Text1,
                    ) {
                        Icon(Icons.Default.Close, "退出随机")
                    }
                    ExtendedFloatingActionButton(
                        onClick = vm::reshuffle,
                        icon = { Icon(Icons.Default.Shuffle, null) },
                        text = { Text("换一批") },
                        shape = AppShapes.Full,
                    )
                } else {
                    ExtendedFloatingActionButton(
                        onClick = vm::startRandom,
                        icon = { Icon(Icons.Default.Shuffle, null) },
                        text = { Text("随机") },
                        shape = AppShapes.Full,
                        // collapse to an icon once the user scrolls down
                        expanded = gridState.firstVisibleItemIndex == 0,
                    )
                }
                SnackbarHost(snackbar)
            }
        }
    }

    if (wide) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(Modifier.width(SIDEBAR_WIDTH.dp), drawerContainerColor = AppColors.Surface1) {
                    DrawerContent(vm, showDone = false, onSelect = { vm.filter.value = it }, onDone = {})
                }
            },
        ) { body() }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = AppColors.Surface1,
                    drawerShape = AppShapes.Medium.copy(topStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp)),
                ) {
                    DrawerContent(
                        vm,
                        showDone = true,
                        onSelect = { f ->
                            vm.filter.value = f
                            scope.launch { drawerState.close() }
                        },
                        onDone = { scope.launch { drawerState.close() } },
                    )
                }
            },
        ) { body() }
    }
}

private const val SIDEBAR_WIDTH = 300

/** First sync: one request brings the whole library, so there is no count to show, only that it's coming. */
@Composable
private fun FirstSync(top: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxSize().padding(top = top)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(AppColors.Selected, AppShapes.Medium),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Sync, null, tint = AppColors.Accent) }
            Column(Modifier.padding(start = 14.dp)) {
                Text("第一次同步：正在从电脑读取资源库", style = MaterialTheme.typography.titleSmall)
                Text("之后只同步变化的部分，几秒就好", style = MaterialTheme.typography.bodySmall, color = AppColors.Text2)
            }
        }
        // skeleton grid
        Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(listOf(216, 92, 216), listOf(162, 216, 122), listOf(216, 216, 92)).forEach { col ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    col.forEach { h -> Box(Modifier.fillMaxWidth().height(h.dp).background(AppColors.Surface2, AppShapes.Medium)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(vm: MainViewModel, filtered: Boolean, filter: Filter, modifier: Modifier) {
    val (icon, title, text) = when {
        filtered -> Triple(Icons.Default.FilterAlt, "没有符合条件的", "同一组里的标签是“或”，不同组之间是“且”。")
        filter == Filter.Favorites -> Triple(Icons.Default.FavoriteBorder, "还没有收藏", "长按缩略图，或在播放页点 ♡")
        filter == Filter.Continue -> Triple(Icons.Default.History, "没有看到一半的视频", "看过一部分、还没看完的视频会出现在这里")
        else -> Triple(Icons.Default.CloudOff, "这里还没有内容", "下拉刷新，或者检查电脑上的服务是否开着")
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(32.dp),
    ) {
        Box(Modifier.size(72.dp).background(AppColors.Surface1, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AppColors.Text3, modifier = Modifier.size(32.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppColors.Text2, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            when {
                filtered -> {
                    Button(onClick = {
                        vm.selectedTags.value = emptySet()
                        vm.search.value = ""
                    }, colors = brandButtonColors()) { Text("清除筛选") }
                    if (filter != Filter.All) OutlinedButton(onClick = { vm.filter.value = Filter.All }) { Text("看全部") }
                }
                filter == Filter.All -> Button(onClick = vm::refresh, colors = brandButtonColors()) { Text("刷新") }
                else -> OutlinedButton(onClick = { vm.filter.value = Filter.All }) { Text("看全部") }
            }
        }
    }
}

@Composable
private fun ConnectionDot(conn: Conn, onClick: () -> Unit) {
    val color = when (conn) {
        Conn.ONLINE -> AppColors.Online
        Conn.OFFLINE -> AppColors.Offline
        Conn.CHECKING -> AppColors.Checking
    }
    Box(
        Modifier
            .padding(end = 6.dp)
            .size(20.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
                .semantics { contentDescription = "服务器连接：" + conn.name }
        )
    }
}

/** Log-scaled slider so small sizes get as much travel as large ones. */
@Composable
private fun ThumbSizeSlider(widthDp: Float, columns: Int, onChange: (Float) -> Unit) {
    val ratio = MAX_THUMB_DP / MIN_THUMB_DP
    Column(Modifier.width(240.dp).padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("缩略图大小", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$columns 列", style = MaterialTheme.typography.labelMedium, color = AppColors.Text2)
        }
        Slider(
            value = (ln(widthDp / MIN_THUMB_DP) / ln(ratio)).coerceIn(0f, 1f),
            onValueChange = { onChange(MIN_THUMB_DP * ratio.pow(it)) },
            colors = SliderDefaults.colors(thumbColor = AppColors.Brand, activeTrackColor = AppColors.Brand),
        )
        Text(
            if (isDesktop) "也可以按住 Ctrl 滚动滚轮" else "也可以在网格上双指缩放",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.Text3,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbCell(
    item: ItemEntity,
    thumbUrl: String,
    showName: Boolean,
    favorite: Boolean,
    progress: Float?,
    bounds: ThumbBounds,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalPlatformContext.current
    DisposableEffect(item.id) { onDispose { bounds.map.remove(item.id) } }
    Column(
        Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // Mouse: right-click does what a long press does.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            onLongClick()
                        }
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(item.aspectRatio)
                .onGloballyPositioned { bounds.map[item.id] = it.boundsInRoot() }
                .clip(AppShapes.Medium)
                .background(AppColors.Surface2),
        ) {
            if (item.hasThumb) {
                AsyncImage(
                    model = remember(thumbUrl) { ImageRequest.Builder(context).data(thumbUrl).build() },
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    item.ext.uppercase(),
                    color = AppColors.Text2,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (favorite) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(MediaColors.Scrim, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "已收藏", tint = AppColors.Favorite, modifier = Modifier.size(13.dp))
                }
            }
            if (item.isVideo && item.duration > 0) {
                Text(
                    formatDuration(item.duration),
                    color = MediaColors.Text,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 7.dp)
                        .background(MediaColors.Scrim, AppShapes.Small)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (progress != null) {
                // 继续观看: how far this video got
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(MediaColors.Track)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(AppColors.Brand))
                }
            }
        }
        if (showName) {
            Text(
                item.name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = AppColors.Text2,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            )
        }
    }
}

private fun filterTitle(f: Filter) = when (f) {
    Filter.All -> "全部"
    Filter.Favorites -> "收藏"
    Filter.Continue -> "继续观看"
    Filter.Uncategorized -> "未分类"
    Filter.Trash -> "回收站"
    is Filter.Folder -> f.name
}

private data class FolderRow(val folder: FolderDto, val depth: Int)

private fun flatten(list: List<FolderDto>, expanded: Set<String>, depth: Int, out: MutableList<FolderRow>) {
    for (f in list) {
        out += FolderRow(f, depth)
        if (f.id in expanded) flatten(f.children, expanded, depth + 1, out)
    }
}

/**
 * Sidebar: 全部 / 收藏 / 继续观看 pick the scope; tags below narrow it down (multi-select, same group = any,
 * different groups = all). Tapping tags keeps the drawer open; the bottom button closes it.
 */
@Composable
private fun DrawerContent(vm: MainViewModel, showDone: Boolean, onSelect: (Filter) -> Unit, onDone: () -> Unit) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val groups by vm.tagGroups.collectAsStateWithLifecycle()
    val selected by vm.selectedTags.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val libraryName by vm.libraryName.collectAsStateWithLifecycle()
    val showFolders by vm.showFolders.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val folderCounts by vm.folderCounts.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var expandedGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    var expandedFolders by rememberSaveable { mutableStateOf(setOf<String>()) }

    val q = query.trim()
    val visibleGroups = remember(groups, q) {
        if (q.isEmpty()) groups
        else groups.mapNotNull { g ->
            val hit = if (g.name.contains(q, true)) g.tags else g.tags.filter { it.tag.contains(q, true) }
            if (hit.isEmpty()) null else g.copy(tags = hit)
        }
    }
    val folderRows = remember(folders, expandedFolders, showFolders) {
        if (!showFolders) emptyList() else mutableListOf<FolderRow>().also { flatten(folders, expandedFolders, 0, it) }
    }
    val rowColors = { on: Boolean -> if (on) AppColors.Selected else Color.Transparent }

    Column(Modifier.fillMaxHeight()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                Text(
                    libraryName.ifEmpty { "入到云盘" },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.statusBarsPadding().padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                )
            }
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索标签") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "清空") }
                    },
                    singleLine = true,
                    shape = AppShapes.Full,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AppColors.Surface2,
                        unfocusedContainerColor = AppColors.Surface2,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                DrawerEntry("全部", totals.all, Icons.Default.PhotoLibrary, filter == Filter.All) { onSelect(Filter.All) }
            }
            item {
                DrawerEntry("收藏", totals.favorites, Icons.Default.FavoriteBorder, filter == Filter.Favorites) {
                    onSelect(Filter.Favorites)
                }
            }
            item {
                DrawerEntry("继续观看", totals.continued, Icons.Default.History, filter == Filter.Continue) {
                    onSelect(Filter.Continue)
                }
            }
            item {
                HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 28.dp, end = 12.dp),
                ) {
                    Text(
                        "标签",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.Text2,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = { vm.selectedTags.value = emptySet() }) { Text("清除 (${selected.size})") }
                    }
                }
            }
            visibleGroups.forEach { group ->
                val open = q.isNotEmpty() || group.name in expandedGroups
                val picked = group.tags.count { it.tag in selected }
                item(key = "g:" + group.name) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(AppShapes.Full)
                            .clickable {
                                expandedGroups = if (group.name in expandedGroups) expandedGroups - group.name
                                else expandedGroups + group.name
                            }
                            .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    ) {
                        Icon(
                            if (open) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(group.name, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        if (picked > 0) {
                            Text(
                                "已选 $picked",
                                style = MaterialTheme.typography.labelMedium,
                                color = AppColors.Accent,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Text("${group.tags.size}", style = MaterialTheme.typography.labelMedium, color = AppColors.Text2)
                    }
                }
                if (open) {
                    items(group.tags, key = { "t:" + it.tag }) { tc ->
                        val on = tc.tag in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .clip(AppShapes.Full)
                                .background(rowColors(on))
                                .clickable { vm.toggleTag(tc.tag) }
                                .padding(start = 28.dp, end = 16.dp),
                        ) {
                            Checkbox(
                                checked = on,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = AppColors.Brand, uncheckedColor = AppColors.Text3),
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                tagValueOf(tc.tag),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (on) AppColors.OnSelected else AppColors.Text1,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatCount(tc.n),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) AppColors.OnSelected else AppColors.Text2,
                            )
                        }
                    }
                }
            }
            if (showFolders) {
                item {
                    HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                    Text(
                        "Eagle 文件夹",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.Text2,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    )
                }
                item {
                    DrawerEntry("未分类", totals.uncategorized, Icons.Default.FolderOff, filter == Filter.Uncategorized) {
                        onSelect(Filter.Uncategorized)
                    }
                }
                item {
                    DrawerEntry("回收站", totals.trash, Icons.Default.Delete, filter == Filter.Trash) { onSelect(Filter.Trash) }
                }
                items(folderRows, key = { "f:" + it.folder.id }) { row ->
                    val f = row.folder
                    val isSel = (filter as? Filter.Folder)?.id == f.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(AppShapes.Full)
                            .background(rowColors(isSel))
                            .clickable { onSelect(Filter.Folder(f.id, f.name)) }
                            .padding(start = (8 + row.depth * 16).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    ) {
                        if (f.children.isNotEmpty()) {
                            Icon(
                                if (f.id in expandedFolders) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                contentDescription = "展开",
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        expandedFolders = if (f.id in expandedFolders) expandedFolders - f.id
                                        else expandedFolders + f.id
                                    },
                            )
                        } else {
                            Spacer(Modifier.width(24.dp))
                        }
                        Icon(
                            if (f.locked) Icons.Default.Lock else Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(20.dp),
                        )
                        Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            formatCount(folderCounts[f.id] ?: 0),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.Text2,
                        )
                    }
                }
            }
        }
        if (showDone) {
            Button(
                onClick = onDone,
                colors = brandButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(48.dp),
            ) { Text("查看 ${formatCount(items.size)} 项") }
        }
    }
}

@Composable
private fun DrawerEntry(
    label: String,
    count: Int,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        badge = { Text(formatCount(count), style = MaterialTheme.typography.labelMedium) },
        selected = selected,
        onClick = onClick,
        shape = AppShapes.Full,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
