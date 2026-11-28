package com.lumapro.camera

import android.Manifest
import android.hardware.camera2.CameraCharacteristics
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
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
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.media.ExifInterface
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.homesoft.photo.libraw.LibRaw
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
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.sp
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
    var focusedAdjustment by remember { mutableStateOf<String?>(null) }
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
    var nightMode by remember { mutableStateOf(false) }
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
    var fullResolutionEnabled by remember { mutableStateOf(false) }
    var standardPhotoModeLabel by remember { mutableStateOf<String?>(null) }
    var fullResolutionModeLabel by remember { mutableStateOf<String?>(null) }
    var hardwareDiagnosticReport by remember { mutableStateOf("Reading camera hardware…") }
    var timerCountdown by remember { mutableIntStateOf(0) }
    val cameraScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(isRecordingVideo) { if (isRecordingVideo) { recordingSeconds = 0; while (isRecordingVideo) { delay(1000); recordingSeconds += 1 } } else recordingSeconds = 0 }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var exposureMin by remember { mutableIntStateOf(0) }
    var exposureMax by remember { mutableIntStateOf(0) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var liveBrightness by remember { mutableFloatStateOf(0.08f) }
    var liveContrast by remember { mutableFloatStateOf(0.15f) }
    var liveSaturation by remember { mutableFloatStateOf(0.05f) }
    var liveWarmth by remember { mutableFloatStateOf(0.03f) }
    var liveTint by remember { mutableFloatStateOf(0.05f) }
    var liveDenoise by remember { mutableFloatStateOf(0.25f) }
    var liveSharpening by remember { mutableFloatStateOf(0.40f) }
    var liveClarity by remember { mutableFloatStateOf(0.12f) }
    var liveTexture by remember { mutableFloatStateOf(0.20f) }
    var liveHighlights by remember { mutableFloatStateOf(0.75f) }
    var liveShadows by remember { mutableFloatStateOf(0.55f) }
    var hdrMode by remember { mutableIntStateOf(0) } // 0=off, 1=HDR, 2=HDR+
    val hdrStrength = when (hdrMode) { 1 -> 0.30f; 2 -> 0.55f; else -> 0f }
    var selectedLiveLook by remember { mutableStateOf("STANDARD") }
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
    var rawDeveloping by remember { mutableStateOf(false) }
    var cameraRestartToken by remember { mutableIntStateOf(0) }
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

    val rawDevelopLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !rawDeveloping) {
            rawDeveloping = true
            cameraScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { developDngToJpeg(context, uri, if (liveDenoise <= 0.001f) 0.65f else liveDenoise, liveBrightness, liveContrast, liveSaturation, liveWarmth, liveTint, liveSharpening, liveClarity, liveTexture, liveHighlights, liveShadows) }
                }
                rawDeveloping = false
                // The system document picker backgrounds the Activity. Always recreate the
                // CameraX session, including when RAW decoding fails.
                cameraRestartToken++
                result.onSuccess {
                    Toast.makeText(context, "RAW developed: Pictures/Luma Pro Camera", Toast.LENGTH_LONG).show()
                }.onFailure {
                    android.util.Log.e("LumaProCamera", "RAW development failed", it)
                    Toast.makeText(context, "RAW development failed: ${it.message ?: it.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
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
            liveHighlights = number("highlights", liveHighlights, 0f, 1f)
            liveShadows = number("shadows", liveShadows, 0f, 1f)
            hdrMode = json.optInt("hdrMode", hdrMode).coerceIn(0, 2)
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
                put("highlights", liveHighlights.toDouble())
                put("shadows", liveShadows.toDouble())
                put("hdrMode", hdrMode)
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
                val complete: (Boolean) -> Unit = { success ->
                    isCapturing = false
                    if (success) {
                        showCaptureFlash = true
                        cameraScope.launch { delay(110); showCaptureFlash = false }
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    Toast.makeText(context,
                        if (success) if (nightMode) "Night photo saved to Pictures/Luma Pro Camera" else "Photo saved to Pictures/Luma Pro Camera"
                        else "Capture failed. Try again.", Toast.LENGTH_SHORT).show()
                }
                if (nightMode && !videoMode) captureNightPhoto(context, capture, ContextCompat.getMainExecutor(context), complete)
                else capturePhoto(
                    context, capture, ContextCompat.getMainExecutor(context),
                    rawCaptureEnabled && rawJpegSupported,
                    PhotoProcessingSettings(
                        brightness = liveBrightness, contrast = liveContrast,
                        saturation = liveSaturation, warmth = liveWarmth, tint = liveTint,
                        denoise = liveDenoise, sharpening = liveSharpening,
                        clarity = liveClarity, texture = liveTexture,
                        highlights = liveHighlights, shadows = liveShadows
                    ),
                    complete
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
                videoMode = videoMode,
                cameraRestartToken = cameraRestartToken,
                liveBrightness = liveBrightness,
                liveContrast = liveContrast,
                liveSaturation = liveSaturation,
                liveWarmth = liveWarmth,
                liveTint = liveTint,
                liveDenoise = liveDenoise,
                liveSharpening = liveSharpening,
                liveClarity = liveClarity,
                liveTexture = liveTexture,
                liveHighlights = liveHighlights,
                liveShadows = liveShadows,
                hdrStrength = hdrStrength,
                liveProcessingEnabled = (kotlin.math.abs(liveBrightness) > 0.001f || kotlin.math.abs(liveContrast) > 0.001f || kotlin.math.abs(liveSaturation) > 0.001f || kotlin.math.abs(liveWarmth) > 0.001f || kotlin.math.abs(liveTint) > 0.001f || liveDenoise > 0.001f || liveSharpening > 0.001f || liveClarity > 0.001f || liveTexture > 0.001f || liveHighlights > 0.001f || liveShadows > 0.001f || hdrStrength > 0.001f),
                jpegQuality = jpegQuality,
                maximizeQuality = maximizeQuality,
                rawCaptureEnabled = rawCaptureEnabled,
                aspectRatioWide = aspectRatioWide,
                fullResolutionEnabled = fullResolutionEnabled,
                analysisEnabled = histogramEnabled || zebraEnabled || peakingEnabled,
                onAnalysis = { bins, clipping, zebras, peaks -> liveHistogram = bins; highlightClipping = clipping; zebraPoints = zebras; peakingPoints = peaks },
                onHardwareInfoChanged = { report -> hardwareDiagnosticReport = report },
                onPhotoModesChanged = { standardLabel, fullLabel ->
                    standardPhotoModeLabel = standardLabel
                    fullResolutionModeLabel = fullLabel
                    if (fullLabel == null) fullResolutionEnabled = false
                },
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
                        // Start each camera session from neutral stock-style automatic controls.
                        // This explicitly clears any stale manual WB/AE locks or exposure bias.
                        setStockAutoCameraDefaults(readyCamera, range.lower, range.upper)
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
                    // Prefer the higher-bit-depth RAW source by default when the
                    // active camera explicitly reports simultaneous RAW+JPEG support.
                    // Users can still turn RAW + JPEG OFF in the Pro settings.
                    rawCaptureEnabled = supported
                },
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

        // Focused single-control adjustment: settings disappear, live viewfinder stays visible.
        val focusedKey = focusedAdjustment
        if (hasPermission && focusedKey != null) {
            val focusLabel = when (focusedKey) {
                "brightness" -> "Brightness"
                "contrast" -> "Contrast"
                "saturation" -> "Saturation"
                "warmth" -> "Warmth"
                "tint" -> "Tint"
                "denoise" -> "Denoise"
                "sharpening" -> "Sharpening"
                "clarity" -> "Clarity"
                "texture" -> "Texture"
                "highlights" -> "Highlights"
                else -> "Shadows"
            }
            val focusValue = when (focusedKey) {
                "brightness" -> liveBrightness
                "contrast" -> liveContrast
                "saturation" -> liveSaturation
                "warmth" -> liveWarmth
                "tint" -> liveTint
                "denoise" -> liveDenoise
                "sharpening" -> liveSharpening
                "clarity" -> liveClarity
                "texture" -> liveTexture
                "highlights" -> liveHighlights
                else -> liveShadows
            }
            val isSigned = focusedKey in setOf("brightness", "contrast", "saturation", "warmth", "tint")
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 220.dp)
                    .background(Color(0xE9161920), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(focusLabel.uppercase(Locale.US), color = Color.White,
                            style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (isSigned) String.format(Locale.US, "%+.0f%%", focusValue * 100f)
                            else "${(focusValue * 100f).roundToInt()}%",
                            color = Color(0xFFFFD54F), style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Surface(
                        modifier = Modifier.size(42.dp).clickable { focusedAdjustment = null },
                        color = Color(0xFF30343D), shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, contentDescription = "Close adjustment", tint = Color.White)
                        }
                    }
                }
                Slider(
                    value = focusValue,
                    onValueChange = { v ->
                        when (focusedKey) {
                            "brightness" -> liveBrightness = v
                            "contrast" -> liveContrast = v
                            "saturation" -> liveSaturation = v
                            "warmth" -> liveWarmth = v
                            "tint" -> liveTint = v
                            "denoise" -> liveDenoise = v
                            "sharpening" -> liveSharpening = v
                            "clarity" -> liveClarity = v
                            "texture" -> liveTexture = v
                            "highlights" -> liveHighlights = v
                            "shadows" -> liveShadows = v
                        }
                    },
                    valueRange = if (isSigned) -1f..1f else 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD54F),
                        activeTrackColor = Color(0xFFFFD54F),
                        inactiveTrackColor = Color(0xFF555D68)
                    )
                )
                Text("Drag to adjust • Live preview updates instantly",
                    color = Color(0xFFB8BEC8), style = MaterialTheme.typography.labelSmall)
            }
        }

        // Modern floating camera toolbar
        if (hasPermission) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Color(0xA6101218))
                    .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("LUMA", color = Color.White, style = MaterialTheme.typography.titleLarge,
                        letterSpacing = 2.sp)
                    Text("PRO CAMERA", color = Color(0xFFB7BDC8), style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.5.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Surface(
                        modifier = Modifier.height(38.dp).clickable {
                            hdrMode = when (hdrMode) { 0 -> 1; 1 -> 2; else -> 0 }
                        },
                        color = if (hdrMode == 0) Color(0x553A3E46) else Color(0xFFFFD54F),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(Modifier.padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
                            Text(when (hdrMode) { 0 -> "HDR OFF"; 1 -> "HDR"; else -> "HDR+" },
                                color = if (hdrMode == 0) Color.White else Color(0xFF141414),
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Surface(
                        modifier = Modifier.size(38.dp).clickable {
                            flashEnabled = !flashEnabled
                            imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        },
                        color = Color(0x553A3E46), shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = "Flash", tint = if (flashEnabled) Color(0xFFFFD54F) else Color.White)
                        }
                    }
                    Surface(
                        modifier = Modifier.size(40.dp).clickable { showGallery = true },
                        color = Color(0x553A3E46), shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
                        }
                    }
                    Surface(
                        modifier = Modifier.size(40.dp).clickable { showSettings = true },
                        color = Color(0x553A3E46), shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Minimal stock-camera shooting deck; tuning is available only in Settings.
        if (hasPermission) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color(0xEA101319), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(7.dp).background(if (isRecordingVideo) Color(0xFFFF5252) else Color(0xFFFFD54F), CircleShape))
                        Text(if (isRecordingVideo) "RECORDING • ${recordingSeconds}s" else if (videoMode) "VIDEO MODE" else if (nightMode) "NIGHT MODE · 5 FRAMES" else "PHOTO MODE",
                            color = Color.White, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        QuickCameraChip(if (aspectRatioWide) "16:9" else "4:3", !isCapturing) {
                            if (!isCapturing) aspectRatioWide = !aspectRatioWide
                        }
                        QuickCameraChip(if (captureTimerSeconds == 0) "TIMER OFF" else "TIMER ${captureTimerSeconds}s", !isCapturing) {
                            captureTimerSeconds = when (captureTimerSeconds) { 0 -> 3; 3 -> 5; 5 -> 10; else -> 0 }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    QuickCameraChip(if (nightMode) "NIGHT ON · 5 FRAMES" else "NIGHT OFF", !isCapturing && !videoMode, nightMode) {
                        if (!isCapturing && !videoMode) {
                            nightMode = !nightMode
                            if (nightMode) {
                                rawCaptureEnabled = false
                                flashEnabled = false
                                imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                                Toast.makeText(context, "Luma Night: 5-frame capture", Toast.LENGTH_SHORT).show()
                            }
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
                            Surface(modifier = Modifier.clickable(enabled = !isRecordingVideo) { videoMode = false }, color = if (!videoMode) Color(0xFFFFD54F) else Color(0x223A3F48), shape = RoundedCornerShape(14.dp)) {
                                Text("PHOTO", color = if (!videoMode) Color(0xFF171717) else Color.LightGray, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                            }
                            Surface(modifier = Modifier.clickable(enabled = !isRecordingVideo) { videoMode = true }, color = if (videoMode) Color(0xFFFFD54F) else Color(0x223A3F48), shape = RoundedCornerShape(14.dp)) {
                                Text("VIDEO", color = if (videoMode) Color(0xFF171717) else Color.LightGray, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
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
                        .background(Color(0xFF12151B), RoundedCornerShape(26.dp))
                        .padding(16.dp).verticalScroll(rememberScrollState())
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Settings", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        QuickCameraChip("DONE", true) { showSettings = false }
                    }
                    Text("Fine-tune the live camera. Pick any image control to open its focused preview adjustment.", color = Color(0xFFB8BEC8), style = MaterialTheme.typography.bodySmall)
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
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("IMAGE TUNING", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                    Text("Choose a control to adjust it over the live viewfinder",
                                        color = Color(0xFFB8BEC8), style = MaterialTheme.typography.bodySmall)
                                }
                                QuickCameraChip("RESET", true) {
                                    liveBrightness = 0f; liveContrast = 0f; liveSaturation = 0f
                                    liveWarmth = 0f; liveTint = 0f; liveDenoise = 0f
                                    liveSharpening = 0f; liveClarity = 0f; liveTexture = 0f
                                    liveHighlights = 0f; liveShadows = 0f; hdrMode = 0
                                    selectedLiveLook = "STANDARD"
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            AdjustmentSettingRow("Brightness", "Exposure look", String.format(Locale.US, "%+.0f%%", liveBrightness * 100f)) {
                                focusedAdjustment = "brightness"; showSettings = false
                            }
                            AdjustmentSettingRow("Contrast", "Deepen or soften tonal range", String.format(Locale.US, "%+.0f%%", liveContrast * 100f)) {
                                focusedAdjustment = "contrast"; showSettings = false
                            }
                            AdjustmentSettingRow("Saturation", "Color intensity", String.format(Locale.US, "%+.0f%%", liveSaturation * 100f)) {
                                focusedAdjustment = "saturation"; showSettings = false
                            }
                            AdjustmentSettingRow("Warmth", "Cool to warm color balance", String.format(Locale.US, "%+.0f%%", liveWarmth * 100f)) {
                                focusedAdjustment = "warmth"; showSettings = false
                            }
                            AdjustmentSettingRow("Tint", "Green to magenta balance", String.format(Locale.US, "%+.0f%%", liveTint * 100f)) {
                                focusedAdjustment = "tint"; showSettings = false
                            }
                            AdjustmentSettingRow("Denoise", "Reduce image noise", "${(liveDenoise * 100f).roundToInt()}%") {
                                focusedAdjustment = "denoise"; showSettings = false
                            }
                            AdjustmentSettingRow("Sharpening", "Enhance edge definition", "${(liveSharpening * 100f).roundToInt()}%") {
                                focusedAdjustment = "sharpening"; showSettings = false
                            }
                            AdjustmentSettingRow("Clarity", "Local contrast", "${(liveClarity * 100f).roundToInt()}%") {
                                focusedAdjustment = "clarity"; showSettings = false
                            }
                            AdjustmentSettingRow("Texture", "Fine detail intensity", "${(liveTexture * 100f).roundToInt()}%") {
                                focusedAdjustment = "texture"; showSettings = false
                            }
                            AdjustmentSettingRow("Highlights", "Recover bright areas", "${(liveHighlights * 100f).roundToInt()}%") {
                                focusedAdjustment = "highlights"; showSettings = false
                            }
                            AdjustmentSettingRow("Shadows", "Lift dark areas", "${(liveShadows * 100f).roundToInt()}%") {
                                focusedAdjustment = "shadows"; showSettings = false
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { importConfigLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303640), contentColor = Color.White)) {
                                    Text("IMPORT PRESET")
                                }
                                Button(onClick = { exportConfigLauncher.launch("LumaCamera-${selectedLiveLook.replace(' ', '-')}.json") },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F), contentColor = Color(0xFF171717))) {
                                    Text("SAVE PRESET")
                                }
                            }
                            Text("HDR mode is controlled from the HDR button on the camera screen: OFF → HDR → HDR+.",
                                color = Color(0xFFB8BEC8), style = MaterialTheme.typography.bodySmall)
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
                            Text("CAMERA HARDWARE DIAGNOSTICS", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text(hardwareDiagnosticReport, color = Color(0xFFB8BEC8), style = MaterialTheme.typography.bodySmall)
                            if (standardPhotoModeLabel != null) {
                                Text("STANDARD PHOTO • $standardPhotoModeLabel", color = Color(0xFFB8BEC8), style = MaterialTheme.typography.bodySmall)
                            }
                            if (fullResolutionModeLabel != null) {
                                QuickCameraChip(if (fullResolutionEnabled) "FULL RESOLUTION ON • $fullResolutionModeLabel" else "FULL RESOLUTION • $fullResolutionModeLabel", !isCapturing) {
                                    if (!isCapturing) fullResolutionEnabled = !fullResolutionEnabled
                                }
                                Text("Uses the largest supported JPEG stream. Capture may take longer and low-light quality can vary.", color = Color(0xFFB8BEC8), style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Full-resolution mode is not exposed by this camera configuration.", color = Color(0xFFB8BEC8), style = MaterialTheme.typography.bodySmall)
                            }
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
                            Button(
                                onClick = { if (!rawDeveloping) rawDevelopLauncher.launch(arrayOf("image/x-adobe-dng", "application/octet-stream", "*/*")) },
                                enabled = !rawDeveloping,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F), contentColor = Color(0xFF171717))
                            ) {
                                Text(if (rawDeveloping) "DEVELOPING RAW…" else "DEVELOP DNG → JPEG")
                            }
                            Text(
                                "Open a Luma DNG file to develop its Bayer sensor data into a full-resolution JPEG. The original DNG is kept unchanged.",
                                color = Color(0xFFB8BEC8), style = MaterialTheme.typography.bodySmall
                            )
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
private fun AdjustmentSettingRow(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color(0xFF20252E),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, color = Color(0xFFADB5C2), style = MaterialTheme.typography.bodySmall)
            }
            Text(value, color = Color(0xFFFFD54F), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(8.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = "Adjust $title", tint = Color(0xFFADB5C2))
        }
    }
    Spacer(Modifier.height(8.dp))
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

private fun setStockAutoCameraDefaults(camera: Camera, exposureMin: Int, exposureMax: Int) {
    runCatching {
        val info = Camera2CameraInfo.from(camera.cameraInfo)
        val builder = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, false)
            .setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            .setCaptureRequestOption(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_OFF)

        // Request the camera HAL's own high-quality still-image ISP paths when supported.
        // This preserves sensor-native processing instead of adding artificial GPU sharpening.
        val edgeModes = info.getCameraCharacteristic(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
        if (edgeModes?.contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY) == true) {
            builder.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
        }
        val noiseModes = info.getCameraCharacteristic(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
        if (noiseModes?.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY) == true) {
            builder.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
        }
        val hotPixelModes = info.getCameraCharacteristic(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)
        if (hotPixelModes?.contains(CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY) == true) {
            builder.setCaptureRequestOption(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY)
        }
        val toneMapModes = info.getCameraCharacteristic(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)
        if (toneMapModes?.contains(CaptureRequest.TONEMAP_MODE_HIGH_QUALITY) == true) {
            builder.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
        }

        val afModes = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        if (afModes?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true) {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        }
        Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(builder.build())
        camera.cameraControl.setExposureCompensationIndex(0.coerceIn(exposureMin, exposureMax))
        android.util.Log.i("LumaProCamera", "Stock-style auto controls applied: AE/AWB auto, EV 0, supported high-quality ISP modes requested")
    }.onFailure {
        android.util.Log.w("LumaProCamera", "Could not apply all stock-style auto controls", it)
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

private fun applyChromaNoiseReduction(
    source: Bitmap,
    destination: Bitmap,
    strength: Float = 0.65f,
    sharpening: Float = 0f,
    clarity: Float = 0f,
    texture: Float = 0f,
    highlights: Float = 0f,
    shadows: Float = 0f
) {
    require(source.width == destination.width && source.height == destination.height)
    require(source !== destination) { "RAW denoiser requires separate source and destination bitmaps" }

    val width = source.width
    val height = source.height
    val top = IntArray(width)
    val middle = IntArray(width)
    val bottom = IntArray(width)
    val outputRow = IntArray(width)
    val amount = strength.coerceIn(0f, 1f)

    // A 3x3 edge-aware bilateral pass reduces both luminance grain and chroma
    // speckle. Unlike the previous fixed cross blur, every neighbour is weighted
    // by its luminance similarity so strong edges are not averaged together.
    val noiseSigma = 7f + amount * 20f
    val lumaBlend = 0.20f + amount * 0.55f
    val chromaBlend = 0.40f + amount * 0.48f
    val sigmaSquared = noiseSigma * noiseSigma

    fun luma(pixel: Int): Float {
        val r = ((pixel ushr 16) and 0xff).toFloat()
        val g = ((pixel ushr 8) and 0xff).toFloat()
        val b = (pixel and 0xff).toFloat()
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    source.getPixels(top, 0, width, 0, 0, width, 1)
    System.arraycopy(top, 0, middle, 0, width)
    source.getPixels(bottom, 0, width, 0, minOf(1, height - 1), width, 1)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val center = middle[x]
            val alpha = (center ushr 24) and 0xff
            val r = ((center ushr 16) and 0xff).toFloat()
            val g = ((center ushr 8) and 0xff).toFloat()
            val b = (center and 0xff).toFloat()
            val centerY = 0.299f * r + 0.587f * g + 0.114f * b
            val centerCb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b
            val centerCr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b

            var sumY = centerY * 2.5f
            var sumCb = centerCb * 2.5f
            var sumCr = centerCr * 2.5f
            var sumWeight = 2.5f
            var neighbourLuma = 0f
            var neighbourCount = 0

            fun addNeighbour(pixel: Int, spatialWeight: Float) {
                val nr = ((pixel ushr 16) and 0xff).toFloat()
                val ng = ((pixel ushr 8) and 0xff).toFloat()
                val nb = (pixel and 0xff).toFloat()
                val ny = 0.299f * nr + 0.587f * ng + 0.114f * nb
                val diff = ny - centerY
                val rangeWeight = 1f / (1f + (diff * diff / sigmaSquared))
                val weight = spatialWeight * rangeWeight
                sumY += ny * weight
                sumCb += (128f - 0.168736f * nr - 0.331264f * ng + 0.5f * nb) * weight
                sumCr += (128f + 0.5f * nr - 0.418688f * ng - 0.081312f * nb) * weight
                sumWeight += weight
                neighbourLuma += ny
                neighbourCount++
            }

            val left = (x - 1).coerceAtLeast(0)
            val right = (x + 1).coerceAtMost(width - 1)
            addNeighbour(middle[left], 1.0f)
            addNeighbour(middle[right], 1.0f)
            addNeighbour(top[x], 1.0f)
            addNeighbour(bottom[x], 1.0f)
            addNeighbour(top[left], 0.70f)
            addNeighbour(top[right], 0.70f)
            addNeighbour(bottom[left], 0.70f)
            addNeighbour(bottom[right], 0.70f)

            val averageY = sumY / sumWeight
            val filteredY = centerY + (averageY - centerY) * lumaBlend
            val filteredCb = centerCb + (sumCb / sumWeight - centerCb) * chromaBlend
            val filteredCr = centerCr + (sumCr / sumWeight - centerCr) * chromaBlend

            // Restore local edge detail after denoising, rather than sharpening
            // the original noisy pixels before smoothing.
            val localAverage = neighbourLuma / neighbourCount.coerceAtLeast(1)
            val edgeDetail = centerY - localAverage
            val detailAmount = (
                sharpening.coerceIn(0f, 1f) * 0.48f +
                texture.coerceIn(0f, 1f) * 0.24f +
                clarity.coerceIn(0f, 1f) * 0.20f
            ).coerceAtMost(0.72f)
            var finalY = filteredY + edgeDetail * detailAmount

            // Restrained single-frame tone shaping. Clipped highlights cannot be
            // reconstructed from one exposure; avoid aggressive shadow amplification.
            val normalizedY = (finalY / 255f).coerceIn(0f, 1f)
            val shadowMask = 1f - ((normalizedY - 0.08f) / 0.58f).coerceIn(0f, 1f)
            val highlightMask = ((normalizedY - 0.34f) / 0.58f).coerceIn(0f, 1f)
            val shadowLift = shadowMask * shadows.coerceIn(0f, 1f) * 0.18f
            val highlightPull = highlightMask * highlights.coerceIn(0f, 1f) * 0.22f
            val mappedY = (normalizedY + shadowLift * (1f - normalizedY) -
                highlightPull * normalizedY).coerceIn(0f, 1f)
            finalY = (mappedY * 255f).coerceIn(0f, 255f)

            val outR = (finalY + 1.402f * (filteredCr - 128f)).toInt().coerceIn(0, 255)
            val outG = (finalY - 0.344136f * (filteredCb - 128f) -
                0.714136f * (filteredCr - 128f)).toInt().coerceIn(0, 255)
            val outB = (finalY + 1.772f * (filteredCb - 128f)).toInt().coerceIn(0, 255)
            outputRow[x] = (alpha shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        destination.setPixels(outputRow, 0, width, 0, y, width, 1)
        if (y < height - 1) {
            System.arraycopy(middle, 0, top, 0, width)
            System.arraycopy(bottom, 0, middle, 0, width)
            source.getPixels(bottom, 0, width, 0, minOf(y + 2, height - 1), width, 1)
        }
    }
}

private fun developDngToJpeg(
    context: Context,
    sourceUri: Uri,
    denoise: Float = 0.55f,
    brightness: Float = 0f,
    contrast: Float = 0f,
    saturation: Float = 0f,
    warmth: Float = 0f,
    tint: Float = 0f,
    sharpening: Float = 0f,
    clarity: Float = 0f,
    texture: Float = 0f,
    highlights: Float = 0f,
    shadows: Float = 0f
) {
    // DNG orientation is stored in TIFF/EXIF metadata. LibRaw's Android bitmap
    // path can return sensor-native orientation, so retain the display rotation.
    val rotationDegrees = runCatching {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            when (ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)
    val bitmap = context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { descriptor ->
        LibRaw().use { raw ->
            // AndroidLibRaw's native openFd() wraps the supplied FD with fdopen()
            // and closes it with fclose() before returning. Never pass the FD owned
            // by this ParcelFileDescriptor: .use{} would close that same FD again
            // and Android fdsan aborts the process for the double ownership.
            // Duplicate it, then detach so native LibRaw is its sole owner.
            val libRawFd = android.os.ParcelFileDescriptor
                .dup(descriptor.fileDescriptor)
                .detachFd()
            val openResult = raw.openFd(libRawFd)
            check(openResult == 0) { "LibRaw could not open this DNG (code $openResult)" }
            // DCB demosaicing (quality 4) is a higher-detail interpolation than AHD (3).
            // Keep full Bayer-domain FBDD enabled before demosaicing to suppress sensor noise.
            raw.setQuality(4)
            raw.setCameraWhiteBalance(true)
            raw.setUseCameraMatrix(LibRaw.USE_CAMERA_MATRIX_DEFAULT)
            raw.setOutputColorSpace(LibRaw.COLORSPACE_SRGB)
            raw.setOutputBps(8)
            // Let LibRaw map the sensor-linear RAW data into a usable display range.
            // Keep a modest pre-demosaic lift and highlight preservation; disabling
            // auto-brightness was the main reason developed frames stayed too dark.
            raw.setAutoBrightness(true)
            // Do not apply a second 1.45x lift before demosaicing: at ISO 12800
            // it magnifies sensor grain before the adaptive denoising pass.
            raw.setExposureCorrectionBeforeDemosaic(false, 1.0f, 0.85f)
            raw.setHighlightMode(2)
            raw.setFbddNoiseReduction(2)
            // AndroidLibRaw 2.0.7 returns a HARDWARE bitmap from decodeBitmap()
            // on Android 10+, which cannot be drawn by our software Canvas finishing pass.
            // Run LibRaw explicitly and request a mutable software bitmap instead.
            raw.setHalfSize(false)
            val processResult = raw.dcrawProcess()
            check(processResult == 0) { "LibRaw processing failed (code $processResult)" }
            raw.getMutableBitmap(Bitmap.Config.ARGB_8888)
        }
    } ?: error("Cannot open selected DNG file")

    // LibRaw's display conversion can look flat on this device. Apply a restrained
    // finishing pass: modest contrast/saturation and a slight warm-neutral balance.
    // Keep the source bitmap untouched until the finished bitmap is ready.
    val finishedBitmap = try {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val contrastScale = (1.06f + contrast.coerceIn(-1f, 1f) * 0.35f).coerceIn(0.65f, 1.45f)
        val brightnessOffset = 3f + brightness.coerceIn(-1f, 1f) * 42f
        val warmthShift = warmth.coerceIn(-1f, 1f) * 18f
        val tintShift = tint.coerceIn(-1f, 1f) * 14f
        val colorMatrix = android.graphics.ColorMatrix().apply {
            // Apply these controls to the saved RAW-developed JPEG as well as preview.
            set(floatArrayOf(
                contrastScale, 0f, 0f, 0f, brightnessOffset + warmthShift,
                0f, contrastScale, 0f, 0f, brightnessOffset + tintShift,
                0f, 0f, contrastScale, 0f, brightnessOffset - warmthShift,
                0f, 0f, 0f, 1f, 0f
            ))
            postConcat(android.graphics.ColorMatrix().apply {
                setSaturation((1.06f + saturation.coerceIn(-1f, 1f) * 0.45f).coerceIn(0.4f, 1.5f))
            })
        }
        android.graphics.Canvas(output).drawBitmap(
            bitmap, 0f, 0f,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
                isFilterBitmap = true
            }
        )
        // Smooth chroma speckle while preserving luminance edges and fabric texture.
        // Row buffers keep peak memory low for this 12MP sensor image.
        applyChromaNoiseReduction(
            output, bitmap,
            strength = denoise.coerceIn(0f, 1f).let { 0.35f + it * 0.45f },
            sharpening = sharpening,
            clarity = clarity,
            texture = texture,
            highlights = highlights,
            shadows = shadows
        )
        // The denoiser writes its result into bitmap (the destination). Return
        // that destination; returning output here would silently discard the
        // denoising pass even though its CPU work had completed.
        if (!output.isRecycled) output.recycle()
        bitmap
    } catch (error: Exception) {
        if (!bitmap.isRecycled) bitmap.recycle()
        throw error
    }

    val orientedBitmap = if (rotationDegrees != 0) {
        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(
            finishedBitmap, 0, 0, finishedBitmap.width, finishedBitmap.height, matrix, true
        ).also { rotated ->
            if (rotated !== finishedBitmap && !finishedBitmap.isRecycled) finishedBitmap.recycle()
        }
    } else {
        finishedBitmap
    }

    try {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Luma_RAW_Developed_$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Luma Pro Camera")
            }
        }
        val outputUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create output JPEG")
        try {
            context.contentResolver.openOutputStream(outputUri, "w")?.use { stream ->
                check(orientedBitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream)) { "JPEG encoding failed" }
                stream.flush()
            } ?: error("Could not write output JPEG")
        } catch (error: Exception) {
            runCatching { context.contentResolver.delete(outputUri, null, null) }
            throw error
        }
    } finally {
        if (!orientedBitmap.isRecycled) orientedBitmap.recycle()
        if (!finishedBitmap.isRecycled) finishedBitmap.recycle()
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}


private fun captureNightPhoto(
    context: Context,
    capture: ImageCapture,
    mainExecutor: Executor,
    onComplete: (Boolean) -> Unit
) {
    val dir = File(context.cacheDir, "luma_night").apply { mkdirs() }
    val frames = (0 until 5).map { File(dir, "frame_$it.jpg").also { file -> file.delete() } }
    val completed = java.util.concurrent.atomic.AtomicBoolean(false)

    fun finish(success: Boolean) {
        if (completed.compareAndSet(false, true)) {
            frames.forEach { it.delete() }
            mainExecutor.execute { onComplete(success) }
        }
    }

    fun chooseSharpestOriginal(): File {
        var bestFile: File? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (file in frames) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid captured frame" }

            val sample = BitmapFactory.Options().apply {
                inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 320)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, sample) ?: continue
            try {
                val width = bitmap.width
                val height = bitmap.height
                if (width < 8 || height < 8) continue
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                // Score focus/detail with a Laplacian variance estimate.
                // Penalize very dark and clipped frames so a noisy/overexposed
                // frame is not selected just because it has high contrast.
                var sum = 0.0
                var sumSquares = 0.0
                var count = 0
                var luminanceSum = 0.0
                var clipped = 0
                for (y in 1 until height - 1 step 2) {
                    for (x in 1 until width - 1 step 2) {
                        fun lum(pixel: Int): Int =
                            (77 * ((pixel shr 16) and 255) +
                             150 * ((pixel shr 8) and 255) +
                             29 * (pixel and 255)) shr 8

                        val center = lum(pixels[y * width + x])
                        val laplacian = 4 * center -
                            lum(pixels[y * width + x - 1]) -
                            lum(pixels[y * width + x + 1]) -
                            lum(pixels[(y - 1) * width + x]) -
                            lum(pixels[(y + 1) * width + x])
                        sum += laplacian
                        sumSquares += laplacian.toDouble() * laplacian
                        luminanceSum += center
                        if (center < 4 || center > 250) clipped++
                        count++
                    }
                }
                if (count == 0) continue
                val mean = sum / count
                val variance = (sumSquares / count - mean * mean).coerceAtLeast(0.0)
                val averageLuma = luminanceSum / count
                val clippedRatio = clipped.toDouble() / count
                val exposurePenalty = when {
                    averageLuma < 12.0 -> 0.35
                    averageLuma > 238.0 -> 0.45
                    else -> 1.0
                }
                val score = variance * exposurePenalty * (1.0 - clippedRatio * 0.7)
                if (score > bestScore) {
                    bestScore = score
                    bestFile = file
                }
            } finally {
                bitmap.recycle()
            }
        }
        return bestFile ?: error("No usable Night frame")
    }

    fun saveOriginalFrame(file: File) {
        val displayName = "Luma_Night_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Luma Pro Camera")
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: error("Could not create Night photo")
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Could not write Night photo")
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }

    fun captureNext(index: Int) {
        if (index == frames.size) {
            Thread({
                try {
                    val selected = chooseSharpestOriginal()
                    saveOriginalFrame(selected)
                    finish(true)
                } catch (error: Exception) {
                    android.util.Log.e("LumaProCamera", "Night best-frame selection failed", error)
                    finish(false)
                }
            }, "LumaNightSelect").start()
            return
        }

        val options = ImageCapture.OutputFileOptions.Builder(frames[index]).build()
        capture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                captureNext(index + 1)
            }

            override fun onError(exception: ImageCaptureException) {
                android.util.Log.e("LumaProCamera", "Night frame $index failed", exception)
                finish(false)
            }
        })
    }

    captureNext(0)
}

