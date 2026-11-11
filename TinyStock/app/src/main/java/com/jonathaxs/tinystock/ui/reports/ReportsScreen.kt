package com.jonathaxs.tinystock.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.report.SalesOrderReportRange
import com.jonathaxs.tinystock.core.report.SalesOrderReportSummary
import com.jonathaxs.tinystock.core.report.SalesReportPeriod
import com.jonathaxs.tinystock.ui.calendar.CalendarOrderFilter
import com.jonathaxs.tinystock.ui.components.AppMenuItem
import com.jonathaxs.tinystock.ui.components.AppMenuRow
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.components.appCard
import com.jonathaxs.tinystock.ui.icons.AppIcons
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

/** Nome curto de cada periodo, os mesmos textos do iOS. */
internal val SalesReportPeriod.nameRes: Int
    get() = when (this) {
        SalesReportPeriod.Today -> R.string.reports_period_today
        SalesReportPeriod.LastSevenDays -> R.string.reports_period_sevenDays
        SalesReportPeriod.CurrentMonth -> R.string.reports_period_month
        SalesReportPeriod.AllTime -> R.string.reports_period_all
    }

/**
 * Resultados por periodo e a operacao atual da loja aberta. Porte do `ReportsView` do iOS (C6).
 * O toque numa fila da operacao abre o Calendario ja filtrado.
 */
@Composable
fun ReportsScreen(
    storeID: UUID,
    onOpenCalendar: (CalendarOrderFilter) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = viewModel(),
) {
    LaunchedEffect(storeID) { viewModel.setStore(storeID) }
    val orders by viewModel.orders.collectAsStateWithLifecycle()

    // Periodo volta para o mes ao trocar de loja, como no iOS.
    var periodRaw by rememberSaveable(storeID) { mutableStateOf(SalesReportPeriod.CurrentMonth.rawValue) }
    var usesCustom by rememberSaveable(storeID) { mutableStateOf(false) }
    var customStartDay by rememberSaveable(storeID) { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var customEndDay by rememberSaveable(storeID) { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var showsCustom by rememberSaveable { mutableStateOf(false) }

    // Atualiza a cada minuto, como o TimelineView do iOS (virada do dia e atrasados).
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Instant.now()
        }
    }

    val zone = ZoneId.systemDefault()
    val period = SalesReportPeriod.fromRawValue(periodRaw) ?: SalesReportPeriod.CurrentMonth
    val range = if (usesCustom) {
        SalesOrderReportRange.custom(
            start = LocalDate.ofEpochDay(customStartDay).atStartOfDay(zone).toInstant(),
            end = LocalDate.ofEpochDay(customEndDay).atStartOfDay(zone).toInstant(),
        )
    } else {
        SalesOrderReportRange.preset(period)
    }
    val periodTitle = if (usesCustom) {
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        "${LocalDate.ofEpochDay(customStartDay).format(formatter)} - ${LocalDate.ofEpochDay(customEndDay).format(formatter)}"
    } else {
        stringResource(period.nameRes)
    }

    Box(modifier.fillMaxSize()) {
        when (val list = orders) {
            null -> Unit
            else -> if (list.isEmpty()) {
                NoDataState(Modifier.align(Alignment.Center))
            } else {
                ReportsBody(
                    orders = list,
                    storeID = storeID,
                    range = range,
                    now = now,
                    zone = zone,
                    periodTitle = periodTitle,
                    selectedPeriod = period.takeUnless { usesCustom },
                    onSelectPeriod = {
                        periodRaw = it.rawValue
                        usesCustom = false
                    },
                    onCustom = { showsCustom = true },
                    usesCustom = usesCustom,
                    onOpenCalendar = onOpenCalendar,
                )
            }
        }
    }

    if (showsCustom) {
        CustomPeriodDialog(
            initialStartDay = customStartDay,
            initialEndDay = customEndDay,
            onDismiss = { showsCustom = false },
            onApply = { start, end ->
                customStartDay = start
                customEndDay = end
                usesCustom = true
                showsCustom = false
            },
        )
    }
}

