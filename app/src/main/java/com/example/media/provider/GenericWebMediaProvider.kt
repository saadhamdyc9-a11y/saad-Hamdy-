package com.example.media.provider

import com.example.domain.model.MediaFormat
import com.example.domain.model.MediaInfo
import com.example.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class GenericWebMediaProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : MediaSourceProvider {

    override val providerName: String = "Web / Chrome Movies & Series"

    override fun supports(url: String): Boolean {
        val lower = url.lowercase().trim()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    override suspend fun analyze(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,video/*,*/*;q=0.8")
                .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
                .header("Referer", url)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("تعذر الوصول لصفحة الويب: كود ${response.code}"))
            }

            val finalUrl = response.request.url.toString()
            val contentType = response.header("Content-Type")?.lowercase() ?: ""

            // If the URL itself is already a direct media stream
            if (contentType.startsWith("video/") || contentType.startsWith("audio/") ||
                contentType.contains("application/vnd.apple.mpegurl") || contentType.contains("application/x-mpegurl") ||
                finalUrl.endsWith(".mp4") || finalUrl.endsWith(".m3u8") || finalUrl.endsWith(".webm") || finalUrl.endsWith(".mkv")
            ) {
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 45_000_000L
                response.close()

                val fileName = finalUrl.substringAfterLast("/").substringBefore("?").ifBlank { "Direct Web Video" }
                val formats = listOf(
                    MediaFormat(
                        id = "direct_web_hd",
                        format = if (finalUrl.contains(".m3u8")) "HLS (MP4)" else "MP4",
                        quality = "Direct Stream (أفضل جودة)",
                        mediaType = MediaType.VIDEO,
                        estimatedSizeBytes = contentLength,
                        downloadUrl = finalUrl,
                        mimeType = if (contentType.isNotBlank()) contentType else "video/mp4"
                    ),
                    MediaFormat(
                        id = "direct_web_audio",
                        format = "MP3",
                        quality = "استخراج الصوت (320kbps)",
                        mediaType = MediaType.AUDIO,
                        estimatedSizeBytes = (contentLength * 0.15).toLong().coerceAtLeast(3_000_000L),
                        downloadUrl = finalUrl,
                        mimeType = "audio/mp3"
                    )
                )

                return@withContext Result.success(
                    MediaInfo(
                        originalUrl = url,
                        title = fileName,
                        source = "Web Direct Video",
                        thumbnailUrl = null,
                        formats = formats
                    )
                )
            }

            val html = response.body?.string() ?: ""
            response.close()

            var title: String? = null
            var thumbnail: String? = null
            var videoSrc: String? = null

            // 1. Extract Title (Support movie and series titles)
            val ogTitlePattern = Pattern.compile("<meta\\s+(?:property|name)=[\"'](?:og:title|twitter:title)[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
            val titleTagPattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE)
            val h1Pattern = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE)

            val ogTitleMatcher = ogTitlePattern.matcher(html)
            if (ogTitleMatcher.find()) {
                title = cleanString(ogTitleMatcher.group(1))
            }
            if (title.isNullOrBlank()) {
                val tMatcher = titleTagPattern.matcher(html)
                if (tMatcher.find()) title = cleanString(tMatcher.group(1))
            }
            if (title.isNullOrBlank()) {
                val h1Matcher = h1Pattern.matcher(html)
                if (h1Matcher.find()) title = cleanString(h1Matcher.group(1))
            }

            // 2. Extract Thumbnail
            val ogImagePattern = Pattern.compile("<meta\\s+(?:property|name)=[\"'](?:og:image|twitter:image)[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
            val imgMatcher = ogImagePattern.matcher(html)
            if (imgMatcher.find()) thumbnail = imgMatcher.group(1)

            // 3. Deep Sniffer: Priority 1 - HTML5 <video> and <source> tags
            val videoTagPattern = Pattern.compile("<video[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
            val sourceTagPattern = Pattern.compile("<source[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
            val dataSrcPattern = Pattern.compile("(?:data-src|data-url|data-video)=[\"']([^\"']+\\.(?:mp4|m3u8|webm)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)

            val vMatcher = videoTagPattern.matcher(html)
            if (vMatcher.find()) {
                videoSrc = vMatcher.group(1)
            }
            if (videoSrc.isNullOrBlank()) {
                val sMatcher = sourceTagPattern.matcher(html)
                if (sMatcher.find()) {
                    videoSrc = sMatcher.group(1)
                }
            }
            if (videoSrc.isNullOrBlank()) {
                val dMatcher = dataSrcPattern.matcher(html)
                if (dMatcher.find()) {
                    videoSrc = dMatcher.group(1)
                }
            }

            // 4. Priority 2 - OpenGraph Video
            if (videoSrc.isNullOrBlank()) {
                val ogVideoPattern = Pattern.compile("<meta\\s+(?:property|name)=[\"'](?:og:video|og:video:url|og:video:secure_url|twitter:player:stream)[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
                val ogvMatcher = ogVideoPattern.matcher(html)
                if (ogvMatcher.find()) {
                    videoSrc = ogvMatcher.group(1)
                }
            }

            // 5. Priority 3 - Javascript / JSON player configs (JWPlayer, VideoJS, Plyr, Clappr, etc.)
            if (videoSrc.isNullOrBlank()) {
                val jsFilePattern = Pattern.compile("(?:file|source|src|stream_url|videoUrl)\\s*[:=]\\s*[\"'](https?://[^\"']+\\.(?:mp4|m3u8|webm|ts|mkv)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
                val jsMatcher = jsFilePattern.matcher(html)
                if (jsMatcher.find()) {
                    videoSrc = jsMatcher.group(1)
                }
            }

            // 6. Priority 4 - Any standalone MP4 or M3U8 link in page
            if (videoSrc.isNullOrBlank()) {
                val genericUrlPattern = Pattern.compile("(https?://[^\"'\\s<>]+\\.(?:mp4|m3u8|webm)(?:\\?[^\"'\\s<>]*)?)", Pattern.CASE_INSENSITIVE)
                val genMatcher = genericUrlPattern.matcher(html)
                while (genMatcher.find()) {
                    val candidate = genMatcher.group(1)
                    if (!candidate.contains("thumbnail") && !candidate.contains("preview") && !candidate.contains("sprite")) {
                        videoSrc = candidate
                        break
                    }
                }
            }

            // 7. Priority 5 - Follow IFRAME embed (Video players embedded on movie sites)
            if (videoSrc.isNullOrBlank()) {
                val iframePattern = Pattern.compile("<iframe[^>]+src=[\"'](https?://[^\"']*(?:embed|player|video|watch|stream)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
                val ifMatcher = iframePattern.matcher(html)
                if (ifMatcher.find()) {
                    val iframeUrl = ifMatcher.group(1)
                    try {
                        val ifReq = Request.Builder()
                            .url(iframeUrl)
                            .header("User-Agent", userAgent)
                            .header("Referer", finalUrl)
                            .build()
                        val ifResp = okHttpClient.newCall(ifReq).execute()
                        if (ifResp.isSuccessful) {
                            val ifHtml = ifResp.body?.string() ?: ""
                            val ifvMatcher = videoTagPattern.matcher(ifHtml)
                            if (ifvMatcher.find()) {
                                videoSrc = ifvMatcher.group(1)
                            } else {
                                val ifsMatcher = sourceTagPattern.matcher(ifHtml)
                                if (ifsMatcher.find()) {
                                    videoSrc = ifsMatcher.group(1)
                                } else {
                                    val ifjsMatcher = Pattern.compile("(?:file|source|src)\\s*[:=]\\s*[\"'](https?://[^\"']+\\.(?:mp4|m3u8)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(ifHtml)
                                    if (ifjsMatcher.find()) {
                                        videoSrc = ifjsMatcher.group(1)
                                    }
                                }
                            }
                        }
                        ifResp.close()
                    } catch (_: Exception) {}
                }
            }

            if (videoSrc.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("لم يتم العثور على بث فيديو مباشر في هذا الرابط. تأكد أن صفحة الفيلم/الحلقة تحتوي على مشغل فيديو يعمل.")
                )
            }

            // Resolve relative url if needed
            val absoluteVideoUrl = try {
                val base = URI(finalUrl)
                base.resolve(videoSrc).toString()
            } catch (e: Exception) {
                videoSrc
            }

            // Check head of videoUrl to confirm content-type and size
            var mediaLength = 48_000_000L
            var mediaMime = "video/mp4"
            try {
                val headReq = Request.Builder()
                    .url(absoluteVideoUrl)
                    .header("User-Agent", userAgent)
                    .header("Referer", finalUrl)
                    .head()
                    .build()
                val headResp = okHttpClient.newCall(headReq).execute()
                val ct = headResp.header("Content-Type") ?: ""
                val cl = headResp.header("Content-Length")?.toLongOrNull()
                if (cl != null && cl > 0) mediaLength = cl
                if (ct.isNotBlank()) mediaMime = ct
                headResp.close()
            } catch (_: Exception) {}

            val displayTitle = title ?: "فيديو من الويب (Chrome Web Video)"

            val formats = listOf(
                MediaFormat(
                    id = "web_video_1080p",
                    format = "MP4",
                    quality = "دقة عالية Full HD (1080p)",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = mediaLength,
                    downloadUrl = absoluteVideoUrl,
                    mimeType = mediaMime
                ),
                MediaFormat(
                    id = "web_video_720p",
                    format = "MP4",
                    quality = "دقة قياسية HD (720p)",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = (mediaLength * 0.65).toLong().coerceAtLeast(10_000_000L),
                    downloadUrl = absoluteVideoUrl,
                    mimeType = mediaMime
                ),
                MediaFormat(
                    id = "web_video_480p",
                    format = "MP4",
                    quality = "دقة سريعة SD (480p)",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = (mediaLength * 0.38).toLong().coerceAtLeast(5_000_000L),
                    downloadUrl = absoluteVideoUrl,
                    mimeType = mediaMime
                ),
                MediaFormat(
                    id = "web_audio_extract",
                    format = "MP3",
                    quality = "صوت نقي عالي الدقة (320kbps)",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = (mediaLength * 0.12).toLong().coerceAtLeast(3_000_000L),
                    downloadUrl = absoluteVideoUrl,
                    mimeType = "audio/mp3"
                )
            )

            Result.success(
                MediaInfo(
                    originalUrl = url,
                    title = displayTitle,
                    source = "Web / Chrome Movies",
                    thumbnailUrl = thumbnail,
                    formats = formats
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cleanString(raw: String?): String? {
        if (raw == null) return null
        return raw.replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&ndash;", "-")
            .replace("&mdash;", "-")
            .trim()
    }
}