private data class PhotoProcessingSettings(
    val brightness: Float, val contrast: Float, val saturation: Float,
    val warmth: Float, val tint: Float, val denoise: Float,
    val sharpening: Float, val clarity: Float, val texture: Float,
    val highlights: Float, val shadows: Float
) {
    val hasAdjustments: Boolean
        get() = listOf(brightness, contrast, saturation, warmth, tint, denoise,
            sharpening, clarity, texture, highlights, shadows)
            .any { kotlin.math.abs(it) > 0.001f }
}

private fun processCapturedJpeg(context: Context, uri: Uri, settings: PhotoProcessingSettings) {
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val source = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        })
    } ?: error("Could not decode captured JPEG")

    var processed: Bitmap? = null
    var rotated: Bitmap? = null
    val temp = File(context.cacheDir, "luma_processed_${System.nanoTime()}.jpg")
    try {
        val contrastScale = (1f + settings.contrast.coerceIn(-1f, 1f) * 0.35f).coerceIn(0.65f, 1.35f)
        val brightnessOffset = settings.brightness.coerceIn(-1f, 1f) * 42f
        val warmthShift = settings.warmth.coerceIn(-1f, 1f) * 18f
        val tintShift = settings.tint.coerceIn(-1f, 1f) * 14f
        val colorMatrix = android.graphics.ColorMatrix().apply {
            set(floatArrayOf(
                contrastScale, 0f, 0f, 0f, brightnessOffset + warmthShift,
                0f, contrastScale, 0f, 0f, brightnessOffset + tintShift,
                0f, 0f, contrastScale, 0f, brightnessOffset - warmthShift,
                0f, 0f, 0f, 1f, 0f
            ))
            postConcat(android.graphics.ColorMatrix().apply {
                setSaturation((1f + settings.saturation.coerceIn(-1f, 1f) * 0.45f).coerceIn(0.4f, 1.5f))
            })
        }
        processed = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(processed!!).drawBitmap(
            source, 0f, 0f,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
                isFilterBitmap = true
            }
        )
        applyChromaNoiseReduction(
            processed!!, source,
            strength = settings.denoise.coerceIn(0f, 1f),
            sharpening = settings.sharpening,
            clarity = settings.clarity,
            texture = settings.texture,
            highlights = settings.highlights,
            shadows = settings.shadows
        )
        processed?.recycle()
        processed = null

        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        rotated = if (rotation != 0f) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height,
                android.graphics.Matrix().apply { postRotate(rotation) }, true)
        } else source

        temp.outputStream().buffered().use { output ->
            check(rotated!!.compress(Bitmap.CompressFormat.JPEG, 96, output)) { "JPEG encoding failed" }
            output.flush()
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            temp.inputStream().buffered().use { input -> input.copyTo(output) }
            output.flush()
        } ?: error("Could not replace saved JPEG")
    } finally {
        if (rotated != null && rotated !== source && !rotated!!.isRecycled) rotated!!.recycle()
        if (processed != null && !processed!!.isRecycled) processed!!.recycle()
        if (!source.isRecycled) source.recycle()
        temp.delete()
    }
}

