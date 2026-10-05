package com.example.media.provider

import com.example.domain.model.MediaInfo
import java.net.URI

class MediaAnalyzer(
    private val providers: List<MediaSourceProvider> = listOf(
        DirectMediaProvider(),
        YouTubeSourceProvider(),
        FacebookSourceProvider(),
        TelegramSourceProvider(),
        SoundCloudSourceProvider(),
        TikTokSourceProvider(),
        InstagramSourceProvider(),
        TwitterXSourceProvider(),
        GenericWebMediaProvider()
    )
) {

    fun validateUrl(rawUrl: String): ValidationResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Error("يرجى إدخال رابط للتحميل.")
        }
        val target = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
        return try {
            val uri = URI(target)
            if (uri.host.isNullOrBlank()) {
                ValidationResult.Error("يرجى إدخال رابط صحيح.")
            } else {
                ValidationResult.Valid(target)
            }
        } catch (e: Exception) {
            ValidationResult.Error("يرجى إدخال رابط صحيح.")
        }
    }

    suspend fun analyze(url: String): Result<MediaInfo> {
        val validation = validateUrl(url)
        if (validation is ValidationResult.Error) {
            return Result.failure(IllegalArgumentException(validation.message))
        }

        val targetUrl = (validation as ValidationResult.Valid).url

        var lastError: Throwable? = null
        for (provider in providers) {
            if (provider.supports(targetUrl)) {
                val result = provider.analyze(targetUrl)
                if (result.isSuccess) {
                    return result
                } else {
                    lastError = result.exceptionOrNull()
                }
            }
        }

        return Result.failure(
            lastError ?: IllegalStateException("هذا المصدر غير مدعوم حالياً أو تعذر تحليل الوسائط منه.")
        )
    }

    sealed class ValidationResult {
        data class Valid(val url: String) : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}
