package com.example.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadEntity
import com.example.data.repository.DownloadRepository
import com.example.downloader.DownloadEngine
import com.example.domain.model.MediaFormat
import com.example.domain.model.MediaInfo
import com.example.media.provider.MediaAnalyzer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val urlInput: String = "",
    val detectedClipboardUrl: String? = null,
    val isAnalyzing: Boolean = false,
    val mediaInfo: MediaInfo? = null,
    val errorMessage: String? = null,
    val duplicateFileForDownload: Pair<MediaInfo, MediaFormat>? = null,
    val lastEnqueuedDownloadId: Long? = null
)

class HomeViewModel(
    private val downloadRepository: DownloadRepository,
    private val downloadEngine: DownloadEngine,
    private val mediaAnalyzer: MediaAnalyzer = MediaAnalyzer()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val recentDownloads: StateFlow<List<DownloadEntity>> = downloadRepository.recentDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val liveProgress = downloadEngine.liveProgress

    private var analyzeJob: Job? = null

    fun onUrlChanged(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl, errorMessage = null) }
    }

    fun onClearUrl() {
        _uiState.update { it.copy(urlInput = "", errorMessage = null) }
    }

    fun onClipboardDetected(url: String?) {
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            if (_uiState.value.urlInput.isBlank()) {
                _uiState.update { it.copy(detectedClipboardUrl = url) }
            }
        } else {
            _uiState.update { it.copy(detectedClipboardUrl = null) }
        }
    }

    fun useClipboardUrl() {
        val detected = _uiState.value.detectedClipboardUrl ?: return
        _uiState.update { it.copy(urlInput = detected, detectedClipboardUrl = null) }
        analyzeUrl(detected)
    }

    fun analyzeUrl(urlToAnalyze: String = _uiState.value.urlInput) {
        val trimmed = urlToAnalyze.trim()
        val validation = mediaAnalyzer.validateUrl(trimmed)
        if (validation is MediaAnalyzer.ValidationResult.Error) {
            _uiState.update { it.copy(errorMessage = validation.message) }
            return
        }

        analyzeJob?.cancel()
        _uiState.update { it.copy(isAnalyzing = true, errorMessage = null, mediaInfo = null) }

        analyzeJob = viewModelScope.launch {
            val result = mediaAnalyzer.analyze(trimmed)
            result.onSuccess { info ->
                _uiState.update { it.copy(isAnalyzing = false, mediaInfo = info, errorMessage = null) }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = ex.message ?: "Could not analyze media link. Please verify the URL."
                    )
                }
            }
        }
    }

    fun cancelAnalysis() {
        analyzeJob?.cancel()
        _uiState.update { it.copy(isAnalyzing = false) }
    }

    fun dismissMediaDetails() {
        _uiState.update { it.copy(mediaInfo = null) }
    }

    fun startDownload(format: MediaFormat, overwrite: Boolean = false) {
        val media = _uiState.value.mediaInfo ?: return
        viewModelScope.launch {
            val result = downloadEngine.enqueueDownload(media, format, overwrite)
            result.onSuccess { downloadId ->
                _uiState.update {
                    it.copy(
                        mediaInfo = null,
                        duplicateFileForDownload = null,
                        lastEnqueuedDownloadId = downloadId
                    )
                }
            }.onFailure { ex ->
                if (ex is FileAlreadyExistsException) {
                    _uiState.update { it.copy(duplicateFileForDownload = Pair(media, format)) }
                } else {
                    _uiState.update { it.copy(errorMessage = ex.message) }
                }
            }
        }
    }

    fun dismissDuplicateDialog() {
        _uiState.update { it.copy(duplicateFileForDownload = null) }
    }

    fun startBatchDownload(
        playlistInfo: com.example.domain.model.PlaylistInfo,
        formatType: String,
        selectedIds: Set<String>
    ) {
        val selectedItems = playlistInfo.items.filter { it.id in selectedIds }
        viewModelScope.launch {
            for (item in selectedItems) {
                val format = item.formats.find { it.id.endsWith(formatType) } ?: item.formats.firstOrNull()
                if (format != null) {
                    val media = MediaInfo(
                        originalUrl = item.originalUrl,
                        title = item.title,
                        source = playlistInfo.title,
                        thumbnailUrl = item.thumbnailUrl,
                        formats = listOf(format)
                    )
                    downloadEngine.enqueueDownload(media, format, false)
                }
            }
            _uiState.update { it.copy(mediaInfo = null) }
        }
    }

    class Factory(
        private val repository: DownloadRepository,
        private val engine: DownloadEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, engine) as T
        }
    }
}
