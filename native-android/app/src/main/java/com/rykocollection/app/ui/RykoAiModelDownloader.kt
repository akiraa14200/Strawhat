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

internal data class RykoAiModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val fileName: String,
    val url: String,
    val estimatedBytes: Long,
    val minimumBytes: Long,
    val sha256: String? = null
)

internal object RykoAiModelDownloader {
    val models = listOf(
        RykoAiModelSpec(
            id = "qwen3-0.6b-int4",
            name = "Qwen3 0.6B · INT4",
            description = "Fast, compact model · Recommended for 8 GB RAM",
            fileName = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            estimatedBytes = 328L * 1024L * 1024L,
            minimumBytes = 300L * 1024L * 1024L
        ),
        RykoAiModelSpec(
            id = "qwen3-0.6b-int8",
            name = "Qwen3 0.6B · INT8",
            description = "Larger download · Compatibility alternative",
            fileName = "Qwen3-0.6B.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            estimatedBytes = 613722264L,
            minimumBytes = 613722264L,
            sha256 = "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108"
        )
    )

    fun directory(context: Context): File =
        File(context.filesDir, "ryko_ai_models").apply { mkdirs() }

    fun fileFor(context: Context, modelId: String): File {
        val model = models.firstOrNull { it.id == modelId } ?: models.first()
        return File(directory(context), model.fileName)
    }

    fun isInstalled(context: Context, modelId: String): Boolean {
        val model = models.firstOrNull { it.id == modelId } ?: return false
        val file = File(directory(context), model.fileName)
        if (!file.isFile || file.length() < model.minimumBytes) return false
        return model.sha256?.let { runCatching { sha256(file) == it }.getOrDefault(false) } ?: true
    }

    suspend fun download(context: Context, modelId: String, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val model = models.firstOrNull { it.id == modelId }
                ?: throw IllegalArgumentException("Unknown Ryko AI model.")
            val destination = File(directory(context), model.fileName)
            if (isInstalled(context, modelId)) return@withContext destination

            val partial = File(directory(context), model.fileName + ".part")
            if (partial.length() > model.estimatedBytes * 2L) partial.delete()
            val requiredSpace = model.estimatedBytes + 100L * 1024L * 1024L
            if (directory(context).usableSpace < requiredSpace) {
                throw IllegalStateException("Not enough free storage. Please free at least " +
                    (requiredSpace / (1024L * 1024L)) + " MB and retry.")
            }

            val existing = partial.length()
            val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 25000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "RykoCollection-Android")
                if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw IllegalStateException("Model server returned HTTP $status.")
                val append = status == HttpURLConnection.HTTP_PARTIAL && existing > 0L
                val startAt = if (append) existing else 0L
                val responseLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                val totalLength = if (append && responseLength > 0L) existing + responseLength else responseLength
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
                            val denominator = if (totalLength > 0L) totalLength else model.estimatedBytes
                            onProgress((total.toFloat() / denominator.toFloat()).coerceIn(0f, 1f))
                        }
                        output.fd.sync()
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (partial.length() < model.minimumBytes) {
                throw IllegalStateException("The model download is incomplete. Open Ryko AI again to resume it.")
            }
            model.sha256?.let { expected ->
                if (sha256(partial) != expected) {
                    partial.delete()
                    throw IllegalStateException("Model verification failed. Please retry the download.")
                }
            }
            if (destination.exists()) destination.delete()
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
            onProgress(1f)
            destination
        }

    suspend fun ensureModel(context: Context, modelId: String, onProgress: (Float) -> Unit): File =
        download(context, modelId, onProgress)

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
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
