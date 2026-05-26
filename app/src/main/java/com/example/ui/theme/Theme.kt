package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SberGreenPrimary,
    secondary = SberGreenBright,
    tertiary = SberGreenDark,
    background = SberBackgroundDark,
    surface = SberSurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = SberOnSurfaceDark,
    onSurface = SberOnSurfaceDark,
    surfaceVariant = Color(0xFF1B2E21),
    onSurfaceVariant = SberOnSurfaceDark,
    primaryContainer = SberGreenDark,
    onPrimaryContainer = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = SberGreenPrimary,
    secondary = SberGreenDark,
    tertiary = SberGreenBright,
    background = SberBackgroundLight,
    surface = SberSurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = SberOnSurfaceLight,
    onSurface = SberOnSurfaceLight,
    surfaceVariant = Color(0xFFEDF2EE),
    onSurfaceVariant = SberOnSurfaceLight,
    primaryContainer = Color(0xFFD3EED8),
    onPrimaryContainer = SberGreenDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
