package com.eaglelan.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.utils.io.charsets.Charsets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** The one HTTP client of the app (API, thumbnails, updates); each platform picks the engine. */
fun <T : HttpClientEngineConfig> createHttpClient(engine: HttpClientEngineFactory<T>, configure: T.() -> Unit = {}) =
    HttpClient(engine) {
        engine(configure)
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 60_000
        }
    }

@Serializable
data class PingDto(
    val app: String = "",
    val library: String = "",
    val items: Int = 0,
    /** 管理员: this token may rate items and move them to Eagle's trash from where the phone is now. */
    val canManage: Boolean = false,
)

@Serializable
data class FolderDto(
    val id: String,
    val name: String = "",
    val locked: Boolean = false,
    val children: List<FolderDto> = emptyList(),
)

@Serializable
data class ItemDto(
    val id: String,
    val name: String = "",
    val ext: String = "",
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Double = 0.0,
    val tags: List<String> = emptyList(),
    val folders: List<String> = emptyList(),
    val star: Int = 0,
    val annotation: String = "",
    val url: String = "",
    val btime: Long = 0,
    val mtime: Long = 0,
    val isDeleted: Boolean = false,
    val hasThumb: Boolean = false,
)

@Serializable
data class SyncDto(
    val cursor: Long,
    val items: List<ItemDto>,
    val ids: List<String>,
    val folders: List<FolderDto>,
)

class ApiException(message: String) : Exception(message)

internal suspend fun HttpResponse.text() = bodyAsText(Charsets.UTF_8)

class Api(private val client: HttpClient, val baseUrl: String, private val token: String) {

    private val tokenParam = token.encodeURLParameter()

    suspend fun ping(): PingDto = get("/api/ping")

    suspend fun sync(since: Long): SyncDto = get("/api/sync?since=$since")

    /** Loudness of a video, or null if the computer hasn't analysed it yet (404). */
    suspend fun loudness(id: String): LoudnessDto? {
        val resp = client.get("$baseUrl/api/loudness/$id") { header("X-Token", token) }
        return when {
            resp.status.value == 404 -> null
            !resp.status.isSuccess() -> throw ApiException("服务器返回 HTTP ${resp.status.value}")
            else -> AppJson.decodeFromString(LoudnessDto.serializer(), resp.text())
        }
    }

    /** Moves an item to Eagle's trash. The token goes in the header only; the server checks the rest. */
    suspend fun trash(id: String) = manage("trash", id, null)

    /** Sets an item's Eagle rating, 0 = none. */
    suspend fun setStar(id: String, star: Int) = manage("star", id, "{\"star\":${star.coerceIn(0, 5)}}")

    private suspend fun manage(action: String, id: String, json: String?) {
        val resp = client.post("$baseUrl/api/$action/${id.encodeURLParameter()}") {
            header("X-Token", token)
            if (json != null) setBody(TextContent(json, ContentType.Application.Json))
        }
        if (resp.status.isSuccess()) return
        val msg = runCatching {
            AppJson.parseToJsonElement(resp.text()).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()
        throw ApiException(msg ?: "服务器返回 HTTP ${resp.status.value}")
    }

    fun thumbUrl(id: String) = "$baseUrl/api/thumb/$id?token=$tokenParam"

    fun fileUrl(id: String) = "$baseUrl/api/file/$id?token=$tokenParam"

    private suspend inline fun <reified T> get(path: String): T {
        val resp = client.get(baseUrl + path) { header("X-Token", token) }
        when {
            resp.status.value == 401 -> throw ApiException("Token 不正确")
            !resp.status.isSuccess() -> throw ApiException("服务器返回 HTTP ${resp.status.value}")
        }
        return AppJson.decodeFromString<T>(resp.text())
    }

    companion object {
        const val DEFAULT_PORT = 41600

        /**
         * "192.168.1.100" -> "http://192.168.1.100:41600" (the phone server's default port).
         * A https:// address keeps its implicit 443, e.g. "https://media.example.com" stays as is.
         */
        fun normalizeBaseUrl(input: String): String {
            var s = input.trim().trimEnd('/')
            if (!s.contains("://")) s = "http://$s"
            val scheme = s.substringBefore("://").lowercase()
            val hostPart = s.substringAfter("://").substringBefore('/')
            if (!hostPart.contains(':') && scheme != "https") {
                s = s.replaceFirst(hostPart, "$hostPart:$DEFAULT_PORT")
            }
            return s
        }
    }
}
