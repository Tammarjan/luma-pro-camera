package com.lumapro.camera

import android.Manifest
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.content.ContentValues
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
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
private fun LumaCameraScreen() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var exposureMin by remember { mutableIntStateOf(0) }
    var exposureMax by remember { mutableIntStateOf(0) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var manualSensorSupported by remember { mutableStateOf(false) }
    var manualExposureEnabled by remember { mutableStateOf(false) }
    var isoMin by remember { mutableIntStateOf(100) }
    var isoMax by remember { mutableIntStateOf(3200) }
    var isoValue by remember { mutableIntStateOf(200) }
    var shutterMinNs by remember { mutableLongStateOf(125_000L) }
    var shutterMaxNs by remember { mutableLongStateOf(1_000_000_000L) }
    var shutterValueNs by remember { mutableLongStateOf(8_333_333L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                lensFacing = lensFacing,
                onCameraReady = { readyCamera, readyCapture ->
                    camera = readyCamera
                    imageCapture = readyCapture
                    manualExposureEnabled = false
                    manualSensorSupported = false
                    if (readyCamera != null) {
                        val range = readyCamera.cameraInfo.exposureState.exposureCompensationRange
                        exposureMin = range.lower
                        exposureMax = range.upper
                        exposureIndex = 0.coerceIn(range.lower, range.upper)
                        zoomRatio = 1f
                        runCatching {
                            val info = Camera2CameraInfo.from(readyCamera.cameraInfo)
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
                }
            )
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
            Text("LUMA PRO", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(
                if (lensFacing == CameraSelector.LENS_FACING_BACK) "REAR CAMERA  •  JPEG"
                else "FRONT CAMERA  •  JPEG",
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val maxZoom = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f
                        val target = 2f.coerceAtMost(maxZoom)
                        camera?.cameraControl?.setZoomRatio(target)
                        zoomRatio = target
                    },
                    enabled = camera != null
                ) { Text("2×") }
                Button(
                    onClick = {
                        camera?.cameraControl?.setZoomRatio(1f)
                        zoomRatio = 1f
                    },
                    enabled = camera != null
                ) { Text("1×") }
                Text(
                    if (zoomRatio > 1.01f) "Zoom 2×" else "Zoom 1×",
                    color = Color.LightGray
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
                        capturePhoto(
                            context = context,
                            imageCapture = capture,
                            executor = ContextCompat.getMainExecutor(context),
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
                ) {
                    Text(if (isCapturing) "…" else "●")
                }
                Text("JPEG", color = Color.White)
            }
        }
    }
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

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    lensFacing: Int,
    onCameraReady: (Camera?, ImageCapture?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(modifier = modifier, factory = { previewView })

    DisposableEffect(previewView, lifecycleOwner, lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                cameraProvider.unbindAll()
                val boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    capture
                )
                onCameraReady(boundCamera, capture)
            } catch (_: Exception) {
                onCameraReady(null, null)
            }
        }, executor)

        onDispose {
            onCameraReady(null, null)
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }
}
