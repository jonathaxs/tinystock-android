package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.model.OrderCalendarEventDraft
import com.jonathaxs.tinystock.core.model.OrderCalendarEventKind
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import java.time.Instant
import java.time.ZoneId

/**
 * Monta rascunhos de eventos de producao e despacho para exportacao ao Calendario.
 * Porte do `OrderCalendarExportPlanner`.
 */
object OrderCalendarExportPlanner {

    fun drafts(
        order: SalesOrder,
        storeName: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<OrderCalendarEventDraft> {
        val status = order.status ?: return emptyList()

        val deadlines: List<Pair<OrderCalendarEventKind, Instant?>> = when (status) {
            SalesOrderStatus.AwaitingProduction, SalesOrderStatus.InProduction -> listOf(
                OrderCalendarEventKind.Production to order.productionDueAt,
                OrderCalendarEventKind.Shipping to order.shippingDueAt,
            )
            SalesOrderStatus.ReadyToShip -> listOf(
                OrderCalendarEventKind.Shipping to order.shippingDueAt,
            )
            SalesOrderStatus.New,
            SalesOrderStatus.Shipped,
            SalesOrderStatus.Completed,
            SalesOrderStatus.Cancelled,
            -> emptyList()
        }

        val names = distinctProductNames(order)
        val primaryProductName = names.firstOrNull()
        val additionalProductCount = (names.size - 1).coerceAtLeast(0)

        return deadlines.mapNotNull { (kind, deadline) ->
            val targetDeadline = deadline ?: return@mapNotNull null
            val day = targetDeadline.atZone(zone).toLocalDate()
            val startDate = day.atStartOfDay(zone).toInstant()
            val endDate = day.plusDays(1).atStartOfDay(zone).toInstant()

            OrderCalendarEventDraft(
                orderID = order.id,
                kind = kind,
                startDate = startDate,
                endDate = endDate,
                storeName = storeName,
                primaryProductName = primaryProductName,
                additionalProductCount = additionalProductCount,
                buyerName = order.buyerName,
                externalReference = order.externalReference,
                channel = order.channel,
                customChannelDisplayName = order.customChannelDisplayName,
            )
        }
    }

    private fun distinctProductNames(order: SalesOrder): List<String> {
        val names = ArrayList<String>()
        for (item in order.itemList) {
            val name = item.productName.trim()
            if (name.isNotEmpty() && name !in names) {
                names.add(name)
            }
        }
        return names
    }
}
