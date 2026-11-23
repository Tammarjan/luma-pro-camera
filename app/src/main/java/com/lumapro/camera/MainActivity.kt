package com.lumapro.camera

import android.Manifest
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Dialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

@Composable
private data class GalleryPhoto(val uri: Uri, val name: String)

@Composable
private fun LumaCameraScreen() {
    val context = LocalContext.current
    var showGallery by remember { mutableStateOf(false) }
    var galleryPhotos by remember { mutableStateOf<List<GalleryPhoto>>(emptyList()) }
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                lensFacing = lensFacing,
                jpegQuality = jpegQuality,
                maximizeQuality = maximizeQuality,
                aspectRatioWide = aspectRatioWide,
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
                .background(Color(0x77000000)).padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("LUMA PRO", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Button(onClick = { showGallery = true }) { Text("Gallery") }
            }
            Text(
                if (lensFacing == CameraSelector.LENS_FACING_BACK) "REAR CAMERA  •  ${if (rawCaptureEnabled && rawJpegSupported) "RAW + JPEG" else "JPEG"}"
                else "FRONT CAMERA  •  ${if (rawCaptureEnabled && rawJpegSupported) "RAW + JPEG" else "JPEG"}",
                color = Color.LightGray,
                style = MaterialTheme.typography.labelSmall
            )
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
                    modifier = Modifier.size(76.dp),
                    enabled = imageCapture != null && !isCapturing,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.Gray
                    ),
                    onClick = {
                        val capture = imageCapture ?: return@Button
                        isCapturing = true
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
                                    Toast.makeText(
                                        context,
                                        if (success) "Photo saved to Pictures/Luma Pro Camera"
                                        else "Unable to capture photo",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
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
                    Text("${galleryPhotos.size} photos", color = Color.LightGray)
                    Spacer(Modifier.height(12.dp))
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
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(photo.uri, "image/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { context.startActivity(intent) }
                                    },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
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
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    lensFacing: Int,
    jpegQuality: Int,
    maximizeQuality: Boolean,
    aspectRatioWide: Boolean,
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

    DisposableEffect(previewView, lifecycleOwner, lensFacing, jpegQuality, maximizeQuality, aspectRatioWide) {
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
                if (supportsRawJpeg) captureBuilder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
                val capture = captureBuilder.build()

                cameraProvider.unbindAll()
                boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    capture
                )
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
