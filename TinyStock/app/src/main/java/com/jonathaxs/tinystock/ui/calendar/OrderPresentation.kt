package com.jonathaxs.tinystock.ui.calendar

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import com.jonathaxs.tinystock.core.service.SalesOrderManagementError
import com.jonathaxs.tinystock.core.service.SalesOrderSchedule
import com.jonathaxs.tinystock.core.service.SalesOrderScheduleEntry
import com.jonathaxs.tinystock.core.util.DomainException
import com.jonathaxs.tinystock.ui.icons.AppIcons
import com.jonathaxs.tinystock.ui.theme.color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Filtros do Calendario, tambem usados pelo toque nas filas dos Relatorios. Porte do `CalendarOrderFilter`. */
sealed interface CalendarOrderFilter {
    data object All : CalendarOrderFilter
    data object Overdue : CalendarOrderFilter
    data object Production : CalendarOrderFilter
    data class Status(val status: SalesOrderStatus) : CalendarOrderFilter

    @get:StringRes
    val titleRes: Int
        get() = when (this) {
            All -> R.string.order_calendar_allStatuses
            Overdue -> R.string.order_calendar_overdue
            Production -> R.string.reports_operation_toProduce
            is Status -> status.nameRes
        }

    val icon: ImageVector
        get() = when (this) {
            All -> AppIcons.Layers
            Overdue -> Icons.Filled.Warning
            Production -> AppIcons.Hammer
            is Status -> statusSymbol(status, dark = false).icon
        }

    /** Texto cru para guardar a escolha com `rememberSaveable`. */
    val key: String
        get() = when (this) {
            All -> "all"
            Overdue -> "overdue"
            Production -> "production"
            is Status -> "status:${status.rawValue}"
        }

    fun includes(order: SalesOrder, now: Instant, zone: ZoneId): Boolean = when (this) {
        All -> true
        Overdue -> SalesOrderSchedule.isOverdue(order, now, zone)
        Production -> order.status == SalesOrderStatus.AwaitingProduction || order.status == SalesOrderStatus.InProduction
        is Status -> order.status == status
    }

    companion object {
        /**
         * Ordem das opcoes no menu do filtro. Getter, e nao valor guardado: montar a lista na inicializacao
         * da interface pegava os objetos ainda nulos.
         */
        val allOptions: List<CalendarOrderFilter>
            get() = listOf(All, Overdue, Production) + SalesOrderStatus.entries.map { Status(it) }

        fun fromKey(key: String): CalendarOrderFilter = allOptions.firstOrNull { it.key == key } ?: All
    }
}

@get:StringRes
internal val SalesOrderStatus.nameRes: Int
    get() = when (this) {
        SalesOrderStatus.New -> R.string.order_status_new
        SalesOrderStatus.AwaitingProduction -> R.string.order_status_awaitingProduction
        SalesOrderStatus.InProduction -> R.string.order_status_inProduction
        SalesOrderStatus.ReadyToShip -> R.string.order_status_readyToShip
        SalesOrderStatus.Shipped -> R.string.order_status_shipped
        SalesOrderStatus.Completed -> R.string.order_status_completed
        SalesOrderStatus.Cancelled -> R.string.order_status_cancelled
    }

internal data class StatusSymbol(val icon: ImageVector, val color: Color)

private fun teal(dark: Boolean) = Color(if (dark) 0xFF40C8E0 else 0xFF30B0C7)

/** Icone e cor do status, usados no circulo das linhas do Calendario. */
internal fun statusSymbol(status: SalesOrderStatus?, dark: Boolean): StatusSymbol = when (status) {
    SalesOrderStatus.New -> StatusSymbol(AppIcons.Sparkles, AppAccentColor.Purple.color(dark))
    SalesOrderStatus.AwaitingProduction -> StatusSymbol(AppIcons.Clock, AppAccentColor.Orange.color(dark))
    SalesOrderStatus.InProduction -> StatusSymbol(AppIcons.Hammer, AppAccentColor.Orange.color(dark))
    SalesOrderStatus.ReadyToShip -> StatusSymbol(AppIcons.Box, AppAccentColor.Blue.color(dark))
    SalesOrderStatus.Shipped -> StatusSymbol(Icons.AutoMirrored.Filled.Send, teal(dark))
    SalesOrderStatus.Completed -> StatusSymbol(Icons.Filled.CheckCircle, AppAccentColor.Green.color(dark))
    SalesOrderStatus.Cancelled -> StatusSymbol(AppIcons.CancelCircle, AppAccentColor.Red.color(dark))
    null -> StatusSymbol(Icons.Filled.Info, Color.Gray)
}

/** Proxima etapa oferecida ao deslizar a linha e no detalhe. */
internal data class OrderQuickAction(
    val status: SalesOrderStatus,
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
    val color: Color,
)

internal object OrderPresentation {

