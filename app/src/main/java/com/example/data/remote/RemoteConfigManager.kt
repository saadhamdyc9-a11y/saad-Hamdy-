package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RemoteAppConfig(
    val isAppActive: Boolean = true,
    val maintenanceTitle: String = "التطبيق متوقف مؤقتاً للصيانة",
    val maintenanceMessage: String = "نعمل حالياً على صيانة وتحديث السيرفرات لتقديم أفضل تجربة تحميل وسرعة. يرجى المحاولة لاحقاً.",
    val minVersionCode: Int = 1,
    val latestVersionName: String = "1.0.0",
    val forceUpdate: Boolean = false,
    val updateTitle: String = "تحديث جديد وإلزامي متوفر",
    val updateMessage: String = "يتوفر إصدار جديد يتضمن تحسينات أساسية وإصلاحات ضرورية لضمان استمرار التحميل. يرجى التحديث الآن للمتابعة.",
    val updateUrl: String = "https://play.google.com/store/apps/details?id=com.aistudio.snapload.kxmpzq",
    val totalUsersCount: Int = 330,
    val activeUsersToday: Int = 45,
    val totalDownloadsCount: String = "1.2K+"
)

sealed class AppAccessState {
    object Allowed : AppAccessState()
    data class Maintenance(val title: String, val message: String) : AppAccessState()
    data class ForceUpdate(
        val title: String,
        val message: String,
        val updateUrl: String,
        val latestVersion: String
    ) : AppAccessState()
}

class RemoteConfigManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("snapload_remote_config", Context.MODE_PRIVATE)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    // Default configuration URL (can be customized by the developer)
    var configEndpointUrl: String
        get() {
            val saved = prefs.getString(KEY_CONFIG_URL, null)
            return if (saved.isNullOrBlank()) DEFAULT_CONFIG_URL else saved
        }
        set(value) {
            prefs.edit().putString(KEY_CONFIG_URL, value).apply()
        }

    // Developer simulation overrides
    var simulateMaintenance: Boolean
        get() = prefs.getBoolean(KEY_SIMULATE_MAINTENANCE, false)
        set(value) = prefs.edit().putBoolean(KEY_SIMULATE_MAINTENANCE, value).apply()

    var simulateForceUpdate: Boolean
        get() = prefs.getBoolean(KEY_SIMULATE_FORCE_UPDATE, false)
        set(value) = prefs.edit().putBoolean(KEY_SIMULATE_FORCE_UPDATE, value).apply()

    private val _accessState = MutableStateFlow<AppAccessState>(AppAccessState.Allowed)
    val accessState = _accessState.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking = _isChecking.asStateFlow()

    init {
        // Load initial state from cache or default
        evaluateAccess(getCachedConfig())
    }

    fun getAppVersionCode(): Int {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    suspend fun checkRemoteStatus(): AppAccessState = withContext(Dispatchers.IO) {
        _isChecking.value = true
        try {
            // Check manual dev simulations first
            if (simulateMaintenance) {
                val state = AppAccessState.Maintenance(
                    title = "وضع الصيانة التجريبي (Dev Mode)",
                    message = "تم تفعيل وضع الصيانة يدوياً من إعدادات المطور للتحقق من الميزة."
                )
                _accessState.value = state
                _isChecking.value = false
                return@withContext state
            }

            if (simulateForceUpdate) {
                val state = AppAccessState.ForceUpdate(
                    title = "تحديث جديد متوفر (Dev Mode)",
                    message = "تم تفعيل شاشة التحديث الإجباري للتجربة.",
                    updateUrl = "https://play.google.com/store/apps/details?id=${context.packageName}",
                    latestVersion = "2.0.0"
                )
                _accessState.value = state
                _isChecking.value = false
                return@withContext state
            }

            if (configEndpointUrl.isBlank()) {
                val state = evaluateAccess(getCachedConfig())
                _accessState.value = state
                _isChecking.value = false
                return@withContext state
            }

            val timestampedUrl = if (configEndpointUrl.contains("?")) {
                "$configEndpointUrl&_cb=${System.currentTimeMillis()}"
            } else {
                "$configEndpointUrl?_cb=${System.currentTimeMillis()}"
            }

            val request = Request.Builder()
                .url(timestampedUrl)
                .header("User-Agent", "Mozilla/5.0 SnapLoad-App/${getAppVersionCode()}")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()

            var response = client.newCall(request).execute()
            if (!response.isSuccessful && configEndpointUrl.contains("/main/")) {
                response.close()
                val masterUrl = configEndpointUrl.replace("/main/", "/master/")
                val masterReq = Request.Builder()
                    .url(masterUrl)
                    .header("User-Agent", "Mozilla/5.0 SnapLoad-App/${getAppVersionCode()}")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build()
                response = client.newCall(masterReq).execute()
            }

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                response.close()
                if (body.isNotBlank()) {
                    val json = JSONObject(body)
                    val config = RemoteAppConfig(
                        isAppActive = json.optBoolean("is_app_active", true),
                        maintenanceTitle = json.optString("maintenance_title", "التطبيق متوقف مؤقتاً للصيانة"),
                        maintenanceMessage = json.optString("maintenance_message", "نعمل حالياً على صيانة وتطوير السيرفرات."),
                        minVersionCode = json.optInt("min_version_code", 1),
                        latestVersionName = json.optString("latest_version_name", "1.0.0"),
                        forceUpdate = json.optBoolean("force_update", false),
                        updateTitle = json.optString("update_title", "تحديث جديد وإلزامي متوفر"),
                        updateMessage = json.optString("update_message", "يتوفر إصدار جديد يتضمن تحسينات أساسية."),
                        updateUrl = json.optString("update_url", DEFAULT_PLAY_STORE_URL),
                        totalUsersCount = json.optInt("user_count", json.optInt("total_users", 330)),
                        activeUsersToday = json.optInt("active_users_today", 45),
                        totalDownloadsCount = json.optString("downloads_count", "1.2K+")
                    )
                    cacheConfig(config)
                    val state = evaluateAccess(config)
                    _accessState.value = state
                    _isChecking.value = false
                    return@withContext state
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch remote config: ${e.message}")
        }

        // Fallback to cached or dev evaluation
        val fallbackState = evaluateAccess(getCachedConfig())
        _accessState.value = fallbackState
        _isChecking.value = false
        return@withContext fallbackState
    }

    private fun evaluateAccess(config: RemoteAppConfig): AppAccessState {
        if (simulateMaintenance) {
            return AppAccessState.Maintenance(
                title = "وضع الصيانة التجريبي (Dev Mode)",
                message = "تم تفعيل وضع الصيانة يدوياً من إعدادات المطور للتحقق من الميزة."
            )
        }
        if (simulateForceUpdate) {
            return AppAccessState.ForceUpdate(
                title = "تحديث جديد متوفر (Dev Mode)",
                message = "تم تفعيل شاشة التحديث الإجباري للتجربة.",
                updateUrl = DEFAULT_PLAY_STORE_URL,
                latestVersion = "2.0.0"
            )
        }

        if (!config.isAppActive) {
            return AppAccessState.Maintenance(
                title = config.maintenanceTitle,
                message = config.maintenanceMessage
            )
        }

        val currentVersion = getAppVersionCode()
        val isOutdated = currentVersion < config.minVersionCode
        if (isOutdated && (config.forceUpdate || config.minVersionCode > currentVersion)) {
            return AppAccessState.ForceUpdate(
                title = config.updateTitle,
                message = config.updateMessage,
                updateUrl = config.updateUrl,
                latestVersion = config.latestVersionName
            )
        }

        return AppAccessState.Allowed
    }

    fun getCachedConfig(): RemoteAppConfig {
        return RemoteAppConfig(
            isAppActive = prefs.getBoolean(KEY_IS_APP_ACTIVE, true),
            maintenanceTitle = prefs.getString(KEY_MAINTENANCE_TITLE, "التطبيق متوقف مؤقتاً للصيانة") ?: "التطبيق متوقف مؤقتاً للصيانة",
            maintenanceMessage = prefs.getString(KEY_MAINTENANCE_MSG, "نعمل حالياً على صيانة وتطوير السيرفرات.") ?: "",
            minVersionCode = prefs.getInt(KEY_MIN_VERSION, 1),
            latestVersionName = prefs.getString(KEY_LATEST_VERSION, "1.0.0") ?: "1.0.0",
            forceUpdate = prefs.getBoolean(KEY_FORCE_UPDATE, false),
            updateTitle = prefs.getString(KEY_UPDATE_TITLE, "تحديث جديد وإلزامي متوفر") ?: "تحديث جديد وإلزامي متوفر",
            updateMessage = prefs.getString(KEY_UPDATE_MSG, "يتوفر إصدار جديد يتضمن تحسينات أساسية.") ?: "",
            updateUrl = prefs.getString(KEY_UPDATE_URL, DEFAULT_PLAY_STORE_URL) ?: DEFAULT_PLAY_STORE_URL,
            totalUsersCount = prefs.getInt(KEY_TOTAL_USERS, 330),
            activeUsersToday = prefs.getInt(KEY_ACTIVE_USERS, 45),
            totalDownloadsCount = prefs.getString(KEY_DOWNLOADS_COUNT, "1.2K+") ?: "1.2K+"
        )
    }

    fun updateAndApplyConfig(config: RemoteAppConfig) {
        cacheConfig(config)
        val state = evaluateAccess(config)
        _accessState.value = state
    }

    private fun cacheConfig(config: RemoteAppConfig) {
        prefs.edit()
            .putBoolean(KEY_IS_APP_ACTIVE, config.isAppActive)
            .putString(KEY_MAINTENANCE_TITLE, config.maintenanceTitle)
            .putString(KEY_MAINTENANCE_MSG, config.maintenanceMessage)
            .putInt(KEY_MIN_VERSION, config.minVersionCode)
            .putString(KEY_LATEST_VERSION, config.latestVersionName)
            .putBoolean(KEY_FORCE_UPDATE, config.forceUpdate)
            .putString(KEY_UPDATE_TITLE, config.updateTitle)
            .putString(KEY_UPDATE_MSG, config.updateMessage)
            .putString(KEY_UPDATE_URL, config.updateUrl)
            .putInt(KEY_TOTAL_USERS, config.totalUsersCount)
            .putInt(KEY_ACTIVE_USERS, config.activeUsersToday)
            .putString(KEY_DOWNLOADS_COUNT, config.totalDownloadsCount)
            .apply()
    }

    fun recordAppLaunch() {
        val launches = prefs.getInt(KEY_APP_LAUNCHES, 0) + 1
        prefs.edit().putInt(KEY_APP_LAUNCHES, launches).apply()
    }

    fun getAppLaunchesCount(): Int = prefs.getInt(KEY_APP_LAUNCHES, 1)

    companion object {
        private const val TAG = "RemoteConfigManager"
        const val DEFAULT_CONFIG_URL = "https://gist.githubusercontent.com/saadhamdyc9-a11y/85cd592d87286ecc45ef7e17fb3b688f/raw"
        const val DEFAULT_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.aistudio.snapload.kxmpzq"

        private const val KEY_CONFIG_URL = "config_url"
        private const val KEY_SIMULATE_MAINTENANCE = "sim_maintenance"
        private const val KEY_SIMULATE_FORCE_UPDATE = "sim_force_update"

        private const val KEY_IS_APP_ACTIVE = "cached_active"
        private const val KEY_MAINTENANCE_TITLE = "cached_maint_title"
        private const val KEY_MAINTENANCE_MSG = "cached_maint_msg"
        private const val KEY_MIN_VERSION = "cached_min_ver"
        private const val KEY_LATEST_VERSION = "cached_latest_ver"
        private const val KEY_FORCE_UPDATE = "cached_force_upd"
        private const val KEY_UPDATE_TITLE = "cached_upd_title"
        private const val KEY_UPDATE_MSG = "cached_upd_msg"
        private const val KEY_UPDATE_URL = "cached_upd_url"

        private const val KEY_TOTAL_USERS = "cached_total_users"
        private const val KEY_ACTIVE_USERS = "cached_active_users"
        private const val KEY_DOWNLOADS_COUNT = "cached_downloads_cnt"
        private const val KEY_APP_LAUNCHES = "local_app_launches"
    }
}
