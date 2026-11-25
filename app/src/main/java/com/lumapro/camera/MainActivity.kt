package com.lumapro.camera

import android.Manifest
import android.hardware.camera2.CameraCharacteristics
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CaptureRequest
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.UseCaseGroup
import androidx.camera.media3.effect.Media3Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.camera.core.CameraSelector
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Recording
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.math.roundToInt
import kotlin.math.exp
import kotlin.math.ln
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    var volumeCaptureAction: (() -> Unit)? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) && volumeCaptureAction != null) {
            volumeCaptureAction?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    LumaCameraScreen()
                }
            }
        }
    }
}

private data class GalleryPhoto(val uri: Uri, val name: String)

@Composable
private fun LumaCameraScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showGallery by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var cameraPanelTab by remember { mutableIntStateOf(0) }
    var galleryPhotos by remember { mutableStateOf<List<GalleryPhoto>>(emptyList()) }
    var selectedPhoto by remember { mutableStateOf<GalleryPhoto?>(null) }
    var selectedPhotoUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val deletePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            selectedPhoto = null
            showGallery = false
            showGallery = true
            Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(showGallery) {
        if (showGallery) {
            galleryPhotos = withContext(Dispatchers.IO) {
                val photos = mutableListOf<GalleryPhoto>()
                val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
                val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    arrayOf("Luma_%"),
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Photo"
                        val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        photos.add(GalleryPhoto(uri, name))
                    }
                }
                photos
            }
        }
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var videoMode by remember { mutableStateOf(false) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var hasAudioPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var audioPermissionAsked by remember { mutableStateOf(hasAudioPermission) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        audioPermissionAsked = true
        Toast.makeText(context, if (granted) "Microphone enabled for video" else "Microphone denied — silent video is available", Toast.LENGTH_SHORT).show()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var showCaptureFlash by remember { mutableStateOf(false) }
    var captureTimerSeconds by remember { mutableIntStateOf(0) }
    var jpegQuality by remember { mutableIntStateOf(100) }
    var maximizeQuality by remember { mutableStateOf(true) }
    var aspectRatioWide by remember { mutableStateOf(false) }
    var timerCountdown by remember { mutableIntStateOf(0) }
    val cameraScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(isRecordingVideo) { if (isRecordingVideo) { recordingSeconds = 0; while (isRecordingVideo) { delay(1000); recordingSeconds += 1 } } else recordingSeconds = 0 }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var exposureMin by remember { mutableIntStateOf(0) }
    var exposureMax by remember { mutableIntStateOf(0) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var liveBrightness by remember { mutableFloatStateOf(0.015f) }
    var liveContrast by remember { mutableFloatStateOf(0.03f) }
    var liveSaturation by remember { mutableFloatStateOf(0.02f) }
    var liveWarmth by remember { mutableFloatStateOf(0.08f) }
    var liveTint by remember { mutableFloatStateOf(0.10f) }
    var liveDenoise by remember { mutableFloatStateOf(0.26f) }
    var liveSharpening by remember { mutableFloatStateOf(0.10f) }
    var liveClarity by remember { mutableFloatStateOf(0.05f) }
    var liveTexture by remember { mutableFloatStateOf(0.05f) }
    var selectedLiveLook by remember { mutableStateOf("LUMA AUTO") }
    var zoomRatio by remember { mutableStateOf(1f) }
    var minZoomRatio by remember { mutableStateOf(1f) }
    var maxZoomRatio by remember { mutableStateOf(1f) }
    var flashAvailable by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var gridEnabled by remember { mutableStateOf(false) }
    var histogramEnabled by remember { mutableStateOf(false) }
    var liveHistogram by remember { mutableStateOf(List(32) { 0f }) }
    var highlightClipping by remember { mutableFloatStateOf(0f) }
    var zebraEnabled by remember { mutableStateOf(false) }
    var peakingEnabled by remember { mutableStateOf(false) }
    var zebraPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var peakingPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var levelEnabled by remember { mutableStateOf(false) }
    var focusLockEnabled by remember { mutableStateOf(false) }
    var rollDegrees by remember { mutableFloatStateOf(0f) }
    var rawJpegSupported by remember { mutableStateOf(false) }
    var rawCaptureEnabled by remember { mutableStateOf(false) }
    var manualSensorSupported by remember { mutableStateOf(false) }
    var manualExposureEnabled by remember { mutableStateOf(false) }
    var isoMin by remember { mutableIntStateOf(100) }
    var isoMax by remember { mutableIntStateOf(3200) }
    var isoValue by remember { mutableIntStateOf(200) }
    var shutterMinNs by remember { mutableLongStateOf(125_000L) }
    var shutterMaxNs by remember { mutableLongStateOf(1_000_000_000L) }
    var shutterValueNs by remember { mutableLongStateOf(8_333_333L) }
    var manualFocusSupported by remember { mutableStateOf(false) }
    var focusDistanceMax by remember { mutableStateOf(0f) }
    var focusDistance by remember { mutableStateOf(0f) }
    var manualFocusEnabled by remember { mutableStateOf(false) }
    var supportedWbModes by remember { mutableStateOf(listOf(CaptureRequest.CONTROL_AWB_MODE_AUTO)) }
    var selectedWbMode by remember { mutableIntStateOf(CaptureRequest.CONTROL_AWB_MODE_AUTO) }

    val importConfigLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { JSONObject(it.readText()) }
                ?: error("Cannot read configuration")
            fun number(key: String, fallback: Float, min: Float, max: Float): Float =
                json.optDouble(key, fallback.toDouble()).toFloat().takeIf { it.isFinite() }?.coerceIn(min, max) ?: fallback
            liveBrightness = number("brightness", 0f, -1f, 1f)
            liveContrast = number("contrast", 0f, -1f, 1f)
            liveSaturation = number("saturation", 0f, -1f, 1f)
            liveWarmth = number("warmth", 0f, -1f, 1f)
            liveTint = number("tint", 0f, -1f, 1f)
            liveDenoise = number("denoise", 0f, 0f, 1f)
            liveSharpening = number("sharpening", 0f, 0f, 1f)
            liveClarity = number("clarity", 0f, 0f, 1f)
            liveTexture = number("texture", 0f, 0f, 1f)
            selectedLiveLook = json.optString("name", "IMPORTED").take(32)
            Toast.makeText(context, "Camera configuration imported", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Invalid Luma configuration", Toast.LENGTH_LONG).show()
        }
    }
    val exportConfigLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            val json = JSONObject().apply {
                put("format", "luma-camera-config")
                put("version", 1)
                put("name", selectedLiveLook)
                put("brightness", liveBrightness.toDouble())
                put("contrast", liveContrast.toDouble())
                put("saturation", liveSaturation.toDouble())
                put("warmth", liveWarmth.toDouble())
                put("tint", liveTint.toDouble())
                put("denoise", liveDenoise.toDouble())
                put("sharpening", liveSharpening.toDouble())
                put("clarity", liveClarity.toDouble())
                put("texture", liveTexture.toDouble())
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray(Charsets.UTF_8)) }
                ?: error("Cannot write configuration")
            Toast.makeText(context, "Configuration exported", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Could not export configuration", Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(levelEnabled) {
        if (!levelEnabled) {
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val measured = Math.toDegrees(kotlin.math.atan2(x.toDouble(), y.toDouble())).toFloat()
                    rollDegrees = (rollDegrees * 0.82f) + (measured * 0.18f)
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (accelerometer != null) {
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val performCapture: () -> Unit = {
        val capture = imageCapture
        if (capture != null && !isCapturing) {
            isCapturing = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            cameraScope.launch {
                timerCountdown = captureTimerSeconds
                while (timerCountdown > 0) {
                    delay(1000)
                    timerCountdown -= 1
                }
                capturePhoto(
                    context = context,
                    imageCapture = capture,
                    executor = ContextCompat.getMainExecutor(context),
                    saveRawAndJpeg = rawCaptureEnabled && rawJpegSupported,
                    onComplete = { success ->
                        isCapturing = false
                        if (success) {
                            showCaptureFlash = true
                            cameraScope.launch {
                                delay(110)
                                showCaptureFlash = false
                            }
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        Toast.makeText(
                            context,
                            if (success) "Photo saved to Pictures/Luma Pro Camera"
                            else "Capture failed. Try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }
    SideEffect {
        (context as? MainActivity)?.volumeCaptureAction =
            if (hasPermission) performCapture else null
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                lensFacing = lensFacing,
                liveBrightness = liveBrightness,
                liveContrast = liveContrast,
                liveSaturation = liveSaturation,
                liveWarmth = liveWarmth,
                liveTint = liveTint,
                liveDenoise = liveDenoise,
                liveSharpening = liveSharpening,
                liveClarity = liveClarity,
                liveTexture = liveTexture,
                jpegQuality = jpegQuality,
                maximizeQuality = maximizeQuality,
                rawCaptureEnabled = rawCaptureEnabled,
                aspectRatioWide = aspectRatioWide,
                analysisEnabled = histogramEnabled || zebraEnabled || peakingEnabled,
                onAnalysis = { bins, clipping, zebras, peaks -> liveHistogram = bins; highlightClipping = clipping; zebraPoints = zebras; peakingPoints = peaks },
                onCameraReady = { readyCamera, readyCapture, readyVideoCapture ->
                    camera = readyCamera
                    imageCapture = readyCapture
                    videoCapture = readyVideoCapture
                    manualExposureEnabled = false
                    manualSensorSupported = false
                    manualFocusEnabled = false
                    manualFocusSupported = false
                    if (readyCamera != null) {
                        flashAvailable = readyCamera.cameraInfo.hasFlashUnit()
                        flashEnabled = false
                        readyCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                        val range = readyCamera.cameraInfo.exposureState.exposureCompensationRange
                        exposureMin = range.lower
                        exposureMax = range.upper
                        exposureIndex = 0.coerceIn(range.lower, range.upper)
                        zoomRatio = 1f
                        val zoomState = readyCamera.cameraInfo.zoomState.value
                        minZoomRatio = zoomState?.minZoomRatio ?: 1f
                        maxZoomRatio = zoomState?.maxZoomRatio ?: 1f
                        runCatching {
                            val info = Camera2CameraInfo.from(readyCamera.cameraInfo)
                            val minFocus = info.getCameraCharacteristic(
                                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
                            ) ?: 0f
                            focusDistanceMax = minFocus
                            manualFocusSupported = minFocus > 0f
                            focusDistance = 0f
                            val afModes = info.getCameraCharacteristic(
                                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
                            )
                            manualFocusSupported = manualFocusSupported &&
                                afModes?.contains(CaptureRequest.CONTROL_AF_MODE_OFF) == true
                            val awbModes = info.getCameraCharacteristic(
                                CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
                            )
                            supportedWbModes = awbModes?.toList()
                                ?: listOf(CaptureRequest.CONTROL_AWB_MODE_AUTO)
                            if (!supportedWbModes.contains(CaptureRequest.CONTROL_AWB_MODE_AUTO)) {
                                supportedWbModes = listOf(CaptureRequest.CONTROL_AWB_MODE_AUTO) + supportedWbModes
                            }
                            selectedWbMode = CaptureRequest.CONTROL_AWB_MODE_AUTO
                            val capabilities = info.getCameraCharacteristic(
                                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
                            )
                            manualSensorSupported = capabilities?.contains(
                                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
                            ) == true
                            val isoRange = info.getCameraCharacteristic(
                                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
                            )
                            val shutterRange = info.getCameraCharacteristic(
                                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
                            )
                            if (isoRange != null && shutterRange != null && manualSensorSupported) {
                                isoMin = isoRange.lower
                                isoMax = isoRange.upper
                                isoValue = 200.coerceIn(isoMin, isoMax)
                                shutterMinNs = shutterRange.lower
                                shutterMaxNs = shutterRange.upper
                                shutterValueNs = 8_333_333L.coerceIn(shutterMinNs, shutterMaxNs)
                            } else {
                                manualSensorSupported = false
                            }
                        }.onFailure { manualSensorSupported = false }
                    }
                },
                onRawCapabilityChanged = { supported ->
                    rawJpegSupported = supported
                    if (!supported) rawCaptureEnabled = false                },
                onTapFocus = {
                    if (manualFocusEnabled) {
                        manualFocusEnabled = false
                        focusDistance = 0f
                        setAutoFocus(camera)
                    }
                }
            )
            if (gridEnabled) RuleOfThirdsGrid(Modifier.fillMaxSize())
            if (histogramEnabled) LiveHistogramOverlay(liveHistogram, highlightClipping, Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 190.dp))
            if (zebraEnabled) AnalysisPointOverlay(zebraPoints, Color(0xFFFFD740), Modifier.fillMaxSize())
            if (peakingEnabled) AnalysisPointOverlay(peakingPoints, Color(0xFF00E676), Modifier.fillMaxSize())
            if (levelEnabled) {
                Canvas(
                    modifier = Modifier.align(Alignment.Center).size(width = 150.dp, height = 36.dp)
                ) {
                    rotate(-rollDegrees, pivot = center) {
                        val lineY = center.y
                        drawLine(Color.White.copy(alpha = 0.9f), Offset(0f, lineY), Offset(size.width, lineY), 3.dp.toPx())
                        drawCircle(Color.White, radius = 5.dp.toPx(), center = center)
                        drawCircle(Color.Black, radius = 2.dp.toPx(), center = center)
                    }
                }
            }
            if (showCaptureFlash) {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.72f)))
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission is required.", color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera")
                }
            }
        }

        // Compact professional top bar
        if (hasPermission) Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color(0xB8000000)).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("LUMA", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("PRO CAMERA", color = Color(0xFFBDBDBD), style = MaterialTheme.typography.labelSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0x66333333), shape = RoundedCornerShape(18.dp)) {
                    Text(if (rawCaptureEnabled && rawJpegSupported) "RAW" else if (videoMode) "VIDEO" else "JPEG",
                        color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
                Surface(
                    modifier = Modifier.clickable { showGallery = true },
                    color = Color(0x88333333), shape = RoundedCornerShape(18.dp)
                ) { Text("GALLERY", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.labelSmall) }
                Surface(
                    modifier = Modifier.clickable { showSettings = true },
                    color = Color(0x88333333), shape = CircleShape
                ) { Text("⚙", color = Color.White, modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium) }
            }
        }

        // Small quick-access strip, kept away from the viewfinder center
        if (hasPermission) Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 66.dp)
                .background(Color(0x66000000), RoundedCornerShape(22.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickCameraChip(if (flashEnabled) "FLASH ON" else "FLASH", flashAvailable) {
                flashEnabled = !flashEnabled
                imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            }
            QuickCameraChip(if (aspectRatioWide) "16:9" else "4:3", !isCapturing) { if (!isCapturing) aspectRatioWide = !aspectRatioWide }
            QuickCameraChip(if (captureTimerSeconds == 0) "TIMER" else "${captureTimerSeconds}s", !isCapturing) {
                captureTimerSeconds = when (captureTimerSeconds) { 0 -> 3; 3 -> 5; 5 -> 10; else -> 0 }
            }
        }

        // Minimal stock-camera shooting deck; tuning is available only in Settings.
        if (hasPermission) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color(0xE6101010), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isRecordingVideo) "● REC ${recordingSeconds}s" else if (videoMode) "VIDEO" else "PHOTO", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickCameraChip(if (flashEnabled) "FLASH ON" else "FLASH", flashAvailable) {
                            flashEnabled = !flashEnabled
                            imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        }
                        QuickCameraChip(if (aspectRatioWide) "16:9" else "4:3", !isCapturing) { if (!isCapturing) aspectRatioWide = !aspectRatioWide }
                        QuickCameraChip(if (captureTimerSeconds == 0) "TIMER" else "${captureTimerSeconds}s", !isCapturing) {
                            captureTimerSeconds = when (captureTimerSeconds) { 0 -> 3; 3 -> 5; 5 -> 10; else -> 0 }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    listOf(0.5f, 1f, 2f).filter { it >= minZoomRatio && it <= maxZoomRatio }.forEach { preset ->
                        QuickCameraChip(if (preset == 0.5f) "0.5×" else "${preset.toInt()}×", true, kotlin.math.abs(zoomRatio - preset) < 0.08f) {
                            camera?.cameraControl?.setZoomRatio(preset); zoomRatio = preset
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.clickable(enabled = !isRecordingVideo) { if (!isRecordingVideo) lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }, color = Color(0x55333333), shape = CircleShape) {
                        Text("↻", color = Color.White, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(modifier = Modifier.clickable(enabled = !isRecordingVideo) { videoMode = false }, color = if (!videoMode) Color.White else Color.Transparent, shape = RoundedCornerShape(14.dp)) {
                                Text("PHOTO", color = if (!videoMode) Color.Black else Color.LightGray, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                            }
                            Surface(modifier = Modifier.clickable(enabled = !isRecordingVideo) { videoMode = true }, color = if (videoMode) Color.White else Color.Transparent, shape = RoundedCornerShape(14.dp)) {
                                Text("VIDEO", color = if (videoMode) Color.Black else Color.LightGray, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            modifier = Modifier.size(76.dp), enabled = if (videoMode) videoCapture != null else imageCapture != null && !isCapturing && !isRecordingVideo,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRecordingVideo) Color(0xFFE53935) else Color.White, contentColor = Color.Black, disabledContainerColor = Color.Gray),
                            onClick = {
                                if (!videoMode) performCapture()
                                else if (isRecordingVideo) { activeRecording?.stop(); activeRecording = null }
                                else {
                                    val useCase = videoCapture ?: return@Button
                                    val name = "Luma_Video_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
                                    val values = ContentValues().apply {
                                        put(MediaStore.Video.Media.DISPLAY_NAME, name)
                                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Luma Pro Camera")
                                    }
                                    val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
                                    if (!hasAudioPermission && !audioPermissionAsked) audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    else runCatching {
                                        val pending = useCase.output.prepareRecording(context, output)
                                        val configured = if (hasAudioPermission) pending.withAudioEnabled() else pending
                                        activeRecording = configured.start(ContextCompat.getMainExecutor(context)) { event ->
                                            when (event) {
                                                is VideoRecordEvent.Start -> isRecordingVideo = true
                                                is VideoRecordEvent.Finalize -> {
                                                    isRecordingVideo = false; activeRecording = null
                                                    Toast.makeText(context, if (!event.hasError()) "Video saved to Movies/Luma Pro Camera" else "Video recording failed", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }.onFailure { error ->
                                        isRecordingVideo = false
                                        Toast.makeText(context, "Video start failed: " + (error.localizedMessage ?: error.javaClass.simpleName), Toast.LENGTH_LONG).show()
                                        android.util.Log.e("LumaProCamera", "Unable to start video recording", error)
                                    }
                                }
                            }
                        ) { Text(if (videoMode) if (isRecordingVideo) "■" else "●" else if (timerCountdown > 0) timerCountdown.toString() else if (isCapturing) "…" else "●", style = MaterialTheme.typography.headlineMedium) }
                    }
                    Surface(modifier = Modifier.clickable { showGallery = true }, color = Color(0x55333333), shape = CircleShape) {
                        Text("▣", color = Color.White, modifier = Modifier.padding(13.dp), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f)).padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("LUMA CAMERA", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text("Camera access is needed to show the viewfinder and take photos or videos.", color = Color.LightGray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("ALLOW CAMERA") }
            }
        }
        if (showSettings) {
            Dialog(onDismissRequest = { showSettings = false }) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp)
                        .background(Color(0xFF151515), RoundedCornerShape(24.dp))
                        .padding(16.dp).verticalScroll(rememberScrollState())
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Camera Settings", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        QuickCameraChip("DONE", true) { showSettings = false }
                    }
                    Text("Standard mode keeps all image adjustments neutral.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("IMAGE", "PRO", "TOOLS").forEachIndexed { index, label ->
                            val active = cameraPanelTab == index
                            Surface(Modifier.weight(1f).clickable { cameraPanelTab = index }, color = if (active) Color(0xFFFFD54F) else Color(0x55333333), shape = RoundedCornerShape(10.dp)) {
                                Text(label, color = if (active) Color.Black else Color.White, modifier = Modifier.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    when (cameraPanelTab) {
                        0 -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("IMAGE ADJUSTMENTS", color = Color.White)
                                QuickCameraChip("RESET", true) {
                                    liveBrightness = 0f; liveContrast = 0f; liveSaturation = 0f
                                    liveWarmth = 0f; liveTint = 0f; liveDenoise = 0f
                                    liveSharpening = 0f; liveClarity = 0f; liveTexture = 0f
                                    selectedLiveLook = "STANDARD"
                                }
                            }
                            Text("Brightness ${(liveBrightness * 100).roundToInt()}%", color = Color.White)
                            Slider(value = liveBrightness, onValueChange = { liveBrightness = it }, valueRange = -1f..1f)
                            Text("Contrast ${(liveContrast * 100).roundToInt()}%", color = Color.White)
                            Slider(value = liveContrast, onValueChange = { liveContrast = it }, valueRange = -1f..1f)
                            Text("Saturation ${(liveSaturation * 100).roundToInt()}%", color = Color.White)
                            Slider(value = liveSaturation, onValueChange = { liveSaturation = it }, valueRange = -1f..1f)
                            Text("Warmth ${(liveWarmth * 100).roundToInt()}%", color = Color.White)
                            Slider(value = liveWarmth, onValueChange = { liveWarmth = it }, valueRange = -1f..1f)
                            Text("Tint ${(liveTint * 100).roundToInt()}%", color = Color.White)
                            Slider(value = liveTint, onValueChange = { liveTint = it }, valueRange = -1f..1f)
                            Text("Denoise ${(liveDenoise * 100).roundToInt()}%", color = Color.White)
                            Slider(value = liveDenoise, onValueChange = { liveDenoise = it }, valueRange = 0f..1f)
                            Text("Sharpening ${(liveSharpening * 100).roundToInt()}%", color = Color.White)
                            Slider(value = liveSharpening, onValueChange = { liveSharpening = it }, valueRange = 0f..1f)
                            Text("Clarity ${(liveClarity * 100).roundToInt()}%", color = Color.White)
                            Slider(value = liveClarity, onValueChange = { liveClarity = it }, valueRange = 0f..1f)
                            Text("Texture ${(liveTexture * 100).roundToInt()}%", color = Color.White)
                            Slider(value = liveTexture, onValueChange = { liveTexture = it }, valueRange = 0f..1f)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { importConfigLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }, modifier = Modifier.weight(1f)) { Text("IMPORT CONFIG") }
                                Button(onClick = { exportConfigLauncher.launch("LumaCamera-${selectedLiveLook.replace(' ', '-')}.json") }, modifier = Modifier.weight(1f)) { Text("EXPORT CONFIG") }
                            }
                            Text("RAW capture remains unprocessed. Standard keeps all these values at zero.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                        1 -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("EXPOSURE COMPENSATION", color = Color.White)
                                Text("EV $exposureIndex", color = Color(0xFFFFD54F))
                            }
                            if (exposureMax > exposureMin) Slider(value = exposureIndex.toFloat().coerceIn(exposureMin.toFloat(), exposureMax.toFloat()), onValueChange = { val v = it.roundToInt().coerceIn(exposureMin, exposureMax); exposureIndex = v; camera?.cameraControl?.setExposureCompensationIndex(v) }, valueRange = exposureMin.toFloat()..exposureMax.toFloat(), steps = (exposureMax - exposureMin - 1).coerceAtLeast(0))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("WHITE BALANCE", color = Color.White)
                                QuickCameraChip(whiteBalanceName(selectedWbMode), camera != null) {
                                    val modes = supportedWbModes
                                    if (modes.isNotEmpty()) { val current = modes.indexOf(selectedWbMode).coerceAtLeast(0); selectedWbMode = modes[(current + 1) % modes.size]; setWhiteBalance(camera, selectedWbMode) }
                                }
                            }
                            QuickCameraChip(if (maximizeQuality) "MAXIMUM QUALITY" else "LOW LATENCY", true) { maximizeQuality = !maximizeQuality }
                            Text("JPEG QUALITY $jpegQuality%", color = Color.White)
                            Slider(value = jpegQuality.toFloat(), onValueChange = { jpegQuality = it.roundToInt().coerceIn(70, 100) }, valueRange = 70f..100f, steps = 29)
                            if (rawJpegSupported) QuickCameraChip(if (rawCaptureEnabled) "RAW + JPEG ON" else "RAW + JPEG OFF", true) { rawCaptureEnabled = !rawCaptureEnabled }
                            if (manualSensorSupported) {
                                QuickCameraChip(if (manualExposureEnabled) "MANUAL EXPOSURE ON" else "MANUAL EXPOSURE OFF", true) {
                                    manualExposureEnabled = !manualExposureEnabled
                                    setManualExposure(camera, manualExposureEnabled, isoValue, shutterValueNs)
                                }
                                Text("ISO $isoValue", color = Color.White)
                                Slider(value = isoValue.toFloat(), onValueChange = { isoValue = it.roundToInt().coerceIn(isoMin, isoMax); if (manualExposureEnabled) setManualExposure(camera, true, isoValue, shutterValueNs) }, valueRange = isoMin.toFloat()..isoMax.toFloat(), enabled = manualExposureEnabled && isoMax > isoMin)
                            }
                        }
                        else -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                QuickCameraChip(if (gridEnabled) "GRID ON" else "GRID", true) { gridEnabled = !gridEnabled }
                                QuickCameraChip(if (histogramEnabled) "HISTOGRAM ON" else "HISTOGRAM", true) { histogramEnabled = !histogramEnabled }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                QuickCameraChip(if (zebraEnabled) "ZEBRA ON" else "ZEBRA", true) { zebraEnabled = !zebraEnabled }
                                QuickCameraChip(if (peakingEnabled) "FOCUS PEAKING ON" else "FOCUS PEAKING", true) { peakingEnabled = !peakingEnabled }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                QuickCameraChip(if (levelEnabled) "LEVEL ON" else "LEVEL", true) { levelEnabled = !levelEnabled }
                                QuickCameraChip(if (focusLockEnabled) "AE/AF LOCK" else "AE/AF AUTO", camera != null) { focusLockEnabled = !focusLockEnabled; setFocusLock(camera, focusLockEnabled) }
                            }
                            QuickCameraChip(if (aspectRatioWide) "16:9" else "4:3", !isCapturing) { if (!isCapturing) aspectRatioWide = !aspectRatioWide }
                        }
                    }
                }
            }
        }
        if (showGallery) {
            Dialog(onDismissRequest = { showGallery = false }) {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF101010)).padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Luma Gallery", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Button(onClick = { showGallery = false }) { Text("Close") }
                    }
                    Text("${galleryPhotos.size} photos • ${selectedPhotoUris.size} selected", color = Color.LightGray)
                    if (selectedPhotoUris.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val uris = galleryPhotos.filter { selectedPhotoUris.contains(it.uri.toString()) }.map { it.uri }
                                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "image/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share photos"))
                            }) { Text("Share ${selectedPhotoUris.size}") }
                            Button(onClick = { selectedPhotoUris = emptySet(); selectedPhoto = null }) { Text("Clear") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (selectedPhoto != null) {
                    Text("Selected: ${selectedPhoto?.name}", color = Color.White, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val photo = selectedPhoto ?: return@Button
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(photo.uri, "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }
                        }) { Text("Open") }
                        Button(onClick = {
                            val photo = selectedPhoto ?: return@Button
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, photo.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share photo"))
                        }) { Text("Share") }
                        Button(onClick = { showDeleteConfirmation = true }) { Text("Delete") }
                    }
                }
                if (galleryPhotos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No Luma photos yet", color = Color.White)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(galleryPhotos, key = { it.uri.toString() }) { photo ->
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = photo.name,
                                    modifier = Modifier.fillMaxWidth().height(116.dp).clickable {
                                        val key = photo.uri.toString()
                                        selectedPhotoUris = if (selectedPhotoUris.contains(key)) selectedPhotoUris - key else selectedPhotoUris + key
                                        selectedPhoto = if (selectedPhoto?.uri == photo.uri) null else photo
                                    }.background(if (selectedPhotoUris.contains(photo.uri.toString())) Color(0xFF526BFF) else Color.Transparent),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
            if (showDeleteConfirmation) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = false },
                    title = { Text("Delete photo?") },
                    text = { Text("This photo will be removed from your Luma gallery.") },
                    confirmButton = {
                        Button(onClick = {
                            val photo = selectedPhoto
                            showDeleteConfirmation = false
                            if (photo != null) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    runCatching {
                                        val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(photo.uri))
                                        deletePhotoLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                                    }.onFailure {
                                        Toast.makeText(context, "Unable to request deletion", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val deleted = runCatching { context.contentResolver.delete(photo.uri, null, null) > 0 }.getOrDefault(false)
                                    if (deleted) {
                                        selectedPhoto = null
                                        showGallery = false
                                        showGallery = true
                                        Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Unable to delete photo", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }) { Text("Delete") }
                    },
                    dismissButton = {
                        Button(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}



@Composable
private fun QuickCameraChip(label: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    Surface(        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        color = if (selected) Color(0xFFFFD54F) else if (enabled) Color(0x66383838) else Color(0x33222222),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(label, color = if (selected) Color.Black else if (enabled) Color.White else Color(0xFF777777),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AnalysisPointOverlay(points: List<Offset>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        points.forEach { point -> drawCircle(color.copy(alpha = 0.82f), 1.8.dp.toPx(), Offset(point.x * size.width, point.y * size.height)) }
    }
}


@Composable
private fun LiveHistogramOverlay(
    bins: List<Float>,
    clipping: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Canvas(modifier = Modifier.size(width = 150.dp, height = 62.dp)) {
            val barWidth = size.width / bins.size
            bins.forEachIndexed { index, value ->
                val barHeight = value.coerceIn(0f, 1f) * size.height
                drawRect(
                    color = Color.White.copy(alpha = 0.88f),
                    topLeft = Offset(index * barWidth, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size((barWidth - 1f).coerceAtLeast(1f), barHeight)
                )
            }
        }
        Text(
            text = if (clipping >= 0.02f) "HIGHLIGHTS CLIPPING " + (clipping * 100).roundToInt() + "%"
                   else "LIVE HISTOGRAM",
            color = if (clipping >= 0.02f) Color(0xFFFFB74D) else Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}


@Composable
private fun RuleOfThirdsGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val lineColor = Color.White.copy(alpha = 0.48f)
        val stroke = 1.dp.toPx()
        val verticalOne = size.width / 3f
        val verticalTwo = size.width * 2f / 3f
        val horizontalOne = size.height / 3f
        val horizontalTwo = size.height * 2f / 3f
        drawLine(lineColor, Offset(verticalOne, 0f), Offset(verticalOne, size.height), stroke)
        drawLine(lineColor, Offset(verticalTwo, 0f), Offset(verticalTwo, size.height), stroke)
        drawLine(lineColor, Offset(0f, horizontalOne), Offset(size.width, horizontalOne), stroke)
        drawLine(lineColor, Offset(0f, horizontalTwo), Offset(size.width, horizontalTwo), stroke)
    }
}



private fun setManualFocus(camera: Camera?, distance: Float) {
    val activeCamera = camera ?: return
    runCatching {
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, distance)
            .build()
        Camera2CameraControl.from(activeCamera.cameraControl).setCaptureRequestOptions(options)
    }
}

private fun setAutoFocus(camera: Camera?) {
    val activeCamera = camera ?: return
    runCatching {
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            .build()
        Camera2CameraControl.from(activeCamera.cameraControl).setCaptureRequestOptions(options)
    }
}

private fun setFocusLock(camera: Camera?, locked: Boolean) {
    val activeCamera = camera ?: return
    runCatching {
        val builder = CaptureRequestOptions.Builder()
        builder.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_LOCK,
            locked
        )
        builder.setCaptureRequestOption(
            CaptureRequest.CONTROL_AWB_LOCK,
            locked
        )
        builder.setCaptureRequestOption(
            CaptureRequest.CONTROL_AF_MODE,
            if (locked) CaptureRequest.CONTROL_AF_MODE_AUTO
            else CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )
        if (locked) {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START
            )
        } else {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_CANCEL
            )
        }
        Camera2CameraControl.from(activeCamera.cameraControl)
            .setCaptureRequestOptions(builder.build())
    }
}

private fun setWhiteBalance(camera: Camera?, mode: Int) {
    val activeCamera = camera ?: return
    runCatching {
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, mode)
            .build()
        Camera2CameraControl.from(activeCamera.cameraControl).setCaptureRequestOptions(options)
    }
}

private fun whiteBalanceName(mode: Int): String = when (mode) {
    CaptureRequest.CONTROL_AWB_MODE_AUTO -> "Auto"
    CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT -> "Incandescent"
    CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT -> "Fluorescent"
    CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "Warm"
    CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT -> "Daylight"
    CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "Cloudy"
    CaptureRequest.CONTROL_AWB_MODE_TWILIGHT -> "Twilight"
    CaptureRequest.CONTROL_AWB_MODE_SHADE -> "Shade"
    else -> "WB $mode"
}

private fun shutterToFraction(value: Long, min: Long, max: Long): Float {
    if (max <= min || min <= 0L) return 0f
    return ((ln(value.coerceIn(min, max).toDouble()) - ln(min.toDouble())) /
        (ln(max.toDouble()) - ln(min.toDouble()))).toFloat().coerceIn(0f, 1f)
}

private fun fractionToShutter(fraction: Float, min: Long, max: Long): Long {
    if (max <= min || min <= 0L) return min
    val logValue = ln(min.toDouble()) +
        (ln(max.toDouble()) - ln(min.toDouble())) * fraction.coerceIn(0f, 1f)
    return exp(logValue).toLong().coerceIn(min, max)
}

private fun setManualExposure(camera: Camera?, enabled: Boolean, iso: Int, shutterNs: Long) {
    val activeCamera = camera ?: return
    runCatching {
        val builder = CaptureRequestOptions.Builder()
        if (enabled) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterNs)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }
        Camera2CameraControl.from(activeCamera.cameraControl).setCaptureRequestOptions(builder.build())
    }
}

