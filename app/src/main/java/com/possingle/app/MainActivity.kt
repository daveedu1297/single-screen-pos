package com.possingle.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.possingle.app.ui.POSScreen
import com.possingle.app.viewmodel.POSViewModel

// A clean, minimal black-and-white palette instead of Material's default
// dynamic/purple colors — flat, high-contrast, "plain SaaS dashboard" look.
private val LightPos = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0F0F0),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF444444),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF1A1A1A),
    outline = Color(0xFFBDBDBD),
    error = Color(0xFFB00020)
)

private val DarkPos = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2A2A2A),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFCCCCCC),
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF616161),
    error = Color(0xFFCF6679)
)

class MainActivity : ComponentActivity() {

    private val viewModel: POSViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkMode = remember { mutableStateOf(false) }
            val colors = if (darkMode.value) DarkPos else LightPos
            MaterialTheme(colorScheme = colors) {
                POSScreen(
                    viewModel = viewModel,
                    darkModeEnabled = darkMode.value,
                    onToggleDarkMode = { darkMode.value = !darkMode.value }
                )
            }
        }
    }
}
