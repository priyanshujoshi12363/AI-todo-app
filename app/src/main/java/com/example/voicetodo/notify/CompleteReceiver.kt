package com.example.voicetodo.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.voicetodo.data.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Marks a task done when the user taps "Done" on its notification. */
class CompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id < 0) return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, id.toInt())
        NotificationHelper.cancel(context, notifId)

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TodoRepository(appContext).markDoneById(id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
