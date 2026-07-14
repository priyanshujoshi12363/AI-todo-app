package com.example.voicetodo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.voicetodo.stt.AndroidSpeech
import com.example.voicetodo.ui.CalendarScreen
import com.example.voicetodo.ui.ChatScreen
import com.example.voicetodo.ui.ChatViewModel
import com.example.voicetodo.ui.NotesScreen
import com.example.voicetodo.ui.NotesViewModel
import com.example.voicetodo.ui.TodoListScreen
import com.example.voicetodo.ui.TodoViewModel
import com.example.voicetodo.ui.VoiceController
import com.example.voicetodo.ui.theme.VoiceTodoTheme

class MainActivity : ComponentActivity() {

    private lateinit var speech: AndroidSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speech = AndroidSpeech(applicationContext)
        requestStartupPermissions()
        setContent { VoiceTodoTheme { AppRoot(speech) } }
    }

    private fun requestStartupPermissions() {
        val wanted = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isNotEmpty()) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                .launch(wanted.toTypedArray())
        }
    }

    override fun onDestroy() {
        speech.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(speech: AndroidSpeech) {
    val context = LocalContext.current
    val vm: TodoViewModel = viewModel()
    val notesVm: NotesViewModel = viewModel()
    val chatVm: ChatViewModel = viewModel()
    val todos by vm.todos.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    val voice = remember { VoiceController() }
    var pendingHandler by remember { mutableStateOf<((String) -> Unit)?>(null) }

    LaunchedEffect(Unit) {
        voice.startImpl = { speech.startListening() }
        voice.stopImpl = { speech.stopListening() }
        speech.init(object : AndroidSpeech.Callbacks {
            override fun onReady() { voice.onReady() }
            override fun onPartial(text: String) { voice.onPartial(text) }
            override fun onFinalText(text: String) { voice.onFinal(text) }
            override fun onError(message: String) {
                voice.onListening(false)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            override fun onListeningChanged(l: Boolean) { voice.onListening(l) }
        })
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val handler = pendingHandler
        pendingHandler = null
        if (granted && handler != null) voice.request(handler)
        else if (!granted) Toast.makeText(context, "Microphone permission needed", Toast.LENGTH_SHORT).show()
    }

    // Any screen calls this to dictate; the result goes to onResult.
    fun startVoice(onResult: (String) -> Unit) {
        if (!voice.ready) { Toast.makeText(context, "Setting up voice…", Toast.LENGTH_SHORT).show(); return }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) voice.request(onResult)
        else { pendingHandler = onResult; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }

    fun speakTasks() = startVoice { text ->
        vm.addFromSpeech(text) { saved ->
            val msg = when {
                saved.isEmpty() -> "Nothing understood"
                saved.size == 1 -> "Saved: ${saved.first().title}"
                else -> "Saved ${saved.size} tasks"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Voice Todo", fontWeight = FontWeight.SemiBold)
                            Text(
                                when (tab) {
                                    1 -> "Tap a day, then speak to schedule it"
                                    2 -> "Rough notes, cleaned up by AI"
                                    3 -> "Chat with on-device Gemma"
                                    else -> if (!voice.ready) "Setting up voice…" else "Tap the mic and speak a task"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            floatingActionButton = {
                if (tab == 0) CornerMic(voice.listening, voice.ready) { speakTasks() }
            },
            bottomBar = { BottomTabs(tab) { tab = it } }
        ) { padding ->
            val mod = Modifier.padding(padding)
            when (tab) {
                0 -> TodoListScreen(
                    todos = todos,
                    onToggle = vm::toggleDone,
                    onDelete = vm::delete,
                    onSave = vm::update,
                    onAddTyped = { text ->
                        vm.addFromSpeech(text) { saved ->
                            val msg = if (saved.size == 1) "Added: ${saved.first().title}"
                            else "Added ${saved.size} tasks"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = mod
                )
                1 -> CalendarScreen(
                    todos = todos,
                    onSpeakForDate = { date -> startVoice { text -> vm.addFromSpeech(text, date) {} } },
                    modifier = mod
                )
                2 -> NotesScreen(vm = notesVm, onDictate = { onText -> startVoice(onText) }, modifier = mod)
                else -> ChatScreen(vm = chatVm, modifier = mod)
            }
        }

        AnimatedVisibility(visible = voice.listening, enter = fadeIn(), exit = fadeOut()) {
            ListeningOverlay(partial = voice.partial, onStop = voice::stop)
        }
    }
}

@Composable
private fun CornerMic(listening: Boolean, ready: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = if (listening) scheme.error else scheme.primary,
        contentColor = scheme.onPrimary,
        modifier = Modifier.size(60.dp)
    ) {
        when {
            !ready && !listening -> CircularProgressIndicator(
                color = scheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)
            )
            else -> Icon(
                if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (listening) "Stop" else "Speak a task",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar(containerColor = scheme.surface, tonalElevation = 0.dp) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = scheme.onPrimary,
            selectedTextColor = scheme.onSurface,
            indicatorColor = scheme.primary,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant
        )
        NavigationBarItem(
            selected = selected == 0, onClick = { onSelect(0) }, colors = colors,
            icon = { Icon(Icons.Filled.Checklist, null) }, label = { Text("Tasks") }
        )
        NavigationBarItem(
            selected = selected == 1, onClick = { onSelect(1) }, colors = colors,
            icon = { Icon(Icons.Filled.CalendarMonth, null) }, label = { Text("Calendar") }
        )
        NavigationBarItem(
            selected = selected == 2, onClick = { onSelect(2) }, colors = colors,
            icon = { Icon(Icons.Filled.EditNote, null) }, label = { Text("Notes") }
        )
        NavigationBarItem(
            selected = selected == 3, onClick = { onSelect(3) }, colors = colors,
            icon = { Icon(Icons.Filled.Forum, null) }, label = { Text("AI Chat") }
        )
    }
}

@Composable
private fun ListeningOverlay(partial: String, onStop: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale"
    )
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = scheme.surface,
            modifier = Modifier.fillMaxWidth().padding(28.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(96.dp).scale(scale).clip(CircleShape).background(scheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Mic, null, tint = scheme.onPrimary, modifier = Modifier.size(40.dp))
                }
                Text("Listening…", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleLarge)
                Text(
                    partial.ifBlank { "Say something…" },
                    Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(onClick = onStop, modifier = Modifier.padding(top = 24.dp)) {
                    Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
                    Text("  Stop", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
