package com.jonathaxs.tinystock.core.report

import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import com.jonathaxs.tinystock.core.service.SalesOrderSchedule
import com.jonathaxs.tinystock.core.util.FoldedNameOrder
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Intervalo fechado no inicio e aberto no fim, equivalente ao `DateInterval` usado nos relatorios do iOS. */
data class SalesOrderDateInterval(
    val start: Instant,
    val end: Instant,
) {
    val duration: Duration get() = Duration.between(start, end)
    val durationSeconds: Long get() = duration.seconds
}

/**
 * Recortes de tempo rapidos dos relatorios. Porte de `SalesReportPeriod` (o resto de `SalesReport.swift`
 * era do modelo legado `Sale` e fica de fora do Android).
 */
enum class SalesReportPeriod(val rawValue: String) {
    Today("today"),
    LastSevenDays("lastSevenDays"),
    CurrentMonth("currentMonth"),
    AllTime("allTime");

    val id: String get() = rawValue

    /** Intervalo fechado no inicio e aberto no fim, para meia-noite entrar so no dia novo. */
    fun dateInterval(
        reference: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): SalesOrderDateInterval? {
        val today = reference.atZone(zone).toLocalDate()
        return when (this) {
            Today -> SalesOrderDateInterval(
                start = today.atStartOfDay(zone).toInstant(),
                end = today.plusDays(1).atStartOfDay(zone).toInstant(),
            )
            LastSevenDays -> SalesOrderDateInterval(
                start = today.minusDays(6).atStartOfDay(zone).toInstant(),
                end = today.plusDays(1).atStartOfDay(zone).toInstant(),
            )
            CurrentMonth -> {
                val monthStart = today.withDayOfMonth(1)
                SalesOrderDateInterval(
                    start = monthStart.atStartOfDay(zone).toInstant(),
                    end = monthStart.plusMonths(1).atStartOfDay(zone).toInstant(),
                )
            }
            AllTime -> null
        }
    }

    fun contains(
        date: Instant,
        reference: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val interval = dateInterval(reference = reference, zone = zone) ?: return this == AllTime
        return date >= interval.start && date < interval.end
    }

