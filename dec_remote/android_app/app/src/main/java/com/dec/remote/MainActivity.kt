package com.dec.remote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etBotToken: EditText
    private lateinit var etChatId: EditText
    private lateinit var etWorkerUrl: EditText
    private lateinit var etAuthToken: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnCloudflareGuide: Button
    private lateinit var tvStatus: TextView

    private val PERM_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etBotToken        = findViewById(R.id.etBotToken)
        etChatId          = findViewById(R.id.etChatId)
        etWorkerUrl       = findViewById(R.id.etWorkerUrl)
        etAuthToken       = findViewById(R.id.etAuthToken)
        btnStart          = findViewById(R.id.btnStart)
        btnStop           = findViewById(R.id.btnStop)
        btnCloudflareGuide= findViewById(R.id.btnCloudflareGuide)
        tvStatus          = findViewById(R.id.tvStatus)

        val prefs = getSharedPreferences("dec_config", MODE_PRIVATE)
        etBotToken.setText(prefs.getString("bot_token", ""))
        etChatId.setText(prefs.getString("chat_id", ""))
        etWorkerUrl.setText(prefs.getString("worker_url", ""))
        etAuthToken.setText(prefs.getString("auth_token", "dec_secret_xyz123"))

        btnStart.setOnClickListener {
            val botToken  = etBotToken.text.toString().trim()
            val chatId    = etChatId.text.toString().trim()
            val workerUrl = etWorkerUrl.text.toString().trim()
            val authToken = etAuthToken.text.toString().trim()

            if (botToken.isEmpty() || chatId.isEmpty() || workerUrl.isEmpty()) {
                Toast.makeText(this, "सभी fields भरो", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("bot_token", botToken)
                .putString("chat_id", chatId)
                .putString("worker_url", workerUrl)
                .putString("auth_token", authToken)
                .apply()

            requestPermissionsAndStart()
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, RemoteService::class.java))
            tvStatus.text = "⛔ Stopped"
        }

        btnCloudflareGuide.setOnClickListener {
            CloudflareGuideDialog(this).show()
        }
    }

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERM_REQUEST)
        } else {
            startRemoteService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST) startRemoteService()
    }

    private fun startRemoteService() {
        val intent = Intent(this, ScreenCaptureActivity::class.java)
        startActivity(intent)
        tvStatus.text = "✅ Running — Telegram पर URL check करो"
    }
}
