package com.example.data.admin

import android.content.Context
import android.content.SharedPreferences
import com.example.data.remote.RemoteAppConfig
import com.example.data.remote.RemoteConfigManager
import org.json.JSONObject

class AdminManager(
    private val context: Context,
    private val remoteConfigManager: RemoteConfigManager
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("snapload_admin_prefs", Context.MODE_PRIVATE)

    fun getPassword(): String {
        return prefs.getString(KEY_ADMIN_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
    }

    var isAdminBypassed: Boolean
        get() = prefs.getBoolean(KEY_ADMIN_BYPASS, false)
        set(value) = prefs.edit().putBoolean(KEY_ADMIN_BYPASS, value).apply()

    fun verifyPassword(input: String): Boolean {
        return input == getPassword()
    }

    fun updatePassword(newPassword: String): Boolean {
        if (newPassword.isBlank()) return false
        prefs.edit().putString(KEY_ADMIN_PASSWORD, newPassword).apply()
        return true
    }

    fun resetPasswordToDefault() {
        prefs.edit().putString(KEY_ADMIN_PASSWORD, DEFAULT_PASSWORD).apply()
    }

    fun getCurrentConfig(): RemoteAppConfig {
        return remoteConfigManager.getCachedConfig()
    }

    fun applyLocalControl(
        isAppActive: Boolean,
        maintenanceTitle: String,
        maintenanceMessage: String,
        forceUpdate: Boolean,
        minVersionCode: Int,
        latestVersionName: String,
        updateUrl: String,
        updateTitle: String,
        updateMessage: String,
        userCount: Int = 330,
        activeUsersToday: Int = 45,
        downloadsCount: String = "1.2K+"
    ) {
        val config = RemoteAppConfig(
            isAppActive = isAppActive,
            maintenanceTitle = maintenanceTitle.ifBlank { "التطبيق متوقف مؤقتاً للصيانة" },
            maintenanceMessage = maintenanceMessage.ifBlank { "نعمل حالياً على صيانة وتحديث السيرفرات." },
            minVersionCode = minVersionCode,
            latestVersionName = latestVersionName.ifBlank { "1.0.0" },
            forceUpdate = forceUpdate,
            updateTitle = updateTitle.ifBlank { "تحديث جديد وإلزامي متوفر" },
            updateMessage = updateMessage.ifBlank { "يتوفر إصدار جديد يتضمن تحسينات أساسية." },
            updateUrl = updateUrl.ifBlank { RemoteConfigManager.DEFAULT_PLAY_STORE_URL },
            totalUsersCount = userCount,
            activeUsersToday = activeUsersToday,
            totalDownloadsCount = downloadsCount
        )
        remoteConfigManager.updateAndApplyConfig(config)
    }

    fun generateConfigJson(
        isAppActive: Boolean,
        maintenanceTitle: String,
        maintenanceMessage: String,
        forceUpdate: Boolean,
        minVersionCode: Int,
        latestVersionName: String,
        updateUrl: String,
        updateTitle: String,
        updateMessage: String,
        userCount: Int = 330,
        activeUsersToday: Int = 45,
        downloadsCount: String = "1.2K+"
    ): String {
        val json = JSONObject()
        json.put("is_app_active", isAppActive)
        json.put("user_count", userCount)
        json.put("active_users_today", activeUsersToday)
        json.put("downloads_count", downloadsCount)
        json.put("maintenance_title", maintenanceTitle)
        json.put("maintenance_message", maintenanceMessage)
        json.put("min_version_code", minVersionCode)
        json.put("latest_version_name", latestVersionName)
        json.put("force_update", forceUpdate)
        json.put("update_title", updateTitle)
        json.put("update_message", updateMessage)
        json.put("update_url", updateUrl)
        return json.toString(2)
    }

    fun getDeviceLaunchCount(): Int = remoteConfigManager.getAppLaunchesCount()

    companion object {
        const val DEFAULT_PASSWORD = "Saad@12345"
        private const val KEY_ADMIN_PASSWORD = "admin_password"
        private const val KEY_ADMIN_BYPASS = "admin_bypass"
    }
}
