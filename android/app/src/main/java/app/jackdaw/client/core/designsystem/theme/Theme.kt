package app.jackdaw.client.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = JackdawAmber,
    onPrimary = JackdawBackgroundDark,
    primaryContainer = JackdawAmberContainer,
    onPrimaryContainer = JackdawAmber,
    secondary = JackdawSilver,
    onSecondary = JackdawBackgroundDark,
    background = JackdawBackgroundDark,
    onBackground = JackdawTextPrimaryDark,
    surface = JackdawSurfaceDark,
    onSurface = JackdawTextPrimaryDark,
    surfaceVariant = JackdawSurfaceElevatedDark,
    onSurfaceVariant = JackdawTextSecondaryDark,
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFF4B5563),
    surfaceContainerHighest = Color(0xFF263238),
    error = SlaUrgentRed,
    errorContainer = SlaUrgentContainerDark,
    onError = JackdawTextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = JackdawAmberHover,
    onPrimary = JackdawSurfaceLight,
    primaryContainer = JackdawAmberLight,
    onPrimaryContainer = JackdawTextPrimaryLight,
    secondary = JackdawTextSecondaryLight,
    onSecondary = JackdawSurfaceLight,
    background = JackdawBackgroundLight,
    onBackground = JackdawTextPrimaryLight,
    surface = JackdawSurfaceLight,
    onSurface = JackdawTextPrimaryLight,
    surfaceVariant = JackdawSurfaceElevatedLight,
    onSurfaceVariant = JackdawTextSecondaryLight,
    outline = Color(0xFF9CA3AF),
    outlineVariant = Color(0xFFD1D5DB),
    surfaceContainerHighest = Color(0xFFE5E7EB),
    error = SlaUrgentRed,
    errorContainer = SlaUrgentContainerLight,
    onError = JackdawSurfaceLight
)

@Composable
fun JackdawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun jackdawSwitchColors(): androidx.compose.material3.SwitchColors {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return androidx.compose.material3.SwitchDefaults.colors(
        checkedThumbColor = Color(0xFF161C24),
        checkedTrackColor = JackdawAmber,
        checkedBorderColor = JackdawAmber,
        uncheckedThumbColor = Color(0xFFFFFFFF),
        uncheckedTrackColor = if (isDark) Color(0xFF4B5563) else Color(0xFF9CA3AF),
        uncheckedBorderColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    )
}
