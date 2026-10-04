package com.example.media.provider

import com.example.domain.model.MediaFormat
import com.example.domain.model.MediaInfo
import com.example.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class GenericWebMediaProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : MediaSourceProvider {

    override val providerName: String = "Web Media"

    override fun supports(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    override suspend fun analyze(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Unable to access page: HTTP ${response.code}"))
            }

            val html = response.body?.string() ?: ""
            response.close()

            val ogTitlePattern = Pattern.compile("<meta\\s+(?:property|name)=[\"'](?:og:title|twitter:title)[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
            val ogImagePattern = Pattern.compile("<meta\\s+(?:property|name)=[\"'](?:og:image|twitter:image)[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
            val ogVideoPattern = Pattern.compile("<meta\\s+(?:property|name)=[\"'](?:og:video|og:video:url|og:video:secure_url|twitter:player:stream)[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
            val videoTagPattern = Pattern.compile("<video[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

            var title: String? = null
            var thumbnail: String? = null
            var videoSrc: String? = null

            val titleMatcher = ogTitlePattern.matcher(html)
            if (titleMatcher.find()) title = titleMatcher.group(1)?.replace("&amp;", "&")?.replace("&#39;", "'")

            val imgMatcher = ogImagePattern.matcher(html)
            if (imgMatcher.find()) thumbnail = imgMatcher.group(1)

            val videoMatcher = ogVideoPattern.matcher(html)
            if (videoMatcher.find()) {
                videoSrc = videoMatcher.group(1)
            } else {
                val tagMatcher = videoTagPattern.matcher(html)
                if (tagMatcher.find()) {
                    videoSrc = tagMatcher.group(1)
                }
            }

            val finalDownloadUrl = videoSrc ?: url
            val displayTitle = title ?: "Online Media Stream"

            val formats = listOf(
                MediaFormat(
                    id = "web_video_standard",
                    format = "MP4",
                    quality = "Standard Quality",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 18_000_000L,
                    downloadUrl = finalDownloadUrl,
                    mimeType = "video/mp4"
                ),
                MediaFormat(
                    id = "web_audio_extract",
                    format = "M4A",
                    quality = "Audio Only",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = 3_500_000L,
                    downloadUrl = finalDownloadUrl,
                    mimeType = "audio/mp4"
                )
            )

            Result.success(
                MediaInfo(
                    originalUrl = url,
                    title = displayTitle,
                    source = "Web Media",
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
