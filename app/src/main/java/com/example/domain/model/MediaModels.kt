package com.example.domain.model

enum class MediaType {
    VIDEO,
    AUDIO
}

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class MediaFormat(
    val id: String,
    val format: String,             // e.g. MP4, WEBM, M4A, MP3
    val quality: String,            // e.g. "1080p", "720p", "480p", "360p", "Audio"
    val mediaType: MediaType,
    val estimatedSizeBytes: Long,   // 0 if unknown
    val downloadUrl: String,
    val mimeType: String,
    val resolutionWidth: Int = 0,
    val resolutionHeight: Int = 0
)

data class MediaInfo(
    val originalUrl: String,
    val title: String,
    val source: String,             // e.g. "YouTube", "TikTok", "Direct Link", "Instagram", "Web", "Facebook"
    val durationSeconds: Long = 0,
    val thumbnailUrl: String? = null,
    val formats: List<MediaFormat> = emptyList(),
    val isProtected: Boolean = false,
    val protectionReason: String? = null,
    val playlistInfo: PlaylistInfo? = null
)

data class PlaylistItem(
    val id: String,
    val title: String,
    val originalUrl: String,
    val durationSeconds: Long = 0,
    val thumbnailUrl: String? = null,
    val isSelected: Boolean = true,
    val formats: List<MediaFormat> = emptyList()
)

data class PlaylistInfo(
    val playlistId: String,
    val title: String,
    val author: String = "",
    val items: List<PlaylistItem>,
    val thumbnailUrl: String? = null,
    val originalUrl: String
)
