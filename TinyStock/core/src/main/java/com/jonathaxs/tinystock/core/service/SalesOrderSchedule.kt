package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

data class SalesOrderScheduleEntry(
    val kind: Kind,
    val date: Instant,
) {
    enum class Kind(val rawValue: String) {
        Production("production"),
        Shipping("shipping"),
        Shipped("shipped"),
        Completed("completed"),
        Cancelled("cancelled"),
        Ordered("ordered");

        companion object {
            fun fromRawValue(raw: String): Kind? = entries.firstOrNull { it.rawValue == raw }
        }
    }
}

/**
 * Compromissos dos pedidos organizados por dia civil local. Porte do `SalesOrderSchedule`.
 */
object SalesOrderSchedule {

    /**
     * Mantem ambos os prazos visiveis enquanto a producao esta pendente. Estados posteriores mostram
     * apenas a proxima obrigacao ou a data efetiva do estado.
     */
    fun entries(order: SalesOrder): List<SalesOrderScheduleEntry> = when (order.status) {
        SalesOrderStatus.AwaitingProduction, SalesOrderStatus.InProduction -> {
            val entries = buildList {
                order.productionDueAt?.let {
                    add(SalesOrderScheduleEntry(SalesOrderScheduleEntry.Kind.Production, it))
                }
                order.shippingDueAt?.let {
                    add(SalesOrderScheduleEntry(SalesOrderScheduleEntry.Kind.Shipping, it))
                }
            }
            entries.ifEmpty {
                listOf(SalesOrderScheduleEntry(SalesOrderScheduleEntry.Kind.Ordered, order.orderedAt))
            }
        }
        SalesOrderStatus.ReadyToShip -> listOf(
            SalesOrderScheduleEntry(
                SalesOrderScheduleEntry.Kind.Shipping,
                order.shippingDueAt ?: order.orderedAt,
            ),
        )
        SalesOrderStatus.Shipped -> listOf(
            SalesOrderScheduleEntry(
                SalesOrderScheduleEntry.Kind.Shipped,
                order.shippedAt ?: order.updatedAt,
            ),
        )
        SalesOrderStatus.Completed -> listOf(
            SalesOrderScheduleEntry(
                SalesOrderScheduleEntry.Kind.Completed,
                order.completedAt ?: order.updatedAt,
            ),
        )
        SalesOrderStatus.Cancelled -> listOf(
            SalesOrderScheduleEntry(
                SalesOrderScheduleEntry.Kind.Cancelled,
                order.cancelledAt ?: order.updatedAt,
            ),
        )
        SalesOrderStatus.New, null -> listOf(
            SalesOrderScheduleEntry(SalesOrderScheduleEntry.Kind.Ordered, order.orderedAt),
        )
    }

    fun orders(
        day: Instant,
        orders: List<SalesOrder>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<SalesOrder> {
        val targetDay = day.atZone(zone).toLocalDate()
        return orders.filter { order ->
            entries(order).any { it.date.atZone(zone).toLocalDate() == targetDay }
        }
    }

    fun countsByDay(
        orders: List<SalesOrder>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<Instant, Int> {
        val counts = LinkedHashMap<Instant, Int>()
        for (order in orders) {
            // Producao e despacho no mesmo dia representam um unico pedido na grade.
            val days = entries(order).map { startOfDay(it.date, zone) }.toSet()
            for (day in days) {
                counts[day] = (counts[day] ?: 0) + 1
            }
        }
        return counts
    }

    fun isOverdue(
        order: SalesOrder,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val deadlines = when (order.status) {
            SalesOrderStatus.AwaitingProduction, SalesOrderStatus.InProduction ->
                listOf(order.productionDueAt, order.shippingDueAt)
            SalesOrderStatus.ReadyToShip ->
                listOf(order.shippingDueAt)
            else -> return false
        }
        val today = now.atZone(zone).toLocalDate()
        return deadlines.filterNotNull().any { it.atZone(zone).toLocalDate().isBefore(today) }
    }

    /** Grade de semanas completas, respeitando primeiro dia da semana e horario de verao. */
    fun monthDays(
        containing: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
    ): List<Instant> {
        val targetDate = containing.atZone(zone).toLocalDate()
        val firstOfMonth = targetDate.withDayOfMonth(1)
        val lastOfMonth = targetDate.withDayOfMonth(targetDate.lengthOfMonth())
        val firstGridDay = firstOfMonth.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val lastGridDay = lastOfMonth.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).plusDays(6)

        val days = ArrayList<Instant>()
        var current = firstGridDay
        while (!current.isAfter(lastGridDay)) {
            days.add(current.atStartOfDay(zone).toInstant())
            current = current.plusDays(1)
        }
        return days
    }

    internal fun startOfDay(date: Instant, zone: ZoneId): Instant =
        date.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
}
