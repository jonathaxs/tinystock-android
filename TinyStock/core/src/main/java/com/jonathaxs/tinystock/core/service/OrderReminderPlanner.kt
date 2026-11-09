package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.model.OrderReminder
import com.jonathaxs.tinystock.core.model.OrderReminderError
import com.jonathaxs.tinystock.core.model.OrderReminderSettings
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.model.StoreScope
import com.jonathaxs.tinystock.core.util.ensure
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Calcula lembretes de producao e despacho por dia civil e estado atual. Porte do `OrderReminderPlanner`.
 */
object OrderReminderPlanner {

    /**
     * Recebe todas as lojas e pedidos. A loja selecionada na interface nao restringe alertas.
     * Nao agenda no passado nem repete atrasos antigos a cada abertura do aplicativo.
     */
    fun reminders(
        orders: List<SalesOrder>,
        stores: List<StoreProfile>,
        settings: OrderReminderSettings,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<OrderReminder> {
        if (!settings.isEnabled) return emptyList()
        ensure(settings.hasValidTime) { OrderReminderError.InvalidTime }

        val activeStores = stores.filter { !it.isArchived && it.id != StoreScope.UNASSIGNED_STORE_ID }
        val result = ArrayList<OrderReminder>()

        val timings = buildList {
            if (settings.dayBeforeEnabled) add(OrderReminder.Timing.DayBefore to -1L)
            if (settings.dueDayEnabled) add(OrderReminder.Timing.DueDay to 0L)
            if (settings.overdueEnabled) add(OrderReminder.Timing.Overdue to 1L)
        }

        for (order in orders) {
            val store = activeStores.firstOrNull { it.id == order.storeID } ?: continue
            val deadlines = buildList {
                when (order.status) {
                    SalesOrderStatus.AwaitingProduction, SalesOrderStatus.InProduction -> {
                        if (settings.productionEnabled && order.productionDueAt != null) {
                            add(OrderReminder.Kind.Production to order.productionDueAt)
                        }
                        if (settings.shippingEnabled && order.shippingDueAt != null) {
                            add(OrderReminder.Kind.Shipping to order.shippingDueAt)
                        }
                    }
                    SalesOrderStatus.ReadyToShip -> {
                        if (settings.shippingEnabled && order.shippingDueAt != null) {
                            add(OrderReminder.Kind.Shipping to order.shippingDueAt)
                        }
                    }
                    else -> Unit
                }
            }
            if (deadlines.isEmpty()) continue

            // O aviso identifica loja e produto sem incluir nome ou dados do comprador.
            val productName = order.itemList.firstOrNull()?.productName.orEmpty()

            for ((kind, deadline) in deadlines) {
                val deadlineDay = deadline.atZone(zone).toLocalDate()
                for ((timing, offsetDays) in timings) {
                    val targetDay = deadlineDay.plusDays(offsetDays)
                    val localDateTime = LocalDateTime.of(
                        targetDay,
                        LocalTime.of(settings.hour, settings.minute),
                    )
                    val transition = zone.rules.getTransition(localDateTime)
                    val zoned = if (transition != null && transition.isGap) {
                        transition.dateTimeAfter.atZone(zone)
                    } else {
                        ZonedDateTime.of(localDateTime, zone).withEarlierOffsetAtOverlap()
                    }
                    if (zoned.toLocalDate() != targetDay) continue
                    val fireDate = zoned.toInstant()
                    if (!fireDate.isAfter(now)) continue

                    result.add(
                        OrderReminder(
                            storeID = store.id,
                            orderID = order.id,
                            kind = kind,
                            timing = timing,
                            fireDate = fireDate,
                            storeName = store.name,
                            productName = productName,
                            soundEnabled = settings.soundEnabled,
                        ),
                    )
                }
            }
        }

        return result.sortedWith(compareBy<OrderReminder> { it.fireDate }.thenBy { it.id })
    }
}
