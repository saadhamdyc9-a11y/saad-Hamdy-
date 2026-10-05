package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

object SalawatReminderManager {

    private const val TAG = "SalawatReminder"
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _bannerVisible = MutableStateFlow(false)
    val bannerVisible = _bannerVisible.asStateFlow()

    private var lastPlayedTimestamp = 0L
    private const val COOLDOWN_MS = 2000L

    fun initTts(context: Context) {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale("ar"))
                    isTtsReady = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
                }
            }
        }
    }

    /**
     * Plays the Salawat audio unconditionally every time the app is entered or resumed.
     * Guaranteed to work on all devices by preparing a clean local media file and ensuring audible media volume.
     */
    fun playSalawat(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastPlayedTimestamp < COOLDOWN_MS) {
            return
        }
        lastPlayedTimestamp = now

        // Trigger visual banner
        _bannerVisible.value = true

        try {
            // Ensure media stream has sound volume so it is heard clearly
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (currentVol == 0 && maxVol > 0) {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        (maxVol * 0.7f).toInt().coerceAtLeast(1),
                        0
                    )
                }
            }

            mediaPlayer?.release()
            mediaPlayer = null

            val targetFile = File(context.filesDir, "salawat_audio.mp3")
            if (!targetFile.exists() || targetFile.length() < 1000L) {
                // Copy from assets
                try {
                    context.assets.open("salawat.mp3").use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not copy from assets, trying raw resources", e)
                    val rawId = context.resources.getIdentifier("salawat", "raw", context.packageName)
                    if (rawId != 0) {
                        context.resources.openRawResource(rawId).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                mp.setDataSource(targetFile.absolutePath)
                mp.prepare()
                mp.setVolume(1.0f, 1.0f)
                mp.setOnCompletionListener { player ->
                    player.release()
                    if (mediaPlayer === player) {
                        mediaPlayer = null
                    }
                }
                mp.start()
                mediaPlayer = mp
            } else {
                // Secondary fallback: raw resource ID
                val rawId = context.resources.getIdentifier("salawat", "raw", context.packageName)
                if (rawId != 0) {
                    val rawMp = MediaPlayer.create(context, rawId)
                    rawMp?.setOnCompletionListener { it.release() }
                    rawMp?.start()
                    mediaPlayer = rawMp
                } else {
                    fallbackToTts(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer playback error, falling back to TTS", e)
            fallbackToTts(context)
        }
    }

    private fun fallbackToTts(context: Context) {
        if (textToSpeech == null) {
            initTts(context)
        }
        if (isTtsReady) {
            textToSpeech?.speak(
                "اللهم صل وسلم وبارك على سيدنا محمد",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "salawat_tts_id"
            )
        }
    }

    fun dismissBanner() {
        _bannerVisible.value = false
    }

    fun release() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isTtsReady = false
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing SalawatReminderManager", e)
        }
    }
}
