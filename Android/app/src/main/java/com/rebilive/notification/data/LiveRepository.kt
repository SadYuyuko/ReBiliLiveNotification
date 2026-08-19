package com.rebilive.notification.data

import com.rebilive.notification.model.LiveResponse
import com.rebilive.notification.model.UserInfoResponse
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import org.json.JSONObject

object LiveRepository {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    @Volatile
    private var buvidCookie: String? = null

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            ensureBuvidCookie()
            val builder = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://live.bilibili.com/")
                .header("Origin", "https://live.bilibili.com")
                .header("Accept", "application/json, text/plain, */*")
            buvidCookie?.let { builder.header("Cookie", it) }
            chain.proceed(builder.build())
        }
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

    private fun ensureBuvidCookie() {
        if (buvidCookie != null) return
        synchronized(this) {
            if (buvidCookie != null) return
            try {
                val conn = URL("https://api.bilibili.com/x/frontend/finger/spi")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Referer", "https://www.bilibili.com/")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val data = JSONObject(body).getJSONObject("data")
                val b3 = data.optString("b_3")
                val b4 = data.optString("b_4")
                buvidCookie = buildString {
                    if (b3.isNotBlank()) append("buvid3=$b3; ")
                    if (b4.isNotBlank()) append("buvid4=$b4; ")
                    append("b_nut=${System.currentTimeMillis() / 1000}")
                }
            } catch (_: Exception) {
            }
        }
    }
}
