package com.jonathaxs.tinystock.ui.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jonathaxs.tinystock.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Data no formato medio na cor de destaque; o toque abre o calendario do Material. [label] null deixa so
 * a data (o titulo ja esta na secao). Equivale ao `DatePicker(.date)` compacto do iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDateRow(
    label: String?,
    accessibilityLabel: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    min: LocalDate? = null,
    max: LocalDate? = null,
) {
    var showsPicker by remember { mutableStateOf(false) }
    val text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button) { showsPicker = true }
            .semantics(mergeDescendants = true) { contentDescription = "$accessibilityLabel, $text" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (label != null) Text(label, modifier = Modifier.weight(1f))
        Text(text, color = MaterialTheme.colorScheme.primary)
    }
    if (!showsPicker) return

    // O seletor trabalha em milissegundos UTC da meia-noite de cada dia.
    fun millis(day: LocalDate) = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = millis(date),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val candidate = day(utcTimeMillis)
                return (min == null || !candidate.isBefore(min)) && (max == null || !candidate.isAfter(max))
            }

            override fun isSelectableYear(year: Int): Boolean =
                (min == null || year >= min.year) && (max == null || year <= max.year)
        },
    )
    DatePickerDialog(
        onDismissRequest = { showsPicker = false },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onDateChange(day(it)) }
                showsPicker = false
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = { TextButton(onClick = { showsPicker = false }) { Text(stringResource(R.string.common_cancel)) } },
    ) {
        // O titulo padrao do Material e grande demais para o dialogo e corta a data.
        DatePicker(
            state = state,
            headline = {
                Text(
                    state.selectedDateMillis?.let { day(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
                        ?: accessibilityLabel,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                )
            },
        )
    }
}

/** Escolha entre poucas opcoes lado a lado, com o selecionado neutro como no iOS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> FormSegmented(
    options: List<FormMenuOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(12.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.value == selected,
                onClick = { onSelect(option.value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    // Dentro do card branco: trilho cinza e a opcao escolhida em branco, como no iOS.
                    activeContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    activeContentColor = MaterialTheme.colorScheme.onSurface,
                    inactiveContainerColor = MaterialTheme.colorScheme.background,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                icon = {},
            ) { Text(option.title) }
        }
    }
}

/** Percentual digitado com o "%" ao lado. Com [label], vira linha "Taxa do canal ... 0 %". */
@Composable
fun FormPercentField(
    value: String,
    onValueChange: (String) -> Unit,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null) Text(label, modifier = Modifier.weight(1f))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = if (label != null) TextAlign.End else TextAlign.Start,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "0",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = if (label != null) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                inner()
            },
            modifier = (if (label != null) Modifier.weight(0.4f) else Modifier.weight(1f))
                .semantics { contentDescription = accessibilityLabel },
        )
        Text("%", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
