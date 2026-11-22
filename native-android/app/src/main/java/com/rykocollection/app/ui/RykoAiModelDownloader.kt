package com.rykocollection.app.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal object RykoAiModelDownloader {
    private const val MODEL_FILE = "Qwen3-0.6B.litertlm"
    private const val MODEL_URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
    private const val MODEL_BYTES = 613_722_264L
    private const val MODEL_SHA256 = "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108"

    suspend fun ensureModel(context: Context, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val destination = File(context.filesDir, MODEL_FILE)
        if (isValid(destination)) return@withContext destination

        val partial = File(context.filesDir, "$MODEL_FILE.part")
        if (partial.length() > MODEL_BYTES) partial.delete()
        if (context.filesDir.usableSpace < MODEL_BYTES + 120L * 1024L * 1024L) {
            throw IllegalStateException("Ryko AI needs about 750 MB of free internal storage to install its model.")
        }

        val existingBytes = partial.length()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Model server returned HTTP $status.")
            val append = status == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0L
            val startAt = if (append) existingBytes else 0L
            RandomAccessFile(partial, "rw").use { output ->
                if (!append) output.setLength(0L)
                output.seek(startAt)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var total = startAt
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                        onProgress((total.toFloat() / MODEL_BYTES).coerceIn(0f, 1f))
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }

        if (partial.length() != MODEL_BYTES) {
            throw IllegalStateException("The model download is incomplete. Open Ryko AI again to resume it.")
        }
        if (sha256(partial) != MODEL_SHA256) {
            partial.delete()
            throw IllegalStateException("Model verification failed. Please retry the download.")
        }
        if (destination.exists()) destination.delete()
        if (!partial.renameTo(destination)) {
            partial.copyTo(destination, overwrite = true)
            partial.delete()
        }
        destination
    }

    private fun isValid(file: File): Boolean =
        file.isFile && file.length() == MODEL_BYTES && runCatching { sha256(file) == MODEL_SHA256 }.getOrDefault(false)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
