package app.jackdaw.client.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
    outline = JackdawBorderDark,
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
    outline = JackdawBorderLight,
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
