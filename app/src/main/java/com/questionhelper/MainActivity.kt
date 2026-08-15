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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = stringResource(R.string.stat_total),
                    value = "$questionCount",
                    icon = Icons.Default.MenuBook,
                    modifier = Modifier.weight(1f),
                    gradient = Brush.linearGradient(
                        listOf(Color(0xFF4A90E2), Color(0xFF357ABD))
                    )
                )
                StatCard(
                    title = stringResource(R.string.stat_wrong),
                    value = "$wrongCount",
                    icon = Icons.Default.Error,
                    modifier = Modifier.weight(1f),
                    gradient = Brush.linearGradient(
                        listOf(Color(0xFFE57373), Color(0xFFD32F2F))
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.feature_list),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(features) { feature ->
                    FeatureCard(feature = feature)
                }
            }
        }

        if (showCaptureDialog) {
            CaptureChoiceDialog(
                onDismiss = { showCaptureDialog = false },
                onRequestMediaProjection = {
                    showCaptureDialog = false
                    onRequestMediaProjection()
                },
                onAccessibility = {
                    showCaptureDialog = false
                    Toast.makeText(context, R.string.capture_accessibility_guide, Toast.LENGTH_LONG).show()
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    gradient: Brush
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = value,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureCard(feature: FeatureItem) {
    Card(
        onClick = feature.action,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = feature.title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CaptureChoiceDialog(
    onDismiss: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onAccessibility: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.capture_choice_title)) },
        text = {
            Column {
                Text(stringResource(R.string.capture_choice_desc))
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    onClick = onRequestMediaProjection,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ScreenShare, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.capture_media_projection),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.capture_media_projection_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    onClick = onAccessibility,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Accessibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.capture_accessibility),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.capture_accessibility_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

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