package com.example.media.provider

import com.example.domain.model.MediaFormat
import com.example.domain.model.MediaInfo
import com.example.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class FacebookSourceProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : MediaSourceProvider {

    override val providerName: String = "Facebook"

    override fun supports(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("facebook.com") ||
                lower.contains("fb.watch") ||
                lower.contains("fb.me") ||
                lower.contains("fb.gg") ||
                lower.contains("m.facebook.com")
    }

    override suspend fun analyze(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            val isStory = url.lowercase().contains("/stories/") || url.lowercase().contains("story.php")

            // Use browser User-Agent to retrieve OpenGraph and embedded JSON
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9,ar;q=0.8")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            val html = if (response.isSuccessful) response.body?.string() ?: "" else ""
            response.close()

            var title = if (isStory) "Facebook Story" else "Facebook Video"
            var thumbnail: String? = null
            var hdVideoUrl: String? = null
            var sdVideoUrl: String? = null

            // 1. Title Extraction
            val ogTitlePattern = Pattern.compile("<meta\\s+property=[\"']og:title[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
            val titleMatcher = ogTitlePattern.matcher(html)
            if (titleMatcher.find()) {
                val extracted = titleMatcher.group(1)?.replace("&quot;", "\"")?.replace("&amp;", "&")
                if (!extracted.isNullOrBlank()) title = extracted
            }

            // 2. Thumbnail Extraction
            val ogImagePattern = Pattern.compile("<meta\\s+property=[\"']og:image[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
            val imgMatcher = ogImagePattern.matcher(html)
            if (imgMatcher.find()) {
                thumbnail = imgMatcher.group(1)?.replace("&amp;", "&")
            }

            // 3. HD Video Extraction
            val hdPatterns = listOf(
                Pattern.compile("\"browser_native_hd_url\"\\s*:\\s*\"(https?:[^\"]+)\""),
                Pattern.compile("\"hd_src\"\\s*:\\s*\"(https?:[^\"]+)\""),
                Pattern.compile("\"hd_src_no_ratelimit\"\\s*:\\s*\"(https?:[^\"]+)\""),
                Pattern.compile("\"playable_url_quality_hd\"\\s*:\\s*\"(https?:[^\"]+)\"")
            )
            for (p in hdPatterns) {
                val m = p.matcher(html)
                if (m.find()) {
                    hdVideoUrl = cleanFacebookEscapedUrl(m.group(1))
                    break
                }
            }

            // 4. SD Video Extraction
            val sdPatterns = listOf(
                Pattern.compile("\"browser_native_sd_url\"\\s*:\\s*\"(https?:[^\"]+)\""),
                Pattern.compile("\"sd_src\"\\s*:\\s*\"(https?:[^\"]+)\""),
                Pattern.compile("\"sd_src_no_ratelimit\"\\s*:\\s*\"(https?:[^\"]+)\""),
                Pattern.compile("\"playable_url\"\\s*:\\s*\"(https?:[^\"]+)\""),
                Pattern.compile("<meta\\s+property=[\"']og:video(?::secure_url)?[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
            )
            for (p in sdPatterns) {
                val m = p.matcher(html)
                if (m.find()) {
                    sdVideoUrl = cleanFacebookEscapedUrl(m.group(1))
                    break
                }
            }

            val primaryVideoUrl = hdVideoUrl ?: sdVideoUrl ?: finalUrl
            val formats = mutableListOf<MediaFormat>()

            if (!hdVideoUrl.isNullOrBlank()) {
                formats.add(
                    MediaFormat(
                        id = "fb_hd",
                        format = "MP4",
                        quality = if (isStory) "Story HD Video" else "HD 1080p/720p",
                        mediaType = MediaType.VIDEO,
                        estimatedSizeBytes = 0,
                        downloadUrl = hdVideoUrl,
                        mimeType = "video/mp4",
                        resolutionWidth = 1920,
                        resolutionHeight = 1080
                    )
                )
            }

            formats.add(
                MediaFormat(
                    id = "fb_sd",
                    format = "MP4",
                    quality = if (isStory) "Story Video" else "SD 480p/360p",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 0,
                    downloadUrl = sdVideoUrl ?: primaryVideoUrl,
                    mimeType = "video/mp4",
                    resolutionWidth = 854,
                    resolutionHeight = 480
                )
            )

            // High Quality Audio Extraction (MP3)
            formats.add(
                MediaFormat(
                    id = "fb_audio",
                    format = "MP3",
                    quality = "Audio Only (320kbps MP3)",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = 0,
                    downloadUrl = primaryVideoUrl,
                    mimeType = "audio/mpeg"
                )
            )

            val info = MediaInfo(
                originalUrl = url,
                title = if (isStory) "قصة فيسبوك (Facebook Story)" else title,
                source = "Facebook",
                thumbnailUrl = thumbnail,
                formats = formats
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cleanFacebookEscapedUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val unescaped = raw.replace("\\/", "/").replace("\\u0025", "%").replace("\\u0026", "&")
            URLDecoder.decode(unescaped, "UTF-8")
        } catch (e: Exception) {
            raw.replace("\\/", "/")
        }
    }
}
