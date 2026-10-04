package com.example.media.provider

import com.example.domain.model.MediaInfo

interface MediaSourceProvider {
    val providerName: String
    fun supports(url: String): Boolean
    suspend fun analyze(url: String): Result<MediaInfo>
}
