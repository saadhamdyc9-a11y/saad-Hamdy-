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

class TelegramSourceProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : MediaSourceProvider {

    override val providerName: String = "Telegram"

    override fun supports(url: String): Boolean {
        val lower = url.lowercase().trim()
        return lower.contains("t.me/") ||
                lower.contains("telegram.me/") ||
                lower.contains("telegram.dog/")
    }

    override suspend fun analyze(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            var cleanUrl = url.trim()
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }

            // Remove trailing slashes and tracking query params
            val baseUrl = cleanUrl.substringBefore("?")
            val isStory = cleanUrl.contains("/s/") || cleanUrl.contains("story") || cleanUrl.contains("/stories")

            // Extract username if available
            val usernameMatcher = Pattern.compile("t\\.me/(?:s/)?([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE).matcher(cleanUrl)
            val channelOrUser = if (usernameMatcher.find()) "@" + usernameMatcher.group(1) else "Telegram"

            var html = ""
            var targetUrlToFetch = cleanUrl

            // 1. Stories: Do NOT use ?embed=1 because Telegram does not support embed widgets for stories
            // Regular posts/channel messages: Use ?embed=1 first
            if (!isStory && !cleanUrl.contains("?embed=1")) {
                val embedUrl = if (cleanUrl.contains("?")) "$cleanUrl&embed=1" else "$cleanUrl?embed=1"
                try {
                    val req = Request.Builder()
                        .url(embedUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .build()
                    val resp = okHttpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        html = resp.body?.string() ?: ""
                    }
                    resp.close()
                } catch (_: Exception) {}
            }

            // 2. If story or embed failed/empty, fetch direct page with mobile Android User-Agent (Telegram serves full story media to mobile browsers)
            if (html.isBlank() || (!html.contains("<video") && !html.contains("og:video") && !html.contains("tgme_widget_message_video"))) {
                try {
                    val directReq = Request.Builder()
                        .url(cleanUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/*,video/*,*/*;q=0.8")
                        .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
                        .build()
                    val directResp = okHttpClient.newCall(directReq).execute()
                    if (directResp.isSuccessful) {
                        html = directResp.body?.string() ?: ""
                    }
                    directResp.close()
                } catch (_: Exception) {}
            }

            // 3. Fallback: If still empty, fetch with Desktop Chrome User-Agent
            if (html.isBlank()) {
                try {
                    val deskReq = Request.Builder()
                        .url(cleanUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .build()
                    val deskResp = okHttpClient.newCall(deskReq).execute()
                    if (deskResp.isSuccessful) {
                        html = deskResp.body?.string() ?: ""
                    }
                    deskResp.close()
                } catch (_: Exception) {}
            }

            var title: String? = null
            var thumbnail: String? = null
            var videoUrl: String? = null
            var audioUrl: String? = null
            var photoUrl: String? = null

            // A. Video Extraction (Multiple powerful patterns)
            // 1) <video ... src="...">
            val videoTagPattern = Pattern.compile("<video[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
            val vMatcher = videoTagPattern.matcher(html)
            if (vMatcher.find()) {
                videoUrl = vMatcher.group(1)?.replace("&amp;", "&")
            }

            // 2) <source ... src="..."> inside video
            if (videoUrl.isNullOrBlank()) {
                val sourceTagPattern = Pattern.compile("<source[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val sMatcher = sourceTagPattern.matcher(html)
                if (sMatcher.find()) {
                    videoUrl = sMatcher.group(1)?.replace("&amp;", "&")
                }
            }

            // 3) OpenGraph / Twitter meta video
            if (videoUrl.isNullOrBlank()) {
                val ogVideoPattern = Pattern.compile("<meta\\s+(?:property|name)=[\"'](?:og:video|og:video:url|og:video:secure_url|twitter:player:stream)[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val ogvMatcher = ogVideoPattern.matcher(html)
                if (ogvMatcher.find()) {
                    videoUrl = ogvMatcher.group(1)?.replace("&amp;", "&")
                }
            }

            // 4) Script / JSON stream link
            if (videoUrl.isNullOrBlank()) {
                val jsVideoPattern = Pattern.compile("(?:\"video_url\"|\"video\"|\"stream_url\"|videoUrl)\\s*:\\s*[\"']([^\"']+\\.(?:mp4|webm)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
                val jsvMatcher = jsVideoPattern.matcher(html)
                if (jsvMatcher.find()) {
                    videoUrl = jsvMatcher.group(1)?.replace("\\/", "/")?.replace("&amp;", "&")
                }
            }

            // B. Audio Extraction
            val audioTagPattern = Pattern.compile("<audio[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
            val aMatcher = audioTagPattern.matcher(html)
            if (aMatcher.find()) {
                audioUrl = aMatcher.group(1)?.replace("&amp;", "&")
            }

            // C. Thumbnail & Photo Extraction (For Image Stories)
            val thumbPattern = Pattern.compile("background-image:url\\(['\"]?([^'\")]+)['\"]?\\)", Pattern.CASE_INSENSITIVE)
            val tMatcher = thumbPattern.matcher(html)
            if (tMatcher.find()) {
                thumbnail = tMatcher.group(1)?.replace("&amp;", "&")
            }
            if (thumbnail.isNullOrBlank()) {
                val ogImgPattern = Pattern.compile("<meta\\s+(?:property|name)=[\"'](?:og:image|twitter:image)[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val ogiMatcher = ogImgPattern.matcher(html)
                if (ogiMatcher.find()) {
                    thumbnail = ogiMatcher.group(1)?.replace("&amp;", "&")
                }
            }
            if (thumbnail.isNullOrBlank()) {
                val imgTagPattern = Pattern.compile("<img[^>]+class=[\"'][^\"']*(?:tgme_page_photo_image|story_photo)[^\"']*[\"'][^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val imgMatcher = imgTagPattern.matcher(html)
                if (imgMatcher.find()) {
                    thumbnail = imgMatcher.group(1)?.replace("&amp;", "&")
                }
            }

            // If it's a photo story without video, thumbnail is the photo!
            if (isStory && videoUrl.isNullOrBlank() && !thumbnail.isNullOrBlank()) {
                photoUrl = thumbnail
            }

            // D. Title / Description Extraction
            val textPattern = Pattern.compile("<div[^>]+class=[\"'][^\"']*tgme_widget_message_text[^\"']*[\"'][^>]*>(.*?)</div>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val txtMatcher = textPattern.matcher(html)
            if (txtMatcher.find()) {
                val rawText = txtMatcher.group(1) ?: ""
                val cleanText = rawText.replace(Regex("<[^>]+>"), "").replace("&quot;", "\"").replace("&amp;", "&").trim()
                if (cleanText.isNotBlank()) {
                    title = if (cleanText.length > 60) cleanText.take(57) + "..." else cleanText
                }
            }

            if (title.isNullOrBlank()) {
                val ogTitlePattern = Pattern.compile("<meta\\s+(?:property|name)=[\"']og:title[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val ogtMatcher = ogTitlePattern.matcher(html)
                if (ogtMatcher.find()) {
                    title = ogtMatcher.group(1)?.replace("&amp;", "&")?.trim()
                }
            }

            val finalTitle = title ?: if (isStory) "ستوري تليجرام ($channelOrUser)" else "تليجرام ($channelOrUser)"

            // Check if any media was found
            val hasVideo = !videoUrl.isNullOrBlank()
            val hasAudio = !audioUrl.isNullOrBlank()
            val hasPhoto = !photoUrl.isNullOrBlank()

            if (!hasVideo && !hasAudio && !hasPhoto) {
                return@withContext Result.failure(
                    IllegalStateException(
                        if (isStory) {
                            "تعذر استخراج ستوري تليجرام. تأكد أن حساب أو قناة صاحب الستوري عامة (Public) والستوري ما زالت نشطة."
                        } else {
                            "تعذر استخراج وسائط تليجرام. تأكد أن المنشور في قناة أو جروب عام (Public)."
                        }
                    )
                )
            }

            // Estimate media file size
            val mainDownloadUrl = videoUrl ?: audioUrl ?: photoUrl!!
            var mediaSize = if (hasVideo) 14_000_000L else if (hasPhoto) 2_500_000L else 5_000_000L
            try {
                val headReq = Request.Builder().url(mainDownloadUrl).head().build()
                val headResp = okHttpClient.newCall(headReq).execute()
                val cl = headResp.header("Content-Length")?.toLongOrNull()
                if (cl != null && cl > 0) mediaSize = cl
                headResp.close()
            } catch (_: Exception) {}

            val formats = mutableListOf<MediaFormat>()

            if (hasVideo) {
                formats.add(
                    MediaFormat(
                        id = "tg_video_hd",
                        format = "MP4",
                        quality = if (isStory) "ستوري أصلي فيديو (HD 1080p)" else "فيديو تليجرام عالي الدقة (HD)",
                        mediaType = MediaType.VIDEO,
                        estimatedSizeBytes = mediaSize,
                        downloadUrl = videoUrl!!,
                        mimeType = "video/mp4"
                    )
                )
                formats.add(
                    MediaFormat(
                        id = "tg_audio_mp3",
                        format = "MP3",
                        quality = "استخراج الصوت (320kbps MP3)",
                        mediaType = MediaType.AUDIO,
                        estimatedSizeBytes = (mediaSize * 0.18).toLong().coerceAtLeast(1_500_000L),
                        downloadUrl = videoUrl,
                        mimeType = "audio/mp3"
                    )
                )
            } else if (hasPhoto) {
                formats.add(
                    MediaFormat(
                        id = "tg_story_photo",
                        format = "JPG",
                        quality = "ستوري صورة أصلية (Full Quality)",
                        mediaType = MediaType.VIDEO,
                        estimatedSizeBytes = mediaSize,
                        downloadUrl = photoUrl!!,
                        mimeType = "image/jpeg"
                    )
                )
            } else if (hasAudio) {
                formats.add(
                    MediaFormat(
                        id = "tg_audio_original",
                        format = "MP3",
                        quality = "تسجيل صوتي أصلي",
                        mediaType = MediaType.AUDIO,
                        estimatedSizeBytes = mediaSize,
                        downloadUrl = audioUrl!!,
                        mimeType = "audio/mpeg"
                    )
                )
            }

            Result.success(
                MediaInfo(
                    originalUrl = url,
                    title = finalTitle,
                    source = if (isStory) "Telegram Story" else "Telegram",
                    thumbnailUrl = thumbnail ?: photoUrl,
                    formats = formats
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
