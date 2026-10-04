package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.DownloadStatus
import com.example.domain.model.MediaType

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalUrl: String,
    val downloadUrl: String,
    val title: String,
    val source: String,
    val thumbnailUrl: String?,
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val mediaType: MediaType,
    val quality: String,
    val format: String,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)
