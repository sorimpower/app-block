package com.sorimpower.app.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun SorimPowerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
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
