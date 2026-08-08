package com.questionhelper.ocr

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrManager(context: Context) {
    private val recognizer: TextRecognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    suspend fun recognizeFromBitmap(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text.trim()
                Log.d("OCR", "Recognized: $text")
                continuation.resume(text)
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Recognition failed", e)
                continuation.resumeWithException(e)
            }
    }

    suspend fun recognizeFromImage(image: Image, rotationDegrees: Int): String = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromMediaImage(image, rotationDegrees)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText.text.trim())
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }

    fun close() {
        recognizer.close()
    }
}
