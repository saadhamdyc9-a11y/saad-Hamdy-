package com.example.ui.settings

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.SettingsManager
import com.example.data.repository.DownloadRepository
import com.example.ui.components.DeveloperFooter
import com.example.utils.StorageUtils
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    downloadRepository: DownloadRepository,
    onNavigateToAbout: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToLegal: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentTheme by settingsManager.themeFlow.collectAsState(initial = "DARK")
    val currentLanguage by settingsManager.languageFlow.collectAsState(initial = "system")
    val defaultQuality by settingsManager.defaultQualityFlow.collectAsState(initial = "Best")
    val defaultAudio by settingsManager.defaultAudioFlow.collectAsState(initial = "M4A")
    val wifiOnly by settingsManager.wifiOnlyFlow.collectAsState(initial = false)

    var themeMenuExpanded by remember { mutableStateOf(false) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }
    var audioMenuExpanded by remember { mutableStateOf(false) }
    var langMenuExpanded by remember { mutableStateOf(false) }
    var historyClearedMessage by remember { mutableStateOf(false) }

    val moviesDir = remember { context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir }
    val freeSpace = remember { StorageUtils.getAvailableDiskSpaceBytes(moviesDir) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // APPEARANCE SECTION
            item {
                SettingsSectionTitle(title = stringResource(R.string.pref_appearance))
                SettingsCard {
                    SettingsRowWithDropdown(
                        icon = Icons.Default.DarkMode,
                        title = "Theme",
                        currentValue = when (currentTheme) {
                            "DARK" -> stringResource(R.string.theme_dark)
                            "LIGHT" -> stringResource(R.string.theme_light)
                            else -> stringResource(R.string.theme_system)
                        },
                        expanded = themeMenuExpanded,
                        onExpandRequest = { themeMenuExpanded = true },
                        onDismissRequest = { themeMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_dark)) },
                            onClick = {
                                scope.launch { settingsManager.setTheme("DARK") }
                                themeMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_light)) },
                            onClick = {
                                scope.launch { settingsManager.setTheme("LIGHT") }
                                themeMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_system)) },
                            onClick = {
                                scope.launch { settingsManager.setTheme("SYSTEM") }
                                themeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // DOWNLOADS PREFERENCES SECTION
            item {
                Spacer(modifier = Modifier.height(14.dp))
                SettingsSectionTitle(title = stringResource(R.string.pref_downloads))
                SettingsCard {
                    SettingsRowWithDropdown(
                        icon = Icons.Default.Download,
                        title = stringResource(R.string.default_video_quality),
                        currentValue = defaultQuality,
                        expanded = qualityMenuExpanded,
                        onExpandRequest = { qualityMenuExpanded = true },
                        onDismissRequest = { qualityMenuExpanded = false }
                    ) {
                        listOf("Best Available", "1080p", "720p", "480p").forEach { q ->
                            DropdownMenuItem(
                                text = { Text(q) },
                                onClick = {
                                    scope.launch { settingsManager.setDefaultQuality(q) }
                                    qualityMenuExpanded = false
                                }
                            )
                        }
                    }

                    SettingsRowWithDropdown(
                        icon = Icons.Default.Download,
                        title = stringResource(R.string.default_audio_format),
                        currentValue = defaultAudio,
                        expanded = audioMenuExpanded,
                        onExpandRequest = { audioMenuExpanded = true },
                        onDismissRequest = { audioMenuExpanded = false }
                    ) {
                        listOf("M4A", "MP3").forEach { a ->
                            DropdownMenuItem(
                                text = { Text(a) },
                                onClick = {
                                    scope.launch { settingsManager.setDefaultAudio(a) }
                                    audioMenuExpanded = false
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.wifi_only),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { scope.launch { settingsManager.setWifiOnly(it) } }
                        )
                    }
                }
            }

            // STORAGE SECTION
            item {
                Spacer(modifier = Modifier.height(14.dp))
                SettingsSectionTitle(title = stringResource(R.string.pref_storage))
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Device Storage",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.storage_available, StorageUtils.formatBytes(freeSpace)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    downloadRepository.clearAll()
                                    historyClearedMessage = true
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = if (historyClearedMessage) "History cleared ✓" else stringResource(R.string.clear_history),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (historyClearedMessage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // LANGUAGE SECTION
            item {
                Spacer(modifier = Modifier.height(14.dp))
                SettingsSectionTitle(title = stringResource(R.string.pref_language))
                SettingsCard {
                    SettingsRowWithDropdown(
                        icon = Icons.Default.Language,
                        title = "Language / اللغة",
                        currentValue = when (currentLanguage) {
                            "ar" -> stringResource(R.string.lang_arabic)
                            "en" -> stringResource(R.string.lang_english)
                            else -> "System Default"
                        },
                        expanded = langMenuExpanded,
                        onExpandRequest = { langMenuExpanded = true },
                        onDismissRequest = { langMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lang_english)) },
                            onClick = {
                                scope.launch { settingsManager.setLanguage("en") }
                                langMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.lang_arabic)) },
                            onClick = {
                                scope.launch { settingsManager.setLanguage("ar") }
                                langMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // ABOUT & LEGAL
            item {
                Spacer(modifier = Modifier.height(14.dp))
                SettingsSectionTitle(title = stringResource(R.string.pref_about))
                SettingsCard {
                    SettingsNavigationRow(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.about_snapload),
                        onClick = onNavigateToAbout
                    )
                    SettingsNavigationRow(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.privacy_policy),
                        onClick = onNavigateToPrivacy
                    )
                    SettingsNavigationRow(
                        icon = Icons.Default.Gavel,
                        title = stringResource(R.string.legal_info),
                        onClick = onNavigateToLegal
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                DeveloperFooter(
                    modifier = Modifier.fillMaxWidth(),
                    showDivider = true
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsRowWithDropdown(
    icon: ImageVector,
    title: String,
    currentValue: String,
    expanded: Boolean,
    onExpandRequest: () -> Unit,
    onDismissRequest: () -> Unit,
    dropdownContent: @Composable () -> Unit
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandRequest() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = currentValue,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest
        ) {
            dropdownContent()
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
    }
}
