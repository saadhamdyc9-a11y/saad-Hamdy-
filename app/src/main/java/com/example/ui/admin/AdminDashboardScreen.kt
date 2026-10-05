package com.example.ui.admin

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.admin.AdminManager
import com.example.data.remote.RemoteConfigManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    adminManager: AdminManager,
    remoteConfigManager: RemoteConfigManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val initialConfig = remember { adminManager.getCurrentConfig() }

    // Maintenance State
    var isMaintenanceActive by remember { mutableStateOf(!initialConfig.isAppActive) }
    var maintenanceTitle by remember { mutableStateOf(initialConfig.maintenanceTitle) }
    var maintenanceMessage by remember { mutableStateOf(initialConfig.maintenanceMessage) }

    // Force Update State
    var isForceUpdateActive by remember { mutableStateOf(initialConfig.forceUpdate) }
    var minVersionCode by remember { mutableIntStateOf(initialConfig.minVersionCode) }
    var latestVersionName by remember { mutableStateOf(initialConfig.latestVersionName) }
    var updateUrl by remember { mutableStateOf(initialConfig.updateUrl) }
    var updateTitle by remember { mutableStateOf(initialConfig.updateTitle) }
    var updateMessage by remember { mutableStateOf(initialConfig.updateMessage) }

    // Analytics & User Counter State
    var totalUsers by remember { mutableIntStateOf(initialConfig.totalUsersCount) }
    var activeUsers by remember { mutableIntStateOf(initialConfig.activeUsersToday) }
    var downloadsCount by remember { mutableStateOf(initialConfig.totalDownloadsCount) }

    // Cloud endpoint state
    var cloudUrl by remember { mutableStateOf(remoteConfigManager.configEndpointUrl) }

    // Password change state
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordChangeMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "لوحة تحكم الإدارة (Admin Panel)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "تحكم فوري بحالة التطبيق والتحديثات",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            adminManager.isAdminBypassed = false
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "خروج",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "قفل كـ مستخدم",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SYSTEM STATUS OVERVIEW
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMaintenanceActive) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                        } else if (isForceUpdateActive) {
                            Color(0xFFFFF3CD)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isMaintenanceActive) Icons.Default.Warning else if (isForceUpdateActive) Icons.Default.SystemUpdate else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isMaintenanceActive) MaterialTheme.colorScheme.error else if (isForceUpdateActive) Color(0xFF856404) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isMaintenanceActive) "الحالة: وضع الصيانة مفعل (التطبيق متوقف)"
                                else if (isForceUpdateActive) "الحالة: التحديث الإجباري مفعل"
                                else "الحالة: التطبيق نشط ويعمل بشكل طبيعي",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isMaintenanceActive) MaterialTheme.colorScheme.onErrorContainer else if (isForceUpdateActive) Color(0xFF856404) else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "الإصدار الحالي مثبت: 1.0.0 (كود: ${remoteConfigManager.getAppVersionCode()})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // SECTION 0: USER ANALYTICS & COUNTER
            item {
                AdminCard(
                    title = "إحصائيات وعداد المستخدمين (Live Analytics)",
                    icon = Icons.Default.People
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Total Users
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "إجمالي المستخدمين",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$totalUsers+",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Active Today
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "النشطين اليوم 🟢",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$activeUsers+",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Device Launches Info
                        Text(
                            text = "مرات فتح التطبيق على هذا الجهاز: ${adminManager.getDeviceLaunchCount()} • عمليات التحميل المقدرة: $downloadsCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "تعديل أرقام العداد الظاهرة للمستخدمين في التطبيق والسيرفر:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = totalUsers.toString(),
                                onValueChange = { totalUsers = it.toIntOrNull() ?: totalUsers },
                                label = { Text("إجمالي المستخدمين") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = activeUsers.toString(),
                                onValueChange = { activeUsers = it.toIntOrNull() ?: activeUsers },
                                label = { Text("النشطين اليوم") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            // SECTION 1: MAINTENANCE CONTROL
            item {
                AdminCard(
                    title = "إيقاف التطبيق مؤقتاً (وضع الصيانة)",
                    icon = Icons.Default.Build
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تفعيل وضع الصيانة وإغلاق التطبيق",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "يمنع كل المستخدمين من استخدام التطبيق حتى إلغاء التفعيل",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isMaintenanceActive,
                            onCheckedChange = { isMaintenanceActive = it }
                        )
                    }

                    if (isMaintenanceActive) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = maintenanceTitle,
                            onValueChange = { maintenanceTitle = it },
                            label = { Text("عنوان رسالة الصيانة") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = maintenanceMessage,
                            onValueChange = { maintenanceMessage = it },
                            label = { Text("نص رسالة الصيانة المعروضة للمستخدمين") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                }
            }

            // SECTION 2: FORCE UPDATE
            item {
                AdminCard(
                    title = "نظام التحديث الإجباري (Force Update)",
                    icon = Icons.Default.SystemUpdate
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "إلزام المستخدمين بالتحديث للنسخة الجديدة",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "يقفل التطبيق فوراً مع زر لتحميل التحديث الجديد",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isForceUpdateActive,
                            onCheckedChange = { isForceUpdateActive = it }
                        )
                    }

                    if (isForceUpdateActive) {
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = latestVersionName,
                                onValueChange = { latestVersionName = it },
                                label = { Text("اسم الإصدار الجديد") },
                                placeholder = { Text("مثال: 2.0.0") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = minVersionCode.toString(),
                                onValueChange = { minVersionCode = it.toIntOrNull() ?: 1 },
                                label = { Text("أدنى كود مطلوب") },
                                placeholder = { Text("مثال: 2") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = updateUrl,
                            onValueChange = { updateUrl = it },
                            label = { Text("رابط تحميل التطبيق الجديد (APK / Store / Direct)") },
                            placeholder = { Text("https://...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = updateTitle,
                            onValueChange = { updateTitle = it },
                            label = { Text("عنوان التحديث") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = updateMessage,
                            onValueChange = { updateMessage = it },
                            label = { Text("رسالة التحديث وشرح الميزات الجديدة") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                }
            }

            // SECTION 3: APPLY & CLOUD SYNC
            item {
                AdminCard(
                    title = "نشر التعديلات والمزامنة السحابية",
                    icon = Icons.Default.CloudSync
                ) {
                    Text(
                        text = "اختر طريقة تطبيق التغييرات:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // PRIMARY APPLY BUTTON
                    Button(
                        onClick = {
                            adminManager.applyLocalControl(
                                isAppActive = !isMaintenanceActive,
                                maintenanceTitle = maintenanceTitle,
                                maintenanceMessage = maintenanceMessage,
                                forceUpdate = isForceUpdateActive,
                                minVersionCode = minVersionCode,
                                latestVersionName = latestVersionName,
                                updateUrl = updateUrl,
                                updateTitle = updateTitle,
                                updateMessage = updateMessage,
                                userCount = totalUsers,
                                activeUsersToday = activeUsers,
                                downloadsCount = downloadsCount
                            )
                            if (isMaintenanceActive || isForceUpdateActive) {
                                adminManager.isAdminBypassed = false
                                Toast.makeText(context, "تم حفظ الإيقاف وتفعيله فوراً!", Toast.LENGTH_SHORT).show()
                                onBack()
                            } else {
                                Toast.makeText(context, "تم حفظ وتفعيل التطبيق بنجاح!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تطبيق وحفظ التعديلات فوراً", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // COPY JSON BUTTON (For Gist/GitHub)
                    OutlinedButton(
                        onClick = {
                            val jsonString = adminManager.generateConfigJson(
                                isAppActive = !isMaintenanceActive,
                                maintenanceTitle = maintenanceTitle,
                                maintenanceMessage = maintenanceMessage,
                                forceUpdate = isForceUpdateActive,
                                minVersionCode = minVersionCode,
                                latestVersionName = latestVersionName,
                                updateUrl = updateUrl,
                                updateTitle = updateTitle,
                                updateMessage = updateMessage,
                                userCount = totalUsers,
                                activeUsersToday = activeUsers,
                                downloadsCount = downloadsCount
                            )
                            clipboardManager.setText(AnnotatedString(jsonString))
                            Toast.makeText(context, "تم نسخ كود الـ JSON المحدث للحافظة بنجاح!", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("نسخ كود الـ JSON المحدث إلى الحافظة")
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    // Cloud Endpoint URL
                    Text(
                        text = "رابط ملف السيرفر السحابي الحالي (Cloud Endpoint URL):",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = cloudUrl,
                        onValueChange = { cloudUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://gist.githubusercontent.com/...") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                remoteConfigManager.configEndpointUrl = cloudUrl.trim()
                                scope.launch {
                                    remoteConfigManager.checkRemoteStatus()
                                    Toast.makeText(context, "تم حفظ الرابط وفحص السيرفر السحابي!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("حفظ الرابط وفحص")
                        }
                        if (cloudUrl.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    cloudUrl = ""
                                    remoteConfigManager.configEndpointUrl = ""
                                    Toast.makeText(context, "تم إفراغ الرابط", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("مسح")
                            }
                        }
                    }
                }
            }

            // SECTION 4: SECURITY & PASSWORD CHANGE
            item {
                AdminCard(
                    title = "أمان لوحة التحكم وتغيير كلمة المرور",
                    icon = Icons.Default.Key
                ) {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("كلمة المرور الجديدة") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("تأكيد كلمة المرور الجديدة") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (newPassword.isBlank()) {
                                passwordChangeMessage = "يرجى كتابة كلمة مرور جديدة!"
                            } else if (newPassword != confirmPassword) {
                                passwordChangeMessage = "كلمتا المرور غير متطابقتين!"
                            } else {
                                val success = adminManager.updatePassword(newPassword)
                                if (success) {
                                    newPassword = ""
                                    confirmPassword = ""
                                    passwordChangeMessage = "تم تغيير كلمة مرور الأدمن بنجاح!"
                                    Toast.makeText(context, "تم تغيير كلمة المرور بنجاح!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("حفظ كلمة المرور الجديدة")
                    }

                    passwordChangeMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (msg.contains("نجاح")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AdminCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
