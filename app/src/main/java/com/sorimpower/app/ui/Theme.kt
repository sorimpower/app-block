package com.sorimpower.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AppNavy = Color(0xFF11182C)
val AppCobalt = Color(0xFF3757FF)
val AppOrange = Color(0xFFFF9D3D)
val AppLilac = Color(0xFFC8CFFF)
val AppCanvas = Color(0xFFF5F6FA)

private val LightColors = lightColorScheme(
    primary = AppCobalt,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF101B69),
    secondary = Color(0xFF555D77),
    secondaryContainer = Color(0xFFE0E3F2),
    tertiary = Color(0xFF8A4D00),
    tertiaryContainer = Color(0xFFFFDDBB),
    background = AppCanvas,
    onBackground = AppNavy,
    surface = Color(0xFFFFFFFF),
    onSurface = AppNavy,
    surfaceVariant = Color(0xFFE7E8EF),
    onSurfaceVariant = Color(0xFF606477),
    outline = Color(0xFF777B8F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C2FF),
    onPrimary = Color(0xFF0622C9),
    primaryContainer = Color(0xFF263CC4),
    onPrimaryContainer = Color(0xFFE0E4FF),
    secondary = Color(0xFFC3C6D6),
    secondaryContainer = Color(0xFF414556),
    tertiary = Color(0xFFFFB86D),
    tertiaryContainer = Color(0xFF693900),
    background = Color(0xFF0E1018),
    onBackground = Color(0xFFE7E8F1),
    surface = Color(0xFF181B25),
    onSurface = Color(0xFFE7E8F1),
    surfaceVariant = Color(0xFF292C38),
    onSurfaceVariant = Color(0xFFC4C6D2),
)

@Composable
fun SorimPowerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(30.dp),
            extraLarge = RoundedCornerShape(36.dp),
        ),
        content = content,
    )
}
