package com.example.voicetodo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.example.voicetodo.data.Note

@Composable
fun NotesScreen(
    vm: NotesViewModel,
    onDictate: ((String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Note?>(null) }

    val current = editing
    if (current != null) {
        NoteEditor(
            note = current,
            aiAvailable = vm.aiAvailable,
            onImprove = vm::improve,
            onDictate = onDictate,
            onSave = { vm.save(it); editing = null },
            onDelete = { if (it.id != 0L) vm.delete(it); editing = null },
            onBack = { editing = null },
            modifier = modifier
        )
        return
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { editing = Note(content = "") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Text("  New note", fontWeight = FontWeight.Medium)
        }

        if (notes.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No notes yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Jot down rough notes — tap ✨ to let AI fix the grammar and format them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().clickable { editing = note }
                    ) {
                        Text(
                            note.content.take(140).ifBlank { "(empty)" },
                            Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 4
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteEditor(
    note: Note,
    aiAvailable: Boolean,
    onImprove: (String, (String?) -> Unit) -> Unit,
    onDictate: ((String) -> Unit) -> Unit,
    onSave: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(note.content) }
    var busy by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Note", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = {
                onDictate { spoken -> text = (text.trim() + " " + spoken).trim() }
            }) { Icon(Icons.Filled.Mic, "Dictate") }
            IconButton(onClick = { onDelete(note) }) { Icon(Icons.Filled.Delete, "Delete") }
            Button(
                onClick = { onSave(note.copy(content = text.trim())) },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save") }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp),
            placeholder = { Text("Write your rough note here…") }
        )

        OutlinedButton(
            onClick = {
                busy = true
                onImprove(text) { improved ->
                    busy = false
                    if (improved != null) text = improved
                    else Toast.makeText(context, "AI couldn't improve this note", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = aiAvailable && !busy && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  Improving…")
            } else {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                Text("  Improve with AI")
            }
        }
        if (!aiAvailable) {
            Text(
                "Add the Gemma model to enable AI improve (see README).",
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
