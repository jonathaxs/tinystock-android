package com.jonathaxs.tinystock.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.format.CurrencyFormatter
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.service.SalesOrderSchedule
import com.jonathaxs.tinystock.ui.components.CircleIcon
import com.jonathaxs.tinystock.ui.components.isLargeFont
import com.jonathaxs.tinystock.ui.theme.color
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal val headline @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
internal val subheadline @Composable get() = MaterialTheme.typography.bodyMedium
internal val caption @Composable get() = MaterialTheme.typography.bodySmall

internal fun BigDecimal.currencyText() = CurrencyFormatter.string(this)

/** Nome do canal, ou o nome digitado no canal "Outro". */
@Composable
internal fun SalesOrder.channelName(): String = customChannelDisplayName ?: stringResource(
    when (channel) {
        SalesChannel.Direct -> R.string.order_channel_direct
        SalesChannel.Shopee -> R.string.order_channel_shopee
        SalesChannel.MercadoLivre -> R.string.order_channel_mercadoLivre
        SalesChannel.Other -> R.string.order_channel_other
        null -> R.string.order_channel_unknown
    },
)

/**
 * Linha da fila com deslizar: para a direita avanca o status, para a esquerda cancela. No TalkBack as
 * duas acoes ficam no menu de acoes da linha. Porte do `SalesOrderRowView` com os `swipeActions`.
 */
@Composable
internal fun SwipeableOrderRow(
    order: SalesOrder,
    selectedDate: LocalDate?,
    now: Instant,
    zone: ZoneId,
    onOpen: () -> Unit,
    onAdvance: (OrderQuickAction) -> Unit,
    onCancel: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val action = OrderPresentation.quickAction(order, dark)
    val canCancel = OrderPresentation.canCancel(order)
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val actionTitle = action?.let { stringResource(it.titleRes) }
    val cancelTitle = stringResource(R.string.order_action_cancel)
    val cancelColor = AppAccentColor.Red.color(dark)

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = action != null,
        enableDismissFromEndToStart = canCancel,
        onDismiss = { value ->
            // A linha volta para o lugar: a mudanca de status chega pela lista atualizada.
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> action?.let(onAdvance)
                SwipeToDismissBoxValue.EndToStart -> onCancel()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { state.reset() }
        },
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> if (action != null) {
                    SwipeBackground(action.icon, actionTitle.orEmpty(), action.color, Alignment.CenterStart)
                }
                SwipeToDismissBoxValue.EndToStart -> SwipeBackground(Icons.Filled.Close, cancelTitle, cancelColor, Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics {
                    customActions = buildList {
                        if (action != null && actionTitle != null) {
                            add(CustomAccessibilityAction(actionTitle) { onAdvance(action); true })
                        }
                        if (canCancel) add(CustomAccessibilityAction(cancelTitle) { onCancel(); true })
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            OrderRowContent(order, selectedDate, now, zone)
        }
    }
}

@Composable
private fun SwipeBackground(icon: ImageVector, title: String, color: Color, alignment: Alignment) {
    Box(Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp), contentAlignment = alignment) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OrderRowContent(order: SalesOrder, selectedDate: LocalDate?, now: Instant, zone: ZoneId) {
    val symbol = statusSymbol(order.status, isSystemInDarkTheme())
    if (isLargeFont()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleIcon(symbol.icon, symbol.color)
            MainInformation(order, selectedDate, now, zone)
            FinancialInformation(order, large = true)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleIcon(symbol.icon, symbol.color)
            Box(Modifier.weight(1f)) { MainInformation(order, selectedDate, now, zone) }
            FinancialInformation(order, large = false)
        }
    }
}

@Composable
private fun MainInformation(order: SalesOrder, selectedDate: LocalDate?, now: Instant, zone: ZoneId) {
    val items = order.itemList
    val first = items.firstOrNull()
    val title = when {
        first == null -> stringResource(R.string.order_queue_untitled)
        items.size > 1 -> stringResource(
            R.string.order_queue_moreItems,
            first.productName,
            NumberFormat.getIntegerInstance().format(items.size - 1),
        )
        else -> first.productName
    }
    val deadlines = if (selectedDate == null) {
        listOf(stringResource(OrderPresentation.deadlineFormat(order), OrderPresentation.shortDate(OrderPresentation.queueDate(order), zone)))
    } else {
        SalesOrderSchedule.entries(order)
            .filter { it.date.atZone(zone).toLocalDate() == selectedDate }
            .map { stringResource(OrderPresentation.calendarFormat(it.kind), OrderPresentation.shortDate(it.date, zone)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = headline)
        if (first != null) {
            val quantity = order.totalQuantity
            val units = stringResource(
                if (quantity.compareTo(BigDecimal.ONE) == 0) R.string.reports_count_units_one else R.string.reports_count_units,
                NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(quantity),
            )
            Text(
                if (first.variantName.isEmpty()) units else "${first.variantName} · $units",
                style = subheadline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (deadline in deadlines) {
            SmallLabel(Icons.Filled.DateRange, deadline, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (SalesOrderSchedule.isOverdue(order, now, zone)) {
            SmallLabel(Icons.Filled.Warning, stringResource(R.string.order_calendar_overdueBadge), AppAccentColor.Red.color(isSystemInDarkTheme()), bold = true)
        }
    }
}

@Composable
private fun SmallLabel(icon: ImageVector, text: String, color: Color, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = caption, color = color, fontWeight = if (bold) FontWeight.SemiBold else null)
    }
}

@Composable
private fun FinancialInformation(order: SalesOrder, large: Boolean) {
    Column(
        horizontalAlignment = if (large) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(order.total.currencyText(), fontWeight = FontWeight.SemiBold)
        Text(
            order.buyerName.ifEmpty { order.channelName() },
            style = caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            textAlign = if (large) null else TextAlign.End,
        )
    }
}
