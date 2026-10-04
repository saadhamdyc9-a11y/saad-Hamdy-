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
                    id = "x_720p",
                    format = "MP4",
                    quality = "720p HD",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 18_000_000L,
                    downloadUrl = url,
                    mimeType = "video/mp4",
                    resolutionWidth = 1280,
                    resolutionHeight = 720
                ),
                MediaFormat(
                    id = "x_480p",
                    format = "MP4",
                    quality = "480p SD",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 9_000_000L,
                    downloadUrl = url,
                    mimeType = "video/mp4",
                    resolutionWidth = 854,
                    resolutionHeight = 480
                ),
                MediaFormat(
                    id = "x_audio",
                    format = "M4A",
                    quality = "Audio Only",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = 2_100_000L,
                    downloadUrl = url,
                    mimeType = "audio/mp4"
                )
            )

            Result.success(
                MediaInfo(
                    originalUrl = url,
                    title = title,
                    source = "X / Twitter",
                    durationSeconds = 0,
                    thumbnailUrl = null,
                    formats = formats
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
