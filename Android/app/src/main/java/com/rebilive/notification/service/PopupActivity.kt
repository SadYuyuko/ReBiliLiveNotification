package com.rebilive.notification.service

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.rebilive.notification.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class PopupActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PopupActivity"
        private const val CONNECT_TIMEOUT = 5000
        private const val READ_TIMEOUT = 5000
    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.popup_live)

        val rid = intent.getStringExtra("rid") ?: return finish()
        val uname = intent.getStringExtra("uname") ?: rid
        val faceUrl = intent.getStringExtra("faceUrl")

        findViewById<TextView>(R.id.tv_uname).text = uname

        if (faceUrl != null) {
            val avatar = findViewById<ImageView>(R.id.iv_avatar)
            executor.execute {
                try {
                    val url = URL(faceUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = CONNECT_TIMEOUT
                    conn.readTimeout = READ_TIMEOUT
                    conn.connect()
                    val bmp = BitmapFactory.decodeStream(conn.inputStream)
                    conn.disconnect()
                    if (!isFinishing && !isDestroyed) {
                        runOnUiThread { avatar.setImageBitmap(bmp) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load avatar from $faceUrl", e)
                }
            }
        }

        findViewById<MaterialButton>(R.id.btn_open).setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://live.bilibili.com/$rid"))
                    .setPackage("tv.danmaku.bili")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
        }

        findViewById<MaterialButton>(R.id.btn_ignore).setOnClickListener { finish() }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
