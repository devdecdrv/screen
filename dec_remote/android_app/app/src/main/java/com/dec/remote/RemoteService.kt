package com.dec.remote

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RemoteService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "dec_remote"
        const val NOTIF_ID   = 1
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotif("Starting..."))

        val prefs     = getSharedPreferences("dec_config", Context.MODE_PRIVATE)
        val botToken  = prefs.getString("bot_token", "")  ?: ""
        val chatId    = prefs.getString("chat_id", "")    ?: ""
        val workerUrl = prefs.getString("worker_url", "").orEmpty().trimEnd('/')
        val authToken = prefs.getString("auth_token", "") ?: ""

        scope.launch {
            registerDevice(workerUrl, authToken)
            sendStartupTelegram(botToken, chatId, workerUrl)
            updateNotif("Running — check Telegram")
            screenshotLoop(workerUrl, authToken)
        }
    }

    private fun registerDevice(workerUrl: String, authToken: String) {
        val info = JSONObject()
        info.put("model",   Build.MODEL)
        info.put("brand",   Build.BRAND)
        info.put("android", Build.VERSION.RELEASE)
        postJson("$workerUrl/push/register", info.toString(), authToken)
    }

    private fun sendStartupTelegram(botToken: String, chatId: String, workerUrl: String) {
        val mcpUrl = "$workerUrl/mcp"
        val msg = "dec-remote started!\n\nDevice: ${Build.BRAND} ${Build.MODEL}\n\nMCP URL:\n$mcpUrl\n\nClaude config:\n{\"mcpServers\":{\"phone\":{\"url\":\"$mcpUrl\",\"type\":\"http\"}}}"
        val body = JSONObject()
        body.put("chat_id", chatId)
        body.put("text", msg)
        postJson("https://api.telegram.org/bot$botToken/sendMessage", body.toString(), "")
    }

    private suspend fun screenshotLoop(workerUrl: String, authToken: String) {
        // Screenshot via MediaProjection requires Activity result token
        // Waiting for token from ScreenCaptureActivity
        while (scope.isActive) {
            val token = RemoteServiceState.projectionToken
            if (token != null) {
                val png = ScreenCapture.capture(this, token, workerUrl, authToken)
                if (png != null) {
                    val b64 = android.util.Base64.encodeToString(png, android.util.Base64.NO_WRAP)
                    val payload = JSONObject()
                    payload.put("data", b64)
                    payload.put("ts", System.currentTimeMillis().toString())
                    postJson("$workerUrl/push/screenshot", payload.toString(), authToken)
                }
            }
            delay(1000L)
        }
    }

    private fun postJson(url: String, body: String, token: String): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            if (token.isNotEmpty()) conn.setRequestProperty("X-Dec-Token", token)
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout    = 5000
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "dec Remote", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("dec Remote")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotif(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotif(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val code = it.getIntExtra("result_code", 0)
            val data = it.getParcelableExtra<Intent>("result_data")
            if (code != 0 && data != null) {
                RemoteServiceState.projectionToken = ProjectionToken(code, data)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

data class ProjectionToken(val resultCode: Int, val data: Intent)

object RemoteServiceState {
    var projectionToken: ProjectionToken? = null
}
