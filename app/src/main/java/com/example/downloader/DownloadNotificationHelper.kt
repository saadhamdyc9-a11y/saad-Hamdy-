package com.example.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.utils.StorageUtils

object DownloadNotificationHelper {

    const val CHANNEL_ID = "snapload_downloads"
    private const val CHANNEL_NAME = "SnapLoad Downloads"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing download progress and completion notifications"
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildProgressNotification(
        context: Context,
        downloadId: Long,
        title: String,
        progressPercent: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("download_id", downloadId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            downloadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progressText = if (totalBytes > 0) {
            "${StorageUtils.formatBytes(downloadedBytes)} / ${StorageUtils.formatBytes(totalBytes)} • ${StorageUtils.formatSpeed(speedBytesPerSec)}"
        } else {
            "${StorageUtils.formatBytes(downloadedBytes)} • ${StorageUtils.formatSpeed(speedBytesPerSec)}"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("SnapLoad: $title")
            .setContentText(progressText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent.coerceIn(0, 100), totalBytes <= 0)
    }

    fun showCompletionNotification(
        context: Context,
        downloadId: Long,
        title: String,
        success: Boolean
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            downloadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (success) {
            context.getString(R.string.download_complete)
        } else {
            context.getString(R.string.notif_failed)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("SnapLoad: $title")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(downloadId.toInt(), notification)
    }
}
