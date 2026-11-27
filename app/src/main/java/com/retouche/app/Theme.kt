package com.retouche.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF1C1263),
    secondary = Color(0xFF0F9D8F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F4EF),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFE0457B),
    onTertiary = Color.White,
    background = Color(0xFFFBF9FF),
    onBackground = Color(0xFF1B1B23),
    surface = Color.White,
    onSurface = Color(0xFF1B1B23),
    surfaceVariant = Color(0xFFEEEAFB),
    onSurfaceVariant = Color(0xFF45424F),
    outline = Color(0xFF7A7686)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC5BFFF),
    onPrimary = Color(0xFF2A1D9E),
    primaryContainer = Color(0xFF4234C0),
    onPrimaryContainer = Color(0xFFE6E1FF),
    secondary = Color(0xFF5FD9CB),
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF00504A),
    onSecondaryContainer = Color(0xFFD0F4EF),
    tertiary = Color(0xFFFFB0C8),
    onTertiary = Color(0xFF5E1130),
    background = Color(0xFF121218),
    onBackground = Color(0xFFE5E1EB),
    surface = Color(0xFF1B1B24),
    onSurface = Color(0xFFE5E1EB),
    surfaceVariant = Color(0xFF2C2B38),
    onSurfaceVariant = Color(0xFFCBC7D6),
    outline = Color(0xFF948F9E)
)

// Textes volontairement grands et lisibles
private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun RetoucheTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
