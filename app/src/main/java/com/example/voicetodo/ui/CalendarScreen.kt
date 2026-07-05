package com.example.voicetodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.voicetodo.data.Todo
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun Todo.localDate(): LocalDate? = dueAt?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
}

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

@Composable
fun CalendarScreen(
    todos: List<Todo>,
    onSpeakForDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }

    val byDate = remember(todos) { todos.mapNotNull { t -> t.localDate()?.let { it to t } }
        .groupBy({ it.first }, { it.second }) }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Filled.ChevronLeft, "Previous month")
            }
            Text(month.format(monthFmt), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.Filled.ChevronRight, "Next month")
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        MonthGrid(
            month = month,
            selected = selected,
            markedDays = byDate.keys,
            onSelect = { selected = it }
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                selected.format(DateTimeFormatter.ofPattern("EEEE, dd MMM", Locale.getDefault())),
                style = MaterialTheme.typography.titleMedium
            )
            FilledIconButton(onClick = { onSpeakForDate(selected) }) {
                Icon(Icons.Filled.Mic, "Add by voice for this day")
            }
        }

        val dayTodos = byDate[selected].orEmpty()
        if (dayTodos.isEmpty()) {
            Text("No tasks for this day.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(dayTodos, key = { it.id }) { t ->
                    Text("• ${t.title}   (${formatDue(t.dueAt)?.substringAfter("· ") ?: ""})")
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    markedDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit
) {
    val firstDay = month.atDay(1)
    // Monday = 1 … Sunday = 7  -> leading blanks before the 1st
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val cells = leadingBlanks + daysInMonth
    val rows = (cells + 6) / 7

    Column {
        var day = 1
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    if (index < leadingBlanks || day > daysInMonth) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(day)
                        DayCell(
                            date = date,
                            isSelected = date == selected,
                            hasTasks = date in markedDays,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(date) }
                        )
                        day++
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    hasTasks: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val today = date == LocalDate.now()
    Box(
        modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> scheme.primary
                    today -> scheme.surfaceVariant
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                color = if (isSelected) scheme.onPrimary else scheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium
            )
            if (hasTasks) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) scheme.onPrimary else scheme.primary)
                        .padding(3.dp)
                )
            }
        }
    }
}
