package com.example.media.provider

import com.example.domain.model.MediaFormat
import com.example.domain.model.MediaInfo
import com.example.domain.model.MediaType
import com.example.domain.model.PlaylistItem
import com.example.domain.model.PlaylistInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    fun extractPlaylistId(url: String): String? {
        val pattern = Pattern.compile("(?i)[?&]list=([a-zA-Z0-9_-]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    override suspend fun analyze(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(url)
        // If it's a playlist URL without a specific single video or explicitly /playlist
        if (playlistId != null && (url.contains("/playlist") || extractVideoId(url) == null)) {
            val playlistResult = analyzePlaylist(url, playlistId)
            if (playlistResult.isSuccess) {
                return@withContext playlistResult
            }
        }

        val videoId = extractVideoId(url)
        if (videoId == null) {
            if (playlistId != null) {
                return@withContext analyzePlaylist(url, playlistId)
            }
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

            val cleanUrl = "https://www.youtube.com/watch?v=$videoId"

            val formats = listOf(
                MediaFormat(
                    id = "yt_1080",
                    format = "MP4",
                    quality = "1080p Full HD",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 65_000_000L,
                    downloadUrl = cleanUrl,
                    mimeType = "video/mp4",
                    resolutionWidth = 1920,
                    resolutionHeight = 1080
                ),
                MediaFormat(
                    id = "yt_720",
                    format = "MP4",
                    quality = "720p HD",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 38_000_000L,
                    downloadUrl = cleanUrl,
                    mimeType = "video/mp4",
                    resolutionWidth = 1280,
                    resolutionHeight = 720
                ),
                MediaFormat(
                    id = "yt_480",
                    format = "MP4",
                    quality = "480p SD",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 22_000_000L,
                    downloadUrl = cleanUrl,
                    mimeType = "video/mp4",
                    resolutionWidth = 854,
                    resolutionHeight = 480
                ),
                MediaFormat(
                    id = "yt_360",
                    format = "MP4",
                    quality = "360p",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 14_000_000L,
                    downloadUrl = cleanUrl,
                    mimeType = "video/mp4",
                    resolutionWidth = 640,
                    resolutionHeight = 360
                ),
                MediaFormat(
                    id = "yt_mp3",
                    format = "MP3",
                    quality = "Audio 320 kbps",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = 7_500_000L,
                    downloadUrl = cleanUrl,
                    mimeType = "audio/mpeg"
                ),
                MediaFormat(
                    id = "yt_m4a",
                    format = "M4A",
                    quality = "Audio 128 kbps",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = 5_000_000L,
                    downloadUrl = cleanUrl,
                    mimeType = "audio/mp4"
                )
            )

            Result.success(
                MediaInfo(
                    originalUrl = cleanUrl,
                    title = if (author.isNotBlank()) "$title • $author" else title,
                    source = "YouTube",
                    durationSeconds = 0,
                    thumbnailUrl = thumbnail,
                    formats = formats,
                    isProtected = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        suspend fun resolveDirectStream(
            client: OkHttpClient,
            mediaUrl: String,
            quality: String,
            format: String
        ): Result<String> = withContext(Dispatchers.IO) {
            try {
                val formatParam = when {
                    format.equals("MP3", ignoreCase = true) -> "mp3"
                    format.equals("M4A", ignoreCase = true) -> "m4a"
                    quality.contains("1080") -> "1080"
                    quality.contains("720") -> "720"
                    quality.contains("480") -> "480"
                    quality.contains("360") -> "360"
                    else -> "720"
                }

                val encodedUrl = URLEncoder.encode(mediaUrl, "UTF-8")
                val startEndpoint = "https://loader.to/ajax/download.php?url=$encodedUrl&format=$formatParam"

                val initReq = Request.Builder()
                    .url(startEndpoint)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val initResp = client.newCall(initReq).execute()
                val bodyStr = initResp.body?.string() ?: ""
                initResp.close()

                val json = JSONObject(bodyStr)
                val directUrl = json.optString("download_url", "")
                if (directUrl.isNotBlank() && directUrl.startsWith("http")) {
                    return@withContext Result.success(directUrl)
                }

                val progressUrl = json.optString("progress_url", "")
                if (progressUrl.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Stream resolution failed. Please try another quality."))
                }

                // Poll progress URL up to 15 iterations (~20s)
                for (i in 0 until 15) {
                    delay(1500)
                    val progReq = Request.Builder()
                        .url(progressUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()

                    val progResp = client.newCall(progReq).execute()
                    val progBody = progResp.body?.string() ?: ""
                    progResp.close()

                    val progJson = JSONObject(progBody)
                    val resolvedUrl = progJson.optString("download_url", "")
                    if (resolvedUrl.isNotBlank() && resolvedUrl.startsWith("http")) {
                        return@withContext Result.success(resolvedUrl)
                    }

                    val textStatus = progJson.optString("text", "")
                    if (textStatus.contains("Error", ignoreCase = true)) {
                        return@withContext Result.failure(IllegalStateException("Media server error: $textStatus"))
                    }
                }

                Result.failure(IllegalStateException("Media stream resolution timed out. Please try again."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun analyzePlaylist(url: String, playlistId: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://www.youtube.com/playlist?list=$playlistId")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9,ar;q=0.8")
                .build()

            val resp = okHttpClient.newCall(req).execute()
            val html = if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            resp.close()

            var playlistTitle = "YouTube Playlist"
            val titlePattern = Pattern.compile("<title>(.*?)(?: - YouTube)?</title>", Pattern.CASE_INSENSITIVE)
            val tm = titlePattern.matcher(html)
            if (tm.find()) {
                val found = tm.group(1)?.trim()
                if (!found.isNullOrBlank()) playlistTitle = found
            }

            // Extract all video IDs in order
            val videoIdPattern = Pattern.compile("\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\"")
            val vm = videoIdPattern.matcher(html)
            val uniqueIds = linkedSetOf<String>()
            while (vm.find()) {
                uniqueIds.add(vm.group(1))
            }

            if (uniqueIds.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("لم نتمكن من قراءة عناصر قائمة التشغيل. يرجى التأكد من أن القائمة عامة وليست خاصة."))
            }

            val items = uniqueIds.mapIndexed { index, vid ->
                val videoUrl = "https://www.youtube.com/watch?v=$vid"
                val formats = listOf(
                    MediaFormat(
                        id = "yt_pl_${vid}_hd",
                        format = "MP4",
                        quality = "720p HD",
                        mediaType = MediaType.VIDEO,
                        estimatedSizeBytes = 0,
                        downloadUrl = videoUrl,
                        mimeType = "video/mp4"
                    ),
                    MediaFormat(
                        id = "yt_pl_${vid}_mp3",
                        format = "MP3",
                        quality = "Audio MP3",
                        mediaType = MediaType.AUDIO,
                        estimatedSizeBytes = 0,
                        downloadUrl = videoUrl,
                        mimeType = "audio/mpeg"
                    )
                )
                PlaylistItem(
                    id = vid,
                    title = "فيديو ${index + 1}",
                    originalUrl = videoUrl,
                    thumbnailUrl = "https://img.youtube.com/vi/$vid/mqdefault.jpg",
                    isSelected = true,
                    formats = formats
                )
            }

            val playlistInfo = PlaylistInfo(
                playlistId = playlistId,
                title = playlistTitle,
                items = items,
                thumbnailUrl = items.firstOrNull()?.thumbnailUrl ?: "https://img.youtube.com/vi/default.jpg",
                originalUrl = url
            )

            val batchFormats = listOf(
                MediaFormat(
                    id = "batch_hd_mp4",
                    format = "MP4",
                    quality = "تحميل الكل: فيديو HD 720p",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 0,
                    downloadUrl = url,
                    mimeType = "video/mp4"
                ),
                MediaFormat(
                    id = "batch_audio_mp3",
                    format = "MP3",
                    quality = "تحميل الكل: صوت MP3 (High Quality)",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = 0,
                    downloadUrl = url,
                    mimeType = "audio/mpeg"
                )
            )

            val info = MediaInfo(
                originalUrl = url,
                title = "قائمة تشغيل: $playlistTitle (${items.size} فيديو)",
                source = "YouTube Playlist",
                thumbnailUrl = playlistInfo.thumbnailUrl,
                formats = batchFormats,
                playlistInfo = playlistInfo
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
