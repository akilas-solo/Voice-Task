package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkYellowColorScheme = darkColorScheme(
    primary = DarkYellowPrimary,
    secondary = DarkYellowSecondary,
    tertiary = DarkYellowTertiary,
    background = DarkYellowBackground,
    surface = DarkYellowSurface,
    onPrimary = DarkYellowOnPrimary,
    onBackground = DarkYellowOnBackground,
    onSurface = DarkYellowOnSurface,
    surfaceVariant = DarkYellowSurface,
    onSurfaceVariant = DarkYellowOnSurface
)

private val LemonColorScheme = lightColorScheme(
    primary = LemonPrimary,
    secondary = LemonSecondary,
    tertiary = LemonTertiary,
    background = LemonBackground,
    surface = LemonSurface,
    onPrimary = LemonOnPrimary,
    onBackground = LemonOnBackground,
    onSurface = LemonOnSurface,
    surfaceVariant = LemonSurface,
    onSurfaceVariant = LemonOnSurface
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkYellowColorScheme
    } else {
        LemonColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
