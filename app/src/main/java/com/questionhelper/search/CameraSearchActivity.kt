package com.questionhelper.search

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.questionhelper.QuestionApp
import com.questionhelper.R
import com.questionhelper.data.QuestionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class CameraSearchActivity : ComponentActivity() {

    private val cameraViewModel: CameraSearchViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraViewModel.onPermissionGranted()
        } else {
            Toast.makeText(this, "相机权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CameraSearchScreen(cameraViewModel, onBack = { finish() })
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            cameraViewModel.onPermissionGranted()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

class CameraSearchViewModel : ViewModel() {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private var lastAnalyzedTimestamp = 0L
    private val repository = QuestionRepository(QuestionApp.database.questionDao())

    data class SearchResult(
        val question: String = "",
        val answer: String = "",
        val analysis: String = "",
        val matched: Boolean = false,
        val isRecognizing: Boolean = false
    )

    private val _result = MutableStateFlow(SearchResult())
    val result: StateFlow<SearchResult> = _result.asStateFlow()

    fun onPermissionGranted() {
        // 相机实际启动在 Composable 中完成
    }

    fun processImage(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalyzedTimestamp < 1000) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestamp = currentTime

        val bitmap = imageProxy.toBitmap() ?: run {
            imageProxy.close()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _result.update { it.copy(isRecognizing = true) }
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val text = recognizer.process(inputImage).await().text
                if (text.isNotBlank()) {
                    val question = repository.searchQuestionSmart(text)
                    if (question != null) {
                        _result.value = SearchResult(
                            question = question.content,
                            answer = question.answer,
                            analysis = question.analysis ?: "",
                            matched = true,
                            isRecognizing = false
                        )
                    } else {
                        _result.value = SearchResult(
                            question = text,
                            answer = "未匹配到题库",
                            analysis = "",
                            matched = false,
                            isRecognizing = false
                        )
                    }
                } else {
                    _result.update { it.copy(isRecognizing = false) }
                }
            } catch (e: Exception) {
                _result.value = SearchResult(
                    question = "识别出错",
                    answer = e.message ?: "",
                    analysis = "",
                    matched = false,
                    isRecognizing = false
                )
            } finally {
                bitmap.recycle()
                imageProxy.close()
            }
        }
    }

    private fun ImageProxy.toBitmap(): android.graphics.Bitmap? {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val yuvImage = android.graphics.YuvImage(
            bytes,
            android.graphics.ImageFormat.NV21,
            width,
            height,
            null
        )
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 80, out)
        val jpegData = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
    }

    override fun onCleared() {
        super.onCleared()
        recognizer.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSearchScreen(
    viewModel: CameraSearchViewModel,
    onBack: () -> Unit
) {
    val result by viewModel.result.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_camera_search)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(surfaceProvider)
                                }
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                    viewModel.processImage(imageProxy)
                                }
                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    }
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (result.isRecognizing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (result.question.isNotEmpty()) {
                        Text(
                            text = result.question,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (result.matched) Color(0xFF2E7D32) else Color(0xFFE53935)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (result.answer.isNotEmpty()) {
                        Text(text = "答案", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = result.answer,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF1565C0)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (result.analysis.isNotEmpty()) {
                        Text(text = "解析", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = result.analysis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (!result.matched && result.question.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "未匹配到题库，以上为识别文本",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}