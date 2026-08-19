package com.rebilive.notification.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.rebilive.notification.data.LiveRepository
import com.rebilive.notification.data.SettingsRepository
import com.rebilive.notification.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class BiliLiveService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val roomStates = mutableMapOf<String, Int>()
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var notificationHelper: NotificationHelper
    private var pollingJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(this)
        notificationHelper = NotificationHelper(this)
        notificationHelper.createChannels()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NotificationHelper.NOTIFY_ID_SERVICE, notificationHelper.getServiceNotification())
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    checkRooms()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "轮询异常", e)
                }
                delay(settingsRepo.getInterval() * 1000L)
            }
        }
    }

    private suspend fun checkRooms() {
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
                        val canOverlay = Settings.canDrawOverlays(this@BiliLiveService)
                        var popupAutoJump = false
                        if (autoJumpEnabled && !jumped) {
                            jumped = true
                            if (canOverlay) {
                                jumpToLive(rid)
                            } else {
                                popupAutoJump = true
                            }
                        }
                        if (notifyEnabled) {
                            val faceUrl = userResp.data?.info?.face
                            showLivePopup(rid, uname, faceUrl, popupAutoJump)
                        }
                    }
                    roomStates[rid] = cur
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (networkCallback != null) return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "网络恢复，立即检测")
                pollNow()
            }
        }
        try {
            cm.registerDefaultNetworkCallback(networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "注册网络回调失败", e)
            networkCallback = null
        }
    }

    private fun pollNow() {
        pollingJob?.cancel()
        pollingJob = null
        startPolling()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showLivePopup(rid: String, uname: String, faceUrl: String?, autoJump: Boolean) {
        val notificationId = settingsRepo.nextLiveNotificationId()
        val requestCode = notificationId
        val intent = Intent(this, PopupActivity::class.java).apply {
            putExtra("rid", rid)
            putExtra("uname", uname)
            putExtra("faceUrl", faceUrl)
            putExtra("autoJump", autoJump)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        getSystemService(NotificationManager::class.java)
            .notify(notificationId, notificationHelper.getLiveNotification("开播提醒", "$uname 正在直播中", pendingIntent))
    }

    private fun jumpToLive(rid: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://live.bilibili.com/$rid"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            intent.setPackage("tv.danmaku.bili")
            startActivity(intent)
        } catch (_: Exception) {
            intent.setPackage(null)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        unregisterNetworkCallback()
        if (settingsRepo.isServiceWasRunning()) {
            val intent = Intent(this, BiliLiveService::class.java)
            try {
                ContextCompat.startForegroundService(this, intent)
            } catch (e: Exception) {
                Log.w(TAG, "后台重启服务受限", e)
            }
        }
        super.onDestroy()
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
    }

    companion object {
        private const val TAG = "BiliLiveService"
    }
}
