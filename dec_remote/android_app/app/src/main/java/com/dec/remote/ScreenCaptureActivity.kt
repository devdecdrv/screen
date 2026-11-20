package com.dec.remote

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Transparent activity — launches the system screen capture permission dialog,
 * passes the result token to RemoteService, then finishes immediately.
 * User sees: standard Android "Start recording?" dialog.
 */
class ScreenCaptureActivity : Activity() {

    private val REQUEST_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Forward token to RemoteService
            val serviceIntent = Intent(this, RemoteService::class.java).apply {
                putExtra("result_code", resultCode)
                putExtra("result_data", data)
            }
            startService(serviceIntent)
        }
        finish()
    }
}
