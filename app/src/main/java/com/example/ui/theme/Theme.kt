package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val InstitutionalDarkColorScheme = darkColorScheme(
    primary = CyanLiquid,
    onPrimary = ObsidianBackground,
    primaryContainer = RoyalBlueLiquid,
    onPrimaryContainer = TextPrimary,
    secondary = EmeraldBullish,
    onSecondary = ObsidianBackground,
    secondaryContainer = ElevatedCardSurface,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberWarning,
    onTertiary = ObsidianBackground,
    error = CoralOverheated,
    onError = ObsidianBackground,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = GlassCardSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCardSurface,
    onSurfaceVariant = TextSecondary,
    outline = HairlineBorder,
    outlineVariant = SubtleDivider
)

val DepthTankShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NiftyDepthTankTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = InstitutionalDarkColorScheme,
        typography = Typography,
        shapes = DepthTankShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    NiftyDepthTankTheme(content = content)
}
