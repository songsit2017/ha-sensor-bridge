package dev.haos.nativeapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp

// Palette taken from Home Assistant's own look: its blue (#03A9F4, a touch deeper for text contrast),
// near-black dark theme with #1C1C1C cards, and a light theme with white cards on a pale grey page.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0288D1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EEFB),
    onPrimaryContainer = Color(0xFF012A40),
    secondary = Color(0xFF4A6572),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1F1F9),
    onSecondaryContainer = Color(0xFF0D2A38),
    background = Color(0xFFF1F4F7),
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFF1F4F7),
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFE3E9ED),
    onSurfaceVariant = Color(0xFF5F6B73),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFAFBFC),
    surfaceContainerHigh = Color(0xFFEDF1F4),
    surfaceContainerHighest = Color(0xFFE3E9ED),
    outline = Color(0xFF8A969D),
    outlineVariant = Color(0xFFDDE3E7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF29B6F6),
    onPrimary = Color(0xFF002A3D),
    primaryContainer = Color(0xFF0B3C57),
    onPrimaryContainer = Color(0xFFCDEBFA),
    secondary = Color(0xFF9FBBCB),
    onSecondary = Color(0xFF0A2431),
    secondaryContainer = Color(0xFF1F3441),
    onSecondaryContainer = Color(0xFFD3E6F2),
    background = Color(0xFF111111),
    onBackground = Color(0xFFE1E1E1),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFE1E1E1),
    surfaceVariant = Color(0xFF2B3338),
    onSurfaceVariant = Color(0xFFA5ADB3),
    surfaceContainerLowest = Color(0xFF0B0B0B),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = Color(0xFF1C1C1C),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF303030),
    outline = Color(0xFF6E777D),
    outlineVariant = Color(0xFF333A3F),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "ตามระบบ"),
    LIGHT("light", "สว่าง"),
    DARK("dark", "มืด");

    companion object {
        fun fromKey(key: String) = values().firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Status colours that Material's scheme doesn't have; they follow the theme actually in use. */
object StatusColors {
    @Composable
    private fun dark() = isDarkTheme()

    @Composable
    fun ok(): Color = if (dark()) Color(0xFF7FD48A) else Color(0xFF1B7F37)

    @Composable
    fun warning(): Color = if (dark()) Color(0xFFFFB86B) else Color(0xFFB45F06)
}

/** True when the theme in use is the dark one (which can differ from the system's). */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Home Assistant's palette on every phone, so the app looks the same everywhere instead of taking
 * the wallpaper's colours. [mode] can override the system day/night setting.
 */
@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            // The top bar is blue (light) or near-black (dark): status bar icons are always light.
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}
