package com.example.voicetodo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.voicetodo.data.ActionType
import com.example.voicetodo.data.Recurrence
import com.example.voicetodo.data.Todo
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.getDefault())
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private fun dueParts(dueAt: Long?): Pair<String, String>? = dueAt?.let {
    val z = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
    z.format(dayFmt) to z.format(timeFmt)
}

/** Kept for the calendar screen's summary line. */
fun formatDue(dueAt: Long?): String? = dueParts(dueAt)?.let { "${it.first} · ${it.second}" }

@Composable
fun TodoListScreen(
    todos: List<Todo>,
    onToggle: (Todo) -> Unit,
    onDelete: (Todo) -> Unit,
    onSave: (Todo) -> Unit,
    onAddTyped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<Todo?>(null) }
    val current = editing
    if (current != null) {
        TaskEditor(
            todo = current,
            onSave = { onSave(it); editing = null },
            onDelete = { onDelete(it); editing = null },
            onBack = { editing = null },
            modifier = modifier
        )
        return
    }

    Column(modifier.fillMaxSize()) {
        TypeTaskBar(onAddTyped)
        if (todos.isEmpty()) {
            EmptyState(Modifier.weight(1f))
        } else {
            val active = todos.filter { !it.isDone }
            val done = todos.filter { it.isDone }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (active.isNotEmpty()) {
                    item { SectionHeader("TO DO", active.size) }
                    items(active, key = { it.id }) { TodoRow(it, onToggle, onDelete) { editing = it } }
                }
                if (done.isNotEmpty()) {
                    item { SectionHeader("COMPLETED", done.size) }
                    items(done, key = { it.id }) { TodoRow(it, onToggle, onDelete) { editing = it } }
                }
                item { Spacer(Modifier.size(72.dp)) } // room for the mic button
            }
        }
    }
}

@Composable
private fun TypeTaskBar(onAdd: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a task — e.g. “call mom at 6 pm”") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        IconButton(
            onClick = { if (input.isNotBlank()) { onAdd(input); input = "" } },
            enabled = input.isNotBlank()
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "Add task")
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        "$title · $count",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun TodoRow(
    todo: Todo,
    onToggle: (Todo) -> Unit,
    onDelete: (Todo) -> Unit,
    onEdit: (Todo) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = Modifier.fillMaxWidth().clickable { onEdit(todo) }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Custom monochrome check circle
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (todo.isDone) scheme.primary else scheme.surface)
                    .border(1.5.dp, if (todo.isDone) scheme.primary else scheme.outline, CircleShape)
                    .clickable { onToggle(todo) },
                contentAlignment = Alignment.Center
            ) {
                if (todo.isDone) Icon(
                    Icons.Filled.Check, null, tint = scheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    todo.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (todo.isDone) scheme.onSurfaceVariant else scheme.onSurface,
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else null
                )
                val parts = dueParts(todo.dueAt)
                val actionChip = actionChip(todo.actionType)
                if (parts != null || actionChip != null) {
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (parts != null) Chip(Icons.Outlined.Schedule, "${parts.first} · ${parts.second}")
                        if (todo.recurrence != Recurrence.NONE) {
                            Chip(Icons.Filled.Repeat, todo.recurrence.name.lowercase(Locale.getDefault()))
                        }
                        if (actionChip != null) Chip(actionChip.first, actionChip.second)
                    }
                }
            }

            IconButton(onClick = { onDelete(todo) }) {
                Icon(Icons.Filled.Delete, "Delete", tint = scheme.onSurfaceVariant)
            }
        }
    }
}

private val editFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

@Composable
private fun TaskEditor(
    todo: Todo,
    onSave: (Todo) -> Unit,
    onDelete: (Todo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(todo.title) }
    var contact by remember { mutableStateOf(todo.contactName ?: "") }
    var whenText by remember {
        mutableStateOf(
            todo.dueAt?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime().format(editFmt)
            } ?: ""
        )
    }

    Column(modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Edit task", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { onDelete(todo) }) { Icon(Icons.Filled.Delete, "Delete") }
        }

        OutlinedTextField(
            value = title, onValueChange = { title = it },
            label = { Text("Task") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = whenText, onValueChange = { whenText = it },
            label = { Text("When (yyyy-MM-dd HH:mm, blank = none)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        if (todo.actionType != ActionType.NONE) {
            OutlinedTextField(
                value = contact, onValueChange = { contact = it },
                label = { Text("Contact / target") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        Button(
            onClick = {
                val dueAt = whenText.trim().takeIf { it.isNotEmpty() }?.let {
                    runCatching {
                        LocalDateTime.parse(it, editFmt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.getOrNull()
                }
                onSave(
                    todo.copy(
                        title = title.trim().ifBlank { todo.title },
                        dueAt = dueAt,
                        contactName = contact.trim().ifBlank { null },
                        phoneNumber = if (contact.trim() != (todo.contactName ?: "")) null else todo.phoneNumber
                    )
                )
            },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Save changes") }
    }
}

private fun actionChip(action: ActionType): Pair<ImageVector, String>? = when (action) {
    ActionType.CALL -> Icons.Filled.Call to "auto-call"
    ActionType.SMS -> Icons.Filled.Sms to "auto-text"
    ActionType.WHATSAPP -> Icons.Filled.Chat to "whatsapp"
    ActionType.OPEN_APP -> Icons.Filled.OpenInNew to "open app"
    ActionType.NAVIGATE -> Icons.Filled.Navigation to "navigate"
    ActionType.NONE -> null
}

@Composable
private fun Chip(icon: ImageVector, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Mic, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(38.dp))
        }
        Text(
            "No tasks yet",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            "Tap the mic and just say it — for example:",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        listOf(
            "“I want to watch a movie today”",
            "“Call mom tomorrow at 6 pm”",
            "“Daily standup at 9 am”",
            "“Pay rent on 31”"
        ).forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
