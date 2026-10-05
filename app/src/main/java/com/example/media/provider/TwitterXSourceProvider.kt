package com.example.media.provider

import com.example.domain.model.MediaFormat
import com.example.domain.model.MediaInfo
import com.example.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class TwitterXSourceProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MediaSourceProvider {

    override val providerName: String = "X / Twitter"

    override fun supports(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("twitter.com") || lower.contains("x.com")
    }

    override suspend fun analyze(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val oEmbedUrl = "https://publish.twitter.com/oembed?url=$encodedUrl"

            val request = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) SnapLoad/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            var title = "X (Twitter) Media"
            var author = ""
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                author = json.optString("author_name", "")
                val html = json.optString("html", "")
                title = if (author.isNotBlank()) "Post by $author" else "X Post"
            }
            response.close()

            val formats = listOf(
                MediaFormat(
                    id = "x_1080p",
                    format = "MP4",
                    quality = "1080p Full HD",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 22_000_000L,
                    downloadUrl = url,
                    mimeType = "video/mp4",
                    resolutionWidth = 1080,
                    resolutionHeight = 1920
                ),
                MediaFormat(
                    id = "x_720p",
                    format = "MP4",
                    quality = "720p HD Video",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 12_000_000L,
                    downloadUrl = url,
                    mimeType = "video/mp4",
                    resolutionWidth = 720,
                    resolutionHeight = 1280
                ),
                MediaFormat(
                    id = "x_audio",
                    format = "MP3",
                    quality = "Audio MP3",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = 3_000_000L,
                    downloadUrl = url,
                    mimeType = "audio/mpeg"
                )
            )

            Result.success(
                MediaInfo(
                    originalUrl = url,
                    title = title,
                    source = "X / Twitter",
                    durationSeconds = 0,
                    thumbnailUrl = null,
                    formats = formats,
                    isProtected = false,
                    protectionReason = null
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