    companion object {
        fun fromRawValue(raw: String): SalesReportPeriod? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** Periodo predefinido ou intervalo personalizado por dias inteiros. Porte do `SalesOrderReportRange`. */
sealed interface SalesOrderReportRange {
    data class Preset(val period: SalesReportPeriod) : SalesOrderReportRange

    /** As duas datas escolhidas representam dias inteiros, inclusive o ultimo dia. */
    data class Custom(val start: Instant, val end: Instant) : SalesOrderReportRange

    fun dateInterval(
        reference: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): SalesOrderDateInterval? = when (this) {
        is Preset -> period.dateInterval(reference = reference, zone = zone)
        is Custom -> {
            val firstDay = start.atZone(zone).toLocalDate()
            val lastDay = end.atZone(zone).toLocalDate()
            if (firstDay.isAfter(lastDay)) {
                null
            } else {
                SalesOrderDateInterval(
                    start = firstDay.atStartOfDay(zone).toInstant(),
                    end = lastDay.plusDays(1).atStartOfDay(zone).toInstant(),
                )
            }
        }
    }

    /** Intervalos invertidos nao viram "todo o historico". O limite final e exclusivo. */
    fun contains(
        date: Instant,
        reference: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val interval = dateInterval(reference = reference, zone = zone)
            ?: return this == Preset(SalesReportPeriod.AllTime)
        return date >= interval.start && date < interval.end
    }

    companion object {
        fun preset(period: SalesReportPeriod): SalesOrderReportRange = Preset(period)
        fun custom(start: Instant, end: Instant): SalesOrderReportRange = Custom(start, end)
    }
}

/** Valores agregados de um grupo de pedidos. Porte do `SalesOrderReportTotals`. */
data class SalesOrderReportTotals(
    val orderIDs: List<UUID>,
    val unitCount: BigDecimal,
    val revenue: BigDecimal,
    val cost: BigDecimal,
    val channelFees: BigDecimal,
) {
    val orderCount: Int get() = orderIDs.size
    val grossProfit: BigDecimal get() = revenue - cost
    val netProfit: BigDecimal get() = grossProfit - channelFees
    val isEmpty: Boolean get() = orderIDs.isEmpty()

    internal constructor(orders: List<SalesOrder>) : this(
        orderIDs = orders.map { it.id },
        unitCount = orders.sumOf { it.totalQuantity },
        revenue = orders.sumOf { it.total },
        cost = orders.sumOf { it.totalCost },
        // O valor salvo pode incluir arredondamento. Nao recalcular usando a porcentagem.
        channelFees = orders.sumOf { it.channelFeeAmount },
    )
}

data class SalesOrderReportQueue(
    val orderIDs: List<UUID>,
    val unitCount: BigDecimal,
) {
    val orderCount: Int get() = orderIDs.size

    internal constructor(orders: List<SalesOrder>) : this(
        orderIDs = orders.map { it.id },
        unitCount = orders.sumOf { it.totalQuantity },
    )
}

/** Filas atuais da loja inteira, sem o recorte financeiro. Atrasados se sobrepoem as outras filas. */
data class SalesOrderOperationalSummary(
    val newOrders: SalesOrderReportQueue,
    val awaitingProduction: SalesOrderReportQueue,
    val inProduction: SalesOrderReportQueue,
    val toProduce: SalesOrderReportQueue,
    val readyToShip: SalesOrderReportQueue,
    val shipped: SalesOrderReportQueue,
    val overdue: SalesOrderReportQueue,
    val unknownStatus: SalesOrderReportQueue,
) {
    internal constructor(orders: List<SalesOrder>, reference: Instant, zone: ZoneId) : this(
        newOrders = SalesOrderReportQueue(orders.filter { it.status == SalesOrderStatus.New }),
        awaitingProduction = SalesOrderReportQueue(
            orders.filter { it.status == SalesOrderStatus.AwaitingProduction },
        ),
        inProduction = SalesOrderReportQueue(orders.filter { it.status == SalesOrderStatus.InProduction }),
        toProduce = SalesOrderReportQueue(
            orders.filter {
                it.status == SalesOrderStatus.AwaitingProduction || it.status == SalesOrderStatus.InProduction
            },
        ),
        readyToShip = SalesOrderReportQueue(orders.filter { it.status == SalesOrderStatus.ReadyToShip }),
        shipped = SalesOrderReportQueue(orders.filter { it.status == SalesOrderStatus.Shipped }),
        overdue = SalesOrderReportQueue(
            orders.filter { SalesOrderSchedule.isOverdue(it, now = reference, zone = zone) },
        ),
        unknownStatus = SalesOrderReportQueue(orders.filter { it.status == null }),
    )
}

data class SalesOrderReportDay(
    val day: Instant,
    val totals: SalesOrderReportTotals,
) {
    val id: Instant get() = day
}

/** Variacoes do mesmo produto sao somadas pelo ID, nunca pelo nome ou pelo catalogo atual. */
data class SalesOrderProductRanking(
    val productID: UUID,
    val productName: String,
    val quantity: BigDecimal,
    val revenue: BigDecimal,
    val cost: BigDecimal,
) {
    val id: UUID get() = productID

    // Taxas pertencem ao pedido; nao ha rateio arbitrario de lucro liquido por produto.
    val grossProfit: BigDecimal get() = revenue - cost
}

data class SalesOrderChannelSummary(
    val id: ID,
    val totals: SalesOrderReportTotals,
) {
    data class ID(
        val rawValue: String,
        val customName: String,
    )

    val channel: SalesChannel? get() = SalesChannel.fromRawValue(id.rawValue)

    /**
     * Nome digitado quando o canal e "Outro". Null quando a UI deve usar a string localizada do canal
     * (ou "desconhecido", se [channel] for null).
     */
    val customDisplayName: String?
        get() = id.customName.takeIf { channel == SalesChannel.Other && it.isNotEmpty() }
}

/**
 * Retrato calculado com os estados atuais, nao uma reconstrucao contabil no passado.
 * Recebidos usam orderedAt; despachados usam shippedAt, mesmo quando ja concluidos.
 * Cancelados e estados desconhecidos nao compoem totais financeiros nem rankings.
 * Porte do `SalesOrderReportSummary`.
 */
class SalesOrderReportSummary(
    orders: List<SalesOrder>,
    val storeID: UUID,
    val range: SalesOrderReportRange,
    reference: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val received: SalesOrderReportTotals
    val dispatched: SalesOrderReportTotals
    val completed: SalesOrderReportQueue
    val cancelled: SalesOrderReportQueue
    val operations: SalesOrderOperationalSummary
    val dayGroups: List<SalesOrderReportDay>
    val channelGroups: List<SalesOrderChannelSummary>
    private val productRankings: List<SalesOrderProductRanking>

    init {
        val scoped = orders.filter { it.storeID == storeID }.sortedWith { a, b ->
            if (a.orderedAt == b.orderedAt) {
                a.id.toString().compareTo(b.id.toString())
            } else {
                b.orderedAt.compareTo(a.orderedAt)
            }
        }

        fun inRange(date: Instant?): Boolean =
            date != null && range.contains(date, reference = reference, zone = zone)

        val valid = scoped.filter { it.status != null && it.status != SalesOrderStatus.Cancelled }
        val receivedOrders = valid.filter { inRange(it.orderedAt) }
        received = SalesOrderReportTotals(receivedOrders)
        dispatched = SalesOrderReportTotals(
            valid.filter {
                (it.status == SalesOrderStatus.Shipped || it.status == SalesOrderStatus.Completed) &&
                    inRange(it.shippedAt)
            },
        )
        // Uma data efetiva ausente nao e substituida por updatedAt ou por prazo planejado.
        completed = SalesOrderReportQueue(
            scoped.filter { it.status == SalesOrderStatus.Completed && inRange(it.completedAt) },
        )
        cancelled = SalesOrderReportQueue(
            scoped.filter { it.status == SalesOrderStatus.Cancelled && inRange(it.cancelledAt) },
        )
        operations = SalesOrderOperationalSummary(scoped, reference = reference, zone = zone)

        dayGroups = receivedOrders
            .groupBy { it.orderedAt.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant() }
            .map { (day, dayOrders) -> SalesOrderReportDay(day = day, totals = SalesOrderReportTotals(dayOrders)) }
            .sortedByDescending { it.day }

        channelGroups = receivedOrders
            .groupBy { order ->
                SalesOrderChannelSummary.ID(
                    rawValue = order.channelRawValue,
                    customName = if (order.channel == SalesChannel.Other) order.customChannelName.trim() else "",
                )
            }
            .map { (channelId, channelOrders) ->
                SalesOrderChannelSummary(id = channelId, totals = SalesOrderReportTotals(channelOrders))
            }
            .sortedWith { a, b ->
                val revenueOrder = b.totals.revenue.compareTo(a.totals.revenue)
                if (revenueOrder != 0) return@sortedWith revenueOrder
                val rawOrder = a.id.rawValue.compareTo(b.id.rawValue)
                if (rawOrder != 0) return@sortedWith rawOrder
                a.id.customName.compareTo(b.id.customName)
            }

        val rankings = LinkedHashMap<UUID, SalesOrderProductRanking>()
        for (order in receivedOrders) {
            for (item in order.itemList) {
                val current = rankings[item.productID]
                // A ordem cronologica mantem o nome do retrato mais recente, inclusive em empates.
                rankings[item.productID] = SalesOrderProductRanking(
                    productID = item.productID,
                    productName = current?.productName ?: item.productName,
                    quantity = (current?.quantity ?: BigDecimal.ZERO) + item.quantity.toBigDecimal(),
                    revenue = (current?.revenue ?: BigDecimal.ZERO) + item.subtotal,
                    cost = (current?.cost ?: BigDecimal.ZERO) + item.subtotalCost,
                )
            }
        }
        productRankings = rankings.values.sortedWith { a, b ->
            val quantityOrder = b.quantity.compareTo(a.quantity)
            if (quantityOrder != 0) return@sortedWith quantityOrder
            val revenueOrder = b.revenue.compareTo(a.revenue)
            if (revenueOrder != 0) return@sortedWith revenueOrder
            val nameOrder = FoldedNameOrder.compare(a.productName, b.productName)
            if (nameOrder != 0) return@sortedWith nameOrder
            a.productID.toString().compareTo(b.productID.toString())
        }
    }

    fun bestSellingProducts(limit: Int = 3): List<SalesOrderProductRanking> {
        if (limit <= 0) return emptyList()
        return productRankings.take(limit)
    }
}
