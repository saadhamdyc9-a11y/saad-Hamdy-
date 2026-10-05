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

class InstagramSourceProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MediaSourceProvider {

    override val providerName: String = "Instagram"

    override fun supports(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("instagram.com") || lower.contains("instagr.am")
    }

    override suspend fun analyze(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            // Attempt to retrieve OpenGraph tags from the public post
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val html = if (response.isSuccessful) response.body?.string() ?: "" else ""
            response.close()

            var title = "Instagram Reel"
            var thumbnail: String? = null
            var videoUrl: String? = null

            val ogTitlePattern = Pattern.compile("<meta\\s+property=[\"']og:title[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
            val ogImagePattern = Pattern.compile("<meta\\s+property=[\"']og:image[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
            val ogVideoPattern = Pattern.compile("<meta\\s+property=[\"']og:video(?::secure_url)?[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)

            val titleMatcher = ogTitlePattern.matcher(html)
            if (titleMatcher.find()) title = titleMatcher.group(1)?.replace("&quot;", "\"") ?: title

            val imgMatcher = ogImagePattern.matcher(html)
            if (imgMatcher.find()) thumbnail = imgMatcher.group(1)

            val videoMatcher = ogVideoPattern.matcher(html)
            if (videoMatcher.find()) videoUrl = videoMatcher.group(1)

            val formats = listOf(
                MediaFormat(
                    id = "ig_hd",
                    format = "MP4",
                    quality = "HD Video (Original)",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 18_000_000L,
                    downloadUrl = videoUrl ?: url,
                    mimeType = "video/mp4",
                    resolutionWidth = 1080,
                    resolutionHeight = 1920
                ),
                MediaFormat(
                    id = "ig_audio",
                    format = "MP3",
                    quality = "Audio MP3",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = 3_200_000L,
                    downloadUrl = videoUrl ?: url,
                    mimeType = "audio/mpeg"
                )
            )

            Result.success(
                MediaInfo(
                    originalUrl = url,
                    title = title,
                    source = "Instagram",
                    durationSeconds = 0,
                    thumbnailUrl = thumbnail,
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
