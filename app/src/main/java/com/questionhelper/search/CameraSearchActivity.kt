package com.questionhelper.search

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.questionhelper.ocr.OcrManager
import com.questionhelper.QuestionApp
import com.questionhelper.data.QuestionRepository
import com.questionhelper.ui.theme.QuestionHelperTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraSearchActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var ocrManager: OcrManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        ocrManager = OcrManager(this)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                setContent {
                    QuestionHelperTheme {
                        CameraSearchScreen(
                            onCapture = { bitmap -> processImage(bitmap) },
                            onClose = { finish() }
                        )
                    }
                }
            } else {
                Toast.makeText(this, "需要相机权限", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) -> {
                setContent {
                    QuestionHelperTheme {
                        CameraSearchScreen(
                            onCapture = { bitmap -> processImage(bitmap) },
                            onClose = { finish() }
                        )
                    }
                }
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun processImage(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                val text = ocrManager.recognizeFromBitmap(bitmap)
                if (text.isNotEmpty()) {
                    val repo = QuestionRepository(QuestionApp.database.questionDao())
                    val question = repo.searchQuestion(text.take(50))
                    if (question != null) {
                        showResult(question.content, question.answer, question.analysis)
                    } else {
                        showResult(text, "未在题库中找到匹配题目", """建议：
1. 检查题目是否已导入
2. 尝试截取更清晰的题目区域
3. 手动搜索关键词""")
                    }
                } else {
                    Toast.makeText(this@CameraSearchActivity, "未识别到文字", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("CameraSearch", "Error", e)
                Toast.makeText(this@CameraSearchActivity, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
        ocrManager.close()
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
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                } catch (e: Exception) {
                    Log.e("Camera", "Binding failed", e)
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

        // 顶部关闭按钮
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onPrimary)
        }

        // 底部拍照按钮
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
                                }
                            }
                        )
                    }
                },
                modifier = Modifier.size(80.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(Icons.Default.Camera, contentDescription = "拍照", modifier = Modifier.size(32.dp))
            }
        }

        if (isProcessing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        // Fallback for YUV format
        val yuvImage = YuvImage(
            bytes,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }
}
