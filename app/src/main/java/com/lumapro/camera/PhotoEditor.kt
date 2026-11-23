package com.lumapro.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.heifwriter.HeifWriter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
internal fun PhotoEditorDialog(photoUri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var source by remember(photoUri) { mutableStateOf<Bitmap?>(null) }
    var edited by remember(photoUri) { mutableStateOf<Bitmap?>(null) }
    var exposure by remember(photoUri) { mutableFloatStateOf(0f) }
    var contrast by remember(photoUri) { mutableFloatStateOf(1f) }
    var saturation by remember(photoUri) { mutableFloatStateOf(1f) }
    var warmth by remember(photoUri) { mutableFloatStateOf(0f) }
    var tint by remember(photoUri) { mutableFloatStateOf(0f) }
    var vibrance by remember(photoUri) { mutableFloatStateOf(0f) }
    var highlights by remember(photoUri) { mutableFloatStateOf(0f) }
    var shadows by remember(photoUri) { mutableFloatStateOf(0f) }
    var processing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var heifSaving by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }

    LaunchedEffect(photoUri) {
        source = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(photoUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 3000 || bounds.outHeight / sample > 3000) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1); inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val decoded = context.contentResolver.openInputStream(photoUri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (decoded == null) null else {
                val orientation = context.contentResolver.openInputStream(photoUri)?.use { input ->
                    ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
                }
                if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            }
        }
        edited = source
    }

    LaunchedEffect(source, exposure, contrast, saturation, warmth, tint, vibrance, highlights, shadows) {
        val original = source ?: return@LaunchedEffect
        delay(100)
        processing = true
        val result = withContext(Dispatchers.Default) {
            val output = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(original.width * original.height)
            original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
            val gain = 2.0.pow(exposure.toDouble()).toFloat()
            val highlightAmount = highlights
            val shadowAmount = shadows
            for (i in pixels.indices) {
                val color = pixels[i]
                val a = (color ushr 24) and 0xFF
                var r = ((color shr 16) and 0xFF).toFloat() * gain
                var g = ((color shr 8) and 0xFF).toFloat() * gain
                var b = (color and 0xFF).toFloat() * gain
                val initialLuma = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 255f)
                val normalizedLuma = initialLuma / 255f
                val shadowMask = (1f - normalizedLuma).pow(2)
                val highlightMask = normalizedLuma.pow(2)
                val toneAdjustment = (shadowMask * shadowAmount - highlightMask * highlightAmount) * 72f
                r += toneAdjustment
                g += toneAdjustment
                b += toneAdjustment
                r = (r - 128f) * contrast + 128f + warmth * 24f + tint * 12f
                g = (g - 128f) * contrast + 128f - tint * 18f
                b = (b - 128f) * contrast + 128f - warmth * 24f + tint * 12f
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val chroma = (maxOf(r, g, b) - minOf(r, g, b)).coerceIn(0f, 255f) / 255f
                val vibranceBoost = 1f + vibrance * (1f - chroma)
                val colorBoost = saturation * vibranceBoost
                r = luma + (r - luma) * colorBoost
                g = luma + (g - luma) * colorBoost
                b = luma + (b - luma) * colorBoost
                pixels[i] = (a shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or
                    (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
            }
            output.setPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
            output
        }
        edited = result
        processing = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF171717),
        title = { Text("Photo Editor", color = Color.White) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 570.dp).verticalScroll(rememberScrollState())) {
                val preview = if (showOriginal) source else edited
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showOriginal = !showOriginal }, enabled = source != null && edited != null, modifier = Modifier.weight(1f)) {
                        Text(if (showOriginal) "Show Edited" else "Before / After")
                    }
                    OutlinedButton(onClick = {
                        exposure = 0f
                        contrast = 1f
                        saturation = 1f
                        warmth = 0f
                        tint = 0f
                        vibrance = 0f
                        highlights = 0f
                        shadows = 0f
                        showOriginal = false
                    }, modifier = Modifier.weight(1f)) { Text("Reset") }
                }
                if (preview != null) Image(preview.asImageBitmap(), if (showOriginal) "Original photo preview" else "Edited photo preview", Modifier.fillMaxWidth().heightIn(max = 300.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                else Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                if (processing) Text("Processing…", color = Color.LightGray)
                EditorSlider("Exposure", exposure, -2f..2f, { exposure = it }, "%.1f EV".format(exposure))
                EditorSlider("Contrast", contrast, 0f..2f, { contrast = it }, (contrast * 100).roundToInt().toString() + "%")
                EditorSlider("Saturation", saturation, 0f..2f, { saturation = it }, (saturation * 100).roundToInt().toString() + "%")
                EditorSlider("Highlights", highlights, -1f..1f, { highlights = it }, if (highlights >= 0) "−" + (highlights * 100).roundToInt() + "%" else "+" + (-highlights * 100).roundToInt() + "%")
                EditorSlider("Shadows", shadows, -1f..1f, { shadows = it }, if (shadows >= 0) "+" + (shadows * 100).roundToInt() + "%" else "−" + (-shadows * 100).roundToInt() + "%")
                EditorSlider("Warmth", warmth, -1f..1f, { warmth = it }, if (warmth >= 0) "Warm +" + (warmth * 100).roundToInt() else "Cool " + (warmth * 100).roundToInt())
                EditorSlider("Tint", tint, -1f..1f, { tint = it }, if (tint >= 0) "Magenta +" + (tint * 100).roundToInt() else "Green " + (tint * 100).roundToInt())
                EditorSlider("Vibrance", vibrance, -1f..1f, { vibrance = it }, if (vibrance >= 0) "+" + (vibrance * 100).roundToInt() + "%" else (vibrance * 100).roundToInt().toString() + "%")
                Text("Original photo remains unchanged. Edited copy is saved separately.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(enabled = edited != null && !processing && !saving && !heifSaving && !showOriginal, onClick = {
                    val bitmap = edited ?: return@Button
                    saving = true
                    saveEditedPhoto(context, bitmap) { success ->
                        saving = false
                        Toast.makeText(context, if (success) "Edited JPEG saved" else "Could not save JPEG", Toast.LENGTH_SHORT).show()
                        if (success) onDismiss()
                    }
                }) { Text(if (saving) "Saving…" else "Save JPEG") }
                OutlinedButton(enabled = edited != null && !processing && !saving && !heifSaving && !showOriginal && Build.VERSION.SDK_INT >= 28, onClick = {
                    val bitmap = edited ?: return@OutlinedButton
                    heifSaving = true
                    saveEditedHeif(context, bitmap) { success, message ->
                        heifSaving = false
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        if (success) onDismiss()
                    }
                }) { Text(if (heifSaving) "Encoding…" else "Save HEIC") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color.White) } }
    )
}

