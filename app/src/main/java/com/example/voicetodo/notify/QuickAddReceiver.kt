package com.example.voicetodo.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.voicetodo.data.TodoRepository
import com.example.voicetodo.nlp.TodoParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the inline "Add task" reply from the quick-add notification. */
class QuickAddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_QUICK_TEXT)?.toString()?.trim()

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            var added: String? = null
            try {
                if (!text.isNullOrBlank()) {
                    val repo = TodoRepository(appContext)
                    val todos = TodoParser.parseMultiple(text)
                    todos.forEach { repo.add(it) }
                    added = if (todos.size == 1) todos.first().title else "${todos.size} tasks"
                }
            } finally {
                // Re-post so the notification stays and confirms what was added.
                NotificationHelper.showQuickAdd(appContext, added)
                pending.finish()
            }
        }
    }
}
