package com.example.voicetodo.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Speech-to-text using Android's built-in [SpeechRecognizer] (Google models).
 * More accurate than Vosk, supports many languages/accents, and prefers on-device
 * recognition when the language pack is available. No model to bundle.
 */
class AndroidSpeech(
    private val context: Context,
    private val language: String = "en-IN" // Indian English handles Hinglish/accent well
) {
    interface Callbacks {
        fun onReady()
        fun onPartial(text: String) {}
        fun onFinalText(text: String)
        fun onError(message: String)
        fun onListeningChanged(listening: Boolean) {}
    }

    private var recognizer: SpeechRecognizer? = null
    private var callbacks: Callbacks? = null

    /** Must be called on the main thread. */
    fun init(callbacks: Callbacks) {
        this.callbacks = callbacks
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callbacks.onError("Speech recognition isn't available on this device")
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
        callbacks.onReady()
    }

    fun startListening() {
        val r = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        callbacks?.onListeningChanged(true)
        r.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        callbacks?.onListeningChanged(false)
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { callbacks?.onPartial(it) }
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results)
            callbacks?.onListeningChanged(false)
            if (!text.isNullOrBlank()) callbacks?.onFinalText(text)
        }

        override fun onError(error: Int) {
            callbacks?.onListeningChanged(false)
            callbacks?.onError(message(error))
        }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun message(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "Didn't catch that — try again"
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Network needed (or install offline speech in the Google app)"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy — try again"
        else -> "Speech error ($code)"
    }
}
