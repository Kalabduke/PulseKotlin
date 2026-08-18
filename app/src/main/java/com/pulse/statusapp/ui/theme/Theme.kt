package com.pulse.statusapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Telegram-style dark palette (matches the web app's theme)
val BgDark = Color(0xFF0B141A)
val BgElevated = Color(0xFF17212B)
val SurfaceDark = Color(0xFF1E2C3A)
val Accent = Color(0xFF2AABEE)      // Telegram blue
val AccentGreen = Color(0xFF31D158) // online dot
val BgLight = Color(0xFFF5F7FA)
val OnDark = Color(0xFFE7ECEF)
val Muted = Color(0xFF93A4B5)

private val DarkColors = darkColorScheme(
    primary = Accent,
    secondary = AccentGreen,
    background = BgDark,
    surface = SurfaceDark,
    surfaceVariant = BgElevated,
    onPrimary = Color.White,
    onBackground = OnDark,
    onSurface = OnDark,
    onSurfaceVariant = Muted,
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Accent,
    secondary = AccentGreen,
    background = BgLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFE7ECEF),
    onPrimary = Color.White,
    onBackground = Color(0xFF0B141A),
    onSurface = Color(0xFF0B141A),
    onSurfaceVariant = Color(0xFF5E6E7E),
)

@Composable
fun PulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
