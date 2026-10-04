package com.example.data.repository

import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {

    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    val activeDownloads: Flow<List<DownloadEntity>> = downloadDao.getActiveDownloads()
    val completedDownloads: Flow<List<DownloadEntity>> = downloadDao.getCompletedDownloads()
    val failedDownloads: Flow<List<DownloadEntity>> = downloadDao.getFailedDownloads()
    val recentDownloads: Flow<List<DownloadEntity>> = downloadDao.getRecentDownloads(5)

    suspend fun getDownloadById(id: Long): DownloadEntity? = downloadDao.getDownloadById(id)

    suspend fun findByFileName(fileName: String): DownloadEntity? = downloadDao.findByFileName(fileName)

    suspend fun insertDownload(entity: DownloadEntity): Long = downloadDao.insertDownload(entity)

    suspend fun updateDownload(entity: DownloadEntity) = downloadDao.updateDownload(entity)

    suspend fun updateProgress(id: Long, status: DownloadStatus, downloaded: Long, total: Long, error: String? = null) {
        downloadDao.updateProgress(id, status, downloaded, total, error)
    }

    suspend fun deleteDownload(id: Long) = downloadDao.deleteById(id)

    suspend fun clearAll() = downloadDao.clearAll()
}
