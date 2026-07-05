package com.example.voicetodo.notify

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.example.voicetodo.data.ActionType
import com.example.voicetodo.data.Recurrence

/** Fires when a reminder is due: notifies or runs the scheduled action, then re-arms recurring ones. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Todo"
        if (id < 0) return

        val action = runCatching {
            ActionType.valueOf(intent.getStringExtra(EXTRA_ACTION) ?: "NONE")
        }.getOrDefault(ActionType.NONE)
        val name = intent.getStringExtra(EXTRA_CONTACT) ?: title
        val number = intent.getStringExtra(EXTRA_NUMBER)
        val arg = intent.getStringExtra(EXTRA_ARG)

        when (action) {
            ActionType.NONE -> NotificationHelper.show(context, id, title)
            ActionType.SMS -> sendSmsOrOpen(context, id, name, number, arg)
            // Call / WhatsApp / open app / navigate all need a foreground screen.
            else -> NotificationHelper.showAction(context, id, action, name, number, arg)
        }

        reschedule(context, id, title, action, name, number, arg, intent)
    }

    private fun sendSmsOrOpen(context: Context, id: Long, name: String, number: String?, msg: String?) {
        val canSend = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (canSend && !number.isNullOrBlank()) {
            try {
                val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    context.getSystemService(SmsManager::class.java)
                else @Suppress("DEPRECATION") SmsManager.getDefault()
                sms?.sendTextMessage(number, null, msg ?: "", null, null)
                NotificationHelper.show(context, id, "Texted $name")
                return
            } catch (e: Exception) {
                // fall through to opening the messaging app
            }
        }
        // No permission / no number: open the messaging app pre-filled via the action screen.
        NotificationHelper.showAction(context, id, ActionType.SMS, name, number, msg)
    }

    private fun reschedule(
        context: Context, id: Long, title: String, action: ActionType,
        name: String, number: String?, arg: String?, intent: Intent
    ) {
        val recurrence = runCatching {
            Recurrence.valueOf(intent.getStringExtra(EXTRA_RECURRENCE) ?: "NONE")
        }.getOrDefault(Recurrence.NONE)
        val dueAt = intent.getLongExtra(EXTRA_DUE_AT, 0L)
        val next = ReminderScheduler.nextOccurrence(dueAt, recurrence) ?: return

        val am = context.getSystemService<AlarmManager>() ?: return
        val nextIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_RECURRENCE, recurrence.name)
            putExtra(EXTRA_DUE_AT, next)
            putExtra(EXTRA_ACTION, action.name)
            putExtra(EXTRA_CONTACT, name)
            putExtra(EXTRA_NUMBER, number)
            putExtra(EXTRA_ARG, arg)
        }
        val pending = PendingIntent.getBroadcast(
            context, id.toInt(), nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            am.canScheduleExactAlarms() else true
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_RECURRENCE = "recurrence"
        const val EXTRA_DUE_AT = "due_at"
        const val EXTRA_ACTION = "action"
        const val EXTRA_CONTACT = "contact"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_ARG = "arg"
    }
}
