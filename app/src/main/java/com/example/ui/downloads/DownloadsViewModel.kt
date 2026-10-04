package com.example.ui.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadEntity
import com.example.data.repository.DownloadRepository
import com.example.downloader.DownloadEngine
import com.example.domain.model.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class DownloadFilterTab {
    ALL,
    ACTIVE,
    COMPLETED,
    FAILED
}

class DownloadsViewModel(
    private val repository: DownloadRepository,
    private val engine: DownloadEngine
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(DownloadFilterTab.ALL)
    val selectedTab: StateFlow<DownloadFilterTab> = _selectedTab.asStateFlow()

    val liveProgress = engine.liveProgress

    val downloadsList: StateFlow<List<DownloadEntity>> = combine(
        repository.allDownloads,
        _selectedTab
    ) { all, tab ->
        when (tab) {
            DownloadFilterTab.ALL -> all
            DownloadFilterTab.ACTIVE -> all.filter {
                it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.PAUSED ||
                it.status == DownloadStatus.PENDING
            }
            DownloadFilterTab.COMPLETED -> all.filter { it.status == DownloadStatus.COMPLETED }
            DownloadFilterTab.FAILED -> all.filter {
                it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: DownloadFilterTab) {
        _selectedTab.value = tab
    }

    fun pauseDownload(id: Long) {
        engine.pauseDownload(id)
    }

    fun resumeDownload(id: Long) {
        engine.resumeDownload(id)
    }

    fun cancelDownload(id: Long) {
        engine.cancelDownload(id)
    }

    fun retryDownload(id: Long) {
        engine.retryDownload(id)
    }

    fun deleteDownload(download: DownloadEntity, deletePhysicalFile: Boolean = true) {
        viewModelScope.launch {
            if (deletePhysicalFile) {
                val file = File(download.filePath)
                if (file.exists()) file.delete()
                val part = File("${download.filePath}.part")
                if (part.exists()) part.delete()
            }
            repository.deleteDownload(download.id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun shareMedia(context: Context, download: DownloadEntity) {
        val file = File(download.filePath)
        if (!file.exists()) return

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = download.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Media via SnapLoad")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    class Factory(
        private val repository: DownloadRepository,
        private val engine: DownloadEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DownloadsViewModel(repository, engine) as T
        }
    }
}
