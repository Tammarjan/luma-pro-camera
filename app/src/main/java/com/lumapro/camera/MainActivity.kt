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
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    var showProSettings by remember { mutableStateOf(false) }
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
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var showCaptureFlash by remember { mutableStateOf(false) }
    var captureTimerSeconds by remember { mutableIntStateOf(0) }
    var jpegQuality by remember { mutableIntStateOf(95) }
    var maximizeQuality by remember { mutableStateOf(true) }
    var aspectRatioWide by remember { mutableStateOf(false) }
    var timerCountdown by remember { mutableIntStateOf(0) }
    val cameraScope = androidx.compose.runtime.rememberCoroutineScope()
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var exposureMin by remember { mutableIntStateOf(0) }
    var exposureMax by remember { mutableIntStateOf(0) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var minZoomRatio by remember { mutableStateOf(1f) }
    var maxZoomRatio by remember { mutableStateOf(1f) }
    var flashAvailable by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var gridEnabled by remember { mutableStateOf(false) }
    var histogramEnabled by remember { mutableStateOf(false) }
    var liveHistogram by remember { mutableStateOf(List(32) { 0f }) }
    var highlightClipping by remember { mutableFloatStateOf(0f) }
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
                jpegQuality = jpegQuality,
                maximizeQuality = maximizeQuality,
                rawCaptureEnabled = rawCaptureEnabled,
                aspectRatioWide = aspectRatioWide,
                analysisEnabled = histogramEnabled,
                onHistogram = { bins, clipping -> liveHistogram = bins; highlightClipping = clipping },
                onCameraReady = { readyCamera, readyCapture ->
                    camera = readyCamera
                    imageCapture = readyCapture
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
                    if (!supported) rawCaptureEnabled = false
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

        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color(0x990A0A0A)).padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("LUMA PRO", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("PRO CAMERA", color = Color(0xFFB9B9B9), style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { showGallery = true },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303030))
                ) { Text("Gallery  ▣") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = Color(0xFF292929), shape = RoundedCornerShape(20.dp)) {
                    Text(
                        if (lensFacing == CameraSelector.LENS_FACING_BACK) "REAR" else "FRONT",
                        color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Surface(color = Color(0xFF292929), shape = RoundedCornerShape(20.dp)) {
                    Text(
                        if (rawCaptureEnabled && rawJpegSupported) "RAW + JPEG" else "JPEG",
                        color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Surface(color = Color(0xFF292929), shape = RoundedCornerShape(20.dp)) {
                    Text(if (aspectRatioWide) "16:9" else "4:3", color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(0xCC101010)).padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CAMERA CONTROLS", color = Color.White, style = MaterialTheme.typography.titleSmall)
                Button(onClick = { showProSettings = !showProSettings }) {
                    Text(if (showProSettings) "Hide Pro Settings" else "Pro Settings")
                }
            }
            if (showProSettings) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("EXPOSURE", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "EV ${if (exposureIndex > 0) "+" else ""}$exposureIndex",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("JPEG QUALITY", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Text("$jpegQuality%", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = jpegQuality.toFloat(),
                    onValueChange = { jpegQuality = it.roundToInt().coerceIn(70, 100) },
                    valueRange = 70f..100f,
                    steps = 29,
                    enabled = !isCapturing
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ASPECT RATIO", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Button(onClick = { aspectRatioWide = !aspectRatioWide }, enabled = !isCapturing) {
                        Text(if (aspectRatioWide) "16:9" else "4:3")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CAPTURE PRIORITY", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Button(onClick = { maximizeQuality = !maximizeQuality }, enabled = !isCapturing) {
                        Text(if (maximizeQuality) "Quality" else "Speed")
                    }
                }
                if (exposureMax > exposureMin) {
                    Slider(
                        value = exposureIndex.toFloat().coerceIn(exposureMin.toFloat(), exposureMax.toFloat()),
                        onValueChange = { value ->
                            val next = value.roundToInt().coerceIn(exposureMin, exposureMax)
                            exposureIndex = next
                            camera?.cameraControl?.setExposureCompensationIndex(next)
                        },
                        valueRange = exposureMin.toFloat()..exposureMax.toFloat(),
                        steps = (exposureMax - exposureMin - 1).coerceAtLeast(0),
                        enabled = camera != null && !manualExposureEnabled
                    )
                } else {
                    Text("Exposure adjustment unavailable on this camera", color = Color.LightGray)
                }
                if (manualSensorSupported) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("MANUAL ISO / SHUTTER", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Button(onClick = {
                            manualExposureEnabled = !manualExposureEnabled
                            setManualExposure(camera, manualExposureEnabled, isoValue, shutterValueNs)
                        }) { Text(if (manualExposureEnabled) "MANUAL ON" else "AUTO") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ISO $isoValue", color = Color.White, modifier = Modifier.weight(1f))
                        Slider(
                            modifier = Modifier.weight(2f),
                            value = isoValue.toFloat(),
                            onValueChange = {
                                isoValue = it.roundToInt().coerceIn(isoMin, isoMax)
                                if (manualExposureEnabled) setManualExposure(camera, true, isoValue, shutterValueNs)
                            },
                            valueRange = isoMin.toFloat()..isoMax.toFloat(),
                            enabled = manualExposureEnabled && isoMax > isoMin
                        )
                    }
                    val shutterFraction = shutterToFraction(shutterValueNs, shutterMinNs, shutterMaxNs)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shutter 1/${(1_000_000_000.0 / shutterValueNs).roundToInt()}s", color = Color.White, modifier = Modifier.weight(1f))
                        Slider(
                            modifier = Modifier.weight(2f),
                            value = shutterFraction,
                            onValueChange = {
                                shutterValueNs = fractionToShutter(it, shutterMinNs, shutterMaxNs)
                                if (manualExposureEnabled) setManualExposure(camera, true, isoValue, shutterValueNs)
                            },
                            valueRange = 0f..1f,
                            enabled = manualExposureEnabled && shutterMaxNs > shutterMinNs
                        )
                    }
                } else {
                    Text("Manual ISO / shutter not supported by this camera", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                }
                if (manualFocusSupported) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (manualFocusEnabled) "MANUAL FOCUS" else "FOCUS AUTO",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Button(onClick = {
                            manualFocusEnabled = !manualFocusEnabled
                            if (manualFocusEnabled) {
                                setManualFocus(camera, focusDistance)
                            } else {
                                setAutoFocus(camera)
                                focusDistance = 0f
                            }
                        }) { Text(if (manualFocusEnabled) "AUTO FOCUS" else "MANUAL") }
                    }
                    Slider(
                        value = focusDistance.coerceIn(0f, focusDistanceMax),
                        onValueChange = {
                            focusDistance = it
                            if (manualFocusEnabled) setManualFocus(camera, it)
                        },
                        valueRange = 0f..focusDistanceMax,
                        enabled = manualFocusEnabled && focusDistanceMax > 0f
                    )
                } else {
                    Text("Manual focus unavailable on this camera", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("WHITE BALANCE", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Button(onClick = {
                        val modes = supportedWbModes
                        val current = modes.indexOf(selectedWbMode).coerceAtLeast(0)
                        selectedWbMode = modes[(current + 1) % modes.size]
                        setWhiteBalance(camera, selectedWbMode)
                    }, enabled = camera != null) {
                        Text(whiteBalanceName(selectedWbMode))
                    }
                }
    
                }

            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        flashEnabled = !flashEnabled
                        imageCapture?.flashMode = if (flashEnabled) {
                            ImageCapture.FLASH_MODE_ON
                        } else {
                            ImageCapture.FLASH_MODE_OFF
                        }
                    },
                    enabled = flashAvailable && imageCapture != null
                ) {
                    Text(if (!flashAvailable) "Flash N/A" else if (flashEnabled) "Flash ON" else "Flash OFF")
                }
                Button(onClick = { gridEnabled = !gridEnabled }) {
                    Text(if (gridEnabled) "Grid ON" else "Grid OFF")
                }
                Button(onClick = { histogramEnabled = !histogramEnabled }) {
                    Text(if (histogramEnabled) "Histogram ON" else "Histogram")
                }
                Button(onClick = { levelEnabled = !levelEnabled }) {
                    Text(if (levelEnabled) "Level ON" else "Level OFF")
                }
                Button(
                    onClick = {
                        focusLockEnabled = !focusLockEnabled
                        setFocusLock(camera, focusLockEnabled)
                    },
                    enabled = camera != null
                ) {
                    Text(if (focusLockEnabled) "AE/AF LOCK" else "AE/AF AUTO")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0.5f, 1f, 2f).filter { it >= minZoomRatio && it <= maxZoomRatio }.forEach { preset ->
                    Button(
                        onClick = {
                            camera?.cameraControl?.setZoomRatio(preset)
                            zoomRatio = preset
                        },
                        enabled = camera != null
                    ) { Text(if (preset == 0.5f) "0.5×" else if (preset == 1f) "1×" else "2×") }
                }
                Text("Zoom " + zoomRatio + "×", color = Color.LightGray)
            }
            if (maxZoomRatio > minZoomRatio) {
                Slider(
                    value = zoomRatio.coerceIn(minZoomRatio, maxZoomRatio),
                    onValueChange = { next ->
                        zoomRatio = next
                        camera?.cameraControl?.setZoomRatio(next)
                    },
                    valueRange = minZoomRatio..maxZoomRatio,
                    enabled = camera != null
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    enabled = hasPermission
                ) { Text("Flip") }

                Button(
                    modifier = Modifier.size(84.dp),
                    enabled = imageCapture != null && !isCapturing,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.Gray
                    ),
                    onClick = { performCapture() }
                ) {
                    Text(if (timerCountdown > 0) timerCountdown.toString() else if (isCapturing) "…" else "●")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                            captureTimerSeconds = when (captureTimerSeconds) {
                                0 -> 3
                                3 -> 5
                                5 -> 10
                                else -> 0
                            }
                        },
                        enabled = !isCapturing
                    ) {
                        Text(if (captureTimerSeconds == 0) "Timer OFF" else "Timer " + captureTimerSeconds + "s")
                    }
                    Text(if (rawCaptureEnabled && rawJpegSupported) "RAW + JPEG" else "JPEG", color = Color.White)
                    if (rawJpegSupported) {
                        Button(onClick = { rawCaptureEnabled = !rawCaptureEnabled }) {
                            Text(if (rawCaptureEnabled) "RAW ON" else "RAW OFF")
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
private fun CameraPreview(
    modifier: Modifier = Modifier,
    lensFacing: Int,
    jpegQuality: Int,
    maximizeQuality: Boolean,
    rawCaptureEnabled: Boolean,
    aspectRatioWide: Boolean,
    analysisEnabled: Boolean,
    onHistogram: (List<Float>, Float) -> Unit,
    onCameraReady: (Camera?, ImageCapture?) -> Unit,
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
                    .build()
                val captureBuilder = ImageCapture.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setCaptureMode(if (maximizeQuality) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(jpegQuality)
                if (supportsRawJpeg && rawCaptureEnabled) {
                    captureBuilder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
                }
                val capture = captureBuilder.build()

                cameraProvider.unbindAll()
                if (analysisEnabled) {
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(320, 240))
                        .build()
                    val analysisExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
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
                                            if (value >= 250) clipped++
                                        }
                                        x += 4
                                    }
                                    y += 4
                                }
                                val peak = (bins.maxOrNull() ?: 1f).coerceAtLeast(1f)
                                val normalized = bins.map { it / peak }
                                val clipping = if (sampled > 0) clipped.toFloat() / sampled else 0f
                                ContextCompat.getMainExecutor(context).execute {
                                    onHistogram(normalized, clipping)
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            image.close()
                        }
                    }
                    try {
                        boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, selector, preview, capture, analysis
                        )
                    } catch (_: Exception) {
                        analysis.clearAnalyzer()
                        analysisExecutor.shutdown()
                        boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, selector, preview, capture
                        )
                    }
                } else {
                    boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, selector, preview, capture
                    )
                    onHistogram(List(32) { 0f }, 0f)
                }
                onCameraReady(boundCamera, capture)
                onRawCapabilityChanged(supportsRawJpeg)
            } catch (_: Exception) {
                onCameraReady(null, null)
            }
        }, executor)

        onDispose {
            boundCamera = null
            onCameraReady(null, null)
            onRawCapabilityChanged(false)
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }
}
