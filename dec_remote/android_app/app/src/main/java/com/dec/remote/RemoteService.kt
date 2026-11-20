package com.dec.remote

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class RemoteService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var prefs: android.content.SharedPreferences
    private var botToken  = ""
    private var chatId    = ""
    private var workerUrl = ""
    private var authToken = ""

    // Screenshot
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay?   = null
    private var imageReader: ImageReader?          = null
    private var screenWidth  = 0
    private var screenHeight = 0
    private var screenDensity = 0

    companion object {
        const val CHANNEL_ID    = "dec_remote_channel"
        const val NOTIF_ID      = 1
        const val SCREENSHOT_MS = 1000L   // every 1 second
        const val ACTION_PROJECTION = "com.dec.remote.ACTION_PROJECTION"
        var projectionResultCode = 0
        var projectionData: Intent? = null
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("dec_config", Context.MODE_PRIVATE)
        botToken  = prefs.getString("bot_token", "")  ?: ""
        chatId    = prefs.getString("chat_id", "")    ?: ""
        workerUrl = prefs.getString("worker_url", "").orEmpty().trimEnd('/')
        authToken = prefs.getString("auth_token", "") ?: ""

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Starting..."))

        // Get screen metrics
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        screenWidth   = metrics.widthPixels
        screenHeight  = metrics.heightPixels
        screenDensity = metrics.densityDpi

        serviceScope.launch {
            registerDevice()
            sendStartupMessage()
            startScreenshotLoop()
        }
    }

    // ── Register device with Cloudflare Worker ────────────────────────────────
    private suspend fun registerDevice() {
        val info = JSONObject().apply {
            put("model",   Build.MODEL)
            put("brand",   Build.BRAND)
            put("android", Build.VERSION.RELEASE)
            put("width",   screenWidth)
            put("height",  screenHeight)
        }
        postToWorker("/push/register", info.toString())
    }

    // ── Send startup Telegram message with HTTPS URL ──────────────────────────
    private suspend fun sendStartupMessage() {
        val mcpUrl = "$workerUrl/mcp"
        val msg = """
🚀 dec-remote started!

📱 Device: ${Build.BRAND} ${Build.MODEL}
🔗 MCP URL: $mcpUrl

Claude Desktop config:
{
  "mcpServers": {
    "phone": {
      "url": "$mcpUrl",
      "type": "http"
    }
  }
}

✅ Copy the URL above and paste in Claude → Settings → MCP
        """.trimIndent()

        sendTelegram(msg)
        updateNotification("✅ Running — check Telegram")
    }

    // ── Screenshot loop ───────────────────────────────────────────────────────
    private suspend fun startScreenshotLoop() {
        // MediaProjection needs Activity result — use AccessibilityService fallback
        // OR use root if available, OR use the MediaProjection token passed from Activity
        while (isActive) {
            try {
                val png = captureScreen()
                if (png != null) {
                    val b64 = Base64.encodeToString(png, Base64.NO_WRAP)
                    val payload = JSONObject().apply {
                        put("data",   b64)
                        put("ts",     System.currentTimeMillis().toString())
                        put("width",  screenWidth)
                        put("height", screenHeight)
                    }
                    postToWorker("/push/screenshot", payload.toString())
                }
            } catch (e: Exception) {
                // ignore frame errors
            }
            delay(SCREENSHOT_MS)
        }
    }

    // ── Screen capture via MediaProjection ────────────────────────────────────
    private fun captureScreen(): ByteArray? {
        // Setup MediaProjection on first call
        if (mediaProjection == null && projectionResultCode != 0 && projectionData != null) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(projectionResultCode, projectionData!!)

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "dec_capture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, null
            )
        }

        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null

        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride   = planes[0].rowStride
            val rowPadding  = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop to exact screen size
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            bitmap.recycle()

            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.PNG, 80, out)
            cropped.recycle()
            out.toByteArray()
        } finally {
            image.close()
        }
    }

    // ── HTTP POST to Cloudflare Worker ────────────────────────────────────────
    private fun postToWorker(endpoint: String, body: String): Boolean {
        return try {
            val conn = URL("$workerUrl$endpoint").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Dec-Token", authToken)
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout    = 5000
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    // ── Telegram send ─────────────────────────────────────────────────────────
    private fun sendTelegram(text: String) {
        try {
            val url  = "https://api.telegram.org/bot$botToken/sendMessage"
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text",    text)
            }.toString()
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.responseCode  // trigger request
        } catch (_: Exception) {}
    }

    // ── Notification helpers ──────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "dec Remote", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("dec Remote")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Receive MediaProjection token from ScreenCaptureActivity
        intent?.let {
            val code = it.getIntExtra("result_code", 0)
            val data = it.getParcelableExtra<Intent>("result_data")
            if (code != 0 && data != null) {
                projectionResultCode = code
                projectionData       = data
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
