package com.imu.phone.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable

private val DarkColorPalette = darkColors(
    primary = SecondaryBlue,
    primaryVariant = PrimaryBlue,
    secondary = AccentBlue,
    secondaryVariant = AccentBlue,
    background = DarkBackground,
    surface = CardBackground,
    error = StatusRed,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onError = TextPrimary
)

@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = DarkColorPalette,
        typography = Typography,
        content = content
    )
}
