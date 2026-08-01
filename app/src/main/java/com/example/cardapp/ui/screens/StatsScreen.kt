package com.example.cardapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardapp.ui.AppViewModel
import com.example.cardapp.ui.DAY_MS
import com.example.cardapp.ui.components.LiquidGlass
import com.example.cardapp.ui.plural
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val words by vm.words.collectAsState()
    val events by vm.events.collectAsState()

    val todayStart = dayStart(System.currentTimeMillis())
    val todayEvents = events.filter { it.timestamp >= todayStart }
    val todayCorrect = todayEvents.count { it.remembered }
    val todayPercent =
        if (todayEvents.isNotEmpty()) "${todayCorrect * 100 / todayEvents.size}%" else "—"

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                value = "${words.size}",
                label = plural(words.size, "слово в словаре", "слова в словаре", "слов в словаре"),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = "${todayEvents.size}",
                label = plural(todayEvents.size, "ответ сегодня", "ответа сегодня", "ответов сегодня"),
                modifier = Modifier.weight(1f),
            )
            StatCard(value = todayPercent, label = "верных ответов", modifier = Modifier.weight(1f))
        }

        LiquidGlass(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Последние 7 дней", style = MaterialTheme.typography.titleMedium)
                for (i in 0..6) {
                    val dayStartMs = todayStart - i * DAY_MS
                    val dayEvents = events.filter {
                        it.timestamp >= dayStartMs && it.timestamp < dayStartMs + DAY_MS
                    }
                    val correct = dayEvents.count { it.remembered }
                    DayRow(
                        label = when (i) {
                            0 -> "Сегодня"
                            1 -> "Вчера"
                            else -> SimpleDateFormat("EEE, d MMM", Locale.getDefault())
                                .format(Date(dayStartMs))
                        },
                        correct = correct,
                        total = dayEvents.size,
                    )
                }
            }
        }

    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    LiquidGlass(shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayRow(label: String, correct: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(110.dp),
        )
        LinearProgressIndicator(
            progress = { if (total > 0) correct.toFloat() / total else 0f },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (total > 0) "$correct/$total" else "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(80.dp),
        )
    }
}

private fun dayStart(timestamp: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
