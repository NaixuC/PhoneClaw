package com.phoneclaw.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val LightScheme = lightColorScheme(
    primary = Fern, onPrimary = Panel, primaryContainer = Mint, onPrimaryContainer = FernDark,
    secondary = Sky, onSecondary = Panel, secondaryContainer = SkyLight,
    tertiary = Coral, onTertiary = Panel,
    background = Paper, onBackground = Ink,
    surface = Panel, onSurface = Ink,
    surfaceVariant = Paper, onSurfaceVariant = MutedInk,
    outline = Line, outlineVariant = LineLight,
    error = Coral, onError = Panel,
)

private val DarkScheme = darkColorScheme(
    primary = Mint, onPrimary = FernDark, primaryContainer = Fern, onPrimaryContainer = Mint,
    secondary = SkyLight, onSecondary = Ink, secondaryContainer = Sky,
    tertiary = CoralLight, onTertiary = Ink,
    background = Ink, onBackground = Paper,
    surface = InkSecondary, onSurface = Paper,
    surfaceVariant = InkSecondary, onSurfaceVariant = MutedInk,
    outline = MutedInk,
    error = CoralLight, onError = Ink,
)

@Composable
fun OperitTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.decorView.systemUiVisibility =
                    (if (!darkTheme) android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0) or
                    (if (!darkTheme) android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = OperitTypography, content = content)
}
