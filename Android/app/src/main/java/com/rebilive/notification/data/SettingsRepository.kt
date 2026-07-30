package com.rebilive.notification.data

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("rebilive", Context.MODE_PRIVATE)

    fun getRoomIds(): List<String> =
        prefs.getString("room_ids", "")!!.replace("，", ",").split(",").filter { it.isNotBlank() }

    fun saveRoomIds(ids: List<String>) =
        prefs.edit().putString("room_ids", ids.joinToString(",").replace("，", ",")).apply()

    fun getInterval(): Int = prefs.getInt("interval", 20)

    fun saveInterval(sec: Int) = prefs.edit().putInt("interval", sec).apply()

    fun isNotifyEnabled(): Boolean = prefs.getBoolean("notify", true)

    fun setNotifyEnabled(v: Boolean) = prefs.edit().putBoolean("notify", v).apply()

    fun isAutoJumpEnabled(): Boolean = prefs.getBoolean("auto_jump", false)

    fun setAutoJumpEnabled(v: Boolean) = prefs.edit().putBoolean("auto_jump", v).apply()
}
