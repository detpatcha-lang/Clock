package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OverclockColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = VoidBlack,
    primaryContainer = SurfaceNavy,
    onPrimaryContainer = NeonCyan,
    secondary = ElectricBlue,
    onSecondary = VoidBlack,
    secondaryContainer = CardNavy,
    onSecondaryContainer = ElectricBlue,
    tertiary = NeonEmerald,
    onTertiary = VoidBlack,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = DarkNavy,
    onSurface = TextPrimary,
    surfaceVariant = CardNavy,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = SurfaceNavy,
    error = NeonCrimson,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OverclockColorScheme,
        typography = Typography,
        content = content
    )
}
