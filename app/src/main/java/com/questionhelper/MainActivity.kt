package com.questionhelper

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.questionhelper.bank.QuestionBankActivity
import com.questionhelper.data.QuestionRepository
import com.questionhelper.search.AccessibilitySearchService
import com.questionhelper.search.CameraSearchActivity
import com.questionhelper.search.FloatWindowService
import com.questionhelper.search.ScreenCaptureService
import com.questionhelper.ui.theme.QuestionHelperTheme
import kotlinx.coroutines.launch

data class FeatureItem(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val action: () -> Unit
)

class MainActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        screenCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                startScreenCaptureService(result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, R.string.permission_media_projection_denied, Toast.LENGTH_SHORT).show()
            }
        }

        // 监听录屏权限被撤销的事件
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == ScreenCaptureService.ACTION_PROJECTION_STOPPED) {
                        ScreenCaptureService.markProjectionStopped()
                        Toast.makeText(this@MainActivity, R.string.permission_projection_stopped, Toast.LENGTH_LONG).show()
                    }
                }
            },
            IntentFilter(ScreenCaptureService.ACTION_PROJECTION_STOPPED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val showCaptureChoice = intent.getBooleanExtra("show_capture_choice", false)
        setContent {
            QuestionHelperTheme {
                MainScreen(
                    showCaptureChoice = showCaptureChoice,
                    onRequestMediaProjection = { requestMediaProjection() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkServiceStateAndStartFloatWindow()
    }

    private fun checkServiceStateAndStartFloatWindow() {
        val hasOverlayPermission = FloatWindowService.checkPermission(this)
        val hasAccessibility = isAccessibilityServiceEnabled()
        val hasScreenCapture = ScreenCaptureService.isRunning && ScreenCaptureService.isInitialized

        if (hasOverlayPermission && (hasAccessibility || hasScreenCapture)) {
            FloatWindowService.start(this)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return isAccessibilityEnabled(this)
    }

    private fun requestMediaProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
        } catch (e: Exception) {
            Log.e("MainActivity", "Launch screen capture intent failed", e)
            Toast.makeText(this, getString(R.string.permission_media_projection_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("result_code", resultCode)
            putExtra("result_data", data)
        }
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, R.string.permission_screen_capture_started, Toast.LENGTH_SHORT).show()
            FloatWindowService.start(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Start screen capture service failed", e)
            Toast.makeText(this, getString(R.string.permission_screen_capture_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    showCaptureChoice: Boolean = false,
    onRequestMediaProjection: () -> Unit = {}
) {
    val context = LocalContext.current
    var questionCount by remember { mutableStateOf(0) }
    var wrongCount by remember { mutableStateOf(0) }
    var showCaptureDialog by remember { mutableStateOf(showCaptureChoice) }

    LaunchedEffect(Unit) {
        val repo = QuestionRepository(QuestionApp.database.questionDao())
        launch {
            questionCount = repo.getQuestionCount()
            wrongCount = repo.getWrongCount()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title), fontSize = 22.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(stringResource(R.string.stat_total), "$questionCount", Icons.Default.MenuBook)
                    StatItem(stringResource(R.string.stat_wrong), "$wrongCount", Icons.Default.Error)
                }
            }

            Text(
                text = stringResource(R.string.feature_list),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val features = listOf(
                FeatureItem(
                    stringResource(R.string.feature_camera_search),
                    Icons.Default.CameraAlt,
                    stringResource(R.string.feature_camera_desc)
                ) {
                    context.startActivity(Intent(context, CameraSearchActivity::class.java))
                },
                FeatureItem(
                    stringResource(R.string.feature_float_search),
                    Icons.Default.TouchApp,
                    stringResource(R.string.feature_float_desc)
                ) {
                    if (!FloatWindowService.checkPermission(context)) {
                        Toast.makeText(context, R.string.permission_overlay_denied, Toast.LENGTH_LONG).show()
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        return@FeatureItem
                    }

                    val hasScreenCapture = ScreenCaptureService.isRunning && ScreenCaptureService.isInitialized
                    val hasAccessibility = isAccessibilityEnabled(context)

                    if (!hasScreenCapture && !hasAccessibility) {
                        showCaptureDialog = true
                    } else {
                        FloatWindowService.start(context)
                        Toast.makeText(context, R.string.float_ball_showing, Toast.LENGTH_SHORT).show()
                    }
                },
                FeatureItem(
                    stringResource(R.string.feature_question_bank),
                    Icons.Default.LibraryBooks,
                    stringResource(R.string.feature_bank_desc)
                ) {
                    context.startActivity(Intent(context, QuestionBankActivity::class.java))
                },
                FeatureItem(
                    stringResource(R.string.feature_wrong_book),
                    Icons.Default.Bookmark,
                    stringResource(R.string.feature_wrong_desc)
                ) {
                    val intent = Intent(context, QuestionBankActivity::class.java)
                    intent.putExtra("mode", "wrong")
                    context.startActivity(intent)
                }
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(features) { feature ->
                    FeatureCard(feature)
                }
            }
        }
    }

    if (showCaptureDialog) {
        AlertDialog(
            onDismissRequest = { showCaptureDialog = false },
            title = { Text(stringResource(R.string.capture_choice_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.capture_choice_desc))
                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        onClick = {
                            showCaptureDialog = false
                            onRequestMediaProjection()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ScreenShare, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.capture_media_projection), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.capture_media_projection_desc), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        onClick = {
                            showCaptureDialog = false
                            Toast.makeText(context, R.string.capture_accessibility_guide, Toast.LENGTH_LONG).show()
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Accessibility, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.capture_accessibility), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.capture_accessibility_desc), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCaptureDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun StatItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FeatureCard(feature: FeatureItem) {
    Card(
        onClick = feature.action,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                feature.icon,
                contentDescription = feature.title,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(feature.title, fontSize = 16.sp)
            Text(
                feature.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 统一的无障碍服务检测工具函数
 * 通过 AccessibilityManager 获取已安装并启用的服务列表，进行精确匹配
 */
fun isAccessibilityEnabled(context: Context): Boolean {
    return try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ) ?: return false

        val expectedComponent = ComponentName(
            context.packageName,
            "com.questionhelper.search.AccessibilitySearchService"
        )

        enabledServices.any { serviceInfo ->
            val resolvedInfo = serviceInfo.resolveInfo?.serviceInfo
            resolvedInfo?.let {
                ComponentName(it.packageName, it.name) == expectedComponent
            } ?: false
        }
    } catch (e: Exception) {
        Log.e("AccessibilityCheck", "Check failed", e)
        false
    }
}

