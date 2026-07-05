package com.example.voicetodo.nlp

import com.example.voicetodo.data.ActionType
import com.example.voicetodo.data.Recurrence
import com.example.voicetodo.data.Todo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Very small, offline, English-keyword natural-language parser.
 * Extracts a reminder date/time and recurrence from a spoken sentence,
 * then treats the leftover words as the todo title.
 *
 * Handles things like:
 *   "buy milk on 31"
 *   "call mom tomorrow at 6 pm"
 *   "daily standup at 9 am"
 *   "gym every day at 7:30 am"
 *   "submit report on 15 july at 5 pm"
 *   "meeting on monday at 10"
 */
object TodoParser {

    private val months = mapOf(
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "sept" to 9, "october" to 10,
        "oct" to 10, "november" to 11, "nov" to 11, "december" to 12, "dec" to 12
    )

    private val weekdays = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY
    )

    data class Parsed(val title: String, val dueAt: Long?, val recurrence: Recurrence)

    fun parse(rawInput: String, now: LocalDateTime = LocalDateTime.now()): Todo {
        // Hinglish -> English, then spoken numbers -> digits ("char baje" -> "at 4").
        var text = " ${normalizeNumbers(normalizeHinglish(rawInput.lowercase(Locale.US).trim()))} "
        val today = now.toLocalDate()

        // --- "now" / "right now" => fire almost immediately ---
        var immediate = false
        val nowRegex = Regex("\\b(right now|right away|immediately|as soon as possible|asap|now)\\b")
        if (nowRegex.containsMatchIn(text)) {
            immediate = true
            text = text.replace(nowRegex, " ")
        }

        // --- recurrence ---
        var recurrence = Recurrence.NONE
        if (Regex("\\b(daily|every ?day|each day)\\b").containsMatchIn(text)) {
            recurrence = Recurrence.DAILY
            text = text.replace(Regex("\\b(daily|every ?day|each day)\\b"), " ")
        } else if (Regex("\\b(weekly|every week|each week)\\b").containsMatchIn(text)) {
            recurrence = Recurrence.WEEKLY
            text = text.replace(Regex("\\b(weekly|every week|each week)\\b"), " ")
        }

        // --- time of day: "4 am", "at 9 am", "17:30", "5:30 pm", "at 10" ---
        var time: LocalTime? = null
        val timeRegex = Regex(
            "\\b(\\d{1,2}):(\\d{2})\\s*(am|pm)?\\b" +   // 1,2,3  HH:MM (am/pm)
                "|\\b(?:at\\s+)?(\\d{1,2})\\s*(am|pm)\\b" +  // 4,5    "4 am" / "at 4 pm"
                "|\\bat\\s+(\\d{1,2})\\b"                    // 6      "at 10"
        )
        timeRegex.find(text)?.let { m ->
            val g = m.groupValues
            var hour = -1
            var minute = 0
            var ampm = ""
            when {
                g[1].isNotEmpty() -> { hour = g[1].toInt(); minute = g[2].toIntOrNull() ?: 0; ampm = g[3] }
                g[4].isNotEmpty() -> { hour = g[4].toInt(); ampm = g[5] }
                g[6].isNotEmpty() -> { hour = g[6].toInt() }
            }
            if (ampm == "pm" && hour in 1..11) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            if (hour in 0..23 && minute in 0..59) {
                time = LocalTime.of(hour, minute)
                text = text.replace(m.value, " ")
            }
        }

        // --- date ---
        var date: LocalDate? = null

        when {
            Regex("\\btoday\\b").containsMatchIn(text) -> {
                date = today; text = text.replace(Regex("\\btoday\\b"), " ")
            }
            Regex("\\btomorrow\\b").containsMatchIn(text) -> {
                date = today.plusDays(1); text = text.replace(Regex("\\btomorrow\\b"), " ")
            }
        }

        // weekday: "on monday" / "monday"
        if (date == null) {
            for ((word, dow) in weekdays) {
                if (Regex("\\b$word\\b").containsMatchIn(text)) {
                    date = today.with(TemporalAdjusters.nextOrSame(dow))
                    if (!date!!.isAfter(today)) date = date!!.plusWeeks(1)
                    text = text.replace(Regex("\\b(on\\s+)?$word\\b"), " ")
                    break
                }
            }
        }

        // "15 july" / "july 15" / "on 15th"
        if (date == null) {
            val dayMonth = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(${months.keys.joinToString("|")})\\b").find(text)
            val monthDay = Regex("\\b(${months.keys.joinToString("|")})\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b").find(text)
            if (dayMonth != null) {
                val day = dayMonth.groupValues[1].toInt()
                val month = months.getValue(dayMonth.groupValues[2])
                date = safeDate(today, month, day)
                text = text.replace(dayMonth.value, " ")
            } else if (monthDay != null) {
                val month = months.getValue(monthDay.groupValues[1])
                val day = monthDay.groupValues[2].toInt()
                date = safeDate(today, month, day)
                text = text.replace(monthDay.value, " ")
            }
        }

        // plain "on 31" / "on the 31st" -> that day this month (or next if passed)
        if (date == null) {
            val dayOnly = Regex("\\bon\\s+(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\b").find(text)
            if (dayOnly != null) {
                val day = dayOnly.groupValues[1].toInt()
                date = safeDate(today, today.monthValue, day)
                text = text.replace(dayOnly.value, " ")
            }
        }

        // If a time but no date was given, assume today (or tomorrow if already past).
        if (date == null && (time != null || recurrence != Recurrence.NONE)) {
            date = today
        }
        if (date == today && time != null && LocalDateTime.of(today, time).isBefore(now)) {
            date = today.plusDays(1)
        }

        val dueAt = when {
            // "right now" -> fire in a few seconds (time to save + schedule).
            immediate -> now.plusSeconds(5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            date != null -> {
                val t = time ?: LocalTime.of(9, 0) // default reminder time
                LocalDateTime.of(date, t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            else -> null
        }

        // --- action detection: call / text / whatsapp / open app / navigate ---
        val spec = detectAction(text.replace(Regex("\\s+"), " ").trim())
        val title = spec?.title ?: cleanTitle(text).ifBlank { "Untitled task" }

        return Todo(
            title = title,
            dueAt = dueAt,
            recurrence = recurrence,
            actionType = spec?.type ?: ActionType.NONE,
            contactName = spec?.contactName,
            actionArg = spec?.arg
        )
    }

    // Verbs that start a new task — used to split "buy milk and call mom".
    private val taskVerbs = listOf(
        "call", "phone", "dial", "ring", "text", "message", "sms", "whatsapp",
        "open", "launch", "start", "navigate", "go", "drive", "buy", "get",
        "watch", "play", "remind", "remember", "schedule", "email", "send",
        "note", "book", "order", "pay", "set", "download", "pick"
    )

    /**
     * Parse ONE spoken command that may contain SEVERAL tasks and return a todo for each.
     * Splits on "and then", "then", "also", "next", and on "and" only when the next word
     * starts a new task (so "text mom I'm coming and bringing food" stays one task).
     */
    fun parseMultiple(rawInput: String, now: LocalDateTime = LocalDateTime.now()): List<Todo> {
        val parts = splitIntoTasks(rawInput)
        val todos = parts.map { parse(it, now) }
        return todos.ifEmpty { listOf(parse(rawInput, now)) }
    }

    private fun splitIntoTasks(input: String): List<String> {
        var t = normalizeHinglish(input.lowercase(Locale.US))
        // Strong separators always split.
        t = t.replace(Regex("\\b(?:and then|then|after that|afterwards|after which|also|next|plus)\\b"), "|")
        // A plain "and" splits when a new task verb follows — allowing filler like
        // "and i want to call ..." or "and please open ...".
        val verbs = taskVerbs.joinToString("|") { Regex.escape(it) }
        val filler = "(?:i want to |i need to |i have to |i wanna |i would like to |please |remind me to |remember to )?"
        t = t.replace(Regex("\\band\\s+$filler(?=(?:$verbs)\\b)"), "|")
        return t.split("|").map { it.trim() }.filter { it.isNotBlank() }
    }

    private data class ActionSpec(
        val type: ActionType,
        val contactName: String?,
        val arg: String?,
        val title: String
    )

    private fun detectAction(input: String): ActionSpec? {
        // Drop leading politeness/intent filler so "please i want to call mummy" still matches.
        var t = input.trim()
        val lead = Regex("^(?:please|can you|could you|hey|ok|okay|i want to|i need to|i have to|i wanna|i would like to|i'd like to|remind me to|remember to|let me)\\s+")
        while (lead.containsMatchIn(t)) {
            val next = t.replace(lead, "").trim()
            if (next == t) break
            t = next
        }
        if (t.isBlank()) return null

        Regex("^open\\s+(.+)$").find(t)?.let {
            val app = it.groupValues[1].trim()
            return ActionSpec(ActionType.OPEN_APP, null, app, "Open ${cap(app)}")
        }
        Regex("^(?:navigate to|navigate|directions to|take me to|go to|drive to)\\s+(.+)$").find(t)?.let {
            val place = it.groupValues[1].trim()
            return ActionSpec(ActionType.NAVIGATE, null, place, "Navigate to ${cap(place)}")
        }
        Regex("^(?:whatsapp|whats app|whatsap|wp)\\s+(\\w+)(?:\\s+(.*))?$").find(t)?.let {
            val name = it.groupValues[1]
            val msg = it.groupValues[2].trim().ifBlank { null }
            return ActionSpec(ActionType.WHATSAPP, name, msg, "WhatsApp ${cap(name)}" + (msg?.let { m -> ": $m" } ?: ""))
        }
        Regex("^(?:text|sms|send (?:a )?(?:text|sms|message)(?: to)?|message)\\s+(\\w+)(?:\\s+(.*))?$").find(t)?.let {
            val name = it.groupValues[1]
            val msg = it.groupValues[2].trim().ifBlank { null }
            return ActionSpec(ActionType.SMS, name, msg, "Text ${cap(name)}" + (msg?.let { m -> ": $m" } ?: ""))
        }
        Regex("^(?:call|phone|ring|dial)\\s+(.+)$").find(t)?.let {
            val name = it.groupValues[1].trim()
            return ActionSpec(ActionType.CALL, name, null, "Call ${cap(name)}")
        }
        return null
    }

    private fun cap(s: String) = s.trim().replaceFirstChar { it.uppercase() }

    private val onesMap = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9
    )
    private val numberWords = onesMap + mapOf(
        "zero" to 0, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50
    )
    private val tensMap = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50)

    private val hindiNumbers = mapOf(
        "ek" to "1", "do" to "2", "teen" to "3", "char" to "4", "chaar" to "4",
        "paanch" to "5", "panch" to "5", "chhe" to "6", "che" to "6", "chah" to "6",
        "saat" to "7", "aath" to "8", "nau" to "9", "das" to "10", "gyarah" to "11",
        "gyaarah" to "11", "barah" to "12", "baarah" to "12"
    )

    /**
     * Map common Hinglish to English so the same rules work:
     *   "kal shaam 6 baje mummy ko call karo" -> "tomorrow at 6 pm call mummy"
     *   "abhi papa ko phone karo"             -> "now call papa"
     *   "aaj youtube kholo"                   -> "today open youtube"
     */
    private fun normalizeHinglish(input: String): String {
        var t = " $input "
        // Hindi digits only when they qualify a clock ("do baje" = 2 o'clock).
        for ((w, d) in hindiNumbers) t = t.replace(Regex("\\b$w\\s+baje\\b"), "$d baje")
        // Time of day + "baje".
        t = t.replace(Regex("\\bsubah\\s*(\\d{1,2})\\s*baje\\b"), " at $1 am ")
        t = t.replace(Regex("\\b(?:shaam|sham|raat|dopahar|dupahar)\\s*(\\d{1,2})\\s*baje\\b"), " at $1 pm ")
        t = t.replace(Regex("\\b(\\d{1,2})\\s*baje\\b"), " at $1 ")
        // Verb-final Hindi word order: "X ko call karo" -> "call X".
        t = t.replace(Regex("\\b(\\w+)\\s+ko\\s+(?:call|phone)\\s*(?:karo|karna|kar|kardo)?\\b"), " call $1 ")
        t = t.replace(Regex("\\b(\\w+)\\s+ko\\s+(?:message|msg|text|whatsapp)\\s*(?:karo|karna|kar|bhejo|bhej)?\\b"), " text $1 ")
        t = t.replace(Regex("\\b(\\w+)\\s+(?:kholo|khol\\s*do|open\\s*karo)\\b"), " open $1 ")
        // Verb-first forms.
        t = t.replace(Regex("\\b(?:call|phone)\\s+(?:karo|karna|kar|kardo)\\b"), " call ")
        t = t.replace(Regex("\\b(?:message|msg|text)\\s+(?:karo|karna|kar|bhejo|bhej|kardo)\\b"), " text ")
        // Day / time words.
        t = t.replace(Regex("\\baaj\\b"), " today ")
        t = t.replace(Regex("\\bkal\\b"), " tomorrow ")
        t = t.replace(Regex("\\b(?:abhi|turant)\\b"), " now ")
        t = t.replace(Regex("\\baur\\b"), " and ")
        t = t.replace(Regex("\\byaad\\s+(?:dilao|dilana|dila|rakhna|rakho)\\b"), " remind me to ")
        // Drop leftover Hindi particles/helpers (safe, non-English tokens).
        t = t.replace(Regex("\\b(?:ko|ka|ki|ke|karo|karna|kardo)\\b"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    /** Turn spoken numbers into digits: "four" -> "4", "twenty five" -> "25", "half past nine" -> "9:30". */
    private fun normalizeNumbers(input: String): String {
        var t = input
        // Compound tens + ones first: "twenty five" -> "25".
        for ((tw, tv) in tensMap) for ((ow, ov) in onesMap) {
            t = t.replace(Regex("\\b$tw[\\s-]$ow\\b"), (tv + ov).toString())
        }
        // Standalone number words.
        for ((w, v) in numberWords) {
            t = t.replace(Regex("\\b$w\\b"), v.toString())
        }
        // Spoken clock phrases.
        t = t.replace(Regex("\\bhalf past (\\d{1,2})\\b")) { "${it.groupValues[1]}:30" }
        t = t.replace(Regex("\\bquarter past (\\d{1,2})\\b")) { "${it.groupValues[1]}:15" }
        t = t.replace(Regex("\\bquarter to (\\d{1,2})\\b")) {
            val h = it.groupValues[1].toInt(); "${if (h == 1) 12 else h - 1}:45"
        }
        t = t.replace(Regex("\\bo'?clock\\b"), " ")
        return t
    }

    /** Pick month/day; if that date already passed this month, roll to next month. */
    private fun safeDate(today: LocalDate, month: Int, day: Int): LocalDate {
        val year = if (month < today.monthValue) today.year + 1 else today.year
        val maxDay = LocalDate.of(year, month, 1).lengthOfMonth()
        var d = LocalDate.of(year, month, day.coerceIn(1, maxDay))
        if (d.isBefore(today) && month == today.monthValue) d = d.plusMonths(1)
        return d
    }

    private fun cleanTitle(text: String): String {
        var t = " $text "
        // Strip "intent" filler so "i want to watch movie" -> "watch movie".
        val fillers = listOf(
            "i want to", "i wanna", "i need to", "i have to", "i've got to", "i gotta",
            "i would like to", "i'd like to", "i am going to", "i'm going to",
            "i'm gonna", "going to", "gonna", "i will", "i'll", "let me", "let's",
            "i plan to", "i'm planning to", "planning to", "i should", "i must",
            "remind me to", "remind me", "remember to", "don't forget to",
            "add", "create", "note", "task", "todo", "to do", "please"
        )
        for (f in fillers) t = t.replace(Regex("\\b${Regex.escape(f)}\\b"), " ")
        // Leftover standalone prepositions/articles from date/time removal.
        t = t.replace(Regex("\\b(at|on|the|a|an)\\b"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
            .replaceFirstChar { it.uppercase() }
    }
}
