package com.rebilive.notification

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rebilive.notification.data.LiveRepository
import com.rebilive.notification.data.SettingsRepository
import com.rebilive.notification.service.BiliLiveService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import org.json.JSONObject

data class RoomStatus(val roomId: String, val uname: String, val status: String)

data class UpdateCheckResult(val success: Boolean, val message: String, val url: String? = null)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)

    val roomIds = MutableStateFlow(settingsRepo.getRoomIds().joinToString(","))
    val interval = MutableStateFlow(settingsRepo.getInterval().toString())
    val notifyEnabled = MutableStateFlow(settingsRepo.isNotifyEnabled())
    val autoJumpEnabled = MutableStateFlow(settingsRepo.isAutoJumpEnabled())

    private val _roomStatusList = MutableStateFlow<List<RoomStatus>>(emptyList())
    val roomStatusList: StateFlow<List<RoomStatus>> = _roomStatusList

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateCheckResult: StateFlow<UpdateCheckResult?> = _updateCheckResult

    fun saveSettings() {
        settingsRepo.saveRoomIds(
            roomIds.value.replace("，", ",").split(",").map { it.trim() }.filter { it.isNotBlank() }
        )
        refreshStatus()
    }

    fun setNotifyEnabled(v: Boolean) {
        notifyEnabled.value = v
        settingsRepo.setNotifyEnabled(v)
    }

    fun setAutoJumpEnabled(v: Boolean) {
        autoJumpEnabled.value = v
        settingsRepo.setAutoJumpEnabled(v)
    }

    fun updateInterval(value: String) {
        interval.value = value
        settingsRepo.saveInterval(value.toIntOrNull() ?: 20)
    }

    fun toggleService() {
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, BiliLiveService::class.java)
        if (_isServiceRunning.value) {
            context.stopService(intent)
            _isServiceRunning.value = false
        } else {
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
            _isServiceRunning.value = true
            refreshStatus()
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val ids = settingsRepo.getRoomIds()
            _roomStatusList.value = ids.map { rid ->
                try {
                    val liveResp = LiveRepository.checkLive(rid)
                    if (liveResp.code == 0 && liveResp.data != null) {
                        val userResp = LiveRepository.getUserInfo(liveResp.data.uid)
                        val uname = userResp.data?.info?.uname ?: rid
                        val status = if (liveResp.data.liveStatus == 1) "直播中" else "未开播"
                        RoomStatus(rid, uname, status)
                    } else {
                        RoomStatus(rid, rid, "获取失败")
                    }
                } catch (_: Exception) {
                    RoomStatus(rid, rid, "网络错误")
                }
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateCheckResult.value = null
            try {
                val json = withContext(Dispatchers.IO) {
                    URL("https://api.github.com/repos/SadYuyuko/biliLiveNotification/releases/latest")
                        .openStream().bufferedReader().use { it.readText() }
                }
                val obj = JSONObject(json)
                val tag = obj.getString("tag_name")
                val body = obj.optString("body", "").trim()
                val htmlUrl = obj.getString("html_url")
                val msg = buildString {
                    append("最新版本: $tag")
                    if (body.isNotEmpty()) append("\n\n更新内容:\n$body")
                    append("\n\n点击确定前往下载页面")
                }
                _updateCheckResult.value = UpdateCheckResult(true, msg, htmlUrl)
            } catch (e: Exception) {
                _updateCheckResult.value = UpdateCheckResult(false, "检查更新失败: ${e.message}")
            }
        }
    }

    fun clearUpdateResult() {
        _updateCheckResult.value = null
    }
}