private fun capturePhoto(
    context: android.content.Context,
    imageCapture: ImageCapture,
    executor: Executor,
    saveRawAndJpeg: Boolean,
    processing: PhotoProcessingSettings,
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
        context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
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
                context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rawValues
            ).build()
            // CameraX invokes this callback once for RAW_SENSOR and once for JPEG.
            // The order is not guaranteed; identify each result by getImageFormat().
            val completed = java.util.concurrent.atomic.AtomicInteger(0)
            val failed = java.util.concurrent.atomic.AtomicBoolean(false)
            val rawUri = java.util.concurrent.atomic.AtomicReference<Uri?>(null)
            val jpegUri = java.util.concurrent.atomic.AtomicReference<Uri?>(null)
            imageCapture.takePicture(rawOptions, outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    when (output.imageFormat) {
                        android.graphics.ImageFormat.RAW_SENSOR -> rawUri.set(output.savedUri)
                        android.graphics.ImageFormat.JPEG -> jpegUri.set(output.savedUri)
                        else -> android.util.Log.w(
                            "LumaProCamera",
                            "Unexpected RAW+JPEG output format: ${output.imageFormat}"
                        )
                    }
                    if (completed.incrementAndGet() == 2 && !failed.get()) {
                        val savedRaw = rawUri.get()
                        val savedJpeg = jpegUri.get()
                        Thread({
                            var processedByRawEngine = false
                            if (savedRaw != null) {
                                processedByRawEngine = runCatching {
                                    developDngToJpeg(
                                        context, savedRaw,
                                        denoise = processing.denoise,
                                        brightness = processing.brightness,
                                        contrast = processing.contrast,
                                        saturation = processing.saturation,
                                        warmth = processing.warmth,
                                        tint = processing.tint,
                                        sharpening = processing.sharpening,
                                        clarity = processing.clarity,
                                        texture = processing.texture,
                                        highlights = processing.highlights,
                                        shadows = processing.shadows
                                    )
                                }.onFailure {
                                    android.util.Log.e("LumaProCamera", "LibRaw development failed; using camera JPEG fallback", it)
                                }.isSuccess
                            }
                            if (!processedByRawEngine && savedJpeg != null && processing.hasAdjustments) {
                                runCatching { processCapturedJpeg(context, savedJpeg, processing) }
                                    .onFailure {
                                        android.util.Log.e("LumaProCamera", "JPEG fallback processing failed; original camera JPEG retained", it)
                                    }
                            } else if (processedByRawEngine && savedJpeg != null) {
                                // Keep the original DNG and the LibRaw-developed JPEG.
                                // Remove the redundant in-camera JPEG only after the developed file exists.
                                runCatching { context.contentResolver.delete(savedJpeg, null, null) }
                            }
                            executor.execute { onComplete(savedJpeg != null || savedRaw != null) }
                        }, "LumaRawDevelop").start()
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    android.util.Log.e("LumaProCamera", "RAW+JPEG capture failed", exception)
                    if (failed.compareAndSet(false, true)) onComplete(false)
                }
            })
        } else {
            imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri
                    if (!processing.hasAdjustments || savedUri == null) {
                        onComplete(true)
                    } else {
                        Thread({
                            val success = runCatching { processCapturedJpeg(context, savedUri, processing) }
                                .onFailure { android.util.Log.e("LumaProCamera", "JPEG processing failed", it) }
                                .isSuccess
                            executor.execute { onComplete(success) }
                        }, "LumaJpegFinish").start()
                    }
                }
                override fun onError(exception: ImageCaptureException) { onComplete(false) }
            })
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
    videoMode: Boolean,
    cameraRestartToken: Int,
    liveBrightness: Float,
    liveContrast: Float,
    liveSaturation: Float,
    liveWarmth: Float,
    liveTint: Float,
    liveDenoise: Float,
    liveSharpening: Float,
    liveClarity: Float,
    liveTexture: Float,
    liveHighlights: Float,
    liveShadows: Float,
    hdrStrength: Float,
    liveProcessingEnabled: Boolean,
    jpegQuality: Int,
    maximizeQuality: Boolean,
    rawCaptureEnabled: Boolean,
    aspectRatioWide: Boolean,
    fullResolutionEnabled: Boolean,
    analysisEnabled: Boolean,
    onAnalysis: (List<Float>, Float, List<Offset>, List<Offset>) -> Unit,
    onPhotoModesChanged: (String?, String?) -> Unit,
    onHardwareInfoChanged: (String) -> Unit,
    onCameraReady: (Camera?, ImageCapture?, VideoCapture<Recorder>?) -> Unit,
    onTapFocus: () -> Unit,
    onRawCapabilityChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var activeMedia3Effect by remember { mutableStateOf<Media3Effect?>(null) }
    LaunchedEffect(activeMedia3Effect, liveProcessingEnabled, liveBrightness, liveContrast, liveSaturation, liveWarmth, liveTint, liveDenoise, liveSharpening, liveClarity, liveTexture, liveHighlights, liveShadows, hdrStrength) {
        val effects = if (liveProcessingEnabled) {
            listOf(
                Brightness(liveBrightness.coerceIn(-1f, 1f)),
                Contrast(liveContrast.coerceIn(-1f, 1f)),
                HslAdjustment.Builder().adjustSaturation((liveSaturation * 100f).coerceIn(-100f, 100f)).build(),
                RgbAdjustment.Builder()
                    .setRedScale((1f + liveWarmth * 0.16f + liveTint * 0.07f).coerceIn(0.75f, 1.25f))
                    .setGreenScale((1f - liveTint * 0.12f).coerceIn(0.75f, 1.25f))
                    .setBlueScale((1f - liveWarmth * 0.16f + liveTint * 0.07f).coerceIn(0.75f, 1.25f))
                    .build(),
                LiveDetailEffect(liveDenoise, liveSharpening, liveClarity, liveTexture, liveHighlights, liveShadows, hdrStrength)
            )
        } else emptyList()
        activeMedia3Effect?.setEffects(effects)
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

    DisposableEffect(previewView, lifecycleOwner, lensFacing, videoMode, cameraRestartToken, jpegQuality, maximizeQuality, rawCaptureEnabled, aspectRatioWide, fullResolutionEnabled, analysisEnabled) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val effectExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val media3Effect = runCatching {
            Media3Effect(
                context,
                CameraEffect.PREVIEW or
                    (if (videoMode) CameraEffect.VIDEO_CAPTURE else 0) or
                    (if (rawCaptureEnabled) 0 else CameraEffect.IMAGE_CAPTURE),
                effectExecutor
            ) { error ->
                android.util.Log.e("LumaProCamera", "Live GPU effect failed", error)
            }
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
                // Redmi 13C stock camera defaults to a binned ~12.5MP 4:3 photo.
                // Prefer the closest matching JPEG size actually advertised by this device;
                // do not assume every 50MP sensor exposes the same binned output dimensions.
                val camera2Info = Camera2CameraInfo.from(cameraProvider.getCameraInfo(selector))
                val hardwareReport = runCatching {
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                    cameraManager.cameraIdList.mapNotNull { cameraId ->
                        val chars = cameraManager.getCameraCharacteristics(cameraId)
                        val facing = chars.get(CameraCharacteristics.LENS_FACING)
                        if (facing != CameraCharacteristics.LENS_FACING_BACK) return@mapNotNull null
                        val pixelArray = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                        val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                        val physicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        val jpegSizes = map?.getOutputSizes(android.graphics.ImageFormat.JPEG)?.toList().orEmpty()
                            .sortedByDescending { it.width.toLong() * it.height.toLong() }
                        val rawSizes = map?.getOutputSizes(android.graphics.ImageFormat.RAW_SENSOR)?.toList().orEmpty()
                            .sortedByDescending { it.width.toLong() * it.height.toLong() }
                        fun formatSizes(sizes: List<android.util.Size>): String =
                            sizes.take(5).joinToString { size ->
                                val mp = size.width.toLong() * size.height.toLong() / 1_000_000.0
                                String.format(Locale.US, "%.1fMP (%dx%d)", mp, size.width, size.height)
                            }.ifBlank { "Not exposed" }
                        val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet().orEmpty()
                        val raw = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                        val manual = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
                        val physicalIds = if (android.os.Build.VERSION.SDK_INT >= 28 &&
                            caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
                        ) runCatching { chars.physicalCameraIds.joinToString().ifBlank { "None reported" } }.getOrDefault("Unavailable")
                        else "Not a logical multi-camera"
                        buildString {
                            append("Camera ID: $cameraId\n")
                            append("Sensor pixel array: ${pixelArray?.let { "${it.width}×${it.height}" } ?: "Not reported"}\n")
                            append("Active array: ${activeArray?.let { "${it.width()}×${it.height()}" } ?: "Not reported"}\n")
                            append("Sensor physical size: ${physicalSize?.let { String.format(Locale.US, "%.2f×%.2f mm", it.width, it.height) } ?: "Not reported"}\n")
                            append("JPEG streams: ${formatSizes(jpegSizes)}\n")
                            append("RAW_SENSOR streams: ${if (raw) formatSizes(rawSizes) else "RAW capability not advertised"}\n")
                            append("Physical camera IDs: $physicalIds\n")
                            append("Manual sensor: ${if (manual) "supported" else "not advertised"}")
                        }
                    }.joinToString("\n\n").ifBlank { "No rear Camera2 IDs exposed by Android." }
                }.getOrElse { "Could not read camera characteristics: ${it.javaClass.simpleName}" }
                onHardwareInfoChanged(hardwareReport + "\n\nOEM Camera Extensions: checking HDR and Night support…")
                // Query the OEM extension provider without changing the active camera session.
                // Actual HDR/Night activation will be added only after device support is confirmed.
                val extensionsFuture = ExtensionsManager.getInstanceAsync(context, cameraProvider)
                extensionsFuture.addListener({
                    val extensionReport = runCatching {
                        val manager = extensionsFuture.get()
                        val hdrAvailable = manager.isExtensionAvailable(selector, ExtensionMode.HDR)
                        val nightAvailable = manager.isExtensionAvailable(selector, ExtensionMode.NIGHT)
                        "OEM HDR extension: ${if (hdrAvailable) "Available" else "Not available"}\\n" +
                            "OEM Night extension: ${if (nightAvailable) "Available" else "Not available"}"
                    }.getOrElse { "OEM Camera Extensions: unavailable (${it.javaClass.simpleName})" }
                    onHardwareInfoChanged(hardwareReport + "\\n\\n" + extensionReport)
                }, executor)
                val jpegSizes = runCatching {
                    camera2Info.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                        ?.toList()
                        .orEmpty()
                }.getOrDefault(emptyList())
                val targetPhotoArea = if (aspectRatioWide) 9_375_000L else 12_500_000L
                val targetRatio = if (aspectRatioWide) 16f / 9f else 4f / 3f
                val matchingAspectSizes = jpegSizes.filter {
                    kotlin.math.abs(it.width.toFloat() / it.height.toFloat() - targetRatio) < 0.06f
                }
                val stockLikePhotoSize = matchingAspectSizes.minByOrNull { size ->
                    kotlin.math.abs(size.width.toLong() * size.height.toLong() - targetPhotoArea)
                } ?: jpegSizes.minByOrNull { size ->
                    kotlin.math.abs(size.width.toLong() * size.height.toLong() - targetPhotoArea)
                }
                val largestPhotoSize = matchingAspectSizes.maxByOrNull { it.width.toLong() * it.height.toLong() }
                fun formatPhotoSize(size: android.util.Size?): String? = size?.let {
                    val megapixels = it.width.toLong() * it.height.toLong() / 1_000_000.0
                    String.format(Locale.US, "%.1fMP · %dx%d", megapixels, it.width, it.height)
                }
                onPhotoModesChanged(formatPhotoSize(stockLikePhotoSize), formatPhotoSize(largestPhotoSize?.takeIf {
                    it.width.toLong() * it.height.toLong() > (stockLikePhotoSize?.width?.toLong() ?: 0L) * (stockLikePhotoSize?.height?.toLong() ?: 0L) * 1.35
                }))
                val resolutionBuilder = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(
                        if (aspectRatioWide) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                        else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                    )
                    .setAllowedResolutionMode(
                        if (fullResolutionEnabled) ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
                        else ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION
                    )
                val requestedPhotoSize = if (fullResolutionEnabled) largestPhotoSize ?: stockLikePhotoSize else stockLikePhotoSize
                if (requestedPhotoSize != null) {
                    resolutionBuilder.setResolutionStrategy(
                        ResolutionStrategy(
                            requestedPhotoSize,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    android.util.Log.i(
                        "LumaProCamera",
                        "${if (fullResolutionEnabled) "Full-resolution" else "Stock-like"} photo target: ${requestedPhotoSize.width}x${requestedPhotoSize.height}"
                    )
                } else {
                    resolutionBuilder.setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    android.util.Log.w("LumaProCamera", "JPEG output sizes unavailable; using CameraX highest available fallback")
                }
                val resolutionSelector = resolutionBuilder.build()
                val captureBuilder = ImageCapture.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setCaptureMode(if (maximizeQuality) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(jpegQuality)

                // Apply the same supported high-quality ISP requests to STILL CAPTURE.
                // CameraControl options alone primarily configure the repeating preview request.
                val stillCaptureInterop = Camera2Interop.Extender(captureBuilder)
                stillCaptureInterop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
                stillCaptureInterop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_AUTO
                )
                stillCaptureInterop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_LOCK,
                    false
                )
                stillCaptureInterop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_LOCK,
                    false
                )
                val stillEdgeModes = camera2Info.getCameraCharacteristic(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
                if (stillEdgeModes?.contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY) == true) {
                    stillCaptureInterop.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                }
                val stillNoiseModes = camera2Info.getCameraCharacteristic(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
                if (stillNoiseModes?.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY) == true) {
                    stillCaptureInterop.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                }
                val stillHotPixelModes = camera2Info.getCameraCharacteristic(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)
                if (stillHotPixelModes?.contains(CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY) == true) {
                    stillCaptureInterop.setCaptureRequestOption(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY)
                }
                val stillToneMapModes = camera2Info.getCameraCharacteristic(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)
                if (stillToneMapModes?.contains(CaptureRequest.TONEMAP_MODE_HIGH_QUALITY) == true) {
                    stillCaptureInterop.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
                }

                if (supportsRawJpeg && rawCaptureEnabled) {
                    captureBuilder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
                }
                val capture = captureBuilder.build()
                // Keep the still-photo session lean. Binding VideoCapture while the user
                // is in PHOTO mode adds another camera stream without providing any benefit.
                // Recreate it only after the user switches to VIDEO mode.
                val video: VideoCapture<Recorder>? = if (videoMode) {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.UHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)))
                        .build()
                    VideoCapture.withOutput(recorder)
                } else {
                    null
                }

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
                            .addUseCase(preview)
                            .addUseCase(capture)
                            .addUseCase(analysis)
                        if (video != null) groupBuilder.addUseCase(video)
                        if (media3Effect != null) groupBuilder.addEffect(media3Effect)
                        boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, groupBuilder.build())
                    } catch (_: Exception) {
                        analysis.clearAnalyzer()
                        analysisExecutor.shutdown()
                        val fallbackBuilder = UseCaseGroup.Builder()
                            .addUseCase(preview)
                            .addUseCase(capture)
                        if (video != null) fallbackBuilder.addUseCase(video)
                        boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, fallbackBuilder.build())
                    }
                } else {
                    try {
                        val groupBuilder = UseCaseGroup.Builder()
                            .addUseCase(preview)
                            .addUseCase(capture)
                        if (video != null) groupBuilder.addUseCase(video)
                        if (media3Effect != null) groupBuilder.addEffect(media3Effect)
                        boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, groupBuilder.build())
                    } catch (_: Exception) {
                        boundCamera = if (video != null) {
                            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture, video)
                        } else {
                            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                        }
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
            // Do not clear RAW support during a rebind: toggling RAW itself disposes this effect,
            // and clearing the capability here immediately turns the user toggle back off.
            onPhotoModesChanged(null, null)
            onHardwareInfoChanged("Camera diagnostics unavailable while camera is closed.")
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