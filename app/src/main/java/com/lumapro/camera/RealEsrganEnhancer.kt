package com.lumapro.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Offline tiled Real-ESRGAN General x4v3 inference.
 * The source is limited to 768 px on its longest edge to keep mobile memory use bounded.
 */
internal object RealEsrganEnhancer {
    private const val MAX_INPUT_EDGE = 768
    private const val TILE_SIZE = 128
    private const val TILE_OVERLAP = 16
    private const val SCALE = 4

    private fun sampleChannel(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float,
        shift: Int
    ): Float {
        val x0 = x.toInt().coerceIn(0, width - 1)
        val y0 = y.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = (x - x0).coerceIn(0f, 1f)
        val fy = (y - y0).coerceIn(0f, 1f)
        val p00 = ((pixels[y0 * width + x0] shr shift) and 255).toFloat()
        val p10 = ((pixels[y0 * width + x1] shr shift) and 255).toFloat()
        val p01 = ((pixels[y1 * width + x0] shr shift) and 255).toFloat()
        val p11 = ((pixels[y1 * width + x1] shr shift) and 255).toFloat()
        val top = p00 + (p10 - p00) * fx
        val bottom = p01 + (p11 - p01) * fx
        return top + (bottom - top) * fy
    }

    fun enhance(
        context: Context,
        source: Bitmap,
        onProgress: (Int) -> Unit = {}
    ): Bitmap {
        val model = AiModelManager.installedModel(context)
            ?: error("AI model is not installed or failed its integrity check")

        val scaled = if (maxOf(source.width, source.height) > MAX_INPUT_EDGE) {
            val ratio = MAX_INPUT_EDGE.toFloat() / maxOf(source.width, source.height)
            Bitmap.createScaledBitmap(
                source,
                (source.width * ratio).roundToInt().coerceAtLeast(1),
                (source.height * ratio).roundToInt().coerceAtLeast(1),
                true
            )
        } else source
        val ownsScaled = scaled !== source

        try {
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions()
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            options.use { sessionOptions ->
                env.createSession(model.absolutePath, sessionOptions).use { session ->
                    val inputName = session.inputNames.firstOrNull()
                        ?: error("AI model has no input tensor")
                    val outputName = session.outputNames.firstOrNull()
                        ?: error("AI model has no output tensor")

                    val outputBitmap = Bitmap.createBitmap(
                        scaled.width * SCALE,
                        scaled.height * SCALE,
                        Bitmap.Config.ARGB_8888
                    )
                    val tilesAcross = (scaled.width + TILE_SIZE - 1) / TILE_SIZE
                    val tilesDown = (scaled.height + TILE_SIZE - 1) / TILE_SIZE
                    val totalTiles = tilesAcross * tilesDown
                    var completedTiles = 0

                    try {
                        var tileY = 0
                        while (tileY < scaled.height) {
                            var tileX = 0
                            val centerBottom = minOf(tileY + TILE_SIZE, scaled.height)
                            val inputTop = maxOf(0, tileY - TILE_OVERLAP)
                            val inputBottom = minOf(centerBottom + TILE_OVERLAP, scaled.height)
                            val centerHeight = centerBottom - tileY
                            val inputHeight = inputBottom - inputTop

                            while (tileX < scaled.width) {
                                val centerRight = minOf(tileX + TILE_SIZE, scaled.width)
                                val inputLeft = maxOf(0, tileX - TILE_OVERLAP)
                                val inputRight = minOf(centerRight + TILE_OVERLAP, scaled.width)
                                val centerWidth = centerRight - tileX
                                val inputWidth = inputRight - inputLeft
                                val pixelCount = inputWidth * inputHeight
                                val pixels = IntArray(pixelCount)
                                scaled.getPixels(pixels, 0, inputWidth, inputLeft, inputTop, inputWidth, inputHeight)

                                val inputData = FloatArray(3 * pixelCount)
                                for (i in pixels.indices) {
                                    val pixel = pixels[i]
                                    inputData[i] = Color.red(pixel) / 255f
                                    inputData[pixelCount + i] = Color.green(pixel) / 255f
                                    inputData[2 * pixelCount + i] = Color.blue(pixel) / 255f
                                }

                                val inputBuffer = ByteBuffer.allocateDirect(inputData.size * 4)
                                    .order(ByteOrder.nativeOrder())
                                    .asFloatBuffer()
                                inputBuffer.put(inputData)
                                inputBuffer.rewind()
                                val tensor = OnnxTensor.createTensor(
                                    env,
                                    inputBuffer,
                                    longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
                                )
                                tensor.use { inputTensor ->
                                    session.run(mapOf(inputName to inputTensor)).use { result ->
                                        val outputTensor = result.get(0) as? OnnxTensor
                                            ?: error("AI model returned an unsupported output")
                                        val shape = outputTensor.info.shape
                                        check(shape.size == 4 && shape[0] == 1L && shape[1] == 3L) {
                                            "Unexpected AI output tensor shape"
                                        }
                                        val outputHeight = shape[2].toInt()
                                        val outputWidth = shape[3].toInt()
                                        check(outputWidth == inputWidth * SCALE && outputHeight == inputHeight * SCALE) {
                                            "AI model did not return the expected 4x output"
                                        }
                                        val output = outputTensor.floatBuffer ?: error("AI model output is not float32")
                                        val planeSize = outputWidth * outputHeight
                                        val cropLeft = (tileX - inputLeft) * SCALE
                                        val cropTop = (tileY - inputTop) * SCALE
                                        val croppedWidth = centerWidth * SCALE
                                        val croppedHeight = centerHeight * SCALE
                                        val croppedPixels = IntArray(croppedWidth * croppedHeight)
                                        for (y in 0 until croppedHeight) {
                                            for (x in 0 until croppedWidth) {
                                                val sourceIndex = (cropTop + y) * outputWidth + cropLeft + x
                                                // Restore a restrained amount of real source micro-texture after AI
                                                // upscaling. This counters the waxy/smoothed look without replacing
                                                // the model's reconstructed edges or inventing extra sharpening.
                                                val sx = (cropLeft + x).toFloat() / SCALE
                                                val sy = (cropTop + y).toFloat() / SCALE
                                                val aiR = output.get(sourceIndex).coerceIn(0f, 1f) * 255f
                                                val aiG = output.get(planeSize + sourceIndex).coerceIn(0f, 1f) * 255f
                                                val aiB = output.get(2 * planeSize + sourceIndex).coerceIn(0f, 1f) * 255f
                                                fun detail(shift: Int): Float {
                                                    val center = sampleChannel(pixels, inputWidth, inputHeight, sx, sy, shift)
                                                    val neighbors = (
                                                        sampleChannel(pixels, inputWidth, inputHeight, sx - 0.8f, sy, shift) +
                                                        sampleChannel(pixels, inputWidth, inputHeight, sx + 0.8f, sy, shift) +
                                                        sampleChannel(pixels, inputWidth, inputHeight, sx, sy - 0.8f, shift) +
                                                        sampleChannel(pixels, inputWidth, inputHeight, sx, sy + 0.8f, shift)
                                                    ) * 0.25f
                                                    return center - neighbors
                                                }
                                                val red = (aiR + detail(16) * 0.32f).roundToInt().coerceIn(0, 255)
                                                val green = (aiG + detail(8) * 0.32f).roundToInt().coerceIn(0, 255)
                                                val blue = (aiB + detail(0) * 0.32f).roundToInt().coerceIn(0, 255)
                                                croppedPixels[y * croppedWidth + x] = Color.rgb(red, green, blue)
                                            }
                                        }
                                        outputBitmap.setPixels(
                                            croppedPixels,
                                            0,
                                            croppedWidth,
                                            tileX * SCALE,
                                            tileY * SCALE,
                                            croppedWidth,
                                            croppedHeight
                                        )
                                    }
                                }

                                completedTiles++
                                onProgress((completedTiles * 100 / totalTiles).coerceIn(0, 100))
                                tileX = centerRight
                            }
                            tileY = centerBottom
                        }
                        return outputBitmap
                    } catch (error: Exception) {
                        outputBitmap.recycle()
                        throw error
                    }
                }
            }
        } finally {
            if (ownsScaled && !scaled.isRecycled) scaled.recycle()
        }
    }
}
