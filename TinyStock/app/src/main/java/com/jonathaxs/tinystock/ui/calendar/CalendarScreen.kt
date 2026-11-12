package com.jonathaxs.tinystock.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.service.SalesOrderSchedule
import com.jonathaxs.tinystock.ui.components.AppMenuItem
import com.jonathaxs.tinystock.ui.components.AppMenuRow
import com.jonathaxs.tinystock.ui.components.AppSectionLabel
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.components.appCard
import com.jonathaxs.tinystock.ui.icons.AppIcons
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Calendario e fila de pedidos da loja aberta. Porte do `SalesView` do iOS (C7): filtro em menu, dia ou
 * lista, mes com a contagem por dia, secoes por status e o detalhe do pedido por cima.
 *
 * [filterRequest] chega do toque numa fila dos Relatorios e e consumido com [onFilterRequestHandled].
 */
@Composable
fun CalendarScreen(
    storeID: UUID,
    filterRequest: CalendarOrderFilter?,
    onFilterRequestHandled: () -> Unit,
    storeName: String,
    onEditOrder: (SalesOrder) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel(),
) {
    LaunchedEffect(storeID) { viewModel.setStore(storeID) }
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val displayMode by viewModel.displayMode.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    // Filtro e dia voltam ao padrao ao trocar de loja, como no iOS.
    var filterKey by rememberSaveable(storeID) { mutableStateOf(CalendarOrderFilter.All.key) }
    var selectedDay by rememberSaveable(storeID) { mutableLongStateOf(LocalDate.now(zone).toEpochDay()) }
    var openOrderID by rememberSaveable(storeID) { mutableStateOf<String?>(null) }
    var cancellingID by rememberSaveable(storeID) { mutableStateOf<String?>(null) }
    val filter = CalendarOrderFilter.fromKey(filterKey)
    val selectedDate = LocalDate.ofEpochDay(selectedDay)

    fun applyFilter(newFilter: CalendarOrderFilter) {
        filterKey = newFilter.key
        // Atrasados costuma envolver dias anteriores ao selecionado no calendario.
        if (newFilter == CalendarOrderFilter.Overdue) viewModel.setDisplayMode(OrderDisplayMode.List)
    }

    LaunchedEffect(filterRequest) {
        val request = filterRequest ?: return@LaunchedEffect
        filterKey = request.key
        viewModel.setDisplayMode(OrderDisplayMode.List)
        selectedDay = LocalDate.now(zone).toEpochDay()
        openOrderID = null
        onFilterRequestHandled()
    }

    // Atualiza a cada minuto, como o TimelineView do iOS (virada do dia e atrasados).
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Instant.now()
        }
    }

    val list = orders ?: return
    val openOrder = openOrderID?.let { id -> list.firstOrNull { it.id.toString() == id } }
    val cancelling = cancellingID?.let { id -> list.firstOrNull { it.id.toString() == id } }

    Box(modifier.fillMaxSize()) {
        if (openOrder != null) {
            BackHandler { openOrderID = null }
            OrderDetail(
                order = openOrder,
                zone = zone,
                storeName = storeName,
                onBack = { openOrderID = null },
                onEdit = { onEditOrder(openOrder) },
                onAdvance = { viewModel.transition(openOrder, it.status) },
                onCancel = { cancellingID = openOrder.id.toString() },
            )
        } else {
            OrderList(
                orders = list,
                filter = filter,
                displayMode = displayMode,
                selectedDate = selectedDate,
                now = now,
                zone = zone,
                onFilter = ::applyFilter,
                onDisplayMode = viewModel::setDisplayMode,
                onSelectDate = { selectedDay = it.toEpochDay() },
                onOpen = { openOrderID = it.id.toString() },
                onAdvance = { order, action -> viewModel.transition(order, action.status) },
                onCancel = { cancellingID = it.id.toString() },
            )
        }
    }

    if (cancelling != null) {
        CancelDialog(
            onDismiss = { cancellingID = null },
            onConfirm = { reason ->
                viewModel.cancel(cancelling, reason)
                cancellingID = null
            },
        )
    }

    viewModel.errorRes?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.order_operation_error_title)) },
            text = { Text(stringResource(message)) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

@Composable
private fun OrderList(
    orders: List<SalesOrder>,
    filter: CalendarOrderFilter,
    displayMode: OrderDisplayMode,
    selectedDate: LocalDate,
    now: Instant,
    zone: ZoneId,
    onFilter: (CalendarOrderFilter) -> Unit,
    onDisplayMode: (OrderDisplayMode) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpen: (SalesOrder) -> Unit,
    onAdvance: (SalesOrder, OrderQuickAction) -> Unit,
    onCancel: (SalesOrder) -> Unit,
) {
    val isDay = displayMode == OrderDisplayMode.Day
    val filtered = remember(orders, filter, now) { orders.filter { filter.includes(it, now, zone) } }
    val visible = if (isDay) {
        SalesOrderSchedule.orders(selectedDate.atStartOfDay(zone).toInstant(), filtered, zone)
    } else {
        filtered
    }
    val sections = OrderQueueSection.make(visible)

    LazyColumn(
        contentPadding = PaddingValues(start = AppStyle.HorizontalPadding, end = AppStyle.HorizontalPadding, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("filter") {
            AppMenuRow(
                icon = AppIcons.Filter,
                title = stringResource(R.string.order_calendar_filter_label),
                value = stringResource(filter.titleRes),
            ) { close ->
                for (option in CalendarOrderFilter.allOptions) {
                    AppMenuItem(stringResource(option.titleRes), option == filter, icon = option.icon, onClick = {
                        close()
                        onFilter(option)
                    })
                }
            }
        }
        item("mode") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                OrderDisplayMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == displayMode,
                        onClick = { onDisplayMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, OrderDisplayMode.entries.size),
                        // Trilho neutro como o seletor segmentado do iOS, sem o lilas padrao do Material.
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onSurface,
                            activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                            inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        icon = {},
                    ) {
                        Text(stringResource(if (mode == OrderDisplayMode.Day) R.string.order_calendar_day else R.string.order_calendar_list))
                    }
                }
            }
        }
        if (isDay) {
            item("month") {
                OrderMonthCard(
                    selectedDate = selectedDate,
                    countsByDay = SalesOrderSchedule.countsByDay(filtered, zone),
                    zone = zone,
                    onSelect = onSelectDate,
                )
            }
            item("dayHeader") {
                AppSectionLabel(selectedDate.fullText(), modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (visible.isEmpty()) {
            item("empty") {
                if (orders.isEmpty()) {
                    EmptyCard(stringResource(R.string.order_queue_empty_title), stringResource(R.string.order_queue_empty_message))
                } else {
                    EmptyCard(stringResource(R.string.order_calendar_empty), null)
                }
            }
        }
        for (section in sections) {
            item("header-${section.key}") { SectionHeader(section) }
            item("rows-${section.key}") {
                Column(Modifier.fillMaxWidth().appCard()) {
                    section.orders.forEachIndexed { index, order ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 60.dp),
                                thickness = Dp.Hairline,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        key(order.id) {
                            SwipeableOrderRow(
                                order = order,
                                selectedDate = if (isDay) selectedDate else null,
                                now = now,
                                zone = zone,
                                onOpen = { onOpen(order) },
                                onAdvance = { onAdvance(order, it) },
                                onCancel = { onCancel(order) },
                            )
                        }
                    }
                }
            }
        }
        item("bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

/** Nome do status na cor de destaque e a quantidade de pedidos a direita, lidos juntos como titulo. */
@Composable
private fun SectionHeader(section: OrderQueueSection) {
    val title = section.status?.let { stringResource(it.nameRes) } ?: stringResource(R.string.order_queue_unknown)
    val count = NumberFormat.getIntegerInstance().format(section.orders.size)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clearAndSetSemantics {
                contentDescription = "$title, $count"
                heading()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppSectionLabel(title, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(
            count,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun EmptyCard(title: String, message: String?) {
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
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Confirmacao do cancelamento com o motivo opcional, como o alerta do iOS. */
@Composable
private fun CancelDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.order_cancel_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.order_cancel_message))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.order_cancel_reason)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }) {
                Text(stringResource(R.string.order_action_cancel), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
