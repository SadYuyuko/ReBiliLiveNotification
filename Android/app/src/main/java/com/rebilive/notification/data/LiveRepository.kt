package com.rebilive.notification.data

import com.rebilive.notification.model.LiveResponse
import com.rebilive.notification.model.UserInfoResponse
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object LiveRepository {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private var currentBaseUrl: String = SettingsRepository.DEFAULT_API_URL
    private var api: BiliApi = createApi(currentBaseUrl)

    private fun createApi(baseUrl: String): BiliApi {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(url)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BiliApi::class.java)
    }

    fun updateBaseUrl(newUrl: String) {
        if (newUrl != currentBaseUrl) {
            currentBaseUrl = newUrl
            api = createApi(newUrl)
        }
    }

    suspend fun checkLive(roomId: String): LiveResponse = api.getLiveStatus(roomId)

    suspend fun getUserInfo(uid: Long): UserInfoResponse = api.getUserInfo(uid)
}
