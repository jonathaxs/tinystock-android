package com.jonathaxs.tinystock.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.ui.components.AppPage
import com.jonathaxs.tinystock.ui.components.AppSectionLabel
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.components.appCard
import com.jonathaxs.tinystock.ui.theme.color
import com.jonathaxs.tinystock.ui.theme.onColor

/** Nome de cada cor, os mesmos textos do iOS. */
private val AppAccentColor.nameRes: Int
    get() = when (this) {
        AppAccentColor.Cyan -> R.string.color_cyan
        AppAccentColor.Green -> R.string.color_green
        AppAccentColor.Blue -> R.string.color_blue
        AppAccentColor.Purple -> R.string.color_purple
        AppAccentColor.Red -> R.string.color_red
        AppAccentColor.Orange -> R.string.color_orange
        AppAccentColor.Yellow -> R.string.color_yellow
        AppAccentColor.Pink -> R.string.color_pink
    }

/** Escolha da cor de destaque em grade de 4 colunas. Porte do `ColorSettingsView` do iOS. */
@Composable
fun ColorSettingsScreen(selected: AppAccentColor, onSelect: (AppAccentColor) -> Unit, onBack: () -> Unit) {
    AppPage(stringResource(R.string.settings_color_title), onBack) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppStyle.HorizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppSectionLabel(stringResource(R.string.settings_color_section))
            Column(
                Modifier
                    .fillMaxWidth()
                    .appCard()
                    .padding(vertical = 20.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                for (row in AppAccentColor.entries.chunked(4)) {
                    Row(Modifier.fillMaxWidth()) {
                        for (color in row) {
                            ColorButton(color, color == selected, { onSelect(color) }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorButton(color: AppAccentColor, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val fill = color.color(isSystemInDarkTheme())
    val name = stringResource(color.nameRes)
    val state = stringResource(if (isSelected) R.string.accessibility_selected else R.string.accessibility_notSelected)
    val haptics = LocalHapticFeedback.current
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = isSelected, role = Role.RadioButton) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            }
            .padding(vertical = 6.dp)
            .semantics {
                contentDescription = name
                stateDescription = state
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(60.dp)
                .border(2.5.dp, if (isSelected) fill else Color.Transparent, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = color.onColor, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
