package com.jonathaxs.tinystock.ui.theme

import androidx.compose.ui.graphics.Color
import com.jonathaxs.tinystock.core.model.AppAccentColor

/** Converte a cor de destaque do :core (ARGB cru) para o Color do Compose, na versao clara ou escura. */
fun AppAccentColor.color(darkTheme: Boolean): Color = Color(if (darkTheme) darkArgb else lightArgb)

/** Cor do texto e dos icones em cima da cor de destaque. */
val AppAccentColor.onColor: Color
    get() = if (prefersDarkContent) Color.Black else Color.White
