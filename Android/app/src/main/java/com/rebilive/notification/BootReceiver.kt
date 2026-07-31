package com.rebilive.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rebilive.notification.data.SettingsRepository
import com.rebilive.notification.service.BiliLiveService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val settingsRepo = SettingsRepository(context)
        if (settingsRepo.isAutoStart() && settingsRepo.getRoomIds().isNotEmpty()) {
            val serviceIntent = Intent(context, BiliLiveService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