@Composable
private fun EditorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, valueLabel: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White)
            Text(valueLabel, color = Color.LightGray)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

private fun saveEditedPhoto(context: Context, bitmap: Bitmap, onComplete: (Boolean) -> Unit) {
    Thread {
        val name = "Luma_Edit_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Luma Pro Camera")
        }
        val success = runCatching {
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("No output URI")
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) error("JPEG encode failed")
            } ?: error("No output stream")
            true
        }.getOrDefault(false)
        android.os.Handler(android.os.Looper.getMainLooper()).post { onComplete(success) }
    }.start()
}


private fun saveEditedHeif(context: Context, bitmap: Bitmap, onComplete: (Boolean, String) -> Unit) {
    if (Build.VERSION.SDK_INT < 28) {
        onComplete(false, "HEIC export requires Android 9 or newer")
        return
    }
    Thread {
        val name = "Luma_Edit_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".heic"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/heic")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Luma Pro Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        var outputUri: Uri? = null
        val result = runCatching {
            outputUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create HEIC file")
            context.contentResolver.openFileDescriptor(outputUri!!, "w")?.use { descriptor ->
                val writer = HeifWriter.Builder(
                    descriptor.fileDescriptor,
                    bitmap.width,
                    bitmap.height,
                    HeifWriter.INPUT_MODE_BITMAP
                ).setQuality(90).build()
                try {
                    writer.start()
                    writer.addBitmap(bitmap)
                    writer.stop(10_000_000L)
                } finally {
                    writer.close()
                }
            } ?: error("Could not open HEIC output")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                context.contentResolver.update(outputUri!!, ready, null, null)
            }
            true
        }
        if (result.isFailure && outputUri != null) {
            runCatching { context.contentResolver.delete(outputUri!!, null, null) }
        }
        val success = result.isSuccess
        val message = if (success) "HEIC copy saved" else "HEIC encoder unavailable or export failed on this device"
        android.os.Handler(android.os.Looper.getMainLooper()).post { onComplete(success, message) }
    }.start()
}
