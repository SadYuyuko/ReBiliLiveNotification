package com.rebilive.notification

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.core.content.ContextCompat
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
    val apiUrl = MutableStateFlow(settingsRepo.getApiUrl())
    val hideToBackground = MutableStateFlow(settingsRepo.isHideToBackground())

    private val _roomStatusList = MutableStateFlow<List<RoomStatus>>(emptyList())
    val roomStatusList: StateFlow<List<RoomStatus>> = _roomStatusList

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateCheckResult: StateFlow<UpdateCheckResult?> = _updateCheckResult

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        LiveRepository.updateBaseUrl(settingsRepo.getApiUrl())
        if (settingsRepo.isServiceWasRunning()) {
            startServiceQuietly()
        }
        registerNetworkCallback()
    }

    private fun startServiceQuietly() {
        try {
            startService()
        } catch (e: Exception) {
            Log.w(TAG, "启动服务受限", e)
        }
    }

    private fun registerNetworkCallback() {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (networkCallback != null) return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!settingsRepo.isServiceWasRunning()) return
                try {
                    startService()
                } catch (e: Exception) {
                    Log.w(TAG, "网络恢复拉起服务受限", e)
                }
            }
        }
        try {
            cm.registerDefaultNetworkCallback(networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "注册网络回调失败", e)
            networkCallback = null
        }
    }

    override fun onCleared() {
        networkCallback?.let {
            try {
                (getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
        super.onCleared()
    }

    fun saveSettings() {
        settingsRepo.saveRoomIds(
            roomIds.value.replace("，", ",").split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
        )
        settingsRepo.saveApiUrl(apiUrl.value.ifBlank { SettingsRepository.DEFAULT_API_URL })
        LiveRepository.updateBaseUrl(apiUrl.value.ifBlank { SettingsRepository.DEFAULT_API_URL })
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

    fun setHideToBackground(v: Boolean) {
        hideToBackground.value = v
        settingsRepo.setHideToBackground(v)
    }

    fun restoreDefaultApi() {
        apiUrl.value = SettingsRepository.DEFAULT_API_URL
    }

    fun updateInterval(value: String) {
        interval.value = value
        settingsRepo.saveInterval(value.toIntOrNull() ?: 20)
    }

    fun toggleService() {
        if (_isServiceRunning.value) {
            stopService()
        } else {
            startService()
        }
    }

    private fun startService() {
        val context = getApplication<Application>()
        val intent = Intent(context, BiliLiveService::class.java)
        ContextCompat.startForegroundService(context, intent)
        _isServiceRunning.value = true
        settingsRepo.setServiceWasRunning(true)
        refreshStatus()
    }

    private fun stopService() {
        val context = getApplication<Application>()
        val intent = Intent(context, BiliLiveService::class.java)
        context.stopService(intent)
        _isServiceRunning.value = false
        settingsRepo.setServiceWasRunning(false)
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

    fun exportSettings(): String = settingsRepo.exportToJson()

    fun importSettings(json: String) {
        try {
            settingsRepo.importFromJson(json)
            LiveRepository.updateBaseUrl(settingsRepo.getApiUrl())
            roomIds.value = settingsRepo.getRoomIds().joinToString(",")
            interval.value = settingsRepo.getInterval().toString()
            notifyEnabled.value = settingsRepo.isNotifyEnabled()
            autoJumpEnabled.value = settingsRepo.isAutoJumpEnabled()
            apiUrl.value = settingsRepo.getApiUrl()
            hideToBackground.value = settingsRepo.isHideToBackground()
            _importResult.value = "导入成功"
            refreshStatus()
        } catch (e: Exception) {
            _importResult.value = "导入失败: ${e.message}"
        }
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateCheckResult.value = null
            try {
                val json = withContext(Dispatchers.IO) {
                    URL("https://api.github.com/repos/SadYuyuko/ReBiliLiveNotification/releases/latest")
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

    companion object {
        private const val TAG = "MainViewModel"
    }
}
