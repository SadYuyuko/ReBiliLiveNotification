package com.rebilive.notification.data

import com.rebilive.notification.model.LiveResponse
import com.rebilive.notification.model.UserInfoResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface BiliApi {
    @GET("room/v1/Room/room_init")
    suspend fun getLiveStatus(@Query("id") roomId: String): LiveResponse

    @GET("live_user/v1/Master/info")
    suspend fun getUserInfo(@Query("uid") uid: Long): UserInfoResponse
}
