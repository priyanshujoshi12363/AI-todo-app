package com.example.voicetodo

import com.example.voicetodo.data.ActionType
import com.example.voicetodo.data.Recurrence
import com.example.voicetodo.nlp.TodoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TodoParserTest {

    // Fixed reference time so date/time assertions are deterministic.
    private val now: LocalDateTime = LocalDateTime.of(2026, 7, 2, 10, 0)

    private fun hourOf(dueAt: Long?): Int =
        Instant.ofEpochMilli(dueAt!!).atZone(ZoneId.systemDefault()).hour

    @Test
    fun movieAtFourAm_parsesTimeAndTitle() {
        val todos = TodoParser.parseMultiple("i want to watch movie at four am", now)
        assertEquals(1, todos.size)
        val t = todos[0]
        assertEquals("Watch movie", t.title)
        assertNotNull("should have a reminder time", t.dueAt)
        assertEquals(4, hourOf(t.dueAt))
    }

    @Test
    fun callRightNow_isCallActionWithImmediateTime() {
        val todos = TodoParser.parseMultiple("call mummy right now", now)
        assertEquals(1, todos.size)
        val t = todos[0]
        assertEquals(ActionType.CALL, t.actionType)
        assertEquals("mummy", t.contactName)
        assertNotNull("immediate should schedule", t.dueAt)
    }

    @Test
    fun threeTasksInOneCommand_splitCorrectly() {
        val todos = TodoParser.parseMultiple("buy milk and call mummy at six pm and open youtube", now)
        assertEquals(3, todos.size)
        assertEquals(ActionType.NONE, todos[0].actionType)   // buy milk
        assertEquals(ActionType.CALL, todos[1].actionType)   // call mummy
        assertEquals(18, hourOf(todos[1].dueAt))             // 6 pm
        assertEquals(ActionType.OPEN_APP, todos[2].actionType)
    }

    @Test
    fun openYoutubeMusic() {
        val todos = TodoParser.parseMultiple("open youtube music at eight pm", now)
        assertEquals(1, todos.size)
        assertEquals(ActionType.OPEN_APP, todos[0].actionType)
        assertEquals("youtube music", todos[0].actionArg)
        assertEquals(20, hourOf(todos[0].dueAt))
    }

    @Test
    fun messageWithAnd_isNotSplit() {
        val todos = TodoParser.parseMultiple("text mom i am coming and bringing food", now)
        assertEquals(1, todos.size)
        assertEquals(ActionType.SMS, todos[0].actionType)
        assertTrue(todos[0].actionArg!!.contains("bringing food"))
    }

    @Test
    fun payRentOnThirtyOne() {
        val todos = TodoParser.parseMultiple("remind me to pay rent on thirty one", now)
        assertEquals(1, todos.size)
        val day = Instant.ofEpochMilli(todos[0].dueAt!!).atZone(ZoneId.systemDefault()).dayOfMonth
        assertEquals(31, day)
    }

    @Test
    fun dailyRecurring() {
        val todos = TodoParser.parseMultiple("daily standup at nine am", now)
        assertEquals(1, todos.size)
        assertEquals(Recurrence.DAILY, todos[0].recurrence)
        assertEquals(9, hourOf(todos[0].dueAt))
    }

    @Test
    fun andThenSeparatorWithFiller() {
        val todos = TodoParser.parseMultiple("watch a movie and then i want to call papa", now)
        assertEquals(2, todos.size)
        assertEquals("Watch movie", todos[0].title)
        assertEquals(ActionType.CALL, todos[1].actionType)
        assertEquals("papa", todos[1].contactName)
    }

    @Test
    fun tenTasksInOneCommand() {
        val cmd = "call mom and text dad and open youtube and play music and buy milk " +
            "and email boss and navigate to office and watch movie and pay rent and message sam"
        val todos = TodoParser.parseMultiple(cmd, now)
        assertEquals(10, todos.size)
    }

    @Test
    fun hinglish_callMummyTomorrowEvening() {
        val todos = TodoParser.parseMultiple("kal shaam 6 baje mummy ko call karo", now)
        assertEquals(1, todos.size)
        assertEquals(ActionType.CALL, todos[0].actionType)
        assertEquals("mummy", todos[0].contactName)
        assertEquals(18, hourOf(todos[0].dueAt))
    }

    @Test
    fun hinglish_openApp() {
        val todos = TodoParser.parseMultiple("aaj youtube kholo", now)
        assertEquals(1, todos.size)
        assertEquals(ActionType.OPEN_APP, todos[0].actionType)
        assertEquals("youtube", todos[0].actionArg)
    }

    @Test
    fun plainTaskNoTime() {
        val todos = TodoParser.parseMultiple("buy groceries", now)
        assertEquals(1, todos.size)
        assertEquals("Buy groceries", todos[0].title)
        assertNull(todos[0].dueAt)
        assertEquals(ActionType.NONE, todos[0].actionType)
    }
}