private fun capturePhoto(
    context: android.content.Context,
    imageCapture: ImageCapture,
    executor: Executor,
    saveRawAndJpeg: Boolean,
    onComplete: (Boolean) -> Unit
) {
    val fileName = "Luma_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Luma Pro Camera")
        }
    }
    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    ).build()

    try {
        if (saveRawAndJpeg && imageCapture.outputFormat == ImageCapture.OUTPUT_FORMAT_RAW_JPEG) {
            val rawValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName.removeSuffix(".jpg") + ".dng")
                put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Luma Pro Camera")
                }
            }
            val rawOptions = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                rawValues
            ).build()
            val completed = java.util.concurrent.atomic.AtomicInteger(0)
            val failed = java.util.concurrent.atomic.AtomicBoolean(false)
            imageCapture.takePicture(
                rawOptions,
                outputOptions,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (completed.incrementAndGet() == 2 && !failed.get()) onComplete(true)
                    }
    
                    override fun onError(exception: ImageCaptureException) {
                        if (failed.compareAndSet(false, true)) onComplete(false)
                    }
                }
            )
        } else {
            imageCapture.takePicture(
                outputOptions,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        onComplete(true)
                    }
    
                    override fun onError(exception: ImageCaptureException) {
                        onComplete(false)
                    }
                }
            )
        }
    } catch (exception: Exception) {
        onComplete(false)
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun CameraPreview(
    modifier: Modifier = Modifier,
    lensFacing: Int,
    liveBrightness: Float,
    liveContrast: Float,
    liveSaturation: Float,
    liveWarmth: Float,
    liveTint: Float,
    liveDenoise: Float,
    liveSharpening: Float,
    liveClarity: Float,
    liveTexture: Float,
    jpegQuality: Int,
    maximizeQuality: Boolean,
    rawCaptureEnabled: Boolean,
    aspectRatioWide: Boolean,
    analysisEnabled: Boolean,
    onAnalysis: (List<Float>, Float, List<Offset>, List<Offset>) -> Unit,
    onCameraReady: (Camera?, ImageCapture?, VideoCapture<Recorder>?) -> Unit,
    onTapFocus: () -> Unit,
    onRawCapabilityChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var activeMedia3Effect by remember { mutableStateOf<Media3Effect?>(null) }
    SideEffect {
        activeMedia3Effect?.setEffects(
            listOf(Brightness(liveBrightness.coerceIn(-1f, 1f)), Contrast(liveContrast.coerceIn(-1f, 1f)), HslAdjustment.Builder().adjustSaturation((liveSaturation * 100f).coerceIn(-100f, 100f)).build(), RgbAdjustment.Builder().setRedScale((1f + liveWarmth * 0.16f + liveTint * 0.07f).coerceIn(0.75f, 1.25f)).setGreenScale((1f - liveTint * 0.12f).coerceIn(0.75f, 1.25f)).setBlueScale((1f - liveWarmth * 0.16f + liveTint * 0.07f).coerceIn(0.75f, 1.25f)).build(), LiveDetailEffect(liveDenoise, liveSharpening, liveClarity, liveTexture))
        )
    }
    AndroidView(
        modifier = modifier.pointerInput(previewView, lensFacing) {
            detectTapGestures { offset ->
                val activeCamera = boundCamera
                if (activeCamera != null && previewView.width > 0 && previewView.height > 0) {
                    onTapFocus()
                    runCatching {
                        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point)
                            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        activeCamera.cameraControl.startFocusAndMetering(action)
                    }
                }
            }
        },
        factory = { previewView }
    )

    DisposableEffect(previewView, lifecycleOwner, lensFacing, jpegQuality, maximizeQuality, rawCaptureEnabled, aspectRatioWide, analysisEnabled) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val effectExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val media3Effect = runCatching {
            Media3Effect(
                context,
                CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE or
                    if (rawCaptureEnabled) 0 else CameraEffect.IMAGE_CAPTURE,
                effectExecutor
            ) { error ->
                android.util.Log.e("LumaProCamera", "Live GPU effect failed", error)
            }.also { it.setEffects(listOf(Brightness(liveBrightness), Contrast(liveContrast), HslAdjustment.Builder().adjustSaturation((liveSaturation * 100f).coerceIn(-100f, 100f)).build(), RgbAdjustment.Builder().setRedScale((1f + liveWarmth * 0.16f + liveTint * 0.07f).coerceIn(0.75f, 1.25f)).setGreenScale((1f - liveTint * 0.12f).coerceIn(0.75f, 1.25f)).setBlueScale((1f - liveWarmth * 0.16f + liveTint * 0.07f).coerceIn(0.75f, 1.25f)).build(), LiveDetailEffect(liveDenoise, liveSharpening, liveClarity, liveTexture))) }
        }.getOrNull()
        activeMedia3Effect = media3Effect
        val lastAnalysisUpdate = java.util.concurrent.atomic.AtomicLong(0L)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val supportedFormats = runCatching {
                    ImageCapture.getImageCaptureCapabilities(cameraProvider.getCameraInfo(selector))
                        .supportedOutputFormats
                }.getOrDefault(setOf(ImageCapture.OUTPUT_FORMAT_JPEG))
                val supportsRawJpeg = supportedFormats.contains(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(
                        if (aspectRatioWide) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                        else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                    )
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                    .build()
                val captureBuilder = ImageCapture.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setCaptureMode(if (maximizeQuality) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(jpegQuality)
                if (supportsRawJpeg && rawCaptureEnabled) {
                    captureBuilder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
                }
                val capture = captureBuilder.build()
                val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.UHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))).build()
                val video = VideoCapture.withOutput(recorder)

                cameraProvider.unbindAll()
                if (analysisEnabled) {
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(320, 240))
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { image ->
                        try {
                            val plane = image.planes.firstOrNull()
                            if (plane != null) {
                                val buffer = plane.buffer
                                val data = ByteArray(buffer.remaining())
                                buffer.get(data)
                                val bins = FloatArray(32)
                                var sampled = 0
                                var clipped = 0
                                val zebraMarks = mutableListOf<Offset>()
                                val focusMarks = mutableListOf<Offset>()
                                val rowStride = plane.rowStride
                                val pixelStride = plane.pixelStride
                                var y = 0
                                while (y < image.height) {
                                    var x = 0
                                    while (x < image.width) {
                                        val offset = y * rowStride + x * pixelStride
                                        if (offset < data.size) {
                                            val value = data[offset].toInt() and 0xFF
                                            bins[(value * 32 / 256).coerceIn(0, 31)] += 1f
                                            sampled++
                                            if (value >= 250) { clipped++; if (zebraMarks.size < 1200) zebraMarks.add(Offset(x.toFloat() / image.width, y.toFloat() / image.height)) }
                                            if (x >= 4 && y >= 4 && focusMarks.size < 1800) {
                                                val left = data[y * rowStride + (x - 4) * pixelStride].toInt() and 0xFF
                                                val above = data[(y - 4) * rowStride + x * pixelStride].toInt() and 0xFF
                                                if (kotlin.math.abs(value - left) + kotlin.math.abs(value - above) >= 90) focusMarks.add(Offset(x.toFloat() / image.width, y.toFloat() / image.height))
                                            }
                                        }
                                        x += 4
                                    }
                                    y += 4
                                }
                                val peak = (bins.maxOrNull() ?: 1f).coerceAtLeast(1f)
                                val normalized = bins.map { it / peak }
                                val clipping = if (sampled > 0) clipped.toFloat() / sampled else 0f
                                val now = android.os.SystemClock.elapsedRealtime()
                                val previous = lastAnalysisUpdate.get()
                                if (now - previous >= 180L && lastAnalysisUpdate.compareAndSet(previous, now)) {
                                    val rotation = image.imageInfo.rotationDegrees
                                    fun rotateMarks(marks: List<Offset>): List<Offset> = marks.map { p -> when (rotation) { 90 -> Offset(1f - p.y, p.x); 180 -> Offset(1f - p.x, 1f - p.y); 270 -> Offset(p.y, 1f - p.x); else -> p } }
                                    val rotatedZebras = rotateMarks(zebraMarks)
                                    val rotatedPeaks = rotateMarks(focusMarks)
                                    ContextCompat.getMainExecutor(context).execute { onAnalysis(normalized, clipping, rotatedZebras, rotatedPeaks) }
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            image.close()
                        }
                    }
                    try {
                        val groupBuilder = UseCaseGroup.Builder()
                            .addUseCase(preview).addUseCase(capture).addUseCase(video).addUseCase(analysis)
                        if (media3Effect != null) groupBuilder.addEffect(media3Effect)
                        boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, groupBuilder.build())
                    } catch (_: Exception) {
                        analysis.clearAnalyzer()
                        analysisExecutor.shutdown()
                        val fallbackBuilder = UseCaseGroup.Builder().addUseCase(preview).addUseCase(capture).addUseCase(video)
                        boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, fallbackBuilder.build())
                    }
                } else {
                    try {
                        val groupBuilder = UseCaseGroup.Builder().addUseCase(preview).addUseCase(capture).addUseCase(video)
                        if (media3Effect != null) groupBuilder.addEffect(media3Effect)
                        boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, groupBuilder.build())
                    } catch (_: Exception) {
                        boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture, video)
                    }
                    onAnalysis(List(32) { 0f }, 0f, emptyList(), emptyList())
                }
                capture.resolutionInfo?.resolution?.let { selectedSize ->
                    android.util.Log.i("LumaProCamera", "Selected still-photo resolution: ${selectedSize.width}x${selectedSize.height}")
                }
                onCameraReady(boundCamera, capture, video)
                onRawCapabilityChanged(supportsRawJpeg)
            } catch (_: Exception) {
                onCameraReady(null, null, null)
            }
        }, executor)

        onDispose {
            boundCamera = null
            onCameraReady(null, null, null)
            onRawCapabilityChanged(false)
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
            analysisExecutor.shutdown()
            activeMedia3Effect = null
            runCatching { media3Effect?.close() }
            effectExecutor.shutdown()
        }
    }
}