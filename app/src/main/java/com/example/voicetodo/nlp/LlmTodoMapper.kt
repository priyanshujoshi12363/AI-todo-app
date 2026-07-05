package com.example.voicetodo.nlp

import android.util.Log
import com.example.voicetodo.data.ActionType
import com.example.voicetodo.data.Recurrence
import com.example.voicetodo.data.Todo
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Converts Gemma's JSON output into [Todo]s. Returns null if the text isn't usable JSON. */
object LlmTodoMapper {

    fun parse(raw: String?, now: LocalDateTime = LocalDateTime.now()): List<Todo>? {
        if (raw.isNullOrBlank()) return null
        val json = extractJsonArray(raw) ?: return null
        return try {
            val arr = JSONArray(json)
            val todos = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { toTodo(it, now) }
            }
            todos.ifEmpty { null }
        } catch (t: Throwable) {
            Log.w("LlmTodoMapper", "Bad JSON from model: ${t.message}")
            null
        }
    }

    /** Pull the first [...] block out of the model text (handles ```json fences / stray words). */
    private fun extractJsonArray(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    private fun toTodo(o: JSONObject, now: LocalDateTime): Todo? {
        val title = o.optString("title").trim()
        if (title.isEmpty()) return null

        val dateStr = o.optStringOrNull("date")
        val timeStr = o.optStringOrNull("time")
        val recurrence = when (o.optString("recurrence", "none").lowercase()) {
            "daily" -> Recurrence.DAILY
            "weekly" -> Recurrence.WEEKLY
            else -> Recurrence.NONE
        }
        val action = when (o.optString("action", "none").lowercase()) {
            "call" -> ActionType.CALL
            "sms", "text" -> ActionType.SMS
            "whatsapp" -> ActionType.WHATSAPP
            "open_app", "open" -> ActionType.OPEN_APP
            "navigate" -> ActionType.NAVIGATE
            else -> ActionType.NONE
        }

        val dueAt = computeDueAt(dateStr, timeStr, recurrence, now)

        return Todo(
            title = title.replaceFirstChar { it.uppercase() },
            dueAt = dueAt,
            recurrence = recurrence,
            actionType = action,
            contactName = o.optStringOrNull("contact"),
            actionArg = o.optStringOrNull("arg")
        )
    }

    private fun computeDueAt(
        dateStr: String?, timeStr: String?, recurrence: Recurrence, now: LocalDateTime
    ): Long? {
        val time = timeStr?.let { runCatching { LocalTime.parse(it.padTimeIfNeeded()) }.getOrNull() }
        var date = dateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        if (date == null && (time != null || recurrence != Recurrence.NONE)) {
            date = now.toLocalDate()
        }
        if (date == null) return null
        val t = time ?: LocalTime.of(9, 0)
        var dt = LocalDateTime.of(date, t)
        // If it's today but already past, roll to the next day (unless a specific date was given).
        if (dateStr == null && dt.isBefore(now)) dt = dt.plusDays(1)
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun String.padTimeIfNeeded(): String =
        if (Regex("^\\d:").containsMatchIn(this)) "0$this" else this

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key).trim()
        return v.ifBlank { null }.takeUnless { it.equals("null", true) }
    }
}
