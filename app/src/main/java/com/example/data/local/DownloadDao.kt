package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('DOWNLOADING', 'PENDING', 'PAUSED') ORDER BY timestamp DESC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY timestamp DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('FAILED', 'CANCELLED') ORDER BY timestamp DESC")
    fun getFailedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDownloads(limit: Int = 5): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE fileName = :fileName LIMIT 1")
    suspend fun findByFileName(fileName: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(entity: DownloadEntity): Long

    @Update
    suspend fun updateDownload(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloaded, totalBytes = :total, errorMessage = :error WHERE id = :id")
    suspend fun updateProgress(id: Long, status: DownloadStatus, downloaded: Long, total: Long, error: String? = null)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}
