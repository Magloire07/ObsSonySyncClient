package com.example.obssonysync

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class CaptureService : Service() {

    private lateinit var mediaProjection: MediaProjection
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader

    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private var isRecDetected = false

    private val captureRunnable = object : Runnable {
        override fun run() {
            val bitmap = captureScreen()
            if (bitmap != null) {
                saveBitmap(bitmap, "test_${System.currentTimeMillis()}")
                checkForREC(bitmap)
            } else {
                Log.e("CAPTURE", "Capture écran échouée")
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val resultData = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        // Create and start foreground notification first so the service is recognized by the system
        val notification = NotificationCompat.Builder(this, "capture_channel")
            .setContentTitle("Capture active")
            .setContentText("Surveillance REC en cours")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)

        // Now it's safe to obtain the MediaProjection
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                handler.removeCallbacks(captureRunnable)
                try {
                    virtualDisplay.release()
                } catch (e: Exception) { /* ignore if not initialized */ }
                try {
                    imageReader.close()
                } catch (e: Exception) { /* ignore if not initialized */ }
                Log.d("CAPTURE", "MediaProjection arrêtée")
            }
        }, handler)

        setupVirtualDisplay()

        handler.post(captureRunnable)

        return START_STICKY
    }

    private fun setupVirtualDisplay() {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val density = resources.displayMetrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )
    }

    private fun captureScreen(): Bitmap? {
        val image = try {
            imageReader.acquireLatestImage()
        } catch (e: Exception) {
            Log.e("CAPTURE", "Failed to acquire image", e)
            null
        } ?: return null

        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = image.width
            val height = image.height
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Return a cropped copy using width/height captured above (do not access image after close)
            return Bitmap.createBitmap(bitmap, 0, 0, width, height)
        } catch (ise: IllegalStateException) {
            Log.e("CAPTURE", "Image invalid/closed while reading", ise)
            return null
        } catch (e: Exception) {
            Log.e("CAPTURE", "Erreur lors de la capture", e)
            return null
        } finally {
            try {
                image.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap, name: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ObsSonySync")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Log.d("CAPTURE", "Image sauvegardée : $uri")
            } else {
                Log.e("CAPTURE", "Erreur : uri null")
            }
        } catch (e: Exception) {
            Log.e("CAPTURE", "Erreur sauvegarde", e)
        }
    }

    private fun checkForREC(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val recCurrentlyDetected = visionText.text.contains("REC", ignoreCase = true)
                if (recCurrentlyDetected != isRecDetected) {
                    sendSignalToPC("toggle")
                    isRecDetected = recCurrentlyDetected
                }
            }
    }

    private fun sendSignalToPC(action: String) {
        val request = Request.Builder()
            .url("http://192.168.1.131:5000/$action")
            .post("".toRequestBody())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { e.printStackTrace() }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("capture_channel", "Capture d'écran", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(captureRunnable)
        if (::mediaProjection.isInitialized) {
            mediaProjection.stop()
        }
    }
}
