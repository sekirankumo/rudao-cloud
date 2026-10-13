package com.eaglelan.app.data

import android.content.Context
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

sealed interface Filter {
    data object All : Filter
    data object Uncategorized : Filter
    data object Trash : Filter
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

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = sp.getString("baseUrl", "") ?: ""
        set(v) = sp.edit().putString("baseUrl", v).apply()
    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v).apply()
    var libraryName: String
        get() = sp.getString("libraryName", "") ?: ""
        set(v) = sp.edit().putString("libraryName", v).apply()
    var cursor: Long
        get() = sp.getLong("cursor", 0)
        set(v) = sp.edit().putLong("cursor", v).apply()
    var foldersJson: String
        get() = sp.getString("folders", "[]") ?: "[]"
        set(v) = sp.edit().putString("folders", v).apply()
    var sort: String?
        get() = sp.getString("sort", null)
        set(v) = sp.edit().putString("sort", v).apply()
    var columns: Int
        get() = sp.getInt("columns", 3)
        set(v) = sp.edit().putInt("columns", v).apply()

    /** Target thumbnail width in dp; the grid fits as many columns as this allows. */
    var thumbWidth: Float
        get() = sp.getFloat("thumbWidth", 360f / columns)
        set(v) = sp.edit().putFloat("thumbWidth", v).apply()
    /** Sort to return to when leaving random mode. */
    var sortBeforeRandom: String?
        get() = sp.getString("sortBeforeRandom", null)
        set(v) = sp.edit().putString("sortBeforeRandom", v).apply()
    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()

    /** All known server addresses (LAN + public), refreshed from the server on every ping. */
    var endpointsJson: String
        get() = sp.getString("endpoints", "[]") ?: "[]"
        set(v) = sp.edit().putString("endpoints", v).apply()

    val isConfigured get() = baseUrl.isNotEmpty() && token.isNotEmpty()
}

class Repository(
    val prefs: Prefs,
    private val db: AppDatabase,
    private val http: OkHttpClient,
) {
    val dao = db.itemDao()

    /** Current base URL; changes when the best route switches between LAN and public. */
    val baseUrl = MutableStateFlow(prefs.baseUrl)

    // Probing must be quick: unreachable LAN addresses shouldn't stall the switch to the public route.
    private val probeHttp = http.newBuilder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    private fun api() = Api(http, prefs.baseUrl, prefs.token)

    fun endpoints(): List<EndpointDto> =
        runCatching { AppJson.decodeFromString<List<EndpointDto>>(prefs.endpointsJson) }.getOrDefault(emptyList())

    val isLanRoute get() = isPrivateUrl(prefs.baseUrl)

    private class Probe(val endpoint: EndpointDto, val ping: PingDto)

    /**
     * Find the best reachable address — LAN first (known LAN addresses, then a LAN broadcast),
     * then public ones — and make it the active route. Returns the server's ping, or null.
     * Throws ApiException("Token 不正确") if a server answered but rejected the token.
     */
    suspend fun resolveBest(
        token: String = prefs.token,
        extra: List<EndpointDto> = emptyList(),
        discover: Boolean = true,
    ): PingDto? {
        val known = (extra + endpoints() +
            listOfNotNull(prefs.baseUrl.takeIf { it.isNotEmpty() }?.let { EndpointDto(it, kindOf(it)) }))
            .distinctBy { it.url }
        var badToken = false

        suspend fun tryAll(list: List<EndpointDto>): Probe? = coroutineScope {
            list.map { ep ->
                async {
                    try {
                        val ping = Api(probeHttp, ep.url, token).ping()
                        if (ping.app == "eagle-lan") Probe(ep, ping) else null
                    } catch (e: ApiException) {
                        if (e.message?.contains("Token") == true) badToken = true
                        null
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().firstOrNull { it != null }
        }

        val hit = tryAll(known.filter { it.kind == "lan" })
            ?: (if (discover) tryAll(Discovery.find().map { EndpointDto(it, "lan") }.filter { d -> known.none { it.url == d.url } }) else null)
            ?: tryAll(known.filter { it.kind != "lan" })
        if (hit == null) {
            if (badToken) throw ApiException("Token 不正确")
            return null
        }
        prefs.baseUrl = hit.endpoint.url
        baseUrl.value = hit.endpoint.url
        if (hit.ping.endpoints.isNotEmpty()) {
            prefs.endpointsJson = AppJson.encodeToString(hit.ping.endpoints)
        }
        return hit.ping
    }

    private fun kindOf(url: String) = if (isPrivateUrl(url)) "lan" else "public"

    fun thumbUrl(id: String) = api().thumbUrl(id)
    fun fileUrl(id: String) = api().fileUrl(id)

    /**
     * Set up with just a token (the computer is found on the LAN), a connection code from the
     * 片库整理 panel ("rudao:…", carries token + addresses), or optionally a manual address.
     * Connecting to a different library wipes the local mirror.
     */
    suspend fun connect(input: String, manualHost: String = ""): PingDto {
        val (token, codeEndpoints) = parseConnectInput(input.trim())
        val extra = codeEndpoints + listOfNotNull(
            manualHost.takeIf { it.isNotBlank() }?.let { Api.normalizeBaseUrl(it) }?.let { EndpointDto(it, kindOf(it)) }
        )
        if (token != prefs.token) prefs.endpointsJson = "[]"   // don't reuse another server's addresses
        val ping = resolveBest(token, extra) ?: throw ApiException(
            "找不到电脑上的服务。在家请确认手机和电脑连着同一个 Wi-Fi；在外面请使用插件里的「连接码」。"
        )
        if (prefs.libraryName.isNotEmpty() && ping.library != prefs.libraryName) {
            db.withTransaction {
                dao.clearFolderRefs()
                dao.clearTagRefs()
                dao.clearItems()
            }
            prefs.cursor = 0
            prefs.foldersJson = "[]"
        }
        prefs.token = token
        prefs.libraryName = ping.library
        return ping
    }

    private fun parseConnectInput(input: String): Pair<String, List<EndpointDto>> {
        if (!input.startsWith("rudao:")) return input to emptyList()
        return try {
            val json = String(android.util.Base64.decode(input.removePrefix("rudao:"), android.util.Base64.URL_SAFE))
            val obj = AppJson.parseToJsonElement(json).jsonObject
            val token = obj["t"]!!.jsonPrimitive.content
            val urls = obj["e"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            token to urls.map { EndpointDto(it, kindOf(it)) }
        } catch (e: Exception) {
            throw ApiException("连接码格式不对，请从插件里重新复制")
        }
    }

    /** Incremental sync. Returns the number of items received. */
    suspend fun sync(): Int {
        // A wiped/migrated local DB must re-download everything.
        if (dao.localCount() == 0) prefs.cursor = 0
        val r = try {
            api().sync(prefs.cursor)
        } catch (e: java.io.IOException) {
            // current route went away (e.g. left home): switch and retry once
            resolveBest() ?: throw e
            api().sync(prefs.cursor)
        }
        db.withTransaction {
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
        val args = mutableListOf<Any>()
        val where = mutableListOf<String>()
        val sql = StringBuilder("SELECT items.* FROM items ")
        when (filter) {
            Filter.All -> where += "items.isDeleted = 0"
            Filter.Uncategorized -> {
                where += "items.isDeleted = 0"
                where += "items.folders = ''"
            }
            Filter.Trash -> where += "items.isDeleted = 1"
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
        sql.append(" ORDER BY ").append(sort.sql)
        return dao.observe(SimpleSQLiteQuery(sql.toString(), args.toTypedArray()))
    }
}
