package com.eaglelan.app.data

import androidx.room.RoomRawQuery
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface Filter {
    data object All : Filter
    data object Uncategorized : Filter
    data object Trash : Filter
    data object Favorites : Filter
    data class Folder(val id: String, val name: String) : Filter
}

enum class Sort(val label: String, val sql: String) {
    ADDED("添加时间", "items.btime DESC"),
    MODIFIED("修改时间", "items.mtime DESC"),
    NAME("名称", "items.name COLLATE NOCASE ASC"),
    DURATION("时长", "items.duration DESC"),
    SIZE("文件大小", "items.size DESC"),
    // Shuffled client-side with a seed so the order stays stable across re-queries.
    RANDOM("随机", "items.id");

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: ADDED
    }
}

/** On Android this is the same SharedPreferences file ("settings") as before the multiplatform move. */
class Prefs(private val sp: Settings) {

    private fun putOrRemove(key: String, v: String?) = if (v == null) sp.remove(key) else sp.putString(key, v)

    var baseUrl: String
        get() = sp.getString("baseUrl", "")
        set(v) = sp.putString("baseUrl", v)
    var token: String
        get() = sp.getString("token", "")
        set(v) = sp.putString("token", v)
    var libraryName: String
        get() = sp.getString("libraryName", "")
        set(v) = sp.putString("libraryName", v)
    var cursor: Long
        get() = sp.getLong("cursor", 0)
        set(v) = sp.putLong("cursor", v)
    var foldersJson: String
        get() = sp.getString("folders", "[]")
        set(v) = sp.putString("folders", v)
    var sort: String?
        get() = sp.getStringOrNull("sort")
        set(v) = putOrRemove("sort", v)
    var columns: Int
        get() = sp.getInt("columns", 3)
        set(v) = sp.putInt("columns", v)

    /** Target thumbnail width in dp; the grid fits as many columns as this allows. */
    var thumbWidth: Float
        get() = sp.getFloat("thumbWidth", 360f / columns)
        set(v) = sp.putFloat("thumbWidth", v)
    /** Sort to return to when leaving random mode. */
    var sortBeforeRandom: String?
        get() = sp.getStringOrNull("sortBeforeRandom")
        set(v) = putOrRemove("sortBeforeRandom", v)
    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0)
        set(v) = sp.putLong("lastUpdateCheck", v)

    /** Show the Eagle folder tree (and 未分类 / 回收站) in the sidebar. */
    var showFolders: Boolean
        get() = sp.getBoolean("showFolders", false)
        set(v) = sp.putBoolean("showFolders", v)
    /** 随机速览 clip length. */
    var previewSeconds: Int
        get() = sp.getInt("previewSeconds", 10)
        set(v) = sp.putInt("previewSeconds", v)

    var loudMode: String?
        get() = sp.getStringOrNull("loudMode")
        set(v) = putOrRemove("loudMode", v)

    val isConfigured get() = baseUrl.isNotEmpty() && token.isNotEmpty()
}

