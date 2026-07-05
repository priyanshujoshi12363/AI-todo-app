package com.example.voicetodo.data

import android.content.Context
import com.example.voicetodo.notify.ReminderScheduler
import kotlinx.coroutines.flow.Flow

/** Single source of truth: DB + reminder scheduling stay in sync here. */
class TodoRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(context).todoDao()
    private val scheduler = ReminderScheduler(appContext)

    val todos: Flow<List<Todo>> = dao.observeAll()

    suspend fun add(todo: Todo): Todo {
        // For contact-based actions, look up the number from contacts now.
        var toSave = todo
        val needsNumber = toSave.actionType == ActionType.CALL ||
            toSave.actionType == ActionType.SMS ||
            toSave.actionType == ActionType.WHATSAPP
        if (needsNumber && toSave.phoneNumber == null && toSave.contactName != null) {
            val number = ContactResolver.findNumber(appContext, toSave.contactName!!)
            toSave = toSave.copy(phoneNumber = number)
        }
        val id = dao.insert(toSave)
        val saved = toSave.copy(id = id)
        if (saved.dueAt != null && !saved.isDone) scheduler.schedule(saved)
        return saved
    }

    /** Mark a task done by id (used from the notification action). */
    suspend fun markDoneById(id: Long) {
        val todo = dao.byId(id) ?: return
        if (!todo.isDone) {
            dao.update(todo.copy(isDone = true))
            scheduler.cancel(id)
        }
    }

    /** Edit an existing task and re-arm its reminder. */
    suspend fun update(todo: Todo) {
        var toSave = todo
        val needsNumber = toSave.actionType == ActionType.CALL ||
            toSave.actionType == ActionType.SMS ||
            toSave.actionType == ActionType.WHATSAPP
        if (needsNumber && toSave.phoneNumber == null && toSave.contactName != null) {
            toSave = toSave.copy(phoneNumber = ContactResolver.findNumber(appContext, toSave.contactName!!))
        }
        dao.update(toSave)
        scheduler.cancel(toSave.id)
        if (toSave.dueAt != null && !toSave.isDone) scheduler.schedule(toSave)
    }

    suspend fun toggleDone(todo: Todo) {
        val updated = todo.copy(isDone = !todo.isDone)
        dao.update(updated)
        if (updated.isDone) scheduler.cancel(updated.id)
        else if (updated.dueAt != null) scheduler.schedule(updated)
    }

    suspend fun delete(todo: Todo) {
        scheduler.cancel(todo.id)
        dao.delete(todo)
    }

    /** Re-arm every reminder — used after device reboot. */
    suspend fun rescheduleAll() {
        dao.withReminders()
            .filter { !it.isDone && it.dueAt != null }
            .forEach { scheduler.schedule(it) }
    }
}
