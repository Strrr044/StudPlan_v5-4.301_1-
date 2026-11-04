package com.example.studplan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private fun schemeFrom(c: AppColors, dark: Boolean): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primarySoft,
            onPrimaryContainer = c.foreground,
            secondaryContainer = c.primarySoft,
            onSecondaryContainer = c.foreground,
            background = c.background,
            onBackground = c.foreground,
            surface = c.card,
            onSurface = c.foreground,
            surfaceVariant = c.muted,
            onSurfaceVariant = c.mutedForeground,
            surfaceContainerLowest = c.card,
            surfaceContainerLow = c.card,
            surfaceContainer = c.card,
            surfaceContainerHigh = c.card,
            surfaceContainerHighest = c.card,
            outline = c.border,
            outlineVariant = c.border,
            error = c.danger,
            errorContainer = c.dangerSoft
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primarySoft,
            onPrimaryContainer = c.foreground,
            secondaryContainer = c.primarySoft,
            onSecondaryContainer = c.foreground,
            background = c.background,
            onBackground = c.foreground,
            surface = c.card,
            onSurface = c.foreground,
            surfaceVariant = c.muted,
            onSurfaceVariant = c.mutedForeground,
            surfaceContainerLowest = c.card,
            surfaceContainerLow = c.card,
            surfaceContainer = c.card,
            surfaceContainerHigh = c.card,
            surfaceContainerHighest = c.card,
            outline = c.border,
            outlineVariant = c.border,
            error = c.danger,
            errorContainer = c.dangerSoft
        )
    }
}

@Composable
fun StudPlanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = schemeFrom(colors, darkTheme),
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}