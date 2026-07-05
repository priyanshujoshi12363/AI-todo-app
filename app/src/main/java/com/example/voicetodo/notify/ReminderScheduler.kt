package com.example.voicetodo.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.example.voicetodo.data.Recurrence
import com.example.voicetodo.data.Todo

/** Schedules exact alarms that fire even in Doze, so reminders arrive on time. */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService<AlarmManager>()!!

    fun schedule(todo: Todo) {
        val dueAt = todo.dueAt ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, todo.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, todo.title)
            putExtra(ReminderReceiver.EXTRA_RECURRENCE, todo.recurrence.name)
            putExtra(ReminderReceiver.EXTRA_DUE_AT, dueAt)
            putExtra(ReminderReceiver.EXTRA_ACTION, todo.actionType.name)
            putExtra(ReminderReceiver.EXTRA_CONTACT, todo.contactName)
            putExtra(ReminderReceiver.EXTRA_NUMBER, todo.phoneNumber)
            putExtra(ReminderReceiver.EXTRA_ARG, todo.actionArg)
        }
        val pending = pendingIntent(todo.id, intent)

        // If exact alarms aren't permitted, fall back to an inexact alarm rather than crash.
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            alarmManager.canScheduleExactAlarms() else true

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pending)
        }
    }

    fun cancel(id: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        alarmManager.cancel(pendingIntent(id, intent))
    }

    private fun pendingIntent(id: Long, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        fun nextOccurrence(dueAt: Long, recurrence: Recurrence): Long? = when (recurrence) {
            Recurrence.DAILY -> dueAt + 24L * 60 * 60 * 1000
            Recurrence.WEEKLY -> dueAt + 7L * 24 * 60 * 60 * 1000
            Recurrence.NONE -> null
        }
    }
}
