package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsManager
import com.example.data.remote.RemoteConfigManager
import com.example.data.repository.DownloadRepository
import com.example.downloader.DownloadEngine
import com.example.downloader.DownloadNotificationHelper
import com.example.ui.navigation.SnapLoadApp
import com.example.ui.theme.SnapLoadTheme
import com.example.util.SalawatReminderManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var downloadEngine: DownloadEngine
    private lateinit var remoteConfigManager: RemoteConfigManager
    private var sharedUrl by mutableStateOf<String?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        DownloadNotificationHelper.createNotificationChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val database = AppDatabase.getDatabase(this)
        downloadRepository = DownloadRepository(database.downloadDao())
        downloadEngine = DownloadEngine(this, downloadRepository)
        settingsManager = SettingsManager(this)
        remoteConfigManager = RemoteConfigManager(this)

        // Initialize TTS fallback
        SalawatReminderManager.initTts(this)

        handleIncomingIntent(intent)

        setContent {
            val themePreference by settingsManager.themeFlow.collectAsState(initial = "DARK")
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themePreference) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemDark
            }

            SnapLoadTheme(darkTheme = isDark) {
                SnapLoadApp(
                    settingsManager = settingsManager,
                    downloadRepository = downloadRepository,
                    downloadEngine = downloadEngine,
                    remoteConfigManager = remoteConfigManager,
                    initialSharedUrl = sharedUrl
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Play Salawat audio unconditionally whenever the user opens or returns to the app
        lifecycleScope.launch {
            kotlinx.coroutines.delay(300)
            SalawatReminderManager.playSalawat(this@MainActivity)
            try {
                remoteConfigManager.checkRemoteStatus()
            } catch (e: Exception) {
                // Ignore any startup lifecycle exceptions
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SalawatReminderManager.release()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                val url = text.split("\\s+".toRegex()).firstOrNull {
                    it.startsWith("http://") || it.startsWith("https://")
                }
                if (url != null) {
                    sharedUrl = url
                }
            }
        }
    }
}
