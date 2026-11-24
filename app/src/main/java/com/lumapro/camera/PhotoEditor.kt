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
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    var whites by remember(photoUri) { mutableFloatStateOf(0f) }
    var blacks by remember(photoUri) { mutableFloatStateOf(0f) }
    var clarity by remember(photoUri) { mutableFloatStateOf(0f) }
    var texture by remember(photoUri) { mutableFloatStateOf(0f) }
    var sharpening by remember(photoUri) { mutableFloatStateOf(0f) }
    var noiseReduction by remember(photoUri) { mutableFloatStateOf(0f) }
    var vignette by remember(photoUri) { mutableFloatStateOf(0f) }
    var processing by remember(photoUri) { mutableStateOf(false) }
    var saving by remember(photoUri) { mutableStateOf(false) }
    var showOriginal by remember(photoUri) { mutableStateOf(false) }
    var selectedEditorLook by remember(photoUri) { mutableStateOf("Natural") }
    var editorSection by remember(photoUri) { mutableStateOf(0) }
    var loadFailed by remember(photoUri) { mutableStateOf(false) }

    LaunchedEffect(photoUri) {
        loadFailed = false
        val loaded = runCatching {
            withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(photoUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 3000 || bounds.outHeight / sample > 3000) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1); inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val decoded = context.contentResolver.openInputStream(photoUri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: error("Could not decode image")
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
        }.getOrNull()
        source = loaded
        edited = loaded
        loadFailed = loaded == null
    }

    LaunchedEffect(source, exposure, contrast, saturation, warmth, tint, vibrance, highlights, shadows, whites, blacks, clarity, texture, sharpening, noiseReduction, vignette) {
        val original = source ?: return@LaunchedEffect
        delay(100)
        processing = true
        val result = withContext(Dispatchers.Default) {
            val output = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val sourcePixels = IntArray(original.width * original.height)
            original.getPixels(sourcePixels, 0, original.width, 0, 0, original.width, original.height)
            val outputPixels = IntArray(sourcePixels.size)
            val gain = 2.0.pow(exposure.toDouble()).toFloat()
            val highlightAmount = highlights
            val shadowAmount = shadows
            val width = original.width
            val height = original.height
            fun pixelLuma(pixel: Int): Float = 0.2126f * ((pixel shr 16) and 0xFF) + 0.7152f * ((pixel shr 8) and 0xFF) + 0.0722f * (pixel and 0xFF)
            for (i in sourcePixels.indices) {
                if (i % width == 0) currentCoroutineContext().ensureActive()
                val color = sourcePixels[i]
                val x = i % width
                val y = i / width
                val a = (color ushr 24) and 0xFF
                var r = ((color shr 16) and 0xFF).toFloat() * gain
                var g = ((color shr 8) and 0xFF).toFloat() * gain
                var b = (color and 0xFF).toFloat() * gain
                val leftPixel = sourcePixels[y * width + (x - 1).coerceAtLeast(0)]
                val rightPixel = sourcePixels[y * width + (x + 1).coerceAtMost(width - 1)]
                val topPixel = sourcePixels[(y - 1).coerceAtLeast(0) * width + x]
                val bottomPixel = sourcePixels[(y + 1).coerceAtMost(height - 1) * width + x]
                val neighborR = (((leftPixel shr 16) and 255) + ((rightPixel shr 16) and 255) + ((topPixel shr 16) and 255) + ((bottomPixel shr 16) and 255)) * 0.25f
                val neighborG = (((leftPixel shr 8) and 255) + ((rightPixel shr 8) and 255) + ((topPixel shr 8) and 255) + ((bottomPixel shr 8) and 255)) * 0.25f
                val neighborB = ((leftPixel and 255) + (rightPixel and 255) + (topPixel and 255) + (bottomPixel and 255)) * 0.25f
                val noiseMix = noiseReduction * 0.65f
                val detailR = r - neighborR * gain
                val detailG = g - neighborG * gain
                val detailB = b - neighborB * gain
                r = r * (1f - noiseMix) + neighborR * gain * noiseMix + detailR * sharpening * 0.9f
                g = g * (1f - noiseMix) + neighborG * gain * noiseMix + detailG * sharpening * 0.9f
                b = b * (1f - noiseMix) + neighborB * gain * noiseMix + detailB * sharpening * 0.9f
                val initialLuma = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 255f)
                val normalizedLuma = initialLuma / 255f
                val shadowMask = (1f - normalizedLuma).pow(2)
                val highlightMask = normalizedLuma.pow(2)
                val toneAdjustment = (shadowMask * shadowAmount - highlightMask * highlightAmount) * 72f
                val whiteMask = normalizedLuma.pow(1.5f)
                val blackMask = (1f - normalizedLuma).pow(1.5f)
                val whiteBlackAdjustment = (whiteMask * whites + blackMask * blacks) * 68f
                fun sampleLuma(sx: Int, sy: Int): Float = pixelLuma(sourcePixels[sy.coerceIn(0, height - 1) * width + sx.coerceIn(0, width - 1)])
                val textureAverage = (sampleLuma(x - 2, y) + sampleLuma(x + 2, y) + sampleLuma(x, y - 2) + sampleLuma(x, y + 2)) * 0.25f
                val clarityAverage = (sampleLuma(x - 7, y) + sampleLuma(x + 7, y) + sampleLuma(x, y - 7) + sampleLuma(x, y + 7)) * 0.25f
                val textureDetail = (initialLuma - textureAverage) * texture * 0.45f
                val clarityDetail = (initialLuma - clarityAverage) * clarity * 0.35f
                val detailAdjustment = textureDetail + clarityDetail
                r += toneAdjustment + whiteBlackAdjustment + detailAdjustment
                g += toneAdjustment + whiteBlackAdjustment + detailAdjustment
                b += toneAdjustment + whiteBlackAdjustment + detailAdjustment
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
                val nx = (x - (width - 1) * 0.5f) / (width * 0.5f).coerceAtLeast(1f)
                val ny = (y - (height - 1) * 0.5f) / (height * 0.5f).coerceAtLeast(1f)
                val radius = (nx * nx + ny * ny).coerceAtMost(1f)
                val vignetteFactor = (1f - vignette * 0.72f * radius * radius).coerceAtLeast(0.1f)
                r *= vignetteFactor
                g *= vignetteFactor
                b *= vignetteFactor
                outputPixels[i] = (a shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or
                    (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
            }
            output.setPixels(outputPixels, 0, original.width, 0, 0, original.width, original.height)
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
                        whites = 0f
                        blacks = 0f
                        clarity = 0f
                        texture = 0f
                        sharpening = 0f
                        noiseReduction = 0f
                        vignette = 0f
                        selectedEditorLook = "Natural"
                        showOriginal = false
                    }, modifier = Modifier.weight(1f)) { Text("Reset") }
                }
                if (preview != null) {
                    Image(preview.asImageBitmap(), if (showOriginal) "Original photo preview" else "Edited photo preview", Modifier.fillMaxWidth().heightIn(max = 300.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                } else {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        if (loadFailed) Text("Could not load this photo. It may have been moved or deleted.", color = Color(0xFFFF8A80))
                        else CircularProgressIndicator()
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selectedEditorLook == "Natural", onClick = { selectedEditorLook = "Natural"; exposure = 0f; contrast = 1f; saturation = 1f; warmth = 0f; tint = 0f; vibrance = 0f }, label = { Text("Natural") })
                    FilterChip(selected = selectedEditorLook == "Vivid", onClick = { selectedEditorLook = "Vivid"; exposure = 0f; contrast = 1.12f; saturation = 1.28f; warmth = 0f; tint = 0f; vibrance = 0.2f }, label = { Text("Vivid") })
                    FilterChip(selected = selectedEditorLook == "Warm", onClick = { selectedEditorLook = "Warm"; exposure = 0f; contrast = 1.05f; saturation = 1.08f; warmth = 0.4f; tint = 0.03f; vibrance = 0.05f }, label = { Text("Warm") })
                    FilterChip(selected = selectedEditorLook == "Cool", onClick = { selectedEditorLook = "Cool"; exposure = 0f; contrast = 1.05f; saturation = 1.08f; warmth = -0.4f; tint = 0f; vibrance = 0.05f }, label = { Text("Cool") })
                }
                if (processing) Text("Processing…", color = Color.LightGray)
                TabRow(selectedTabIndex = editorSection, containerColor = Color(0xFF171717), contentColor = Color(0xFFFFC107)) {
                    listOf("LIGHT", "COLOR", "DETAIL", "EFFECTS").forEachIndexed { index, label ->
                        Tab(selected = editorSection == index, onClick = { editorSection = index }, text = { Text(label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                when (editorSection) {
                    0 -> {
                        EditorSlider("Exposure", exposure, -2f..2f, { exposure = it; selectedEditorLook = "Custom" }, "%.1f EV".format(exposure))
                        EditorSlider("Contrast", contrast, 0f..2f, { contrast = it; selectedEditorLook = "Custom" }, (contrast * 100).roundToInt().toString() + "%")
                        EditorSlider("Highlights", highlights, -1f..1f, { highlights = it; selectedEditorLook = "Custom" }, if (highlights >= 0) "−" + (highlights * 100).roundToInt() + "%" else "+" + (-highlights * 100).roundToInt() + "%")
                        EditorSlider("Shadows", shadows, -1f..1f, { shadows = it; selectedEditorLook = "Custom" }, if (shadows >= 0) "+" + (shadows * 100).roundToInt() + "%" else "−" + (-shadows * 100).roundToInt() + "%")
                        EditorSlider("Whites", whites, -1f..1f, { whites = it; selectedEditorLook = "Custom" }, if (whites >= 0) "+" + (whites * 100).roundToInt() + "%" else "−" + (-whites * 100).roundToInt() + "%")
                        EditorSlider("Blacks", blacks, -1f..1f, { blacks = it; selectedEditorLook = "Custom" }, if (blacks >= 0) "+" + (blacks * 100).roundToInt() + "%" else "−" + (-blacks * 100).roundToInt() + "%")
                    }
                    1 -> {
                        EditorSlider("Saturation", saturation, 0f..2f, { saturation = it; selectedEditorLook = "Custom" }, (saturation * 100).roundToInt().toString() + "%")
                        EditorSlider("Vibrance", vibrance, -1f..1f, { vibrance = it; selectedEditorLook = "Custom" }, if (vibrance >= 0) "+" + (vibrance * 100).roundToInt() + "%" else (vibrance * 100).roundToInt().toString() + "%")
                        EditorSlider("Warmth", warmth, -1f..1f, { warmth = it; selectedEditorLook = "Custom" }, if (warmth >= 0) "Warm +" + (warmth * 100).roundToInt() else "Cool " + (warmth * 100).roundToInt())
                        EditorSlider("Tint", tint, -1f..1f, { tint = it; selectedEditorLook = "Custom" }, if (tint >= 0) "Magenta +" + (tint * 100).roundToInt() else "Green " + (tint * 100).roundToInt())
                    }
                    2 -> {
                        EditorSlider("Texture", texture, -1f..1f, { texture = it; selectedEditorLook = "Custom" }, if (texture >= 0) "+" + (texture * 100).roundToInt() + "%" else "−" + (-texture * 100).roundToInt() + "%")
                        EditorSlider("Clarity", clarity, -1f..1f, { clarity = it; selectedEditorLook = "Custom" }, if (clarity >= 0) "+" + (clarity * 100).roundToInt() + "%" else "−" + (-clarity * 100).roundToInt() + "%")
                        EditorSlider("Sharpening", sharpening, 0f..1f, { sharpening = it; selectedEditorLook = "Custom" }, (sharpening * 100).roundToInt().toString() + "%")
                        EditorSlider("Noise Reduction", noiseReduction, 0f..1f, { noiseReduction = it; selectedEditorLook = "Custom" }, (noiseReduction * 100).roundToInt().toString() + "%")
                    }
                    else -> {
                        EditorSlider("Vignette", vignette, 0f..1f, { vignette = it; selectedEditorLook = "Custom" }, (vignette * 100).roundToInt().toString() + "%")
                    }
                }
                Text("Original photo remains unchanged. Edited copy is saved separately.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(enabled = edited != null && !processing && !saving && !showOriginal, onClick = {
                    val bitmap = edited ?: return@Button
                    saving = true
                    saveEditedPhoto(context, bitmap) { success ->
                        saving = false
                        Toast.makeText(context, if (success) "Edited JPEG saved" else "Could not save JPEG", Toast.LENGTH_SHORT).show()
                        if (success) onDismiss()
                    }
                }) { Text(if (saving) "Saving…" else "Save JPEG") }

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Luma Pro Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        var outputUri: Uri? = null
        val success = runCatching {
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("No output URI")
            outputUri = uri
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream)) error("JPEG encode failed")
                stream.flush()
            } ?: error("No output stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publish = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                if (context.contentResolver.update(uri, publish, null, null) <= 0) error("Could not publish JPEG")
            }
            true
        }.getOrDefault(false)
        if (!success) outputUri?.let { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
        android.os.Handler(android.os.Looper.getMainLooper()).post { onComplete(success) }
    }.start()
}