class Repository(
    val prefs: Prefs,
    private val db: AppDatabase,
    private val http: HttpClient,
) {
    val dao = db.itemDao()

    private fun api() = Api(http, prefs.baseUrl, prefs.token)

    suspend fun ping(): PingDto = api().ping()

    // Analysed videos don't change, so keep recent answers; misses are re-asked (analysis may finish later).
    private val loudCache = LinkedHashMap<String, LoudnessDto>()
    private val loudLock = Mutex()

    suspend fun loudness(id: String): LoudnessDto? {
        loudLock.withLock {
            loudCache.remove(id)?.let {
                loudCache[id] = it          // re-insert: most recently used goes last
                return it
            }
        }
        val d = try { api().loudness(id) } catch (e: Exception) { null } ?: return null
        loudLock.withLock {
            loudCache[id] = d
            if (loudCache.size > 500) loudCache.remove(loudCache.keys.first())
        }
        return d
    }

    fun thumbUrl(id: String) = api().thumbUrl(id)
    fun fileUrl(id: String) = api().fileUrl(id)

    private suspend fun transaction(block: suspend () -> Unit) =
        db.useWriterConnection { it.immediateTransaction { block() } }

    /** Verifies the server, then saves it. Switching servers wipes the local mirror. */
    suspend fun connect(host: String, token: String): PingDto {
        val base = Api.normalizeBaseUrl(host)
        val ping = Api(http, base, token.trim()).ping()
        if (ping.app != "eagle-lan") throw ApiException("这个地址不是 Eagle LAN 服务")
        if (base != prefs.baseUrl) {
            transaction {
                dao.clearFolderRefs()
                dao.clearTagRefs()
                dao.clearItems()
            }
            prefs.cursor = 0
            prefs.foldersJson = "[]"
        }
        prefs.baseUrl = base
        prefs.token = token.trim()
        prefs.libraryName = ping.library
        return ping
    }

    /** Incremental sync. Returns the number of items received. */
    suspend fun sync(): Int {
        // A wiped/migrated local DB must re-download everything.
        if (dao.localCount() == 0) prefs.cursor = 0
        val r = api().sync(prefs.cursor)
        transaction {
            r.items.chunked(400).forEach { chunk ->
                val ids = chunk.map { it.id }
                dao.upsertItems(chunk.map { it.toEntity() })
                dao.deleteFolderRefs(ids)
                dao.insertFolderRefs(chunk.flatMap { i -> i.folders.map { ItemFolder(i.id, it) } })
                dao.deleteTagRefs(ids)
                dao.insertTagRefs(chunk.flatMap { i -> i.tags.distinct().map { ItemTag(i.id, it) } })
            }
            val keep = r.ids.toHashSet()
            dao.allIds().filter { it !in keep }.chunked(400).forEach {
                dao.deleteFolderRefs(it)
                dao.deleteTagRefs(it)
                dao.deleteItems(it)
            }
        }
        prefs.cursor = r.cursor
        prefs.foldersJson = AppJson.encodeToString(r.folders)
        return r.items.size
    }

    fun folders(): List<FolderDto> =
        runCatching { AppJson.decodeFromString<List<FolderDto>>(prefs.foldersJson) }.getOrDefault(emptyList())

    fun observe(filter: Filter, sort: Sort, search: String, tags: Set<String>): Flow<List<ItemEntity>> {
        val args = mutableListOf<String>()
        val where = mutableListOf<String>()
        val sql = StringBuilder("SELECT items.* FROM items ")
        when (filter) {
            Filter.All -> where += "items.isDeleted = 0"
            Filter.Uncategorized -> {
                where += "items.isDeleted = 0"
                where += "items.folders = ''"
            }
            Filter.Trash -> where += "items.isDeleted = 1"
            Filter.Favorites -> {
                sql.append("INNER JOIN favorites fav ON fav.itemId = items.id ")
                where += "items.isDeleted = 0"
            }
            is Filter.Folder -> {
                sql.append("INNER JOIN item_folder f ON f.itemId = items.id ")
                where += "f.folderId = ?"
                args += filter.id
                where += "items.isDeleted = 0"
            }
        }
        val q = search.trim()
        if (q.isNotEmpty()) {
            where += "(items.name LIKE ? OR items.tags LIKE ? OR items.annotation LIKE ?)"
            repeat(3) { args += "%$q%" }
        }
        // Tags: OR within a group ("清晰度: 1080p" / "清晰度: 4K"), AND across groups.
        tags.groupBy(::tagGroupOf).values.forEach { group ->
            where += "items.id IN (SELECT itemId FROM item_tag WHERE tag IN (${group.joinToString(",") { "?" }}))"
            args.addAll(group)
        }
        sql.append("WHERE ").append(where.joinToString(" AND "))
        // In 收藏 the default order is "most recently favourited first".
        val order = if (filter == Filter.Favorites && sort == Sort.ADDED) "fav.addedAt DESC" else sort.sql
        sql.append(" ORDER BY ").append(order)
        return dao.observe(RoomRawQuery(sql.toString()) { st -> args.forEachIndexed { i, a -> st.bindText(i + 1, a) } })
    }
}
