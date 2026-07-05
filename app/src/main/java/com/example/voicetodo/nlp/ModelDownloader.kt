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
     * Direct-download URL of a Gemma `.task` model (MediaPipe/LiteRT).
     * Replace with a real, ungated direct link (or your own server) before shipping.
     * Example: a HuggingFace "resolve" URL for a litert-community Gemma .task file.
     */
    const val DEFAULT_URL =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"

    /** Downloads to [dest], reporting 0..100. Returns true on success. */
    suspend fun download(url: String, dest: File, onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            val tmp = File(dest.parentFile, dest.name + ".part")
            try {
                dest.parentFile?.mkdirs()
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    Log.e("ModelDownloader", "HTTP ${conn.responseCode}")
                    return@withContext false
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
                tmp.renameTo(dest)
            } catch (t: Throwable) {
                Log.e("ModelDownloader", "Download failed", t)
                tmp.delete()
                false
            } finally {
                conn?.disconnect()
            }
        }
}