@Composable
private fun ReportsBody(
    orders: List<SalesOrder>,
    storeID: UUID,
    range: SalesOrderReportRange,
    now: Instant,
    zone: ZoneId,
    periodTitle: String,
    selectedPeriod: SalesReportPeriod?,
    usesCustom: Boolean,
    onSelectPeriod: (SalesReportPeriod) -> Unit,
    onCustom: () -> Unit,
    onOpenCalendar: (CalendarOrderFilter) -> Unit,
) {
    val summary = remember(orders, storeID, range, now) {
        SalesOrderReportSummary(orders = orders, storeID = storeID, range = range, reference = now, zone = zone)
    }
    Column(Modifier.fillMaxSize()) {
        PeriodMenu(periodTitle, selectedPeriod, usesCustom, onSelectPeriod, onCustom)
        LazyColumn(
            contentPadding = PaddingValues(start = AppStyle.HorizontalPadding, end = AppStyle.HorizontalPadding, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item("operation") { OperationSection(summary.operations, onOpenCalendar) }
            if (summary.received.isEmpty) {
                item("empty") { EmptyPeriodState() }
            } else {
                item("received") { ReceivedSection(summary.received) }
                item("best") { BestSellersSection(summary.bestSellingProducts()) }
                item("channels") { ChannelsSection(summary.channelGroups) }
                item("daily") { DailySection(summary.dayGroups, zone) }
            }
            item("activity") { ActivitySection(summary) }
            item("bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

/** Seletor de periodo: linha arredondada com o periodo atual e o menu de opcoes. */
@Composable
private fun PeriodMenu(
    periodTitle: String,
    selected: SalesReportPeriod?,
    usesCustom: Boolean,
    onSelect: (SalesReportPeriod) -> Unit,
    onCustom: () -> Unit,
) {
    AppMenuRow(
        icon = Icons.Filled.DateRange,
        title = stringResource(R.string.reports_period_label),
        value = periodTitle,
        modifier = Modifier.padding(horizontal = AppStyle.HorizontalPadding, vertical = 8.dp),
    ) { close ->
        for (option in SalesReportPeriod.entries) {
            AppMenuItem(stringResource(option.nameRes), option == selected, onClick = {
                close()
                onSelect(option)
            })
        }
        HorizontalDivider()
        AppMenuItem(stringResource(R.string.reports_period_custom), usesCustom, onClick = {
            close()
            onCustom()
        })
    }
}

/** Intervalo de dias inteiros. O seletor de intervalo do Material ja impede o fim antes do inicio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPeriodDialog(
    initialStartDay: Long,
    initialEndDay: Long,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit,
) {
    // O seletor trabalha em milissegundos UTC da meia-noite de cada dia.
    fun toMillis(day: Long) = LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun toDay(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = toMillis(initialStartDay),
        initialSelectedEndDateMillis = toMillis(initialEndDay),
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onApply(toDay(start!!), toDay(end ?: start!!)) }, enabled = start != null) {
                Text(stringResource(R.string.common_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    ) {
        DateRangePicker(
            state = state,
            title = {
                Text(
                    stringResource(R.string.reports_custom_title),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
            // O titulo padrao do Material e grande demais para o dialogo e quebra as datas.
            headline = {
                val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                val startText = start?.let { LocalDate.ofEpochDay(toDay(it)).format(formatter) }
                    ?: stringResource(R.string.reports_custom_start)
                val endText = end?.let { LocalDate.ofEpochDay(toDay(it)).format(formatter) }
                    ?: stringResource(R.string.reports_custom_end)
                Text(
                    "$startText - $endText",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Loja sem nenhum pedido ainda. */
@Composable
private fun NoDataState(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(AppIcons.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
        Text(stringResource(R.string.reports_placeholder_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.reports_placeholder_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Nenhuma venda no periodo escolhido, dentro de um card. */
@Composable
private fun EmptyPeriodState() {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .appCard()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text(stringResource(R.string.reports_empty_period_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.reports_empty_period_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
