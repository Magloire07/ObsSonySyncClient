package com.example.obssonysync

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import android.graphics.BitmapFactory
import okhttp3.RequestBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import android.util.Log
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val btnCheck = findViewById<Button>(R.id.btnCheck)
        btnCheck.setOnClickListener {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.text_rec)
            checkForREC(bitmap)
        }

    }

    private fun checkForREC(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (visionText.text.contains("REC", ignoreCase = true)) {
                    Toast.makeText(this, "REC détecté !", Toast.LENGTH_SHORT).show()
                    sendSignalToPc("toggle")
                } else {
                    Toast.makeText(this, "Pas de REC trouvé", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erreur OCR", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendSignalToPc(action: String) {
        val client = OkHttpClient()
        val body = "".toRequestBody()
        val request = Request.Builder()
            .url("http://192.168.1.131:5000/$action")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("APP", "Failed to notify PC", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("APP", "PC notified: $action")
            }
        })
    }
}
