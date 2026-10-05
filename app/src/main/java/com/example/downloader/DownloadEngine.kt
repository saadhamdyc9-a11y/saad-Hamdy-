package com.example.downloader

import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import com.example.data.local.DownloadEntity
import com.example.data.repository.DownloadRepository
import com.example.domain.model.DownloadStatus
import com.example.domain.model.MediaFormat
import com.example.domain.model.MediaInfo
import com.example.domain.model.MediaType
import com.example.utils.StorageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class DownloadProgressInfo(
    val id: Long,
    val title: String,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val remainingSeconds: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progressPercent: Int = 0,
    val error: String? = null
)

class DownloadEngine(
    private val context: Context,
    private val repository: DownloadRepository,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val pausedFlags = ConcurrentHashMap<Long, AtomicBoolean>()

    private val _liveProgress = MutableStateFlow<Map<Long, DownloadProgressInfo>>(emptyMap())
    val liveProgress: StateFlow<Map<Long, DownloadProgressInfo>> = _liveProgress.asStateFlow()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun isFileExisting(mediaInfo: MediaInfo, format: MediaFormat): File {
        val ext = format.format.lowercase().let { if (it.startsWith(".")) it else ".$it" }
        val filename = StorageUtils.sanitizeFilename(mediaInfo.title, ext)
        val dir = getDestinationDir(format.mediaType)
        return File(dir, filename)
    }

    private fun getDestinationDir(mediaType: MediaType): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val snaploadDir = File(publicDownloads, "SnapLoad")
        if (!snaploadDir.exists()) {
            try {
                snaploadDir.mkdirs()
            } catch (_: Exception) {}
        }
        return if (snaploadDir.exists() && snaploadDir.canWrite()) {
            snaploadDir
        } else {
            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val subDir = File(appDir, "SnapLoad").apply { if (!exists()) mkdirs() }
            subDir
        }
    }

    suspend fun enqueueDownload(
        mediaInfo: MediaInfo,
        format: MediaFormat,
        overwrite: Boolean = false
    ): Result<Long> {
        val ext = format.format.lowercase().let { if (it.startsWith(".")) it else ".$it" }
        val filename = StorageUtils.sanitizeFilename(mediaInfo.title, ext)
        val dir = getDestinationDir(format.mediaType)
        val finalFile = File(dir, filename)

        if (finalFile.exists() && finalFile.length() > 0 && !overwrite) {
            val existing = repository.findByFileName(filename)
            if (existing != null && existing.status == DownloadStatus.COMPLETED) {
                return Result.failure(FileAlreadyExistsException(finalFile, null, "File already exists"))
            }
        }

        // Check storage space
        val requiredBytes = if (format.estimatedSizeBytes > 0) format.estimatedSizeBytes else 20_000_000L
        val availableBytes = StorageUtils.getAvailableDiskSpaceBytes(dir)
        if (availableBytes < requiredBytes) {
            return Result.failure(
                IllegalStateException("Not enough storage space. Required: ${StorageUtils.formatBytes(requiredBytes)}, Available: ${StorageUtils.formatBytes(availableBytes)}")
            )
        }

        val entity = DownloadEntity(
            originalUrl = mediaInfo.originalUrl,
            downloadUrl = format.downloadUrl,
            title = mediaInfo.title,
            source = mediaInfo.source,
            thumbnailUrl = mediaInfo.thumbnailUrl,
            filePath = finalFile.absolutePath,
            fileName = filename,
            mimeType = format.mimeType,
            mediaType = format.mediaType,
            quality = format.quality,
            format = format.format,
            totalBytes = format.estimatedSizeBytes,
            downloadedBytes = 0,
            status = DownloadStatus.DOWNLOADING
        )

        val id = repository.insertDownload(entity)
        startDownloadJob(id, entity)
        return Result.success(id)
    }

    fun pauseDownload(id: Long) {
        pausedFlags[id]?.set(true)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        _liveProgress.update { map ->
            val curr = map[id] ?: return@update map
            map + (id to curr.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0))
        }

        engineScope.launch {
            val entity = repository.getDownloadById(id)
            if (entity != null) {
                val current = _liveProgress.value[id]
                val downloaded = current?.downloadedBytes ?: entity.downloadedBytes
                repository.updateProgress(id, DownloadStatus.PAUSED, downloaded, entity.totalBytes)
            }
            checkForegroundServiceStatus()
        }
    }

    fun resumeDownload(id: Long) {
        engineScope.launch {
            val entity = repository.getDownloadById(id) ?: return@launch
            pausedFlags[id] = AtomicBoolean(false)
            startDownloadJob(id, entity)
        }
    }

    fun cancelDownload(id: Long) {
        pausedFlags[id]?.set(true)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)

        _liveProgress.update { it - id }
        notificationManager.cancel(id.toInt())

        engineScope.launch {
            val entity = repository.getDownloadById(id)
            if (entity != null) {
                val partFile = File("${entity.filePath}.part")
                if (partFile.exists()) partFile.delete()
                repository.updateProgress(id, DownloadStatus.CANCELLED, 0, entity.totalBytes)
            }
            checkForegroundServiceStatus()
        }
    }

    fun retryDownload(id: Long) {
        resumeDownload(id)
    }

    private fun startDownloadJob(id: Long, entity: DownloadEntity) {
        DownloadService.startService(context)
        pausedFlags[id] = AtomicBoolean(false)

        val job = engineScope.launch {
            val partFile = File("${entity.filePath}.part")
            val finalFile = File(entity.filePath)
            var startByte = 0L

            if (partFile.exists()) {
                startByte = partFile.length()
            }

            _liveProgress.update { map ->
                map + (id to DownloadProgressInfo(
                    id = id,
                    title = entity.title,
                    downloadedBytes = startByte,
                    totalBytes = entity.totalBytes,
                    status = DownloadStatus.DOWNLOADING
                ))
            }

            try {
                var streamUrl = entity.downloadUrl
                if (entity.source.equals("YouTube", ignoreCase = true) ||
                    streamUrl.contains("youtube.com") || streamUrl.contains("youtu.be")) {
                    val resolved = com.example.media.provider.YouTubeSourceProvider.resolveDirectStream(
                        okHttpClient,
                        entity.originalUrl,
                        entity.quality,
                        entity.format
                    )
                    if (resolved.isFailure) {
                        throw resolved.exceptionOrNull() ?: IllegalStateException("Could not resolve media stream URL")
                    }
                    streamUrl = resolved.getOrThrow()
                } else if (streamUrl.contains("tiktok.com")) {
                    val formBody = okhttp3.FormBody.Builder().add("url", entity.originalUrl).build()
                    val req = Request.Builder()
                        .url("https://www.tikwm.com/api/")
                        .post(formBody)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                    val resp = okHttpClient.newCall(req).execute()
                    val b = resp.body?.string() ?: ""
                    resp.close()
                    if (b.isNotBlank()) {
                        val j = org.json.JSONObject(b)
                        if (j.optInt("code") == 0 && j.has("data")) {
                            val d = j.getJSONObject("data")
                            val play = d.optString("play", "")
                            val hd = d.optString("hdplay", "")
                            val music = d.optString("music", "")
                            streamUrl = when {
                                entity.format.equals("MP3", ignoreCase = true) && music.isNotBlank() -> music
                                hd.isNotBlank() -> hd
                                play.isNotBlank() -> play
                                else -> streamUrl
                            }
                        }
                    }
                } else if (entity.source.equals("Instagram", ignoreCase = true) &&
                    (streamUrl.contains("instagram.com") || streamUrl.contains("instagr.am"))) {
                    val resolved = com.example.media.provider.YouTubeSourceProvider.resolveDirectStream(
                        okHttpClient,
                        entity.originalUrl,
                        entity.quality,
                        entity.format
                    )
                    if (resolved.isSuccess) {
                        streamUrl = resolved.getOrThrow()
                    }
                } else if (entity.source.contains("Twitter", ignoreCase = true) ||
                    entity.source.contains("X", ignoreCase = true) ||
                    streamUrl.contains("twitter.com") || streamUrl.contains("x.com")) {
                    val resolved = com.example.media.provider.YouTubeSourceProvider.resolveDirectStream(
                        okHttpClient,
                        entity.originalUrl,
                        entity.quality,
                        entity.format
                    )
                    if (resolved.isSuccess) {
                        streamUrl = resolved.getOrThrow()
                    }
                }

                val requestBuilder = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

                if (startByte > 0) {
                    requestBuilder.header("Range", "bytes=$startByte-")
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful && response.code != 206) {
                    // If range not satisfiable, restart from 0
                    if (response.code == 416) {
                        partFile.delete()
                        startByte = 0L
                    } else {
                        throw IllegalStateException("Server returned HTTP ${response.code}")
                    }
                }

                val responseBody = response.body
                    ?: throw IllegalStateException("Empty response body from media source")

                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                if (contentType.contains("text/html") || contentType.contains("text/plain")) {
                    throw IllegalStateException("The media URL returned a web page (HTML) instead of a direct video/audio stream. Direct media stream required.")
                }

                val contentLength = responseBody.contentLength()
                val totalBytes = if (contentLength > 0) {
                    startByte + contentLength
                } else if (entity.totalBytes > 0) {
                    entity.totalBytes
                } else 0L

                val isAppend = startByte > 0 && response.code == 206
                val outputStream = if (isAppend) {
                    FileOutputStream(partFile, true)
                } else {
                    FileOutputStream(partFile, false)
                }

                val inputStream = responseBody.byteStream()
                val buffer = ByteArray(32 * 1024) // 32 KB chunk buffer
                var bytesRead: Int
                var totalDownloaded = startByte
                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L
                var speed: Long = 0
                var lastUiUpdateTime = 0L

                outputStream.use { out ->
                    inputStream.use { input ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (pausedFlags[id]?.get() == true) {
                                break
                            }

                            out.write(buffer, 0, bytesRead)
                            totalDownloaded += bytesRead
                            bytesSinceLastCalc += bytesRead

                            val now = System.currentTimeMillis()
                            val speedDelta = now - lastSpeedCalcTime
                            if (speedDelta >= 500) {
                                speed = (bytesSinceLastCalc * 1000) / speedDelta
                                bytesSinceLastCalc = 0L
                                lastSpeedCalcTime = now
                            }

                            val uiDelta = now - lastUiUpdateTime
                            if (uiDelta >= 300) {
                                lastUiUpdateTime = now
                                val remainingBytes = if (totalBytes > totalDownloaded) totalBytes - totalDownloaded else 0L
                                val remainingSecs = if (speed > 0) remainingBytes / speed else 0L
                                val percent = if (totalBytes > 0) ((totalDownloaded * 100) / totalBytes).toInt() else 0

                                _liveProgress.update { map ->
                                    map + (id to DownloadProgressInfo(
                                        id = id,
                                        title = entity.title,
                                        downloadedBytes = totalDownloaded,
                                        totalBytes = totalBytes,
                                        speedBytesPerSec = speed,
                                        remainingSeconds = remainingSecs,
                                        status = DownloadStatus.DOWNLOADING,
                                        progressPercent = percent
                                    ))
                                }

                                // Update notification
                                val notif = DownloadNotificationHelper.buildProgressNotification(
                                    context = context,
                                    downloadId = id,
                                    title = entity.title,
                                    progressPercent = percent,
                                    downloadedBytes = totalDownloaded,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed
                                )
                                notificationManager.notify(id.toInt(), notif.build())
                            }
                        }
                        out.flush()
                    }
                }

                if (pausedFlags[id]?.get() == true) {
                    // Paused or cancelled cleanly
                    return@launch
                }

                // Download completed! Rename partFile to finalFile
                if (finalFile.exists()) finalFile.delete()
                val renameSuccess = partFile.renameTo(finalFile)
                if (!renameSuccess) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                if (finalFile.length() < 1024) {
                    throw IllegalStateException("Downloaded file is empty or corrupted (${finalFile.length()} bytes).")
                }

                // Register with Android MediaStore immediately
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(finalFile.absolutePath),
                        arrayOf(entity.mimeType)
                    ) { _, _ -> }
                    val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                        data = android.net.Uri.fromFile(finalFile)
                    }
                    context.sendBroadcast(mediaScanIntent)
                } catch (_: Exception) {}

                repository.updateProgress(id, DownloadStatus.COMPLETED, totalDownloaded, totalBytes)
                _liveProgress.update { map ->
                    map + (id to DownloadProgressInfo(
                        id = id,
                        title = entity.title,
                        downloadedBytes = totalDownloaded,
                        totalBytes = totalDownloaded,
                        status = DownloadStatus.COMPLETED,
                        progressPercent = 100
                    ))
                }

                DownloadNotificationHelper.showCompletionNotification(
                    context = context,
                    downloadId = id,
                    title = entity.title,
                    success = true
                )

            } catch (e: Exception) {
                if (e is CancellationException || pausedFlags[id]?.get() == true) {
                    return@launch
                }
                val errorMsg = e.message ?: "Network error during download"
                repository.updateProgress(id, DownloadStatus.FAILED, startByte, entity.totalBytes, errorMsg)
                _liveProgress.update { map ->
                    map + (id to DownloadProgressInfo(
                        id = id,
                        title = entity.title,
                        status = DownloadStatus.FAILED,
                        error = errorMsg
                    ))
                }
                DownloadNotificationHelper.showCompletionNotification(
                    context = context,
                    downloadId = id,
                    title = entity.title,
                    success = false
                )
            } finally {
                activeJobs.remove(id)
                checkForegroundServiceStatus()
            }
        }

        activeJobs[id] = job
    }

    private fun checkForegroundServiceStatus() {
        if (activeJobs.isEmpty()) {
            DownloadService.stopService(context)
        }
    }
}
