package com.lumapro.camera

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads and verifies the pinned Real-ESRGAN General x4v3 ONNX model.
 * The model is stored in app-private storage and is reusable offline.
 */
object AiModelManager {
    private const val MODEL_FILE = "realesr-general-x4v3.onnx"
    private const val MODEL_URL =
        "https://huggingface.co/Samo629/real-esrgan-onnx/resolve/d24b075/realesr-general-x4v3.onnx"
    private const val EXPECTED_SHA256 =
        "ee28b94a5d06ff32c4920370417e094d1dc7aae4e568e2502afb3371377e41fd"
    private const val MAX_MODEL_BYTES = 12L * 1024L * 1024L

    fun installedModel(context: Context): File? {
        val file = File(context.filesDir, MODEL_FILE)
        return file.takeIf { it.isFile && it.length() in 1..MAX_MODEL_BYTES }
    }

    @Synchronized
    fun downloadAndVerify(context: Context, onProgress: (Int) -> Unit = {}): File {
        installedModel(context)?.let { existing ->
            if (sha256(existing) == EXPECTED_SHA256) return existing
            existing.delete()
        }

        val destination = File(context.filesDir, MODEL_FILE)
        val temporary = File(context.filesDir, "$MODEL_FILE.download")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LumaProCamera/1.0")
            }
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Model download failed: HTTP ${connection.responseCode}"
            }
            val expectedLength = connection.contentLengthLong
            if (expectedLength > 0L) check(expectedLength <= MAX_MODEL_BYTES) {
                "Unexpected model size"
            }

            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_MODEL_BYTES) { "Model file is unexpectedly large" }
                        output.write(buffer, 0, count)
                        if (expectedLength > 0L) {
                            onProgress(((total * 100L) / expectedLength).toInt().coerceIn(0, 100))
                        }
                    }
                    output.fd.sync()
                }
            }
            check(temporary.length() > 1L) { "Downloaded model is empty" }
            check(sha256(temporary) == EXPECTED_SHA256) { "Model integrity check failed" }
            if (destination.exists()) destination.delete()
            check(temporary.renameTo(destination)) { "Could not install AI model" }
            onProgress(100)
            return destination
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection?.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
