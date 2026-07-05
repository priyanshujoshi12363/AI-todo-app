package com.example.voicetodo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicetodo.data.Todo
import com.example.voicetodo.data.TodoRepository
import com.example.voicetodo.nlp.LlmNlu
import com.example.voicetodo.nlp.LlmTodoMapper
import com.example.voicetodo.nlp.TodoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TodoRepository(app)
    private val llm = LlmNlu.get(app)

    val todos: StateFlow<List<Todo>> = repo.todos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Load Gemma in the background if the model is present; harmless if it isn't.
        viewModelScope.launch(Dispatchers.Default) { llm.init() }
    }

    /**
     * Turn a spoken command into tasks and save them.
     * Uses on-device Gemma when the model is loaded, otherwise the offline rule parser.
     */
    fun addFromSpeech(
        spoken: String,
        forcedDate: java.time.LocalDate? = null,
        onSaved: (List<Todo>) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val now = LocalDateTime.now()
            var parsed = parseSmart(spoken, now)
            if (forcedDate != null) parsed = parsed.map { applyDate(it, forcedDate) }
            val saved = parsed.map { repo.add(it) }
            withContext(Dispatchers.Main) { onSaved(saved) }
        }
    }

    /** Force a task onto a specific calendar day, keeping any spoken time-of-day. */
    private fun applyDate(todo: Todo, date: java.time.LocalDate): Todo {
        val time = todo.dueAt?.let {
            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        } ?: java.time.LocalTime.of(9, 0)
        val due = LocalDateTime.of(date, time)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return todo.copy(dueAt = due)
    }

    fun update(todo: Todo) = viewModelScope.launch { repo.update(todo) }

    private fun parseSmart(spoken: String, now: LocalDateTime): List<Todo> {
        if (llm.isAvailable) {
            val fromLlm = LlmTodoMapper.parse(llm.run(spoken, describe(now)), now)
            if (!fromLlm.isNullOrEmpty()) return fromLlm
        }
        return TodoParser.parseMultiple(spoken, now)
    }

    private fun describe(now: LocalDateTime): String =
        now.format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm", Locale.US))

    fun addManual(todo: Todo) = viewModelScope.launch { repo.add(todo) }

    fun toggleDone(todo: Todo) = viewModelScope.launch { repo.toggleDone(todo) }

    fun delete(todo: Todo) = viewModelScope.launch { repo.delete(todo) }
}
