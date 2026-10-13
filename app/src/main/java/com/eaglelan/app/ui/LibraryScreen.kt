package com.eaglelan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eaglelan.app.data.Filter
import com.eaglelan.app.data.FolderDto
import com.eaglelan.app.data.ItemEntity
import com.eaglelan.app.data.Sort
import com.eaglelan.app.data.aspectRatio
import com.eaglelan.app.data.isVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: MainViewModel, onOpen: (Int) -> Unit, onSettings: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val thumbWidth by vm.thumbWidth.collectAsStateWithLifecycle()
    val conn by vm.conn.collectAsStateWithLifecycle()
    // read so thumbnails pick up the new address when the route switches
    val baseUrl by vm.baseUrl.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val selectedTags by vm.selectedTags.collectAsStateWithLifecycle()
    val seed by vm.shuffleSeed.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyStaggeredGridState()
    val snackbar = remember { SnackbarHostState() }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var tagSheet by rememberSaveable { mutableStateOf(false) }
    var pinching by remember { mutableStateOf(false) }

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
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }
    LaunchedEffect(filter, sort, selectedTags, seed) { gridState.scrollToItem(0) }

    if (tagSheet) TagFilterSheet(vm, onDismiss = { tagSheet = false })

    // Tablets / wide windows: pinned sidebar, and more columns at the same thumbnail size.
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val wide = screenWidth >= 840
    val gridWidth = if (wide) screenWidth - SIDEBAR_WIDTH else screenWidth
    // Stepless size: the user picks a target thumbnail width; fit as many columns as it allows.
    val gridColumns = (gridWidth / thumbWidth).roundToInt().coerceIn(1, 12)

    val body: @Composable () -> Unit = {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (sort == Sort.RANDOM) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallFloatingActionButton(onClick = vm::stopRandom) {
                            Icon(Icons.Default.Close, "退出随机")
                        }
                        ExtendedFloatingActionButton(
                            onClick = vm::reshuffle,
                            icon = { Icon(Icons.Default.Shuffle, null) },
                            text = { Text("换一批") },
                        )
                    }
                } else {
                    ExtendedFloatingActionButton(
                        onClick = vm::startRandom,
                        icon = { Icon(Icons.Default.Shuffle, null) },
                        text = { Text("随机") },
                        // collapse to an icon once the user scrolls down
                        expanded = gridState.firstVisibleItemIndex == 0,
                    )
                }
            },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (!wide) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, "菜单")
                            }
                        }
                    },
                    title = {
                        if (searching) {
                            TextField(
                                value = search,
                                onValueChange = { vm.search.value = it },
                                placeholder = { Text("搜索名称 / 标签 / 注释") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Column {
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
                                    Text(filterTitle(filter), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    "${items.size} 项" + if (conn == Conn.ONLINE) " · ${vm.route}" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        if (searching) {
                            IconButton(onClick = {
                                searching = false
                                vm.search.value = ""
                            }) { Icon(Icons.Default.Close, "关闭搜索") }
                        } else {
                            IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "搜索") }
                        }
                        IconButton(onClick = { tagSheet = true }) {
                            BadgedBox(badge = {
                                if (selectedTags.isNotEmpty()) Badge { Text("${selectedTags.size}") }
                            }) {
                                Icon(Icons.Default.FilterList, "按标签筛选")
                            }
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, "排序")
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                Sort.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label) },
                                        leadingIcon = { RadioButton(selected = s == sort, onClick = null) },
                                        onClick = {
                                            vm.setSort(s)
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { moreMenu = true }) {
                                Icon(Icons.Default.MoreVert, "更多")
                            }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                ThumbSizeSlider(thumbWidth, gridColumns) { vm.setThumbWidth(it) }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("检查更新（当前 v${vm.appVersion}）") },
                                    leadingIcon = { Icon(Icons.Default.SystemUpdate, null) },
                                    onClick = {
                                        moreMenu = false
                                        vm.checkForUpdate(manual = true)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("服务器设置") },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = {
                                        moreMenu = false
                                        onSettings()
                                    },
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
          Column(Modifier.padding(padding)) {
            if (selectedTags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    items(selectedTags.toList(), key = { it }) { tag ->
                        InputChip(
                            selected = true,
                            onClick = { vm.toggleTag(tag) },
                            label = { Text(tag) },
                            trailingIcon = { Icon(Icons.Default.Close, "移除", Modifier.size(16.dp)) },
                        )
                    }
                    item {
                        AssistChip(onClick = { vm.selectedTags.value = emptySet() }, label = { Text("清除全部") })
                    }
                }
            }
            PullToRefreshBox(
                isRefreshing = syncing,
                onRefresh = vm::refresh,
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
                    },
            ) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(gridColumns),
                    state = gridState,
                    contentPadding = PaddingValues(6.dp),
                    verticalItemSpacing = 6.dp,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                        ThumbCell(item, vm.thumbUrl(item, baseUrl), showName = gridWidth / gridColumns >= 110) { onOpen(index) }
                    }
                }
                if (pinching) {
                    Text(
                        "$gridColumns 列",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color(0x99000000), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (items.isEmpty() && !syncing) {
                    Text(
                        if (search.isNotBlank() || selectedTags.isNotEmpty()) "没有匹配的结果" else "这里还没有内容，下拉刷新试试",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
          }
        }
    }

    if (wide) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(Modifier.width(SIDEBAR_WIDTH.dp)) {
                    DrawerContent(vm) { vm.filter.value = it }
                }
            },
        ) { body() }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(vm) { f ->
                        vm.filter.value = f
                        scope.launch { drawerState.close() }
                    }
                }
            },
        ) { body() }
    }
}

