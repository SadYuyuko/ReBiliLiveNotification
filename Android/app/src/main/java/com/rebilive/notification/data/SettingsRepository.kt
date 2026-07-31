package com.rebilive.notification.data

import android.content.Context
import org.json.JSONObject

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("rebilive", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_API_URL = "https://api.live.bilibili.com/"
    }

    fun getRoomIds(): List<String> =
        prefs.getString("room_ids", "")!!.replace("，", ",").split(",").filter { it.isNotBlank() }.distinct()

    fun saveRoomIds(ids: List<String>) =
        prefs.edit().putString("room_ids", ids.joinToString(",").replace("，", ",")).apply()

    fun getInterval(): Int = prefs.getInt("interval", 20)

    fun saveInterval(sec: Int) = prefs.edit().putInt("interval", sec).apply()

    fun isNotifyEnabled(): Boolean = prefs.getBoolean("notify", true)

    fun setNotifyEnabled(v: Boolean) = prefs.edit().putBoolean("notify", v).apply()

    fun isAutoJumpEnabled(): Boolean = prefs.getBoolean("auto_jump", false)

    fun setAutoJumpEnabled(v: Boolean) = prefs.edit().putBoolean("auto_jump", v).apply()

    fun getApiUrl(): String = prefs.getString("api_url", DEFAULT_API_URL) ?: DEFAULT_API_URL

    fun saveApiUrl(url: String) = prefs.edit().putString("api_url", url).apply()

    fun isHideToBackground(): Boolean = prefs.getBoolean("hide_to_background", false)

    fun setHideToBackground(v: Boolean) = prefs.edit().putBoolean("hide_to_background", v).apply()

    fun isAutoStart(): Boolean = prefs.getBoolean("auto_start", false)

    fun setAutoStart(v: Boolean) = prefs.edit().putBoolean("auto_start", v).apply()

    fun isServiceWasRunning(): Boolean = prefs.getBoolean("service_was_running", false)

    fun setServiceWasRunning(v: Boolean) = prefs.edit().putBoolean("service_was_running", v).apply()

    fun exportToJson(): String {
        val obj = JSONObject()
        obj.put("room_ids", prefs.getString("room_ids", ""))
        obj.put("interval", prefs.getInt("interval", 20))
        obj.put("notify", prefs.getBoolean("notify", true))
        obj.put("auto_jump", prefs.getBoolean("auto_jump", false))
        obj.put("api_url", prefs.getString("api_url", DEFAULT_API_URL))
        obj.put("hide_to_background", prefs.getBoolean("hide_to_background", false))
        obj.put("auto_start", prefs.getBoolean("auto_start", false))
        return obj.toString(2)
    }

    fun importFromJson(json: String) {
        val obj = JSONObject(json)
        val editor = prefs.edit()
        if (obj.has("room_ids")) editor.putString("room_ids", obj.getString("room_ids"))
        if (obj.has("interval")) editor.putInt("interval", obj.getInt("interval"))
        if (obj.has("notify")) editor.putBoolean("notify", obj.getBoolean("notify"))
        if (obj.has("auto_jump")) editor.putBoolean("auto_jump", obj.getBoolean("auto_jump"))
        if (obj.has("api_url")) editor.putString("api_url", obj.getString("api_url"))
        if (obj.has("hide_to_background")) editor.putBoolean("hide_to_background", obj.getBoolean("hide_to_background"))
        if (obj.has("auto_start")) editor.putBoolean("auto_start", obj.getBoolean("auto_start"))
        editor.apply()
    }
}
