package com.sorimpower.app.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AppNavy = Color(0xFF19151F)
val AppCobalt = Color(0xFF9528E8)
val AppOrange = Color(0xFFE92A91)
val AppLilac = Color(0xFFEAD7FC)
val AppCanvas = Color(0xFFF7F7F9)
val AppPurple = AppCobalt
val AppMagenta = AppOrange
val AppGreen = Color(0xFF24C875)
val AppRed = Color(0xFFF15B64)
val AppSoftGray = Color(0xFFF1F1F3)
val AppSelected = Color(0xFFF2E7FC)

enum class AppThemeMode(val label: String) {
    SYSTEM("시스템 설정"),
    LIGHT("라이트"),
    DARK("다크"),
}

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 37.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

private val LightColors = lightColorScheme(
    primary = AppCobalt,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0DDFD),
    onPrimaryContainer = Color(0xFF451064),
    secondary = Color(0xFF735F7C),
    secondaryContainer = Color(0xFFF2ECF5),
    tertiary = AppMagenta,
    tertiaryContainer = Color(0xFFFDE2F1),
    background = AppCanvas,
    onBackground = AppNavy,
    surface = Color(0xFFFFFFFF),
    onSurface = AppNavy,
    surfaceVariant = AppSoftGray,
    onSurfaceVariant = Color(0xFF7D7881),
    outline = Color(0xFFD8D5DB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CB9FF),
    onPrimary = Color(0xFF002E62),
    primaryContainer = Color(0xFF173E68),
    onPrimaryContainer = Color(0xFFD5E6FF),
    secondary = Color(0xFFAFC7E7),
    onSecondary = Color(0xFF19324D),
    secondaryContainer = Color(0xFF304860),
    onSecondaryContainer = Color(0xFFD7E8FF),
    tertiary = Color(0xFF6DD8C8),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF14524A),
    onTertiaryContainer = Color(0xFFA9F2E5),
    background = Color(0xFF0C121A),
    onBackground = Color(0xFFE5EAF1),
    surface = Color(0xFF141B24),
    onSurface = Color(0xFFE5EAF1),
    surfaceVariant = Color(0xFF202A36),
    onSurfaceVariant = Color(0xFFBBC6D4),
    outline = Color(0xFF465565),
)

@Composable
fun SorimPowerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
