package dev.shallowdusty.oplusotastudio.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import dev.shallowdusty.oplusotastudio.R

object DownloadForegroundNotification {
    const val ChannelId = "download_progress"
    const val NotificationId = 2010
}

class DownloadForegroundInfoFactory(
    private val context: Context,
) {
    fun create(taskId: String): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, DownloadForegroundNotification.ChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.download_notification_title))
            .setContentText(taskId)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DownloadForegroundNotification.NotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(DownloadForegroundNotification.NotificationId, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                DownloadForegroundNotification.ChannelId,
                context.getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
