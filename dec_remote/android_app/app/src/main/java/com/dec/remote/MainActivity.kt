package com.dec.remote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dec.remote.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val PERM_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load saved config
        val prefs = getSharedPreferences("dec_config", MODE_PRIVATE)
        binding.etBotToken.setText(prefs.getString("bot_token", ""))
        binding.etChatId.setText(prefs.getString("chat_id", ""))
        binding.etWorkerUrl.setText(prefs.getString("worker_url", ""))
        binding.etAuthToken.setText(prefs.getString("auth_token", "dec_secret_xyz123"))

        binding.btnStart.setOnClickListener {
            val botToken  = binding.etBotToken.text.toString().trim()
            val chatId    = binding.etChatId.text.toString().trim()
            val workerUrl = binding.etWorkerUrl.text.toString().trim()
            val authToken = binding.etAuthToken.text.toString().trim()

            if (botToken.isEmpty() || chatId.isEmpty() || workerUrl.isEmpty()) {
                Toast.makeText(this, "सभी fields भरो", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save config
            prefs.edit()
                .putString("bot_token", botToken)
                .putString("chat_id", chatId)
                .putString("worker_url", workerUrl)
                .putString("auth_token", authToken)
                .apply()

            requestPermissionsAndStart()
        }

        binding.btnStop.setOnClickListener {
            stopService(Intent(this, RemoteService::class.java))
            binding.tvStatus.text = "⛔ Stopped"
        }

        binding.btnCloudflareGuide.setOnClickListener {
            CloudflareGuideDialog(this).show()
        }
    }

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.FOREGROUND_SERVICE)
        }

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
        if (requestCode == PERM_REQUEST) {
            startRemoteService()
        }
    }

    private fun startRemoteService() {
        val intent = Intent(this, RemoteService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        binding.tvStatus.text = "✅ Running — Telegram पर URL check करो"
    }
}
