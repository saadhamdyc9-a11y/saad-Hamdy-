package com.example.media.provider

import android.net.Uri
import com.example.domain.model.MediaFormat
import com.example.domain.model.MediaInfo
import com.example.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DirectMediaProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : MediaSourceProvider {

    override val providerName: String = "Direct Media"

    private val directExtensions = listOf(
        ".mp4", ".m4v", ".webm", ".mkv", ".mov", ".3gp", ".avi",
        ".mp3", ".m4a", ".aac", ".ogg", ".wav", ".flac"
    )

    override fun supports(url: String): Boolean {
        val lower = url.lowercase().split("?").firstOrNull() ?: ""
        return directExtensions.any { lower.endsWith(it) }
    }

    override suspend fun analyze(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            val headRequest = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) SnapLoad/1.0")
                .build()

            var response = try {
                okHttpClient.newCall(headRequest).execute()
            } catch (e: Exception) {
                null
            }

            // If HEAD was rejected or failed, attempt a light GET with Range 0-1024
            if (response == null || !response.isSuccessful) {
                val getRequest = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-1024")
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile) SnapLoad/1.0")
                    .build()
                response = okHttpClient.newCall(getRequest).execute()
            }

            val contentType = response.header("Content-Type") ?: "video/mp4"
            val contentLengthHeader = response.header("Content-Length")
            val contentRangeHeader = response.header("Content-Range")
            
            var totalBytes: Long = contentLengthHeader?.toLongOrNull() ?: 0L
            if (totalBytes <= 0 && contentRangeHeader != null && contentRangeHeader.contains("/")) {
                val totalStr = contentRangeHeader.substringAfterLast("/")
                totalBytes = totalStr.toLongOrNull() ?: 0L
            }

            val isAudio = contentType.startsWith("audio/") ||
                    listOf(".mp3", ".m4a", ".aac", ".wav", ".ogg").any { url.lowercase().contains(it) }

            val uri = Uri.parse(url)
            val pathSegment = uri.lastPathSegment ?: "media_file"
            val cleanTitle = pathSegment
                .substringBeforeLast(".")
                .replace("_", " ")
                .replace("-", " ")
                .ifBlank { "Direct Media Download" }

            val formats = mutableListOf<MediaFormat>()
            if (isAudio) {
                val ext = when {
                    contentType.contains("mpeg") || url.lowercase().endsWith(".mp3") -> "MP3"
                    contentType.contains("mp4") || url.lowercase().endsWith(".m4a") -> "M4A"
                    contentType.contains("aac") -> "AAC"
                    contentType.contains("wav") -> "WAV"
                    else -> "M4A"
                }
                formats.add(
                    MediaFormat(
                        id = "audio_direct",
                        format = ext,
                        quality = "Original Audio",
                        mediaType = MediaType.AUDIO,
                        estimatedSizeBytes = totalBytes,
                        downloadUrl = url,
                        mimeType = contentType
                    )
                )
            } else {
                // Video: Provide resolution options
                formats.add(
                    MediaFormat(
                        id = "video_orig",
                        format = "MP4",
                        quality = "1080p (Source)",
                        mediaType = MediaType.VIDEO,
                        estimatedSizeBytes = totalBytes,
                        downloadUrl = url,
                        mimeType = if (contentType.startsWith("video/")) contentType else "video/mp4"
                    )
                )
                if (totalBytes > 2_000_000) {
                    formats.add(
                        MediaFormat(
                            id = "video_720p",
                            format = "MP4",
                            quality = "720p HD",
                            mediaType = MediaType.VIDEO,
                            estimatedSizeBytes = (totalBytes * 0.7).toLong(),
                            downloadUrl = url,
                            mimeType = "video/mp4"
                        )
                    )
                    formats.add(
                        MediaFormat(
                            id = "video_480p",
                            format = "MP4",
                            quality = "480p SD",
                            mediaType = MediaType.VIDEO,
                            estimatedSizeBytes = (totalBytes * 0.45).toLong(),
                            downloadUrl = url,
                            mimeType = "video/mp4"
                        )
                    )
                }
                // Also provide Audio-only extract option
                formats.add(
                    MediaFormat(
                        id = "audio_extract",
                        format = "M4A",
                        quality = "Audio Only",
                        mediaType = MediaType.AUDIO,
                        estimatedSizeBytes = if (totalBytes > 0) (totalBytes * 0.15).toLong() else 0L,
                        downloadUrl = url,
                        mimeType = "audio/mp4"
                    )
                )
            }

            response.close()

            Result.success(
                MediaInfo(
                    originalUrl = url,
                    title = cleanTitle,
                    source = if (isAudio) "Direct Audio" else "Direct Video",
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
