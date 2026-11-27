package com.picklerally.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PickleRallyColorScheme = darkColorScheme(
    primary = LimeAccent,
    onPrimary = NavyDeep,
    primaryContainer = NavySelected,
    onPrimaryContainer = LimeBright,
    secondary = OrangeAccent,
    onSecondary = NavyDeep,
    secondaryContainer = NavyElevated,
    onSecondaryContainer = OrangeSoft,
    tertiary = SkyAccent,
    onTertiary = NavyDeep,
    background = CourtApron,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderSlate,
    outlineVariant = BorderSubtle,
    error = RoseFault,
    onError = TextPrimary,
    errorContainer = RoseDarkBg,
    onErrorContainer = TextPrimary,
)

private val PickleRallyShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun PickleRallyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PickleRallyColorScheme,
        typography = PickleRallyTypography,
        shapes = PickleRallyShapes,
        content = content,
    )
}
