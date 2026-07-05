package com.example.voicetodo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Bridges the single Vosk engine to whichever screen wants to dictate right now.
 * A screen calls [request] with a callback; the next final transcript goes there.
 */
class VoiceController {
    var ready by mutableStateOf(false)
        private set
    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set

    private var pending: ((String) -> Unit)? = null

    // Wired to the Activity in AppRoot.
    var startImpl: (() -> Unit)? = null
    var stopImpl: (() -> Unit)? = null

    /** Ask to listen; [onResult] receives the recognized text once. Returns false if not ready. */
    fun request(onResult: (String) -> Unit): Boolean {
        if (!ready) return false
        pending = onResult
        startImpl?.invoke()
        return true
    }

    fun stop() = stopImpl?.invoke() ?: Unit

    // --- callbacks from Vosk ---
    fun onReady() { ready = true }
    fun onPartial(text: String) { partial = text }
    fun onListening(value: Boolean) { listening = value; if (!value) partial = "" }
    fun onFinal(text: String) {
        val cb = pending
        pending = null
        cb?.invoke(text)
    }
}
