package com.eaglelan.app

import com.eaglelan.app.data.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Streams [url] into [file], reporting 0..1 progress (falls back to [expectedSize] without Content-Length). */
internal suspend fun HttpClient.downloadTo(url: String, file: File, expectedSize: Long, onProgress: (Float) -> Unit) =
    withContext(Dispatchers.IO) {
        prepareGet(url).execute { resp ->
            if (!resp.status.isSuccess()) throw ApiException("下载失败：HTTP ${resp.status.value}")
            val total = resp.contentLength()?.takeIf { it > 0 } ?: expectedSize
            val input = resp.bodyAsChannel()
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.readAvailable(buf, 0, buf.size)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        file
    }
