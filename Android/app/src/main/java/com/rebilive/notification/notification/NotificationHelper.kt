package com.rebilive.notification.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rebilive.notification.MainActivity
import com.rebilive.notification.R

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "bili_live"
        const val SERVICE_CHANNEL_ID = "bili_service"
        const val LIVE_CHANNEL_ID = "bili_live_notify"
        const val NOTIFY_ID_SERVICE = 1001
    }

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(SERVICE_CHANNEL_ID, "后台服务", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(
                LIVE_CHANNEL_ID,
                "开播提醒",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    fun getServiceNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle("Re：B站开播提醒")
            .setContentText("后台检测中")
            .setSmallIcon(R.drawable.ic_stat_live)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun getLiveNotification(
        title: String,
        text: String,
        fullScreenPendingIntent: PendingIntent
    ): Notification {
        return NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_live)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .build()
    }
}
