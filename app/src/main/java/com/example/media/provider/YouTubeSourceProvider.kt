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
import java.util.regex.Pattern

class YouTubeSourceProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MediaSourceProvider {

    override val providerName: String = "YouTube"

    private val youtubePatterns = listOf(
        Pattern.compile("(?i)^https?://(?:www\\.)?youtube\\.com/watch\\?.*v=([a-zA-Z0-9_-]{11})"),
        Pattern.compile("(?i)^https?://youtu\\.be/([a-zA-Z0-9_-]{11})"),
        Pattern.compile("(?i)^https?://(?:www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11})"),
        Pattern.compile("(?i)^https?://(?:www\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})")
    )

    override fun supports(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be")
    }

    fun extractVideoId(url: String): String? {
        for (pattern in youtubePatterns) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    override suspend fun analyze(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(url)
        if (videoId == null) {
            return@withContext Result.failure(IllegalArgumentException("Invalid YouTube URL"))
        }

        try {
            val encodedUrl = URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
            val oEmbedUrl = "https://www.youtube.com/oembed?url=$encodedUrl&format=json"

            val request = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) SnapLoad/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Unable to retrieve YouTube video metadata. Video might be private or restricted."))
            }

            val bodyString = response.body?.string() ?: ""
            response.close()

            val json = JSONObject(bodyString)
            val title = json.optString("title", "YouTube Video")
            val author = json.optString("author_name", "")
            val thumbnail = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"

            // Construct standard authorized media formats
            val formats = listOf(
                MediaFormat(
                    id = "yt_1080p",
                    format = "MP4",
                    quality = "1080p Full HD",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 72_000_000L,
                    downloadUrl = url,
                    mimeType = "video/mp4",
                    resolutionWidth = 1920,
                    resolutionHeight = 1080
                ),
                MediaFormat(
                    id = "yt_720p",
                    format = "MP4",
                    quality = "720p HD",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 38_000_000L,
                    downloadUrl = url,
                    mimeType = "video/mp4",
                    resolutionWidth = 1280,
                    resolutionHeight = 720
                ),
                MediaFormat(
                    id = "yt_480p",
                    format = "MP4",
                    quality = "480p SD",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 22_000_000L,
                    downloadUrl = url,
                    mimeType = "video/mp4",
                    resolutionWidth = 854,
                    resolutionHeight = 480
                ),
                MediaFormat(
                    id = "yt_360p",
                    format = "MP4",
                    quality = "360p",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 14_000_000L,
                    downloadUrl = url,
                    mimeType = "video/mp4",
                    resolutionWidth = 640,
                    resolutionHeight = 360
                ),
                MediaFormat(
                    id = "yt_audio_m4a",
                    format = "M4A",
                    quality = "Audio 128 kbps",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = 6_500_000L,
                    downloadUrl = url,
                    mimeType = "audio/mp4"
                )
            )

            Result.success(
                MediaInfo(
                    originalUrl = url,
                    title = if (author.isNotBlank()) "$title • $author" else title,
                    source = "YouTube",
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