private const val SIDEBAR_WIDTH = 300

@Composable
private fun ConnectionDot(conn: Conn, onClick: () -> Unit) {
    val color = when (conn) {
        Conn.ONLINE -> Color(0xFF57AB5A)
        Conn.OFFLINE -> Color(0xFFE5534B)
        Conn.CHECKING -> Color(0xFF8B949E)
    }
    Box(
        Modifier
            .padding(end = 8.dp)
            .size(20.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape)
                .semantics { contentDescription = "服务器连接：" + conn.name }
        )
    }
}

/** Log-scaled slider so small sizes get as much travel as large ones. */
@Composable
private fun ThumbSizeSlider(widthDp: Float, columns: Int, onChange: (Float) -> Unit) {
    val ratio = MAX_THUMB_DP / MIN_THUMB_DP
    Column(Modifier.width(260.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("缩略图大小", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("$columns 列", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = (ln(widthDp / MIN_THUMB_DP) / ln(ratio)).coerceIn(0f, 1f),
            onValueChange = { onChange(MIN_THUMB_DP * ratio.pow(it)) },
        )
        Text(
            "也可以在网格上双指缩放",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThumbCell(item: ItemEntity, thumbUrl: String, showName: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(item.aspectRatio)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (item.hasThumb) {
                AsyncImage(
                    model = remember(thumbUrl) {
                        // cache by item, not URL, so LAN/public route switches reuse cached thumbnails
                        val key = "thumb-${item.id}-${item.mtime}"
                        ImageRequest.Builder(context).data(thumbUrl).diskCacheKey(key).memoryCacheKey(key).build()
                    },
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    item.ext.uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (item.isVideo && item.duration > 0) {
                Text(
                    formatDuration(item.duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        if (showName) {
            Text(
                item.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp),
            )
        }
    }
}

private fun filterTitle(f: Filter) = when (f) {
    Filter.All -> "全部"
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

@Composable
private fun DrawerContent(vm: MainViewModel, onSelect: (Filter) -> Unit) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val counts by vm.folderCounts.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val libraryName by vm.libraryName.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val rows = remember(folders, expanded) {
        mutableListOf<FolderRow>().also { flatten(folders, expanded, 0, it) }
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Text(
                libraryName.ifEmpty { "Eagle" },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }
        item {
            DrawerEntry("全部", totals.all, Icons.Default.PhotoLibrary, filter == Filter.All) {
                onSelect(Filter.All)
            }
        }
        item {
            DrawerEntry("未分类", totals.uncategorized, Icons.Default.FolderOff, filter == Filter.Uncategorized) {
                onSelect(Filter.Uncategorized)
            }
        }
        item {
            DrawerEntry("回收站", totals.trash, Icons.Default.Delete, filter == Filter.Trash) {
                onSelect(Filter.Trash)
            }
        }
        item {
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            Text(
                "文件夹",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
        items(rows, key = { it.folder.id }) { row ->
            val f = row.folder
            val selected = (filter as? Filter.Folder)?.id == f.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onSelect(Filter.Folder(f.id, f.name)) }
                    .padding(start = (8 + row.depth * 16).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            ) {
                if (f.children.isNotEmpty()) {
                    Icon(
                        if (f.id in expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = "展开",
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                expanded = if (f.id in expanded) expanded - f.id else expanded + f.id
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
                    "${counts[f.id] ?: 0}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DrawerEntry(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        badge = { Text("$count") },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
