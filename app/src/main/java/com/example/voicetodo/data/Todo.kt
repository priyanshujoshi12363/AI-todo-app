package com.example.voicetodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Recurrence for reminders. */
enum class Recurrence { NONE, DAILY, WEEKLY }

/** What the app does when the task is due. */
enum class ActionType { NONE, CALL, SMS, WHATSAPP, OPEN_APP, NAVIGATE }

@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** When the reminder should fire, epoch millis. Null = no reminder. */
    val dueAt: Long? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** For action tasks like "call mummy", "text dad ...", "open YouTube", "navigate to office". */
    val actionType: ActionType = ActionType.NONE,
    val contactName: String? = null,
    val phoneNumber: String? = null,
    /** Extra payload: message text (SMS/WhatsApp), app name (OPEN_APP), or place (NAVIGATE). */
    val actionArg: String? = null
)
