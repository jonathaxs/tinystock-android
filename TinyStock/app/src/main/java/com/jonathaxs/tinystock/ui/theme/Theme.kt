package com.jonathaxs.tinystock.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.jonathaxs.tinystock.core.model.AppAccentColor

// Fundo acinzentado e cards/superficies brancos, como as listas agrupadas do iOS. Os Card e as bottom
// sheets usam os surfaceContainer*, entao todos viram a cor de card.
private val LightColors = lightColorScheme(
    background = GroupedBackgroundLight,
    onBackground = Color.Black,
    surface = GroupedBackgroundLight,
    onSurface = Color.Black,
    surfaceContainerLowest = GroupedSurfaceLight,
    surfaceContainerLow = GroupedSurfaceLight,
    surfaceContainer = GroupedSurfaceLight,
    surfaceContainerHigh = GroupedSurfaceLight,
    surfaceContainerHighest = GroupedSurfaceLight,
    onSurfaceVariant = SecondaryLabelLight,
    outlineVariant = SeparatorLight,
)

private val DarkColors = darkColorScheme(
    background = GroupedBackgroundDark,
    onBackground = Color.White,
    surface = GroupedBackgroundDark,
    onSurface = Color.White,
    surfaceContainerLowest = GroupedSurfaceDark,
    surfaceContainerLow = GroupedSurfaceDark,
    surfaceContainer = GroupedSurfaceDark,
    surfaceContainerHigh = GroupedSurfaceHighDark,
    surfaceContainerHighest = GroupedSurfaceHighDark,
    onSurfaceVariant = SecondaryLabelDark,
    outlineVariant = SeparatorDark,
)

/**
 * Tema do app. Sem cores dinamicas do sistema: o TinyStock tem a propria cor de destaque, que vira o
 * `primary` (botoes, switches, links), como o tint do iOS.
 */
@Composable
fun TinyStockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AppAccentColor = AppAccentColor.Default,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkColors else LightColors
    val primary = accent.color(darkTheme)
    val colorScheme = base.copy(
        primary = primary,
        onPrimary = accent.onColor,
        primaryContainer = primary.copy(alpha = 0.15f),
        onPrimaryContainer = primary,
        secondaryContainer = if (darkTheme) GroupedSurfaceHighDark else Color(0xFFE5E5EA),
    )

    // Icones das barras do sistema (status e navegacao) acompanham o modo claro/escuro.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography) {
        // Texto e icones fora de uma Surface usam esta cor (sem isso o Compose usa preto, invisivel no escuro).
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground, content = content)
    }
}
