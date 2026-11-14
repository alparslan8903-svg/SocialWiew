package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val VibeReelDarkColorScheme = darkColorScheme(
    primary = NeonCoral,
    onPrimary = Color.White,
    primaryContainer = NeonCoralDarkContainer,
    onPrimaryContainer = NeonCoralLightText,
    secondary = ElectricCyan,
    onSecondary = Color(0xFF00382F),
    secondaryContainer = ElectricCyanDarkContainer,
    onSecondaryContainer = ElectricCyanLightText,
    tertiary = WarmAmber,
    onTertiary = Color(0xFF3D2400),
    tertiaryContainer = WarmAmberDarkContainer,
    background = ObsidianBg,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianTextSecondary,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    error = Color(0xFFFF5263),
    onError = Color.White
)

private val VibeReelLightColorScheme = lightColorScheme(
    primary = LightCoral,
    onPrimary = Color.White,
    primaryContainer = LightCoralContainer,
    onPrimaryContainer = LightCoralOnContainer,
    secondary = LightTeal,
    onSecondary = Color.White,
    secondaryContainer = LightTealContainer,
    onSecondaryContainer = LightTealOnContainer,
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerHigh = Color(0xFFE4E8F2),
    outline = LightOutline,
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val VibeReelShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun VibeReelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VibeReelDarkColorScheme else VibeReelLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VibeReelTypography,
        shapes = VibeReelShapes,
        content = content
    )
}
