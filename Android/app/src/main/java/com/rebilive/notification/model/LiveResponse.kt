package com.rebilive.notification.model

import com.google.gson.annotations.SerializedName

data class LiveResponse(
    val code: Int,
    val msg: String?,
    val data: LiveData?
)

data class LiveData(
    @SerializedName("room_id") val roomId: Long,
    val uid: Long,
    @SerializedName("live_status") val liveStatus: Int
)

data class UserInfoResponse(
    val code: Int,
    val data: UserInfoData?
)

data class UserInfoData(
    val info: UserInfo?
)

data class UserInfo(
    val uname: String,
    val face: String
)
