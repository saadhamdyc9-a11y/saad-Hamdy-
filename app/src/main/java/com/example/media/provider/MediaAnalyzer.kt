package com.example.media.provider

import com.example.domain.model.MediaInfo
import java.net.URI

class MediaAnalyzer(
    private val providers: List<MediaSourceProvider> = listOf(
        DirectMediaProvider(),
        YouTubeSourceProvider(),
        TikTokSourceProvider(),
        InstagramSourceProvider(),
        TwitterXSourceProvider(),
        GenericWebMediaProvider()
    )
) {

    fun validateUrl(rawUrl: String): ValidationResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Error("No link entered.")
        }
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return ValidationResult.Error("Please enter a valid URL starting with https://")
        }
        return try {
            val uri = URI(trimmed)
            if (uri.host.isNullOrBlank()) {
                ValidationResult.Error("Please enter a valid URL.")
            } else {
                ValidationResult.Valid(trimmed)
            }
        } catch (e: Exception) {
            ValidationResult.Error("Please enter a valid URL.")
        }
    }

    suspend fun analyze(url: String): Result<MediaInfo> {
        val validation = validateUrl(url)
        if (validation is ValidationResult.Error) {
            return Result.failure(IllegalArgumentException(validation.message))
        }

        val targetUrl = (validation as ValidationResult.Valid).url

        for (provider in providers) {
            if (provider.supports(targetUrl)) {
                val result = provider.analyze(targetUrl)
                if (result.isSuccess) {
                    return result
                }
            }
        }

        return Result.failure(IllegalStateException("This source is not currently supported or media could not be analyzed."))
    }

    sealed class ValidationResult {
        data class Valid(val url: String) : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}
