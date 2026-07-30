package com.rebilive.notification.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "bili_live"
        const val SERVICE_CHANNEL_ID = "bili_service"
        const val NOTIFY_ID_SERVICE = 1001
    }

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(SERVICE_CHANNEL_ID, "后台服务", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun getServiceNotification(): Notification {
        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle("Re：B站开播提醒")
            .setContentText("后台检测中")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
