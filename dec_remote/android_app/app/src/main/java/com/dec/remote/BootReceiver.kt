package com.dec.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Auto-starts RemoteService when phone boots.
 * Only starts if previously configured (bot token saved).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("dec_config", Context.MODE_PRIVATE)
        val token = prefs.getString("bot_token", "") ?: ""
        if (token.isEmpty()) return   // not configured yet

        val serviceIntent = Intent(context, RemoteService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
