package com.example.voicetodo.ui

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicetodo.nlp.LlmNlu
import com.example.voicetodo.nlp.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMsg(val user: Boolean, val text: String)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val llm = LlmNlu.get(app)

    var available by mutableStateOf(false); private set
    var loading by mutableStateOf(true); private set
    var downloading by mutableStateOf(false); private set
    var progress by mutableStateOf(0); private set
    var messages by mutableStateOf(listOf<ChatMsg>()); private set
    var thinking by mutableStateOf(false); private set

    init { load() }

    /** Load the inbuilt Gemma model (copies it out of assets on first run). */
    private fun load() {
        loading = true
        viewModelScope.launch(Dispatchers.Default) {
            val ok = llm.init()
            withContext(Dispatchers.Main) { available = ok; loading = false }
        }
    }

    fun retry() = load()

    /** Download the gated Gemma model (~1 GB) with a HuggingFace token, then load it. */
    fun enableAi(token: String) {
        if (downloading) return
        downloading = true
        progress = 0
        viewModelScope.launch {
            val res = ModelDownloader.download(
                ModelDownloader.DEFAULT_URL, llm.modelFile(), token.ifBlank { null }
            ) { p -> progress = p }
            val loaded = if (res.success) withContext(Dispatchers.Default) { llm.init() } else false
            downloading = false
            available = loaded
            if (!loaded) push(ChatMsg(false, res.error ?: "Couldn't load the model."))
        }
    }

    /** Import a .task model the user already has (e.g. from AI Edge Gallery / Downloads). */
    fun importModel(uri: Uri) {
        if (downloading) return
        downloading = true
        progress = 0
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val dest = llm.modelFile()
                dest.parentFile?.mkdirs()
                val resolver = getApplication<Application>().contentResolver
                val total = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                resolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(1 shl 16)
                        var read: Int
                        var done = 0L
                        var last = -1
                        while (input.read(buf).also { read = it } >= 0) {
                            output.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != last) { last = pct; progress = pct }
                            }
                        }
                    }
                } ?: error("Couldn't open the file")
                true
            }.getOrDefault(false)

            val loaded = if (ok) llm.init() else false
            withContext(Dispatchers.Main) {
                downloading = false
                available = loaded
                if (!loaded) push(ChatMsg(false, "Couldn't load that file. Make sure it's a Gemma .task model."))
            }
        }
    }

    private val main = Handler(Looper.getMainLooper())

    fun send(text: String) {
        val msg = text.trim()
        if (msg.isEmpty() || !available || thinking) return
        val history = messages.map { (if (it.user) "user" else "model") to it.text }
        push(ChatMsg(true, msg))
        push(ChatMsg(false, ""))   // empty assistant bubble we stream into
        thinking = true

        llm.chatStream(
            message = msg,
            history = history,
            onToken = { chunk -> main.post { appendToLast(chunk) } },
            onDone = { main.post { thinking = false } }
        )
    }

    private fun appendToLast(chunk: String) {
        val list = messages
        if (list.isEmpty()) return
        val last = list.last()
        messages = list.dropLast(1) + last.copy(text = last.text + chunk)
    }

    private fun push(m: ChatMsg) { messages = messages + m }
}
