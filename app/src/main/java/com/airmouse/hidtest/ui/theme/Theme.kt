package com.airmouse.hidtest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AirMouseColors {
    val Background = Color(0xFF0B0E14)
    val Surface = Color(0xFF121722)
    val SurfaceElevated = Color(0xFF18202F)
    val TouchpadSurface = Color(0xFF0E131D)
    val Border = Color(0xFF232D40)
    val BorderActive = Color(0xFF38BDF8)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    val Accent = Color(0xFF38BDF8)
    val AccentDim = Color(0x2638BDF8)
    val Success = Color(0xFF22C55E)
    val SuccessDim = Color(0x2222C55E)
    val Warning = Color(0xFFF59E0B)
    val WarningDim = Color(0x24F59E0B)
    val Error = Color(0xFFEF4444)
    val ErrorDim = Color(0x24EF4444)
}

private val AirMouseDarkColorScheme = darkColorScheme(
    primary = AirMouseColors.Accent,
    onPrimary = Color(0xFF06131F),
    primaryContainer = Color(0xFF0F293D),
    onPrimaryContainer = AirMouseColors.Accent,
    secondary = AirMouseColors.Success,
    onSecondary = Color(0xFF052010),
    background = AirMouseColors.Background,
    onBackground = AirMouseColors.TextPrimary,
    surface = AirMouseColors.Surface,
    onSurface = AirMouseColors.TextPrimary,
    surfaceVariant = AirMouseColors.SurfaceElevated,
    onSurfaceVariant = AirMouseColors.TextSecondary,
    outline = AirMouseColors.Border,
    error = AirMouseColors.Error,
    onError = Color(0xFF2B0808)
)

private val AirMouseTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun AirMouseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AirMouseDarkColorScheme,
        typography = AirMouseTypography,
        content = content
    )
}
