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

class TikTokSourceProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MediaSourceProvider {

    override val providerName: String = "TikTok"

    override fun supports(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("tiktok.com")
    }

    override suspend fun analyze(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val oEmbedUrl = "https://www.tiktok.com/oembed?url=$encodedUrl"

            val request = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) SnapLoad/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Unable to retrieve TikTok media info. Content might be private."))
            }

            val body = response.body?.string() ?: ""
            response.close()

            val json = JSONObject(body)
            val title = json.optString("title", "TikTok Video")
            val author = json.optString("author_name", "")
            val thumbnail = json.optString("thumbnail_url").takeIf { it.isNotBlank() }

            val formats = listOf(
                MediaFormat(
                    id = "tt_hd",
                    format = "MP4",
                    quality = "1080p HD",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 28_000_000L,
                    downloadUrl = url,
                    mimeType = "video/mp4",
                    resolutionWidth = 1080,
                    resolutionHeight = 1920
                ),
                MediaFormat(
                    id = "tt_sd",
                    format = "MP4",
                    quality = "720p SD",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 14_000_000L,
                    downloadUrl = url,
                    mimeType = "video/mp4",
                    resolutionWidth = 720,
                    resolutionHeight = 1280
                ),
                MediaFormat(
                    id = "tt_audio",
                    format = "M4A",
                    quality = "Audio Only",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = 3_200_000L,
                    downloadUrl = url,
                    mimeType = "audio/mp4"
                )
            )

            Result.success(
                MediaInfo(
                    originalUrl = url,
                    title = if (author.isNotBlank()) "$title • @$author" else title,
                    source = "TikTok",
                    durationSeconds = 0,
                    thumbnailUrl = thumbnail,
                    formats = formats
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
