package com.example.voicetodo.nlp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads the Gemma .task model into the app so users don't need adb. */
object ModelDownloader {

    /**
     * Direct-download URL of a Gemma `.task` model (MediaPipe/LiteRT). Gated — needs a HF token.
     * Prefer importing a model you already have (see ChatViewModel.importModel).
     */
    const val DEFAULT_URL =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"

    /**
     * Downloads to [dest], reporting 0..100. Returns [Result] with an error message on failure.
     * [token] is a HuggingFace read token (hf_...) for gated models like Gemma.
     */
    suspend fun download(
        url: String,
        dest: File,
        token: String? = null,
        onProgress: (Int) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        val tmp = File(dest.parentFile, dest.name + ".part")
        try {
            dest.parentFile?.mkdirs()
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 30000
                instanceFollowRedirects = true
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.e("ModelDownloader", "HTTP $code")
                val msg = when (code) {
                    401, 403 -> "Access denied ($code). Accept the Gemma license on HuggingFace and use a valid token."
                    404 -> "Model file not found ($code). Check the URL."
                    else -> "Download failed (HTTP $code)."
                }
                return@withContext Result(false, msg)
            }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(1 shl 16)
                        var read: Int
                        var done = 0L
                        var lastPct = -1
                        while (input.read(buf).also { read = it } >= 0) {
                            output.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                            }
                        }
                    }
                }
                if (dest.exists()) dest.delete()
                val ok = tmp.renameTo(dest)
                if (ok) Result(true, null) else Result(false, "Could not save the model file.")
            } catch (t: Throwable) {
                Log.e("ModelDownloader", "Download failed", t)
                tmp.delete()
                Result(false, "Download error: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                conn?.disconnect()
            }
        }

    data class Result(val success: Boolean, val error: String?)
}
