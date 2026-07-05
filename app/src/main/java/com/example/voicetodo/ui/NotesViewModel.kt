package com.example.voicetodo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicetodo.data.AppDatabase
import com.example.voicetodo.data.Note
import com.example.voicetodo.nlp.LlmNlu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).noteDao()
    private val llm = LlmNlu.get(app)

    val aiAvailable: Boolean get() = llm.isAvailable

    val notes: StateFlow<List<Note>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(note: Note) = viewModelScope.launch {
        if (note.id == 0L) dao.insert(note.copy(updatedAt = System.currentTimeMillis()))
        else dao.update(note.copy(updatedAt = System.currentTimeMillis()))
    }

    fun delete(note: Note) = viewModelScope.launch { dao.delete(note) }

    /**
     * Improve a note's grammar/formatting with on-device Gemma.
     * Returns the improved text, or null if AI isn't available / failed.
     */
    fun improve(text: String, onResult: (String?) -> Unit) {
        if (text.isBlank()) { onResult(null); return }
        viewModelScope.launch(Dispatchers.Default) {
            val improved = if (llm.isAvailable) llm.improveNote(text) else null
            withContext(Dispatchers.Main) { onResult(improved) }
        }
    }
}
