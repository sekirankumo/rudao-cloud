package com.eaglelan.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eaglelan.app.EagleApp
import com.eaglelan.app.data.Filter
import com.eaglelan.app.data.Release
import com.eaglelan.app.data.ItemEntity
import com.eaglelan.app.data.Sort
import com.eaglelan.app.data.TagCount
import com.eaglelan.app.data.UNGROUPED_TAGS
import com.eaglelan.app.data.tagGroupOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

data class Totals(val all: Int = 0, val uncategorized: Int = 0, val trash: Int = 0, val favorites: Int = 0)

data class TagGroup(val name: String, val total: Int, val tags: List<TagCount>)

enum class Conn { CHECKING, ONLINE, OFFLINE }

/** Self-update flow: available -> downloading(progress) -> (installer opens) / error. */
data class UpdateUi(
    val release: Release? = null,
    val progress: Float? = null,      // non-null while downloading
    val error: String? = null,
    val needsPermission: Boolean = false,
)

const val MIN_THUMB_DP = 50f
const val MAX_THUMB_DP = 480f

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as EagleApp).repo
    private val prefs = repo.prefs
    private val updater = (app as EagleApp).updater
    private var downloaded: java.io.File? = null

    val configured = MutableStateFlow(prefs.isConfigured)
    val libraryName = MutableStateFlow(prefs.libraryName)
    val filter = MutableStateFlow<Filter>(Filter.All)
    val sort = MutableStateFlow(Sort.from(prefs.sort))
    val search = MutableStateFlow("")
    val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val shuffleSeed = MutableStateFlow(Random.nextLong())
    val thumbWidth = MutableStateFlow(prefs.thumbWidth.coerceIn(MIN_THUMB_DP, MAX_THUMB_DP))
    val conn = MutableStateFlow(Conn.CHECKING)
    val connDetail = MutableStateFlow("")
    val update = MutableStateFlow(UpdateUi())
    val appVersion get() = updater.currentVersion
    val syncing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val folders = MutableStateFlow(repo.folders())

    val items: StateFlow<List<ItemEntity>> =
        combine(filter, sort, search.debounce(250), selectedTags, shuffleSeed) { f, s, q, t, seed ->
            Query(f, s, q, t, seed)
        }
            .flatMapLatest { q ->
                repo.observe(q.filter, q.sort, q.search, q.tags).map { list ->
                    if (q.sort == Sort.RANDOM) list.shuffled(Random(q.seed)) else list
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Tag groups ordered by usage; tags inside a group ordered by count. */
    val tagGroups: StateFlow<List<TagGroup>> =
        repo.dao.tagCounts()
            .map { counts ->
                counts.groupBy { tagGroupOf(it.tag) }
                    .map { (name, tags) -> TagGroup(name, tags.sumOf { it.n }, tags.sortedByDescending { it.n }) }
                    // Keep the catch-all group last.
                    .sortedWith(compareBy<TagGroup> { it.name == UNGROUPED_TAGS }.thenByDescending { it.total })
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folderCounts: StateFlow<Map<String, Int>> =
        repo.dao.folderCounts()
            .map { list -> list.associate { it.folderId to it.n } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val totals: StateFlow<Totals> =
        combine(repo.dao.countAll(), repo.dao.countUncategorized(), repo.dao.countTrash(), repo.dao.countFavorites()) { a, u, t, f ->
            Totals(a, u, t, f)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, Totals())

    /** Phone-local favourites (item ids). */
    val favorites: StateFlow<Set<String>> =
        repo.dao.favoriteIds()
            .map { it.toHashSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val showFolders = MutableStateFlow(prefs.showFolders)
    val loudMode = MutableStateFlow(com.eaglelan.app.data.LoudMode.from(prefs.loudMode))
    val previewSeconds = MutableStateFlow(prefs.previewSeconds)

    val serverUrl get() = prefs.baseUrl
    val serverToken get() = prefs.token

    init {
        if (configured.value) refresh()
        checkForUpdate(manual = false)
    }

    fun refresh() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            try {
                repo.sync()
                folders.value = repo.folders()
                setConn(true, "")
            } catch (e: Exception) {
                val why = e.message ?: e.javaClass.simpleName
                setConn(false, why)
                message.value = "同步失败：$why"
            } finally {
                syncing.value = false
            }
        }
    }

    // ---- connection indicator ----------------------------------------------
    private fun setConn(ok: Boolean, detail: String) {
        conn.value = if (ok) Conn.ONLINE else Conn.OFFLINE
        connDetail.value = detail
    }

    /** Lightweight reachability check (the UI calls this periodically while visible). */
    suspend fun checkConnection() {
        if (!configured.value) return
        try {
            val ping = repo.ping()
            setConn(true, "${ping.library} · ${ping.items} 项")
        } catch (e: Exception) {
            setConn(false, e.message ?: e.javaClass.simpleName)
        }
    }

    fun recheckConnection() {
        conn.value = Conn.CHECKING
        viewModelScope.launch { checkConnection() }
    }

    // ---- random --------------------------------------------------------------
    fun startRandom() {
        if (sort.value != Sort.RANDOM) {
            prefs.sortBeforeRandom = sort.value.name
            setSort(Sort.RANDOM)
        }
        reshuffle()
    }

    fun stopRandom() = setSort(Sort.from(prefs.sortBeforeRandom).takeIf { it != Sort.RANDOM } ?: Sort.ADDED)

    // ---- thumbnail size (stepless) -----------------------------------------
    fun setThumbWidth(dp: Float, persist: Boolean = true) {
        thumbWidth.value = dp.coerceIn(MIN_THUMB_DP, MAX_THUMB_DP)
        if (persist) prefs.thumbWidth = thumbWidth.value
    }

    // ---- self-update ----------------------------------------------------------
    fun checkForUpdate(manual: Boolean) {
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.lastUpdateCheck < 6 * 3600_000L) return
        viewModelScope.launch {
            try {
                val rel = updater.checkNewer()
                prefs.lastUpdateCheck = now
                if (rel != null) update.value = UpdateUi(release = rel)
                else if (manual) message.value = "已是最新版本（v${updater.currentVersion}）"
            } catch (e: Exception) {
                if (manual) message.value = "检查更新失败：${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun dismissUpdate() {
        update.value = UpdateUi()
    }

    fun startUpdate() {
        val rel = update.value.release ?: return
        if (!updater.canInstall()) {
            update.value = update.value.copy(needsPermission = true)
            updater.openInstallPermissionSettings()
            return
        }
        downloaded?.takeIf { it.exists() && it.name.contains(rel.version) }?.let {
            updater.install(it)
            return
        }
        viewModelScope.launch {
            update.value = update.value.copy(progress = 0f, error = null, needsPermission = false)
            try {
                val file = updater.download(rel) { p -> update.value = update.value.copy(progress = p) }
                downloaded = file
                update.value = update.value.copy(progress = null)
                updater.install(file)
            } catch (e: Exception) {
                update.value = update.value.copy(progress = null, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Returns an error message, or null on success. */
    suspend fun connect(host: String, token: String): String? = try {
        repo.connect(host, token)
        libraryName.value = prefs.libraryName
        configured.value = true
        filter.value = Filter.All
        refresh()
        null
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }

    fun setSort(s: Sort) {
        sort.value = s
        prefs.sort = s.name
    }

    /** Returns true if the item is now a favourite. */
    fun toggleFavorite(id: String): Boolean {
        val add = id !in favorites.value
        viewModelScope.launch {
            if (add) repo.dao.addFavorite(com.eaglelan.app.data.Favorite(id, System.currentTimeMillis()))
            else repo.dao.removeFavorite(id)
        }
        return add
    }

    fun setShowFolders(show: Boolean) {
        showFolders.value = show
        prefs.showFolders = show
        // leaving a folder view that is no longer reachable from the sidebar
        if (!show && filter.value !in listOf(Filter.All, Filter.Favorites)) filter.value = Filter.All
    }

    fun cycleLoudMode() {
        loudMode.value = loudMode.value.next()
        prefs.loudMode = loudMode.value.name
        message.value = when (loudMode.value) {
            com.eaglelan.app.data.LoudMode.STANDARD -> "响度统一：标准（太响的压低，太轻的最多拉高 8 dB；整体会轻一些）"
            com.eaglelan.app.data.LoudMode.DOWN -> "响度统一：只压低太响的，安静的保持原样"
            com.eaglelan.app.data.LoudMode.OFF -> "响度统一已关闭"
        }
    }

    suspend fun loudness(id: String) = repo.loudness(id)

    fun setPreviewSeconds(sec: Int) {
        previewSeconds.value = sec
        prefs.previewSeconds = sec
    }

    fun toggleTag(tag: String) {
        selectedTags.value = selectedTags.value.let { if (tag in it) it - tag else it + tag }
    }

    fun reshuffle() {
        shuffleSeed.value = Random.nextLong()
    }


    fun thumbUrl(item: ItemEntity) = repo.thumbUrl(item.id) + "&v=${item.mtime}"
    fun fileUrl(item: ItemEntity) = repo.fileUrl(item.id)
}

private data class Query(
    val filter: Filter,
    val sort: Sort,
    val search: String,
    val tags: Set<String>,
    val seed: Long,
)