    fun quickAction(order: SalesOrder, dark: Boolean): OrderQuickAction? = when (order.status) {
        SalesOrderStatus.AwaitingProduction, SalesOrderStatus.InProduction -> OrderQuickAction(
            SalesOrderStatus.ReadyToShip, R.string.order_action_produced, Icons.Filled.CheckCircle,
            AppAccentColor.Green.color(dark),
        )
        SalesOrderStatus.ReadyToShip -> OrderQuickAction(
            SalesOrderStatus.Shipped, R.string.order_action_shipped, Icons.AutoMirrored.Filled.Send,
            AppAccentColor.Blue.color(dark),
        )
        SalesOrderStatus.Shipped -> OrderQuickAction(
            SalesOrderStatus.Completed, R.string.order_action_completed, Icons.Filled.CheckCircle,
            AppAccentColor.Green.color(dark),
        )
        else -> null
    }

    fun canCancel(order: SalesOrder): Boolean = order.canTransition(SalesOrderStatus.Cancelled)

    fun queueDate(order: SalesOrder): Instant = when (order.status) {
        SalesOrderStatus.AwaitingProduction, SalesOrderStatus.InProduction ->
            order.productionDueAt ?: order.shippingDueAt ?: order.orderedAt
        SalesOrderStatus.ReadyToShip -> order.shippingDueAt ?: order.orderedAt
        SalesOrderStatus.Shipped -> order.shippedAt ?: order.updatedAt
        SalesOrderStatus.Completed -> order.completedAt ?: order.updatedAt
        SalesOrderStatus.Cancelled -> order.cancelledAt ?: order.updatedAt
        SalesOrderStatus.New, null -> order.orderedAt
    }

    /** Texto do prazo da fila ("Produzir ate %s"), com a data no formato curto do aparelho. */
    @StringRes
    fun deadlineFormat(order: SalesOrder): Int = when (order.status) {
        SalesOrderStatus.AwaitingProduction, SalesOrderStatus.InProduction -> R.string.order_queue_productionDate
        SalesOrderStatus.ReadyToShip -> R.string.order_queue_shippingDate
        SalesOrderStatus.Shipped -> R.string.order_queue_shippedDate
        SalesOrderStatus.Completed -> R.string.order_queue_completedDate
        SalesOrderStatus.Cancelled -> R.string.order_queue_cancelledDate
        SalesOrderStatus.New, null -> R.string.order_queue_orderDate
    }

    @StringRes
    fun calendarFormat(kind: SalesOrderScheduleEntry.Kind): Int = when (kind) {
        SalesOrderScheduleEntry.Kind.Production -> R.string.order_queue_productionDate
        SalesOrderScheduleEntry.Kind.Shipping -> R.string.order_queue_shippingDate
        SalesOrderScheduleEntry.Kind.Shipped -> R.string.order_queue_shippedDate
        SalesOrderScheduleEntry.Kind.Completed -> R.string.order_queue_completedDate
        SalesOrderScheduleEntry.Kind.Cancelled -> R.string.order_queue_cancelledDate
        SalesOrderScheduleEntry.Kind.Ordered -> R.string.order_queue_orderDate
    }

    fun shortDate(date: Instant, zone: ZoneId): String =
        date.atZone(zone).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    fun dateTime(date: Instant, zone: ZoneId): String =
        date.atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

    @StringRes
    fun errorMessage(error: Throwable): Int = when ((error as? DomainException)?.error) {
        SalesOrderManagementError.OrderUnavailable -> R.string.order_management_error_unavailable
        SalesOrderManagementError.EditingNotAllowed -> R.string.order_management_error_editing
        SalesOrderManagementError.CancellationNotAllowed -> R.string.order_management_error_cancellation
        SalesOrderManagementError.StockHistoryMismatch -> R.string.order_management_error_stockHistory
        SalesOrderManagementError.QuantityOverflow -> R.string.order_management_error_quantityOverflow
        SalesOrderManagementError.TransitionNotAllowed -> R.string.order_management_error_transition
        SalesOrderManagementError.InvalidTransitionDate -> R.string.order_management_error_transitionDate
        else -> R.string.order_management_error_data
    }
}

/** Secao da lista: pedidos de um status, na ordem da fila. Porte do `SalesOrderQueueSection`. */
internal data class OrderQueueSection(val key: String, val status: SalesOrderStatus?, val orders: List<SalesOrder>) {
    companion object {
        fun make(orders: List<SalesOrder>): List<OrderQueueSection> {
            val result = SalesOrderStatus.entries.mapNotNull { status ->
                val matches = orders.filter { it.status == status }
                if (matches.isEmpty()) null else OrderQueueSection(status.rawValue, status, sorted(matches, status.isTerminal))
            }.toMutableList()
            val unknown = orders.filter { it.status == null }
            if (unknown.isNotEmpty()) result.add(OrderQueueSection("unknown", null, sorted(unknown, terminal = false)))
            return result
        }

        private fun sorted(orders: List<SalesOrder>, terminal: Boolean): List<SalesOrder> = orders.sortedWith { a, b ->
            val left = OrderPresentation.queueDate(a)
            val right = OrderPresentation.queueDate(b)
            when {
                left == right -> a.id.toString().compareTo(b.id.toString())
                terminal -> right.compareTo(left)
                else -> left.compareTo(right)
            }
        }
    }
}
