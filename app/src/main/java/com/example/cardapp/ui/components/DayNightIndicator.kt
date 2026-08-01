package com.example.cardapp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

/**
 * Значок в правом верхнем углу — индикатор времени суток (не кнопка).
 * Днём солнце, ночью луна. Приложение всегда в тёмной теме, переключателя
 * больше нет — значок носит чисто декоративно-информативный смысл.
 */
@Composable
fun DayNightIndicator(modifier: Modifier = Modifier) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val day = hour in 7..20
    Text(
        text = if (day) "☀️" else "🌙",
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = 12.dp),
    )
}
