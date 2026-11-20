package com.dec.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream

object ScreenCapture {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay?    = null
    private var imageReader: ImageReader?           = null
    private var initialized = false

    fun capture(context: Context, token: ProjectionToken, workerUrl: String, authToken: String): ByteArray? {
        return try {
            val wm      = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(metrics)

            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val d = metrics.densityDpi

            if (!initialized) {
                val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = mpm.getMediaProjection(token.resultCode, token.data)
                imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
                virtualDisplay = mediaProjection!!.createVirtualDisplay(
                    "dec_cap", w, h, d,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface, null, null
                )
                initialized = true
                Thread.sleep(300) // wait for first frame
            }

            val image = imageReader?.acquireLatestImage() ?: return null
            try {
                val planes     = image.planes
                val buffer     = planes[0].buffer
                val pxStride   = planes[0].pixelStride
                val rowStride  = planes[0].rowStride
                val rowPad     = rowStride - pxStride * w

                val bmp = Bitmap.createBitmap(w + rowPad / pxStride, h, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bmp, 0, 0, w, h)
                bmp.recycle()

                val out = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.PNG, 70, out)
                cropped.recycle()
                out.toByteArray()
            } finally {
                image.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun release() {
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        initialized = false
    }
}
