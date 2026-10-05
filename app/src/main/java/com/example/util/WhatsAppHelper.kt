package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object WhatsAppHelper {

    const val WHATSAPP_PHONE_DISPLAY = "01060275054"
    const val WHATSAPP_PHONE_INTERNATIONAL = "201060275054"

    fun openWhatsApp(context: Context, message: String = "السلام عليكم، تواصل بخصوص تطبيق SnapLoad") {
        try {
            val encodedMsg = Uri.encode(message)
            val uri = Uri.parse("https://wa.me/$WHATSAPP_PHONE_INTERNATIONAL?text=$encodedMsg")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Secondary attempt with direct whatsapp:// scheme
                val fallbackUri = Uri.parse("whatsapp://send?phone=$WHATSAPP_PHONE_INTERNATIONAL")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "الرقم للتواصل: $WHATSAPP_PHONE_DISPLAY", Toast.LENGTH_LONG).show()
            }
        }
    }
}
