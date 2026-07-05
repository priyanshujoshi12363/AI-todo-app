package com.example.voicetodo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(vm: ChatViewModel, modifier: Modifier = Modifier) {
    if (!vm.available) {
        EnableAiCard(vm.downloading, vm.progress, vm::enableAi, modifier)
        return
    }

    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.messages) { m -> ChatBubble(m) }
            if (vm.thinking) item {
                Text("Gemma is thinking…", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Gemma…") }
            )
            IconButton(
                onClick = { vm.send(input); input = "" },
                enabled = input.isNotBlank() && !vm.thinking
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMsg) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.user) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (m.user) scheme.primary else scheme.surfaceVariant
        ) {
            Text(
                m.text,
                Modifier.padding(12.dp),
                color = if (m.user) scheme.onPrimary else scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EnableAiCard(downloading: Boolean, progress: Int, onEnable: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("On-device AI chat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Enable the offline Gemma model to chat locally and improve your notes. " +
                "This downloads ~1 GB once, then works fully offline.",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (downloading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )
            Text("Downloading… $progress%", Modifier.padding(top = 8.dp))
        } else {
            Button(onClick = onEnable, Modifier.padding(top = 20.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Enable AI (downloads ~1 GB)")
            }
        }
        if (downloading) CircularProgressIndicator(Modifier.padding(top = 16.dp).size(20.dp), strokeWidth = 2.dp)
    }
}
