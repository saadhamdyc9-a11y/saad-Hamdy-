package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "snapload_settings")

class SettingsManager(private val context: Context) {

    companion object {
        val KEY_THEME = stringPreferencesKey("app_theme")               // "DARK", "LIGHT", "SYSTEM"
        val KEY_LANGUAGE = stringPreferencesKey("app_language")         // "en", "ar", "system"
        val KEY_DEFAULT_QUALITY = stringPreferencesKey("default_quality") // "Best", "1080p", "720p", "480p"
        val KEY_DEFAULT_AUDIO = stringPreferencesKey("default_audio")     // "M4A", "MP3"
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch_done")
        val KEY_SALAWAT_REMINDER = booleanPreferencesKey("salawat_reminder_audio")
    }

    val salawatReminderFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SALAWAT_REMINDER] ?: true // Enabled by default
    }

    suspend fun setSalawatReminder(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SALAWAT_REMINDER] = enabled }
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME] ?: "DARK" // Dark mode by default per design specification
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "system"
    }

    val defaultQualityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_QUALITY] ?: "Best"
    }

    val defaultAudioFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_AUDIO] ?: "M4A"
    }

    val wifiOnlyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_WIFI_ONLY] ?: false
    }

    val firstLaunchDoneFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FIRST_LAUNCH] ?: false
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME] = theme }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { prefs -> prefs[KEY_LANGUAGE] = language }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.dataStore.edit { prefs -> prefs[KEY_DEFAULT_QUALITY] = quality }
    }

    suspend fun setDefaultAudio(audio: String) {
        context.dataStore.edit { prefs -> prefs[KEY_DEFAULT_AUDIO] = audio }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setFirstLaunchDone(done: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_FIRST_LAUNCH] = done }
    }
}
