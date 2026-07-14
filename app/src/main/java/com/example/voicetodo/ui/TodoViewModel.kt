package com.example.voicetodo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicetodo.data.Todo
import com.example.voicetodo.data.TodoRepository
import com.example.voicetodo.nlp.TodoParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TodoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TodoRepository(app)

    val todos: StateFlow<List<Todo>> = repo.todos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Turn a spoken/typed command into tasks and save them — instantly, using the
     * offline rule engine (English + Hinglish, multi-task). No model wait.
     */
    fun addFromSpeech(
        spoken: String,
        forcedDate: LocalDate? = null,
        onSaved: (List<Todo>) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val now = LocalDateTime.now()
            var parsed = TodoParser.parseMultiple(spoken, now)
            if (forcedDate != null) parsed = parsed.map { applyDate(it, forcedDate) }
            val saved = parsed.map { repo.add(it) }
            withContext(Dispatchers.Main) { onSaved(saved) }
        }
    }

    /** Force a task onto a specific calendar day, keeping any spoken time-of-day. */
    private fun applyDate(todo: Todo, date: LocalDate): Todo {
        val time = todo.dueAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
        } ?: LocalTime.of(9, 0)
        val due = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return todo.copy(dueAt = due)
    }

    fun update(todo: Todo) = viewModelScope.launch { repo.update(todo) }

    fun addManual(todo: Todo) = viewModelScope.launch { repo.add(todo) }

    fun toggleDone(todo: Todo) = viewModelScope.launch { repo.toggleDone(todo) }

    fun delete(todo: Todo) = viewModelScope.launch { repo.delete(todo) }
}
