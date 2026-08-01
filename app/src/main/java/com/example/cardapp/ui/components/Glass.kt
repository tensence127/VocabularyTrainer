package com.example.cardapp.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Полупрозрачный фон для нижней навигации/панелей поверх меша. */
@Composable
fun glassBarColor(): Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
