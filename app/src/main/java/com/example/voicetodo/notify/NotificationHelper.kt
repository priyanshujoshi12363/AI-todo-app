package com.example.voicetodo.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.getSystemService
import com.example.voicetodo.MainActivity
import com.example.voicetodo.R
import com.example.voicetodo.data.ActionType
import com.example.voicetodo.ui.ActionActivity

object NotificationHelper {
    const val CHANNEL_ID = "todo_reminders"
    const val CALL_CHANNEL_ID = "call_alerts"
    const val QUICK_CHANNEL_ID = "quick_add"
    const val QUICK_NOTIF_ID = 424242
    const val KEY_QUICK_TEXT = "quick_text"

    fun createChannel(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Todo reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Reminders for your daily tasks" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CALL_CHANNEL_ID, "Action alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "Wakes the screen to run a scheduled action (call, message, open app, navigate)"
                    setBypassDnd(true)
                }
        )
        nm.createNotificationChannel(
            NotificationChannel(QUICK_CHANNEL_ID, "Quick add", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Add a task straight from the notification shade" }
        )
    }

    /** Persistent notification with an inline reply to add a task without opening the app. */
    fun showQuickAdd(context: Context, lastAdded: String? = null) {
        val remoteInput = RemoteInput.Builder(KEY_QUICK_TEXT).setLabel("Type a task…").build()
        val replyPending = PendingIntent.getBroadcast(
            context, 7, Intent(context, QuickAddReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )
        val action = NotificationCompat.Action.Builder(R.drawable.ic_check, "Add task", replyPending)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val openPending = PendingIntent.getActivity(
            context, 8, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, QUICK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Voice Todo")
            .setContentText(lastAdded?.let { "Added: $it" } ?: "Tap ‘Add task’ to jot one down")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(action)
            .setContentIntent(openPending)
            .build()
        context.getSystemService<NotificationManager>()?.notify(QUICK_NOTIF_ID, notification)
    }

    fun show(context: Context, id: Long, title: String) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id.toInt(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // "Done" action — marks the task complete and dismisses the notification.
        val doneIntent = Intent(context, CompleteReceiver::class.java).apply {
            putExtra(CompleteReceiver.EXTRA_ID, id)
            putExtra(CompleteReceiver.EXTRA_NOTIF_ID, id.toInt())
        }
        val donePending = PendingIntent.getBroadcast(
            context, "done$id".hashCode(), doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Todo reminder")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .addAction(R.drawable.ic_check, "✓ Done", donePending)
            .setAutoCancel(true)
            .build()
        context.getSystemService<NotificationManager>()?.notify(id.toInt(), notification)
    }

    /** Full-screen alert that wakes/unlocks the phone and launches the action screen. */
    fun showAction(
        context: Context,
        id: Long,
        action: ActionType,
        name: String,
        number: String?,
        arg: String?
    ) {
        val actionIntent = Intent(context, ActionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ActionActivity.EXTRA_ACTION, action.name)
            putExtra(ActionActivity.EXTRA_NAME, name)
            putExtra(ActionActivity.EXTRA_NUMBER, number)
            putExtra(ActionActivity.EXTRA_ARG, arg)
            putExtra(ActionActivity.EXTRA_NOTIF_ID, id.toInt())
        }
        val fullScreen = PendingIntent.getActivity(
            context, id.toInt(), actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (action) {
            ActionType.CALL -> "Calling $name"
            ActionType.WHATSAPP -> "Messaging $name on WhatsApp"
            ActionType.OPEN_APP -> "Opening ${arg ?: "app"}"
            ActionType.NAVIGATE -> "Navigating to ${arg ?: ""}"
            else -> "Scheduled action"
        }
        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText("Tap to open")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .setAutoCancel(true)
            .build()
        context.getSystemService<NotificationManager>()?.notify(id.toInt(), notification)
    }

    fun cancel(context: Context, notifId: Int) {
        context.getSystemService<NotificationManager>()?.cancel(notifId)
    }
}
