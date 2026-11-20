package com.dec.remote

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

class CloudflareGuideDialog(private val ctx: Context) {

    fun show() {
        AlertDialog.Builder(ctx)
            .setTitle("☁️ Cloudflare Setup (3 मिनट)")
            .setMessage("""
Step 1: cloudflare.com पर free account बनाओ

Step 2: Login → Workers & Pages → Create Application → Create Worker

Step 3: Editor में पुराना code delete करो, नया paste करो
        (worker.js file ZIP में है)

Step 4: "AUTH_TOKEN" को बदलो — same value यहाँ App में डालो

Step 5: Save and Deploy → तुम्हारा URL मिलेगा:
        https://dec-remote.yourname.workers.dev

Step 6: वो URL यहाँ App में "Worker URL" field में डालो

Step 7: Start press करो → Telegram पर MCP URL आएगा
            """.trimIndent())
            .setPositiveButton("Cloudflare खोलो") { _, _ ->
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://dash.cloudflare.com"))
                )
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
