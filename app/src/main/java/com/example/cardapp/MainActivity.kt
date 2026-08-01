package com.example.cardapp

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardapp.ui.AppViewModel
import com.example.cardapp.ui.CardApp
import com.example.cardapp.ui.components.AppBackground
import com.example.cardapp.ui.theme.CardAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            val vm: AppViewModel = viewModel()
            CardAppTheme {
                // Меш-фон из палитры фото под всем приложением
                AppBackground {
                    CardApp(vm)
                }
            }
        }
    }

    /**
     * Просит систему отдавать приложению максимальную частоту обновления
     * дисплея (например, 120 Гц) при текущем разрешении. Иначе многие
     * прошивки держат обычные приложения на 60 Гц ради экономии батареи.
     */
    private fun requestHighestRefreshRate() {
        @Suppress("DEPRECATION")
        val display =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
            else windowManager.defaultDisplay
        display ?: return
        val current = display.mode ?: return
        val best = display.supportedModes
            .filter {
                it.physicalWidth == current.physicalWidth &&
                    it.physicalHeight == current.physicalHeight
            }
            .maxByOrNull { it.refreshRate } ?: return
        if (best.modeId != current.modeId) {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = best.modeId
            }
        }
    }
}
