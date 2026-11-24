package com.lumapro.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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

                                val tensor = OnnxTensor.createTensor(
                                    env,
                                    FloatBuffer.wrap(inputData),
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
                                        val output = outputTensor.floatBuffer
                                        val planeSize = outputWidth * outputHeight
                                        val cropLeft = (tileX - inputLeft) * SCALE
                                        val cropTop = (tileY - inputTop) * SCALE
                                        val croppedWidth = centerWidth * SCALE
                                        val croppedHeight = centerHeight * SCALE
                                        val croppedPixels = IntArray(croppedWidth * croppedHeight)
                                        for (y in 0 until croppedHeight) {
                                            for (x in 0 until croppedWidth) {
                                                val sourceIndex = (cropTop + y) * outputWidth + cropLeft + x
                                                val red = (output.get(sourceIndex).coerceIn(0f, 1f) * 255f).roundToInt()
                                                val green = (output.get(planeSize + sourceIndex).coerceIn(0f, 1f) * 255f).roundToInt()
                                                val blue = (output.get(2 * planeSize + sourceIndex).coerceIn(0f, 1f) * 255f).roundToInt()
                                                croppedPixels[y * croppedWidth + x] =
                                                    Color.rgb(red, green, blue)
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
