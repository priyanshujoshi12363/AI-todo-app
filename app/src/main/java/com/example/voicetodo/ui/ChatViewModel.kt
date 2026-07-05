package com.example.voicetodo.ui

import android.app.Application
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
    var downloading by mutableStateOf(false); private set
    var progress by mutableStateOf(0); private set
    var messages by mutableStateOf(listOf<ChatMsg>()); private set
    var thinking by mutableStateOf(false); private set

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val ok = llm.init()
            withContext(Dispatchers.Main) { available = ok }
        }
    }

    /** Download the Gemma model (~1 GB) then load it. */
    fun enableAi() {
        if (downloading) return
        downloading = true
        progress = 0
        viewModelScope.launch {
            val ok = ModelDownloader.download(ModelDownloader.DEFAULT_URL, llm.modelFile()) { p ->
                progress = p
            }
            val loaded = if (ok) withContext(Dispatchers.Default) { llm.init() } else false
            downloading = false
            available = loaded
            if (!loaded) push(ChatMsg(false, "Couldn't set up AI. Check internet/storage or the model URL."))
        }
    }

    fun send(text: String) {
        val msg = text.trim()
        if (msg.isEmpty() || !available || thinking) return
        push(ChatMsg(true, msg))
        thinking = true
        viewModelScope.launch(Dispatchers.Default) {
            val history = messages.map { (if (it.user) "user" else "model") to it.text }
            val reply = llm.chat(msg, history) ?: "(no response)"
            withContext(Dispatchers.Main) {
                push(ChatMsg(false, reply))
                thinking = false
            }
        }
    }

    private fun push(m: ChatMsg) { messages = messages + m }
}
