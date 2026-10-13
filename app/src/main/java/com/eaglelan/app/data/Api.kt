package com.eaglelan.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

@Serializable
data class PingDto(val app: String = "", val library: String = "", val items: Int = 0)

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

class Api(private val client: OkHttpClient, val baseUrl: String, private val token: String) {

    private val tokenParam = URLEncoder.encode(token, "UTF-8")

    suspend fun ping(): PingDto = get("/api/ping")

    suspend fun sync(since: Long): SyncDto = get("/api/sync?since=$since")

    fun thumbUrl(id: String) = "$baseUrl/api/thumb/$id?token=$tokenParam"

    fun fileUrl(id: String) = "$baseUrl/api/file/$id?token=$tokenParam"

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl + path).header("X-Token", token).build()
        client.newCall(request).execute().use { resp ->
            when {
                resp.code == 401 -> throw ApiException("Token 不正确")
                !resp.isSuccessful -> throw ApiException("服务器返回 HTTP ${resp.code}")
            }
            AppJson.decodeFromString<T>(resp.body!!.string())
        }
    }

    companion object {
        const val DEFAULT_PORT = 41600

        /** "192.168.1.100" -> "http://192.168.1.100:41600" */
        fun normalizeBaseUrl(input: String): String {
            var s = input.trim().trimEnd('/')
            if (!s.contains("://")) s = "http://$s"
            val hostPart = s.substringAfter("://").substringBefore('/')
            if (!hostPart.contains(':')) {
                s = s.replaceFirst(hostPart, "$hostPart:$DEFAULT_PORT")
            }
            return s
        }
    }
}
