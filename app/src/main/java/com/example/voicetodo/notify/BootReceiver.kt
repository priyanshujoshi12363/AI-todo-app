package com.example.voicetodo.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.voicetodo.data.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** After a reboot, alarms are cleared by the OS — re-arm every saved reminder. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TodoRepository(appContext).rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
