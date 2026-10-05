package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LaptopWindows
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PcInstallationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "تشغيل وتثبيت التطبيق على الكمبيوتر (PC)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = "يمكنك تثبيت وتشغيل SnapLoad على الكمبيوتر بدون أي محاكي نهائياً ليفتح كنافذة ويندوز أصلية سريعة وخفيفة مع دعم كامل للماوس والكيبورد:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Method 1: WSA (Windows 11 Native - No Emulator)
                PcMethodItem(
                    icon = Icons.Default.Window,
                    title = "1. الطريقة الرسمية بدون محاكي: (Windows 11 WSA)",
                    description = "ويندوز 11 يحتوي على محرك تشغيل أندرويد رسمي من مايكروسوفت (WSA):\n• ثبّت أداة WSA PacMan المجانية لويندوز.\n• اضغط دبل كليك على ملف الـ APK.\n• سيتم تثبيت SnapLoad مباشرة ويظهر في قائمة Start كبرنامج ويندوز أصلي!"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Method 2: Microsoft Phone Link (No Emulator)
                PcMethodItem(
                    icon = Icons.Default.LaptopWindows,
                    title = "2. عبر تطبيق ربط الهاتف بالويندوز (Microsoft Phone Link)",
                    description = "بدون تثبيت أي شيء على الكمبيوتر:\n• افتح برنامج 'Link to Windows' المدمج في ويندوز 10 و 11.\n• سيظهر SnapLoad كنافذة مستقلة على سطح المكتب وتتحكم به بالكامل بالماوس والكيبورد والتحميل المباشر!"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Method 3: Desktop shortcuts
                PcMethodItem(
                    icon = Icons.Default.Keyboard,
                    title = "3. اختصارات لوحة المفاتيح والماوس",
                    description = "على الكمبيوتر: اضغط Ctrl+V للصق الرابط، واضغط Enter لبدء التحميل، وEsc لإلغاء أي نافذة منبثقة."
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Direct APK Copy Button
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString("https://play.google.com/store/apps/details?id=com.aistudio.snapload.kxmpzq"))
                        Toast.makeText(context, "تم نسخ رابط التحميل للكمبيوتر بنجاح!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("نسخ رابط تحميل التطبيق للكمبيوتر")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("حسناً، فهمت")
            }
        }
    )
}

@Composable
private fun PcMethodItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
