package com.example.voicetodo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { vm.importModel(it) }
        }
        EnableAiCard(
            downloading = vm.downloading,
            progress = vm.progress,
            onEnable = vm::enableAi,
            onImport = { picker.launch(arrayOf("application/octet-stream", "*/*")) },
            modifier = modifier
        )
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
private fun EnableAiCard(
    downloading: Boolean,
    progress: Int,
    onEnable: (String) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier
) {
    var token by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enable on-device AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "This Gemma model powers voice understanding, note cleanup, and chat — all offline. " +
                "Best option: import the Gemma .task file you already have on your phone.",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (downloading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )
            Text("Setting up… $progress%", Modifier.padding(top = 8.dp))
            CircularProgressIndicator(Modifier.padding(top = 16.dp).size(20.dp), strokeWidth = 2.dp)
            return@Column
        }

        // Primary: import an existing .task file
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Import Gemma .task file") }
        Text(
            "Pick the model you already downloaded (e.g. via AI Edge Gallery / Downloads).",
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Alternative: download with a HuggingFace token
        Text(
            "— or download it (needs a free HuggingFace token, ~3 GB) —",
            Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text("HuggingFace token (hf_…)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedButtonEnable(token, onEnable)
    }
}

@Composable
private fun OutlinedButtonEnable(token: String, onEnable: (String) -> Unit) {
    Button(
        onClick = { onEnable(token) },
        enabled = token.isNotBlank(),
        modifier = Modifier.padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) { Text("Download & Enable") }
}
