package com.rebilive.notification.service

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import com.rebilive.notification.data.LiveRepository
import com.rebilive.notification.data.SettingsRepository
import com.rebilive.notification.notification.NotificationHelper
import kotlinx.coroutines.*

class BiliLiveService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val roomStates = mutableMapOf<String, Int>()
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(this)
        notificationHelper = NotificationHelper(this)
        notificationHelper.createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NotificationHelper.NOTIFY_ID_SERVICE, notificationHelper.getServiceNotification())
        settingsRepo.setServiceWasRunning(true)
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                val roomIds = settingsRepo.getRoomIds()
                val notifyEnabled = settingsRepo.isNotifyEnabled()
                val autoJumpEnabled = settingsRepo.isAutoJumpEnabled()
                var jumped = false

                for (rid in roomIds) {
                    try {
                        val liveResp = LiveRepository.checkLive(rid)
                        if (liveResp.code == 0 && liveResp.data != null) {
                            val prev = roomStates[rid]
                            val cur = liveResp.data.liveStatus
                            if (cur == 1 && prev != 1) {
                                val userResp = LiveRepository.getUserInfo(liveResp.data.uid)
                                val uname = userResp.data?.info?.uname ?: rid
                                if (notifyEnabled) {
                                    val faceUrl = userResp.data?.info?.face
                                    showLivePopup(rid, uname, faceUrl)
                                }
                                if (autoJumpEnabled && !jumped) {
                                    jumped = true
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://live.bilibili.com/$rid")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try {
                                        intent.setPackage("tv.danmaku.bili")
                                        startActivity(intent)
                                    } catch (_: Exception) {
                                        intent.setPackage(null)
                                        startActivity(intent)
                                    }
                                }
                            }
                            roomStates[rid] = cur
                        }
                    } catch (_: Exception) { }
                }

                delay(settingsRepo.getInterval() * 1000L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showLivePopup(rid: String, uname: String, faceUrl: String?) {
        startActivity(Intent(this, PopupActivity::class.java).apply {
            putExtra("rid", rid)
            putExtra("uname", uname)
            putExtra("faceUrl", faceUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
