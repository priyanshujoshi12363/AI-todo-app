package com.example.voicetodo.nlp

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import java.io.File

/**
 * On-device Gemma (via MediaPipe / LiteRT) that turns a spoken command into JSON tasks.
 *
 * The model is NOT bundled (it's ~1–1.5 GB). Put a Gemma `.task` file at:
 *   Android/data/com.example.voicetodo/files/llm/gemma.task
 * (push with:  adb push gemma.task /sdcard/Android/data/com.example.voicetodo/files/llm/gemma.task )
 *
 * If the model is missing or fails to load, [isAvailable] stays false and the app
 * falls back to the offline rule-based [TodoParser].
 */
class LlmNlu private constructor(private val context: Context) {

    companion object {
        @Volatile private var INSTANCE: LlmNlu? = null
        /** One shared engine for the whole app — loading the model twice would OOM the phone. */
        fun get(context: Context): LlmNlu =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LlmNlu(context.applicationContext).also { INSTANCE = it }
            }
    }

    private var llm: LlmInference? = null
    val isAvailable: Boolean get() = llm != null

    fun modelFile(): File = File(context.getExternalFilesDir(null), "llm/gemma.task")

    /** Load the model. Heavy + slow — call off the main thread. Returns true if ready. */
    fun init(): Boolean {
        if (llm != null) return true
        val model = modelFile()
        // If not imported/downloaded yet, try a model bundled in the APK (assets/llm/gemma.task).
        if (!model.exists()) copyBundledModelIfPresent(model)
        if (!model.exists()) {
            Log.i("LlmNlu", "No Gemma model at ${model.absolutePath} — using rule parser")
            return false
        }
        return try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(model.absolutePath)
                .setMaxTokens(2048) // room for ~10 tasks of JSON, or a cleaned-up note
                .build()
            llm = LlmInference.createFromOptions(context, options)
            Log.i("LlmNlu", "Gemma loaded")
            true
        } catch (t: Throwable) {
            Log.e("LlmNlu", "Failed to load Gemma", t)
            null.also { llm = null }
            false
        }
    }

    /** Copy a model bundled at assets/llm/gemma.task into files storage (once). */
    private fun copyBundledModelIfPresent(dest: File) {
        val assetPath = "llm/gemma.task"
        val exists = runCatching { context.assets.open(assetPath).close(); true }.getOrDefault(false)
        if (!exists) return
        try {
            dest.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            Log.i("LlmNlu", "Copied bundled Gemma model into ${dest.absolutePath}")
        } catch (t: Throwable) {
            Log.e("LlmNlu", "Failed to copy bundled model", t)
            dest.delete()
        }
    }

    /** Run the model; returns its raw text (expected to contain a JSON array) or null. */
    fun run(sentence: String, nowDescription: String): String? {
        val engine = llm ?: return null
        return try {
            engine.generateResponse(buildPrompt(sentence, nowDescription))
        } catch (t: Throwable) {
            Log.e("LlmNlu", "Inference failed", t)
            null
        }
    }

    /** Clean up a rough note: fix grammar, spelling, punctuation, and format it neatly. */
    fun improveNote(rough: String): String? {
        val engine = llm ?: return null
        val instruction = """
            Rewrite the following note with correct grammar, spelling and punctuation.
            Keep the original meaning and all facts. Format it cleanly: use short paragraphs,
            and bullet points ("- ") for lists where it helps readability.
            Output ONLY the improved note text, nothing else.

            Note:
            $rough
        """.trimIndent()
        val prompt = "<start_of_turn>user\n$instruction<end_of_turn>\n<start_of_turn>model\n"
        return try {
            engine.generateResponse(prompt)?.trim()?.removeGemmaArtifacts()
        } catch (t: Throwable) {
            Log.e("LlmNlu", "Note improve failed", t)
            null
        }
    }

    /**
     * Streaming chat: [onToken] fires for each partial chunk as Gemma generates it,
     * [onDone] fires when finished. Runs on a MediaPipe background thread.
     */
    fun chatStream(
        message: String,
        history: List<Pair<String, String>>,
        onToken: (String) -> Unit,
        onDone: () -> Unit
    ) {
        val engine = llm ?: run { onDone(); return }
        val prompt = buildChatPrompt(message, history)
        try {
            engine.generateResponseAsync(prompt, ProgressListener<String> { partial, done ->
                if (!partial.isNullOrEmpty()) onToken(partial)
                if (done) onDone()
            })
        } catch (t: Throwable) {
            Log.e("LlmNlu", "Streaming chat failed", t)
            onDone()
        }
    }

    private fun buildChatPrompt(message: String, history: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        for ((role, text) in history.takeLast(6)) {
            sb.append("<start_of_turn>$role\n$text<end_of_turn>\n")
        }
        sb.append("<start_of_turn>user\n$message<end_of_turn>\n<start_of_turn>model\n")
        return sb.toString()
    }

    /** Free-form local chat (blocking). [history] is prior turns as ("user"/"model", text). */
    fun chat(message: String, history: List<Pair<String, String>> = emptyList()): String? {
        val engine = llm ?: return null
        val sb = StringBuilder()
        for ((role, text) in history.takeLast(6)) {
            sb.append("<start_of_turn>$role\n$text<end_of_turn>\n")
        }
        sb.append("<start_of_turn>user\n$message<end_of_turn>\n<start_of_turn>model\n")
        return try {
            engine.generateResponse(sb.toString())?.trim()?.removeGemmaArtifacts()
        } catch (t: Throwable) {
            Log.e("LlmNlu", "Chat failed", t)
            null
        }
    }

    private fun String.removeGemmaArtifacts(): String =
        replace("<end_of_turn>", "").replace("<start_of_turn>", "").trim()

    private fun buildPrompt(sentence: String, nowDescription: String): String {
        val instruction = """
            You extract to-do tasks from a spoken command and output ONLY a JSON array.
            Current date and time: $nowDescription.
            For each task output an object with these fields:
              "title": short task text (imperative, no filler)
              "date": "YYYY-MM-DD" or null
              "time": "HH:MM" 24-hour or null
              "recurrence": "none" | "daily" | "weekly"
              "action": "none" | "call" | "sms" | "whatsapp" | "open_app" | "navigate"
              "contact": person name for call/sms/whatsapp, else null
              "arg": message text (sms/whatsapp), app name (open_app), or place (navigate), else null
            Split a command with several tasks into several objects.
            Resolve relative dates/times ("tomorrow", "in 2 hours", "right now") against the current date-time.
            Output only the JSON array, no markdown, no commentary.
            Command: "$sentence"
        """.trimIndent()
        // Gemma chat template.
        return "<start_of_turn>user\n$instruction<end_of_turn>\n<start_of_turn>model\n"
    }

    fun close() {
        try { llm?.close() } catch (_: Throwable) {}
        llm = null
    }
}
