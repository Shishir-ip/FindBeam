package com.findbeam.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.findbeam.app.core.store.SettingsStore

/** Extra colors the Material scheme can't express directly. */
data class AppColors(
    val neon: Color,
    val danger: Color,
    val amber: Color,
    val border: Color,
    val textDim: Color,
    val textFaint: Color,
    val surfaceHi: Color,
    val isDark: Boolean
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        neon = Neon, danger = Danger, amber = Amber,
        border = DarkBorder, textDim = DarkTextDim, textFaint = DarkTextFaint,
        surfaceHi = DarkSurfaceHi, isDark = true
    )
}

private val DarkScheme = darkColorScheme(
    primary = Neon,
    onPrimary = Color(0xFF03130A),
    primaryContainer = Color(0xFF0C2B1B),
    onPrimaryContainer = NeonSoft,
    secondary = Color(0xFFB9C1C7),
    onSecondary = Color(0xFF0A0C0D),
    error = Danger,
    onError = Color(0xFF1A0507),
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceHi,
    onSurfaceVariant = DarkTextDim,
    outline = DarkBorder,
    outlineVariant = DarkBorder
)

private val LightScheme = lightColorScheme(
    primary = NeonDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8F5E6),
    onPrimaryContainer = Color(0xFF004524),
    secondary = Color(0xFF394248),
    onSecondary = Color(0xFFFFFFFF),
    error = DangerDark,
    onError = Color(0xFFFFFFFF),
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceHi,
    onSurfaceVariant = LightTextDim,
    outline = LightBorder,
    outlineVariant = LightBorder
)

@Composable
fun FindBeamTheme(
    themeMode: SettingsStore.ThemeMode,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        SettingsStore.ThemeMode.DARK -> true
        SettingsStore.ThemeMode.LIGHT -> false
        SettingsStore.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    val appColors = if (dark) {
        AppColors(
            neon = Neon, danger = Danger, amber = Amber,
            border = DarkBorder, textDim = DarkTextDim, textFaint = DarkTextFaint,
            surfaceHi = DarkSurfaceHi, isDark = true
        )
    } else {
        AppColors(
            neon = NeonDark, danger = DangerDark, amber = Color(0xFFB7791F),
            border = LightBorder, textDim = LightTextDim, textFaint = LightTextFaint,
            surfaceHi = LightSurfaceHi, isDark = false
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content
        )
    }
}
