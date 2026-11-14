package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DeepMossDarkColorScheme = darkColorScheme(
    primary = BotanicalPrimaryDark,
    onPrimary = BotanicalOnPrimaryDark,
    primaryContainer = BotanicalPrimaryContainerDark,
    onPrimaryContainer = BotanicalOnPrimaryContainerDark,
    secondary = BotanicalSecondaryDark,
    onSecondary = BotanicalOnPrimaryDark,
    secondaryContainer = BotanicalSecondaryContainerDark,
    onSecondaryContainer = BotanicalOnBackgroundDark,
    tertiary = BotanicalTertiaryDark,
    background = BotanicalBackgroundDark,
    onBackground = BotanicalOnBackgroundDark,
    surface = BotanicalSurfaceDark,
    onSurface = BotanicalOnSurfaceDark,
    surfaceVariant = BotanicalSurfaceVariantDark,
    onSurfaceVariant = BotanicalOnSurfaceVariantDark,
    outline = BotanicalOutlineDark,
    outlineVariant = BotanicalOutlineVariantDark,
    error = StudioErrorDark
)

private val NordicBotanicalLightColorScheme = lightColorScheme(
    primary = BotanicalPrimaryLight,
    onPrimary = BotanicalOnPrimaryLight,
    primaryContainer = BotanicalPrimaryContainerLight,
    onPrimaryContainer = BotanicalOnPrimaryContainerLight,
    secondary = BotanicalSecondaryLight,
    onSecondary = BotanicalOnPrimaryLight,
    secondaryContainer = BotanicalSecondaryContainerLight,
    onSecondaryContainer = BotanicalOnBackgroundLight,
    tertiary = BotanicalTertiaryLight,
    background = BotanicalBackgroundLight,
    onBackground = BotanicalOnBackgroundLight,
    surface = BotanicalSurfaceLight,
    onSurface = BotanicalOnSurfaceLight,
    surfaceVariant = BotanicalSurfaceVariantLight,
    onSurfaceVariant = BotanicalOnSurfaceVariantLight,
    outline = BotanicalOutlineLight,
    outlineVariant = BotanicalOutlineVariantLight,
    error = StudioErrorLight
)

val BotanicalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DeepMossDarkColorScheme
    } else {
        NordicBotanicalLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = BotanicalShapes,
        content = content
    )
}
