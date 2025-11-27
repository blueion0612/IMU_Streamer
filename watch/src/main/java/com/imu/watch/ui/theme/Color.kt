package com.imu.watch.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors

// New color scheme based on tree logo
val PrimaryBlue = Color(0xFF1A3A5C)      // Dark blue from tree trunk
val SecondaryBlue = Color(0xFF2196F3)    // Light blue from leaves
val AccentBlue = Color(0xFF4FC3F7)       // Bright blue accent
val DarkBackground = Color(0xFF0D1520)   // Dark background
val SurfaceColor = Color(0xFF1E2A38)     // Card/surface color
val StatusGreen = Color(0xFF4CAF50)      // Connected/active status
val StatusRed = Color(0xFFE53935)        // Disconnected/error status
val TextPrimary = Color(0xFFFFFFFF)      // White text
val TextSecondary = Color(0xFFB0BEC5)    // Gray text

internal val wearColorPalette: Colors = Colors(
    primary = SecondaryBlue,
    primaryVariant = PrimaryBlue,
    secondary = AccentBlue,
    secondaryVariant = AccentBlue,
    error = StatusRed,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onError = Color.White
)
