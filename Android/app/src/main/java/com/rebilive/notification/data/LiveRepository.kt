package com.rebilive.notification.data

import com.rebilive.notification.model.LiveResponse
import com.rebilive.notification.model.UserInfoResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object LiveRepository {
    private val api = Retrofit.Builder()
        .baseUrl("https://api.live.bilibili.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BiliApi::class.java)

    suspend fun checkLive(roomId: String): LiveResponse = api.getLiveStatus(roomId)

    suspend fun getUserInfo(uid: Long): UserInfoResponse = api.getUserInfo(uid)
}
