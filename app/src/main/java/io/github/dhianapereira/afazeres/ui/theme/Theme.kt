package io.github.dhianapereira.afazeres.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = LightContent,
    onPrimary = LightSurface,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightContent,
    onSurface = LightContent,
    onSurfaceVariant = LightContentMuted,
    outline = LightOutline,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightContent,
    error = LightError,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkContent,
    onSurface = DarkContent,
    onSurfaceVariant = DarkContentMuted,
    outline = DarkOutline,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkContent,
    error = DarkError,
)

@Composable
fun AfazeresTheme(theme: String = "system", content: @Composable () -> Unit) {
    val darkTheme = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
