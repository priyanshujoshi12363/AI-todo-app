package com.example.voicetodo.stt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.LogLevel
import org.vosk.LibVosk
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/**
 * Fully-offline speech-to-text using Vosk (Apache-2.0).
 *
 * We copy the model out of assets ourselves (instead of Vosk's StorageService, which
 * swallows native errors) so we can load it reliably and report the real failure.
 *
 * Model lives in `app/src/main/assets/<modelAssetDir>` (default "model-en-us").
 */
class VoskManager(
    private val context: Context,
    private val modelAssetDir: String = "model-en-us"
) : RecognitionListener {

    interface Callbacks {
        fun onReady()
        fun onPartial(text: String) {}
        fun onFinalText(text: String)
        fun onError(message: String)
        fun onListeningChanged(listening: Boolean) {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var callbacks: Callbacks? = null
    @Volatile private var loading = false
    // Guards against Vosk delivering the same final text via both onResult and onFinalResult.
    private var resultEmitted = false

    val isModelLoaded: Boolean get() = model != null

    fun init(callbacks: Callbacks) {
        this.callbacks = callbacks
        if (model != null) { callbacks.onReady(); return }
        if (loading) return
        loading = true

        Thread {
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                val modelDir = ensureModelCopied()
                val loaded = Model(modelDir.absolutePath)
                model = loaded
                mainHandler.post { this.callbacks?.onReady() }
            } catch (t: Throwable) {
                Log.e("VoskManager", "Model load failed", t)
                val msg = "Speech engine failed to load: ${t.message ?: t.javaClass.simpleName}"
                mainHandler.post { this.callbacks?.onError(msg) }
            } finally {
                loading = false
            }
        }.apply { name = "vosk-model-loader" }.start()
    }

    /** Copy the model from assets to internal storage once; returns the model directory. */
    private fun ensureModelCopied(): File {
        val outDir = File(context.filesDir, modelAssetDir)
        val marker = File(outDir, "conf/model.conf")
        if (marker.exists()) return outDir // already copied
        // Fresh copy (handles partial/failed previous copies).
        if (outDir.exists()) outDir.deleteRecursively()
        copyAsset(modelAssetDir, outDir)
        if (!marker.exists()) {
            throw IllegalStateException("Model files missing after copy — is the Vosk model in assets/$modelAssetDir ?")
        }
        return outDir
    }

    private fun copyAsset(assetPath: String, outFile: File) {
        val assets = context.assets
        val children = assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            // It's a file — copy it.
            outFile.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            // It's a directory — recurse.
            outFile.mkdirs()
            for (child in children) {
                copyAsset("$assetPath/$child", File(outFile, child))
            }
        }
    }

    fun startListening() {
        val m = model ?: run { callbacks?.onError("Speech engine still loading — try again in a moment"); return }
        if (speechService != null) return
        resultEmitted = false
        try {
            val recognizer = Recognizer(m, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f).also { it.startListening(this) }
            callbacks?.onListeningChanged(true)
        } catch (e: Exception) {
            callbacks?.onError("Microphone error: ${e.message}")
        }
    }

    /** Emit the recognized command exactly once per listening session. */
    private fun emitFinal(text: String) {
        if (resultEmitted || text.isBlank()) return
        resultEmitted = true
        callbacks?.onFinalText(text)
    }

    fun stopListening() {
        speechService?.stop()
        speechService = null
        callbacks?.onListeningChanged(false)
    }

    fun release() {
        speechService?.shutdown()
        speechService = null
        model?.close()
        model = null
    }

    // --- Vosk RecognitionListener ---
    override fun onPartialResult(hypothesis: String?) {
        val text = hypothesis?.let { JSONObject(it).optString("partial") }.orEmpty()
        if (text.isNotBlank()) callbacks?.onPartial(text)
    }

    override fun onResult(hypothesis: String?) {
        val text = hypothesis?.let { JSONObject(it).optString("text") }.orEmpty()
        if (text.isNotBlank()) {
            emitFinal(text)
            stopListening()
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        val text = hypothesis?.let { JSONObject(it).optString("text") }.orEmpty()
        emitFinal(text)
        callbacks?.onListeningChanged(false)
    }

    override fun onError(exception: Exception?) {
        callbacks?.onError(exception?.message ?: "Unknown speech error")
        stopListening()
    }

    override fun onTimeout() {
        stopListening()
    }
}
