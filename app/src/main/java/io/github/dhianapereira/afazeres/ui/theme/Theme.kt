package io.github.dhianapereira.afazeres.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
val CategoryColors = listOf(Color(0xFF409AF5), Color(0xFFA57DE4), Color(0xFF59B968), Color(0xFFF06B6B), Color(0xFFA57DE4), Color(0xFFEAAF46), Color(0xFF96989D))
private val Light = lightColorScheme(primary = Color(0xFF101113), onPrimary = Color.White, background = Color(0xFFF8F8F9), surface = Color.White, surfaceVariant = Color(0xFFF0F0F2), onBackground = Color(0xFF101113), onSurface = Color(0xFF101113), onSurfaceVariant = Color(0xFF65676D), outline = Color(0xFFDFDFE3), secondaryContainer = Color(0xFFEDEDEF), onSecondaryContainer = Color(0xFF101113), error = Color(0xFFBB3030))
private val Dark = darkColorScheme(primary = Color(0xFFF3DFCF), onPrimary = Color(0xFF171411), background = Color(0xFF0B0D0E), surface = Color(0xFF17191B), surfaceVariant = Color(0xFF222529), onBackground = Color(0xFFF5EBE4), onSurface = Color(0xFFF5EBE4), onSurfaceVariant = Color(0xFFA5A8AE), outline = Color(0xFF35383D), secondaryContainer = Color(0xFF292C30), onSecondaryContainer = Color(0xFFF5EBE4), error = Color(0xFFFF7C77))
@Composable fun AfazeresTheme(theme: String = "system", content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (theme == "dark" || theme == "system" && isSystemInDarkTheme()) Dark else Light, shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)), content = content)
}
