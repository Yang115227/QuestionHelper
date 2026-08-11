package com.questionhelper.search

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import com.questionhelper.QuestionApp
import com.questionhelper.R
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ocr.OcrManager
import com.questionhelper.ui.theme.QuestionHelperTheme
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraSearchActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var ocrManager: OcrManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                initOcrManager()
                showCameraScreen()
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    Toast.makeText(
                        this,
                        R.string.permission_camera_denied_permanent,
                        Toast.LENGTH_LONG
                    ).show()
                    openAppSettings()
                } else {
                    Toast.makeText(this, R.string.permission_camera_required, Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }

        when (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                initOcrManager()
                showCameraScreen()
            }
            else -> {
                Toast.makeText(this, R.string.permission_camera_requesting, Toast.LENGTH_SHORT).show()
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun showCameraScreen() {
        setContent {
            QuestionHelperTheme {
                CameraSearchScreen(
                    onCapture = { bitmap -> processImage(bitmap) },
                    onClose = { finish() }
                )
            }
        }
    }

    private fun initOcrManager() {
        if (ocrManager != null) return
        try {
            ocrManager = OcrManager(this)
            Log.d("CameraSearch", "OCR initialized, ready=${ocrManager?.isReady}")
        } catch (e: Throwable) {
            Log.e("CameraSearch", "OCR init failed", e)
            Toast.makeText(this, getString(R.string.ocr_init_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun processImage(bitmap: Bitmap) {
        initOcrManager()
        val manager = ocrManager
        if (manager == null) {
            Toast.makeText(this, R.string.ocr_not_initialized, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val text = manager.recognizeFromBitmap(bitmap)
                if (text.isNotEmpty()) {
                    val repo = QuestionRepository(QuestionApp.database.questionDao())
                    val question = repo.searchQuestion(text.take(50))
                    if (question != null) {
                        showResult(question.content, question.answer, question.analysis)
                    } else {
                        showResult(text, getString(R.string.no_match_found), getString(R.string.search_suggestions))
                    }
                } else {
                    Toast.makeText(this@CameraSearchActivity, R.string.ocr_no_text, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Throwable) {
                Log.e("CameraSearch", "Error", e)
                Toast.makeText(this@CameraSearchActivity, getString(R.string.ocr_failed_with_reason, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showResult(question: String, answer: String, analysis: String) {
        val intent = Intent(this, SearchResultActivity::class.java).apply {
            putExtra("question", question)
            putExtra("answer", answer)
            putExtra("analysis", analysis)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        ocrManager?.close()
    }
}

@Composable
fun CameraSearchScreen(
    onCapture: (Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(previewView) {
        previewView?.let { view ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(view.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture

                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                } catch (e: Throwable) {
                    Log.e("Camera", "Binding failed", e)
                    Toast.makeText(context, R.string.camera_bind_failed, Toast.LENGTH_LONG).show()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    if (isProcessing) return@FilledTonalButton
                    isProcessing = true
                    imageCapture?.let { capture ->
                        capture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bitmap = imageProxyToBitmap(image)
                                    image.close()
                                    bitmap?.let { onCapture(it) }
                                    isProcessing = false
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    isProcessing = false
                                    Log.e("Camera", "Capture failed", exception)
                                    Toast.makeText(context, R.string.camera_capture_failed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } ?: run {
                        isProcessing = false
                        Toast.makeText(context, R.string.camera_not_ready, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(80.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(
                    Icons.Default.Camera,
                    contentDescription = stringResource(R.string.take_photo),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (isProcessing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        when (image.format) {
            ImageFormat.JPEG, ImageFormat.RGB_565 -> {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            else -> yuv420888ToBitmap(image)
        }
    } catch (e: Throwable) {
        Log.e("Camera", "Convert ImageProxy to Bitmap failed", e)
        null
    }
}

private fun yuv420888ToBitmap(image: ImageProxy): Bitmap? {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(
        nv21,
        ImageFormat.NV21,
        image.width,
        image.height,
        null
    )
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
    return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}
