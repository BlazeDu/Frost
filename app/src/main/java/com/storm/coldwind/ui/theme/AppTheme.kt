package com.storm.coldwind.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.storm.coldwind.ThemeManager

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3F51B5),
    secondary = Color(0xFF5C6BC0),
    tertiary = Color(0xFF7986CB),
    background = Color(0xFF1A1A2E),
    surface = Color(0xFF16213E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3F51B5),
    secondary = Color(0xFF5C6BC0),
    tertiary = Color(0xFF7986CB),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1A2E),
    onSurface = Color(0xFF1A1A2E)
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = ThemeManager.isDarkTheme(context)
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}