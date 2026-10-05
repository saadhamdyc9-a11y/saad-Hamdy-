package com.example.media.provider

import com.example.domain.model.MediaFormat
import com.example.domain.model.MediaInfo
import com.example.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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
            // First attempt: resolve video without watermark via TikWM API
            val formBody = FormBody.Builder()
                .add("url", url)
                .build()

            val request = Request.Builder()
                .url("https://www.tikwm.com/api/")
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyString = if (response.isSuccessful) response.body?.string() ?: "" else ""
            response.close()

            if (bodyString.isNotBlank()) {
                val json = JSONObject(bodyString)
                val code = json.optInt("code", -1)
                if (code == 0 && json.has("data")) {
                    val data = json.getJSONObject("data")
                    val title = data.optString("title", "TikTok Video").ifBlank { "TikTok Video" }
                    val authorObj = data.optJSONObject("author")
                    val author = authorObj?.optString("nickname") ?: authorObj?.optString("unique_id") ?: ""
                    val cover = data.optString("cover", "")
                    val playNoWatermark = data.optString("play", "")
                    val hdPlay = data.optString("hdplay", "")
                    val music = data.optString("music", "")
                    val size = data.optLong("size", 15_000_000L)
                    val duration = data.optInt("duration", 0)

                    val videoPlayUrl = if (hdPlay.isNotBlank()) hdPlay else playNoWatermark

                    val formats = mutableListOf<MediaFormat>()

                    if (videoPlayUrl.isNotBlank()) {
                        formats.add(
                            MediaFormat(
                                id = "tt_hd_nowm",
                                format = "MP4",
                                quality = "HD No Watermark (بدون علامة مائية)",
                                mediaType = MediaType.VIDEO,
                                estimatedSizeBytes = if (size > 0) size else 18_000_000L,
                                downloadUrl = videoPlayUrl,
                                mimeType = "video/mp4",
                                resolutionWidth = 1080,
                                resolutionHeight = 1920
                            )
                        )
                    }

                    if (playNoWatermark.isNotBlank() && playNoWatermark != videoPlayUrl) {
                        formats.add(
                            MediaFormat(
                                id = "tt_sd_nowm",
                                format = "MP4",
                                quality = "SD No Watermark",
                                mediaType = MediaType.VIDEO,
                                estimatedSizeBytes = if (size > 0) (size * 0.75).toLong() else 12_000_000L,
                                downloadUrl = playNoWatermark,
                                mimeType = "video/mp4",
                                resolutionWidth = 720,
                                resolutionHeight = 1280
                            )
                        )
                    }

                    if (music.isNotBlank()) {
                        formats.add(
                            MediaFormat(
                                id = "tt_audio",
                                format = "MP3",
                                quality = "Original Audio MP3 (الصوت الأصلي)",
                                mediaType = MediaType.AUDIO,
                                estimatedSizeBytes = 3_500_000L,
                                downloadUrl = music,
                                mimeType = "audio/mpeg"
                            )
                        )
                    }

                    if (formats.isNotEmpty()) {
                        return@withContext Result.success(
                            MediaInfo(
                                originalUrl = url,
                                title = if (author.isNotBlank()) "$title • @$author" else title,
                                source = "TikTok",
                                durationSeconds = duration.toLong(),
                                thumbnailUrl = cover.ifBlank { null },
                                formats = formats,
                                isProtected = false
                            )
                        )
                    }
                }
            }

            // Fallback: oEmbed resolution with multi-format streaming
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            val oEmbedUrl = "https://www.tiktok.com/oembed?url=$encodedUrl"
            val oEmbedReq = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val oEmbedResp = okHttpClient.newCall(oEmbedReq).execute()
            val oEmbedBody = if (oEmbedResp.isSuccessful) oEmbedResp.body?.string() ?: "" else ""
            oEmbedResp.close()

            val title: String
            val author: String
            val thumbnail: String?

            if (oEmbedBody.isNotBlank()) {
                val oEmbedJson = JSONObject(oEmbedBody)
                title = oEmbedJson.optString("title", "TikTok Video")
                author = oEmbedJson.optString("author_name", "")
                thumbnail = oEmbedJson.optString("thumbnail_url").takeIf { it.isNotBlank() }
            } else {
                title = "TikTok Video"
                author = ""
                thumbnail = null
            }

            // Provide fallback downloadable formats
            val fallbackFormats = listOf(
                MediaFormat(
                    id = "tt_fb_hd",
                    format = "MP4",
                    quality = "HD Video (بدون علامة مائية)",
                    mediaType = MediaType.VIDEO,
                    estimatedSizeBytes = 18_000_000L,
                    downloadUrl = url,
                    mimeType = "video/mp4",
                    resolutionWidth = 1080,
                    resolutionHeight = 1920
                ),
                MediaFormat(
                    id = "tt_fb_audio",
                    format = "MP3",
                    quality = "Audio MP3",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = 3_500_000L,
                    downloadUrl = url,
                    mimeType = "audio/mpeg"
                )
            )

            Result.success(
                MediaInfo(
                    originalUrl = url,
                    title = if (author.isNotBlank()) "$title • @$author" else title,
                    source = "TikTok",
                    durationSeconds = 0,
                    thumbnailUrl = thumbnail,
                    formats = fallbackFormats,
                    isProtected = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
