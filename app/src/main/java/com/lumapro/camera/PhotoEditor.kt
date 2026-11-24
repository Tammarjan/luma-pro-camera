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
import kotlinx.coroutines.launch
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
    var editBase by remember(photoUri) { mutableStateOf<Bitmap?>(null) }
    var edited by remember(photoUri) { mutableStateOf<Bitmap?>(null) }
    var exposure by remember(photoUri) { mutableFloatStateOf(0.08f) }
    var contrast by remember(photoUri) { mutableFloatStateOf(1.06f) }
    var saturation by remember(photoUri) { mutableFloatStateOf(1.05f) }
    var warmth by remember(photoUri) { mutableFloatStateOf(0.02f) }
    var tint by remember(photoUri) { mutableFloatStateOf(0f) }
    var vibrance by remember(photoUri) { mutableFloatStateOf(0.10f) }
    var highlights by remember(photoUri) { mutableFloatStateOf(0.12f) }
    var shadows by remember(photoUri) { mutableFloatStateOf(0.12f) }
    var whites by remember(photoUri) { mutableFloatStateOf(0.02f) }
    var blacks by remember(photoUri) { mutableFloatStateOf(-0.03f) }
    var clarity by remember(photoUri) { mutableFloatStateOf(0.07f) }
    var texture by remember(photoUri) { mutableFloatStateOf(0.10f) }
    var sharpening by remember(photoUri) { mutableFloatStateOf(0.14f) }
    var noiseReduction by remember(photoUri) { mutableFloatStateOf(0.10f) }
    var vignette by remember(photoUri) { mutableFloatStateOf(0f) }
    var processing by remember(photoUri) { mutableStateOf(false) }
    var saving by remember(photoUri) { mutableStateOf(false) }
    var showOriginal by remember(photoUri) { mutableStateOf(false) }
    var selectedEditorLook by remember(photoUri) { mutableStateOf("Luma Auto") }
    var editorSection by remember(photoUri) { mutableStateOf(0) }
    var loadFailed by remember(photoUri) { mutableStateOf(false) }
    var aiModelReady by remember(photoUri) { mutableStateOf(false) }
    var aiDownloading by remember(photoUri) { mutableStateOf(false) }
    var aiEnhancing by remember(photoUri) { mutableStateOf(false) }
    var aiProgress by remember(photoUri) { mutableIntStateOf(0) }
    var aiModelMessage by remember(photoUri) { mutableStateOf("AI model not installed") }
    val editorScope = rememberCoroutineScope()

    LaunchedEffect(photoUri) {
        aiModelReady = withContext(Dispatchers.IO) { AiModelManager.installedModel(context) != null }
        aiModelMessage = if (aiModelReady) "Verified AI model ready for offline use" else "Download the AI model once; it will be stored on this device"
    }

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
        editBase = loaded
        edited = loaded
        loadFailed = loaded == null
    }

    LaunchedEffect(editBase, exposure, contrast, saturation, warmth, tint, vibrance, highlights, shadows, whites, blacks, clarity, texture, sharpening, noiseReduction, vignette) {
        val original = editBase ?: return@LaunchedEffect
        // Debounce slider drags so a full image render starts only after the user pauses.
        delay(220)

        val unchanged = exposure == 0f && contrast == 1f && saturation == 1f &&
            warmth == 0f && tint == 0f && vibrance == 0f && highlights == 0f &&
            shadows == 0f && whites == 0f && blacks == 0f && clarity == 0f &&
            texture == 0f && sharpening == 0f && noiseReduction == 0f && vignette == 0f
        if (unchanged) {
            edited = original
            processing = false
            return@LaunchedEffect
        }

        processing = true
        try {
            val result = withContext(Dispatchers.Default) {
                val width = original.width
                val height = original.height
                val count = width * height
                val input = IntArray(count)
                original.getPixels(input, 0, width, 0, 0, width, height)
                val outputPixels = IntArray(count)
                val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val gain = 2.0.pow(exposure.toDouble()).toFloat()
                val denoiseMix = noiseReduction.coerceIn(0f, 1f) * 0.65f
                for (y in 0 until height) {
                    currentCoroutineContext().ensureActive()
                    val row = y * width
                    val upRow = (y - 1).coerceAtLeast(0) * width
                    val downRow = (y + 1).coerceAtMost(height - 1) * width
                    for (x in 0 until width) {
                        val i = row + x
                        val pixel = input[i]
                        val left = input[row + (x - 1).coerceAtLeast(0)]
                        val right = input[row + (x + 1).coerceAtMost(width - 1)]
                        val up = input[upRow + x]
                        val down = input[downRow + x]
                        val a = (pixel ushr 24) and 255
                        val centerR = ((pixel shr 16) and 255).toFloat()
                        val centerG = ((pixel shr 8) and 255).toFloat()
                        val centerB = (pixel and 255).toFloat()
                        val avgR = (((left shr 16) and 255) + ((right shr 16) and 255) + ((up shr 16) and 255) + ((down shr 16) and 255)) * 0.25f
                        val avgG = (((left shr 8) and 255) + ((right shr 8) and 255) + ((up shr 8) and 255) + ((down shr 8) and 255)) * 0.25f
                        val avgB = ((left and 255) + (right and 255) + (up and 255) + (down and 255)) * 0.25f

                        var r = centerR * (1f - denoiseMix) + avgR * denoiseMix
                        var g = centerG * (1f - denoiseMix) + avgG * denoiseMix
                        var b = centerB * (1f - denoiseMix) + avgB * denoiseMix
                        val detailR = centerR - avgR
                        val detailG = centerG - avgG
                        val detailB = centerB - avgB
                        val textureAmount = texture.coerceIn(-1f, 1f) * 0.45f
                        val clarityAmount = clarity.coerceIn(-1f, 1f) * 0.35f
                        r += detailR * (sharpening * 0.9f + textureAmount + clarityAmount)
                        g += detailG * (sharpening * 0.9f + textureAmount + clarityAmount)
                        b += detailB * (sharpening * 0.9f + textureAmount + clarityAmount)

                        r *= gain
                        g *= gain
                        b *= gain
                        val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 255f) / 255f
                        val shadowMask = 1f - luma
                        val highlightMask = luma
                        val tone = (shadowMask * shadows - highlightMask * highlights) * 72f
                        val whiteBlack = (highlightMask * whites + shadowMask * blacks) * 48f
                        r += tone + whiteBlack
                        g += tone + whiteBlack
                        b += tone + whiteBlack

                        r = (r - 128f) * contrast + 128f + warmth * 24f + tint * 12f
                        g = (g - 128f) * contrast + 128f - tint * 18f
                        b = (b - 128f) * contrast + 128f - warmth * 24f + tint * 12f
                        val finalLuma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                        val chroma = (maxOf(r, g, b) - minOf(r, g, b)).coerceIn(0f, 255f) / 255f
                        val colorBoost = saturation * (1f + vibrance * (1f - chroma))
                        r = finalLuma + (r - finalLuma) * colorBoost
                        g = finalLuma + (g - finalLuma) * colorBoost
                        b = finalLuma + (b - finalLuma) * colorBoost
                        val nx = (x - (width - 1) * 0.5f) / (width * 0.5f).coerceAtLeast(1f)
                        val ny = (y - (height - 1) * 0.5f) / (height * 0.5f).coerceAtLeast(1f)
                        val vignetteFactor = (1f - vignette * 0.72f * (nx * nx + ny * ny)).coerceAtLeast(0.1f)
                        r *= vignetteFactor
                        g *= vignetteFactor
                        b *= vignetteFactor
                        outputPixels[i] = (a shl 24) or
                            (r.roundToInt().coerceIn(0, 255) shl 16) or
                            (g.roundToInt().coerceIn(0, 255) shl 8) or
                            b.roundToInt().coerceIn(0, 255)
                    }
                }
                output.setPixels(outputPixels, 0, width, 0, 0, width, height)
                output
            }
            edited = result
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: OutOfMemoryError) {
            aiModelMessage = "Not enough memory to process this photo. Close other apps and retry."
        } catch (error: Exception) {
            aiModelMessage = "Photo processing failed: " + (error.message ?: "please retry")
        } finally {
            processing = false
        }
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
                        editBase = source
                        exposure = 0.08f
                        contrast = 1.06f
                        saturation = 1.05f
                        warmth = 0.02f
                        tint = 0f
                        vibrance = 0.10f
                        highlights = 0.12f
                        shadows = 0.12f
                        whites = 0.02f
                        blacks = -0.03f
                        clarity = 0.07f
                        texture = 0.10f
                        sharpening = 0.14f
                        noiseReduction = 0.10f
                        vignette = 0f
                        selectedEditorLook = "Luma Auto"
                        showOriginal = false
                    }, modifier = Modifier.weight(1f)) { Text("Reset") }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF252525)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("AI Enhancement Model", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text(if (aiModelReady) "VERIFIED" else "NOT INSTALLED", color = if (aiModelReady) Color(0xFF80CBC4) else Color(0xFFFFCC80), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(aiModelMessage, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        if (aiDownloading || aiEnhancing) {
                            LinearProgressIndicator(progress = { aiProgress / 100f }, modifier = Modifier.fillMaxWidth())
                            Text(if (aiEnhancing) "Enhancing photo…" else "$aiProgress%", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            enabled = !aiDownloading && !aiEnhancing,
                            onClick = {
                                editorScope.launch {
                                    aiDownloading = true
                                    aiProgress = 0
                                    aiModelMessage = "Downloading and verifying model…"
                                    try {
                                        withContext(Dispatchers.IO) {
                                            AiModelManager.downloadAndVerify(context) { value ->
                                                android.os.Handler(android.os.Looper.getMainLooper()).post { aiProgress = value }
                                            }
                                        }
                                        aiModelReady = true
                                        aiModelMessage = "Model verified and saved for offline use"
                                    } catch (error: Exception) {
                                        aiModelReady = false
                                        aiModelMessage = error.message?.take(120) ?: "Model download failed"
                                    } finally {
                                        aiDownloading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (aiDownloading) "Installing AI Model…" else if (aiModelReady) "Verify / Reinstall AI Model" else "Download AI Model")
                        }
                        OutlinedButton(
                            enabled = aiModelReady && !aiDownloading && !aiEnhancing && !processing && (edited != null || source != null),
                            onClick = {
                                val inputBitmap = if (showOriginal) source else (edited ?: source)
                                if (inputBitmap == null) return@OutlinedButton
                                editorScope.launch {
                                    aiEnhancing = true
                                    aiProgress = 0
                                    aiModelMessage = "Running Real-ESRGAN 4× enhancement on this device…"
                                    try {
                                        val enhanced = withContext(Dispatchers.Default) {
                                            RealEsrganEnhancer.enhance(context, inputBitmap) { value ->
                                                android.os.Handler(android.os.Looper.getMainLooper()).post { aiProgress = value }
                                            }
                                        }
                                        editBase = enhanced
                                        edited = enhanced
                                        showOriginal = false
                                        aiModelMessage = "AI enhancement complete · ${enhanced.width} × ${enhanced.height}"
                                    } catch (error: OutOfMemoryError) {
                                        aiModelMessage = "Not enough free memory for AI enhancement. Close other apps and try a smaller photo."
                                    } catch (error: Exception) {
                                        aiModelMessage = error.message?.take(140) ?: "AI enhancement failed"
                                    } finally {
                                        aiEnhancing = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (aiEnhancing) "AI Enhancing…" else "Enhance Photo · 4×")
                        }
                        Text("Processes a maximum 768 px input edge to limit phone memory use.", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    }
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
                    FilterChip(selected = selectedEditorLook == "Luma Auto", onClick = {
                        selectedEditorLook = "Luma Auto"
                        exposure = 0.08f; contrast = 1.06f; saturation = 1.05f; warmth = 0.02f; tint = 0f
                        vibrance = 0.10f; highlights = 0.12f; shadows = 0.12f; whites = 0.02f; blacks = -0.03f
                        clarity = 0.07f; texture = 0.10f; sharpening = 0.14f; noiseReduction = 0.10f; vignette = 0f
                    }, label = { Text("Luma Auto") })
                    FilterChip(selected = selectedEditorLook == "Natural", onClick = { selectedEditorLook = "Natural"; exposure = 0f; contrast = 1f; saturation = 1f; warmth = 0f; tint = 0f; vibrance = 0f; highlights = 0f; shadows = 0f; whites = 0f; blacks = 0f; clarity = 0f; texture = 0f; sharpening = 0f; noiseReduction = 0f; vignette = 0f }, label = { Text("Natural") })
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
                Button(enabled = edited != null && !processing && !saving && !aiEnhancing && !showOriginal, onClick = {
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


private fun saveEditedPhoto(
    context: Context,
    bitmap: Bitmap,
    onComplete: (Boolean) -> Unit
) {
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
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)) error("JPEG encoder unavailable")
                stream.flush()
            } ?: error("Could not open output stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val complete = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, complete, null, null)
            }
            true
        }.getOrElse {
            outputUri?.let { context.contentResolver.delete(it, null, null) }
            false
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post { onComplete(success) }
    }.start()
}
