package com.example.voicetodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- Monochrome palette: black / white / gray only ----
private val Ink = Color(0xFF111114)
private val Graphite = Color(0xFF3A3A3E)
private val Steel = Color(0xFF6E6E76)
private val Mist = Color(0xFFEDEDF0)
private val Cloud = Color(0xFFF6F6F8)
private val Line = Color(0xFFDDDDE2)
private val Snow = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Snow,
    primaryContainer = Ink,
    onPrimaryContainer = Snow,
    secondary = Graphite,
    onSecondary = Snow,
    background = Cloud,
    onBackground = Ink,
    surface = Snow,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Steel,
    outline = Line,
    outlineVariant = Line,
    error = Color(0xFF9A1C1C),
    onError = Snow,
)

private val DarkColors = darkColorScheme(
    primary = Snow,
    onPrimary = Ink,
    primaryContainer = Snow,
    onPrimaryContainer = Ink,
    secondary = Color(0xFFCACACF),
    onSecondary = Ink,
    background = Color(0xFF0B0B0D),
    onBackground = Snow,
    surface = Color(0xFF161619),
    onSurface = Snow,
    surfaceVariant = Color(0xFF232327),
    onSurfaceVariant = Color(0xFFA6A6AE),
    outline = Color(0xFF3A3A3E),
    outlineVariant = Color(0xFF2A2A2E),
    error = Color(0xFFE57373),
    onError = Ink,
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
)

@Composable
fun VoiceTodoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
