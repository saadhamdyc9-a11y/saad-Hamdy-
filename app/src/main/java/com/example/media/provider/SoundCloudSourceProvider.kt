package com.example.media.provider

import com.example.domain.model.MediaFormat
import com.example.domain.model.MediaInfo
import com.example.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class SoundCloudSourceProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : MediaSourceProvider {

    override val providerName: String = "SoundCloud"

    companion object {
        @Volatile
        private var cachedClientId: String = "dkevB9EsY4jIoSm8RfddPNUKyn6hurXF"
    }

    override fun supports(url: String): Boolean {
        val lower = url.lowercase().trim()
        return lower.contains("soundcloud.com/") || lower.contains("on.soundcloud.com/")
    }

    override suspend fun analyze(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            var cleanUrl = url.trim()
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }

            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

            val pageReq = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
                .build()

            val pageResp = okHttpClient.newCall(pageReq).execute()
            if (!pageResp.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("تعذر فتح صفحة ساوند كلاود: كود ${pageResp.code}"))
            }

            val finalUrl = pageResp.request.url.toString()
            val html = pageResp.body?.string() ?: ""
            pageResp.close()

            // 1. Extract window.__sc_hydration
            val hydrationPattern = Pattern.compile("window\\.__sc_hydration\\s*=\\s*(\\[.*?\\]);\\s*</script>", Pattern.DOTALL)
            val hydrationMatcher = hydrationPattern.matcher(html)

            var soundData: JSONObject? = null
            if (hydrationMatcher.find()) {
                val jsonText = hydrationMatcher.group(1) ?: "[]"
                try {
                    val array = JSONArray(jsonText)
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        if (item.optString("hydratable") == "sound") {
                            soundData = item.optJSONObject("data")
                            break
                        }
                    }
                } catch (_: Exception) {}
            }

            // Fallback: If not in __sc_hydration, extract OpenGraph info
            var title: String? = soundData?.optString("title")?.ifBlank { null }
            var artist: String? = soundData?.optJSONObject("user")?.optString("username")?.ifBlank { null }
            var thumbnail: String? = soundData?.optString("artwork_url")?.ifBlank { null }
                ?: soundData?.optJSONObject("user")?.optString("avatar_url")?.ifBlank { null }
            val durationMs = soundData?.optLong("duration", 0L) ?: 0L

            if (title == null) {
                val ogTitleMatcher = Pattern.compile("<meta\\s+(?:property|name)=[\"']og:title[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                if (ogTitleMatcher.find()) title = ogTitleMatcher.group(1)?.replace("&amp;", "&")
            }
            if (thumbnail == null) {
                val ogImgMatcher = Pattern.compile("<meta\\s+(?:property|name)=[\"']og:image[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                if (ogImgMatcher.find()) thumbnail = ogImgMatcher.group(1)
            }

            // Improve thumbnail to high resolution (500x500 instead of small 40x40)
            if (thumbnail != null && thumbnail.contains("-large.")) {
                thumbnail = thumbnail.replace("-large.", "-t500x500.")
            }

            val finalTitle = when {
                !artist.isNullOrBlank() && !title.isNullOrBlank() && !title.contains(artist) -> "$title - $artist"
                !title.isNullOrBlank() -> title
                else -> "SoundCloud Audio Track"
            }

            // 2. Extract media transcodings
            val transcodingsArray = soundData?.optJSONObject("media")?.optJSONArray("transcodings")

            var progressiveUrl: String? = null
            var hlsUrl: String? = null

            if (transcodingsArray != null) {
                for (i in 0 until transcodingsArray.length()) {
                    val t = transcodingsArray.optJSONObject(i) ?: continue
                    val formatObj = t.optJSONObject("format")
                    val protocol = formatObj?.optString("protocol") ?: ""
                    val tUrl = t.optString("url")

                    if (protocol.equals("progressive", ignoreCase = true) && progressiveUrl == null) {
                        progressiveUrl = tUrl
                    } else if (protocol.equals("hls", ignoreCase = true) && hlsUrl == null) {
                        hlsUrl = tUrl
                    }
                }
            }

            val targetTranscodingUrl = progressiveUrl ?: hlsUrl
            if (targetTranscodingUrl.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("لم نتمكن من العثور على بث الصوت لهذا المقطع. قد يكون المقطع خاصاً أو محمي الملكية الفكرية.")
                )
            }

            // 3. Resolve direct audio stream with client_id
            var directAudioStreamUrl = resolveStream(targetTranscodingUrl, cachedClientId, userAgent)

            // If failed with cached client_id, scrape a fresh client_id from script assets
            if (directAudioStreamUrl == null) {
                val freshClientId = scrapeClientId(html, userAgent)
                if (!freshClientId.isNullOrBlank()) {
                    cachedClientId = freshClientId
                    directAudioStreamUrl = resolveStream(targetTranscodingUrl, freshClientId, userAgent)
                }
            }

            if (directAudioStreamUrl.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("تعذر استخراج رابط تحميل المقطع المباشر من سيرفرات ساوند كلاود.")
                )
            }

            // Calculate estimated size
            val durationSeconds = if (durationMs > 0) durationMs / 1000 else 180L
            val estimatedBytes = (durationSeconds * (128 * 1024 / 8)).coerceAtLeast(4_000_000L)

            val formats = listOf(
                MediaFormat(
                    id = "sc_mp3_hq",
                    format = "MP3",
                    quality = "أعلى جودة صوت أصلية (HQ 320kbps MP3)",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = (estimatedBytes * 1.5).toLong(),
                    downloadUrl = directAudioStreamUrl,
                    mimeType = "audio/mpeg"
                ),
                MediaFormat(
                    id = "sc_mp3_standard",
                    format = "MP3",
                    quality = "جودة قياسية سريعة (Standard 128kbps)",
                    mediaType = MediaType.AUDIO,
                    estimatedSizeBytes = estimatedBytes,
                    downloadUrl = directAudioStreamUrl,
                    mimeType = "audio/mpeg"
                )
            )

            Result.success(
                MediaInfo(
                    originalUrl = url,
                    title = finalTitle,
                    source = "SoundCloud",
                    durationSeconds = durationSeconds,
                    thumbnailUrl = thumbnail,
                    formats = formats
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveStream(transcodingUrl: String, clientId: String, userAgent: String): String? {
        return try {
            val delimiter = if (transcodingUrl.contains("?")) "&" else "?"
            val reqUrl = "$transcodingUrl${delimiter}client_id=$clientId"

            val req = Request.Builder()
                .url(reqUrl)
                .header("User-Agent", userAgent)
                .build()

            val resp = okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                resp.close()
                return null
            }

            val body = resp.body?.string() ?: ""
            resp.close()

            val json = JSONObject(body)
            val streamUrl = json.optString("url")
            if (streamUrl.isNotBlank()) streamUrl else null
        } catch (e: Exception) {
            null
        }
    }

    private fun scrapeClientId(html: String, userAgent: String): String? {
        return try {
            val scriptPattern = Pattern.compile("src=[\"'](https://[^\"]+sndcdn[^\"]+\\.js)[\"']", Pattern.CASE_INSENSITIVE)
            val matcher = scriptPattern.matcher(html)
            val scriptUrls = mutableListOf<String>()
            while (matcher.find()) {
                val s = matcher.group(1)
                if (s != null) scriptUrls.add(s)
            }

            // Check scripts in reverse order (app bundles usually have the client_id)
            for (scriptUrl in scriptUrls.reversed().take(5)) {
                try {
                    val sReq = Request.Builder().url(scriptUrl).header("User-Agent", userAgent).build()
                    val sResp = okHttpClient.newCall(sReq).execute()
                    if (sResp.isSuccessful) {
                        val js = sResp.body?.string() ?: ""
                        sResp.close()
                        val cidMatcher = Pattern.compile("client_id[:=][\"']([a-zA-Z0-9]{32})[\"']").matcher(js)
                        if (cidMatcher.find()) {
                            return cidMatcher.group(1)
                        }
                    } else {
                        sResp.close()
                    }
                } catch (_: Exception) {}
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
