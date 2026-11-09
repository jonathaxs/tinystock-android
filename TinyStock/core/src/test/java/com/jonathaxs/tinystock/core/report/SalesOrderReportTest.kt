package com.jonathaxs.tinystock.core.report

import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderItem
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import com.jonathaxs.tinystock.core.support.assertDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** Porte de `SalesOrderReportTests.swift`. */
class SalesOrderReportTest {

    private val storeID = UUID.randomUUID()
    private val zone: ZoneId = ZoneId.of("America/New_York")

    private fun date(month: Int = 9, day: Int = 5, hour: Int = 12, year: Int = 2026): Instant =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant()

    private fun order(
        status: SalesOrderStatus = SalesOrderStatus.ReadyToShip,
        day: Int = 5,
        quantity: Int = 2,
        price: BigDecimal = BigDecimal(50),
        cost: BigDecimal = BigDecimal(20),
        fee: BigDecimal = BigDecimal(10),
        channel: SalesChannel = SalesChannel.Direct,
        productID: UUID = UUID.randomUUID(),
        name: String = "Produto",
    ): SalesOrder = SalesOrder(
        storeID = storeID,
        channelRawValue = channel.rawValue,
        statusRawValue = status.rawValue,
        orderedAt = date(9, day),
        channelFeePercentage = BigDecimal(99),
        channelFeeAmount = fee,
        items = listOf(
            SalesOrderItem(
                storeID = storeID,
                productID = productID,
                productName = name,
                unitPrice = price,
                unitCost = cost,
                quantity = quantity,
            ),
        ),
    )

    private fun summary(
        orders: List<SalesOrder>,
        range: SalesOrderReportRange = SalesOrderReportRange.preset(SalesReportPeriod.Today),
    ): SalesOrderReportSummary = SalesOrderReportSummary(
        orders = orders,
        storeID = storeID,
        range = range,
        reference = date(),
        zone = zone,
    )

    @Test
    fun resumoVazioZeraTotaisEFilas() {
        val report = summary(emptyList())
        assertEquals(storeID, report.storeID)
        assertTrue(report.received.isEmpty)
        assertEquals(0, report.received.orderCount)
        assertDecimal("0", report.received.unitCount)
        assertDecimal("0", report.received.revenue)
        assertDecimal("0", report.received.cost)
        assertDecimal("0", report.received.channelFees)
        assertDecimal("0", report.received.grossProfit)
        assertDecimal("0", report.received.netProfit)
        assertTrue(report.dispatched.isEmpty)
        assertEquals(0, report.completed.orderCount)
        assertEquals(0, report.cancelled.orderCount)
        assertEquals(0, report.operations.toProduce.orderCount)
        assertEquals(0, report.operations.readyToShip.orderCount)
        assertEquals(0, report.operations.overdue.orderCount)
        assertTrue(report.dayGroups.isEmpty())
        assertTrue(report.channelGroups.isEmpty())
        assertTrue(report.bestSellingProducts().isEmpty())
    }

    @Test
    fun lojaIsolaFinancasRankingsEventosEFilas() {
        val own = order()
        val foreign = SalesOrderStatus.entries.map { status ->
            order(status = status, quantity = 99, channel = SalesChannel.Shopee).copy(
                storeID = UUID.randomUUID(),
                productionDueAt = date(9, 1),
                shippingDueAt = date(9, 1),
                shippedAt = date(),
                completedAt = date(),
                cancelledAt = date(),
            )
        }
        val report = summary(listOf(own) + foreign)
        assertEquals(listOf(own.id), report.received.orderIDs)
        assertDecimal("100", report.received.revenue)
        assertEquals(1, report.bestSellingProducts().size)
        assertEquals(listOf("direct"), report.channelGroups.map { it.id.rawValue })
        assertEquals(listOf(own.id), report.dayGroups.first().totals.orderIDs)
        assertTrue(report.dispatched.isEmpty)
        assertEquals(0, report.completed.orderCount)
        assertEquals(0, report.cancelled.orderCount)
        assertEquals(listOf(own.id), report.operations.readyToShip.orderIDs)
        assertEquals(0, report.operations.toProduce.orderCount)
        assertEquals(0, report.operations.overdue.orderCount)
    }

    @Test
    fun recebidosUsamDataDaVendaEExcluemCanceladosEDesconhecidos() {
        val orders = SalesOrderStatus.entries.map { order(status = it) }
        val unknown = order().copy(statusRawValue = "future-status")
        val outside = order(day = 4).copy(shippingDueAt = date(), updatedAt = date())
        val report = summary(orders + listOf(unknown, outside))
        assertEquals(6, report.received.orderCount)
        assertDecimal("12", report.received.unitCount)
        assertDecimal("600", report.received.revenue)
        assertDecimal("240", report.received.cost)
        assertDecimal("60", report.received.channelFees)
        assertDecimal("360", report.received.grossProfit)
        assertDecimal("300", report.received.netProfit)
        assertEquals(listOf(unknown.id), report.operations.unknownStatus.orderIDs)
        assertEquals(6, report.bestSellingProducts(limit = 99).size)
        assertEquals(report.received, report.dayGroups.first().totals)
        assertEquals(report.received, report.channelGroups.first().totals)
    }

    @Test
    fun valoresDecimaisETaxaSalvaPreservamPrejuizo() {
        val value = order(
            quantity = 3,
            price = BigDecimal("10.10"),
            cost = BigDecimal("11.25"),
            fee = BigDecimal("2"),
        )
        val report = summary(listOf(value))
        assertDecimal("30.30", report.received.revenue)
        assertDecimal("33.75", report.received.cost)
        assertDecimal("2", report.received.channelFees)
        assertDecimal("-5.45", report.received.netProfit)
        assertDecimal("-5.45", report.channelGroups.first().totals.netProfit)
    }

    @Test
    fun resultadoEhRetratoImutavelSemAlterarPedido() {
        val value = order()
        val report = summary(listOf(value))
        assertEquals(SalesOrderStatus.ReadyToShip, value.status)
        assertDecimal("10", value.channelFeeAmount)

        val mutated = value.copy(
            statusRawValue = SalesOrderStatus.Cancelled.rawValue,
            channelFeeAmount = BigDecimal.ZERO,
            items = listOf(value.items.first().copy(unitPrice = BigDecimal(999), productName = "Renomeado")),
        )
        assertDecimal("100", report.received.revenue)
        assertDecimal("50", report.received.netProfit)
        assertEquals("Produto", report.bestSellingProducts().first().productName)
        assertTrue(summary(listOf(mutated)).received.isEmpty)
    }

    @Test
    fun quantidadesAgregadasNaoEstouramInt() {
        val first = order(quantity = Int.MAX_VALUE, price = BigDecimal.ONE, cost = BigDecimal.ZERO, fee = BigDecimal.ZERO)
        val second = order(
            quantity = Int.MAX_VALUE,
            price = BigDecimal.ONE,
            cost = BigDecimal.ZERO,
            fee = BigDecimal.ZERO,
            productID = first.items.first().productID,
        )
        val expected = Int.MAX_VALUE.toBigDecimal() * BigDecimal(2)
        val report = summary(listOf(first, second))
        assertEquals(0, expected.compareTo(report.received.unitCount))
        assertEquals(0, expected.compareTo(report.received.revenue))
        assertEquals(0, expected.compareTo(report.operations.readyToShip.unitCount))
        assertEquals(0, expected.compareTo(report.bestSellingProducts().first().quantity))
    }

    @Test
    fun despachoUsaDataEfetivaEIncluiPedidoJaConcluido() {
        val shipped = order(status = SalesOrderStatus.Shipped, day = 1).copy(shippedAt = date())
        val completed = order(status = SalesOrderStatus.Completed, day = 2).copy(
            shippedAt = date(),
            completedAt = date(9, 6),
        )
        val planned = order().copy(shippingDueAt = date())
        val olderShipment = order(status = SalesOrderStatus.Completed).copy(
            shippedAt = date(9, 4),
            completedAt = date(),
        )
        val report = summary(listOf(shipped, completed, planned, olderShipment))
        assertEquals(setOf(shipped.id, completed.id), report.dispatched.orderIDs.toSet())
        assertDecimal("200", report.dispatched.revenue)
        assertDecimal("100", report.dispatched.netProfit)
        assertEquals(listOf(olderShipment.id), report.completed.orderIDs)
        assertEquals(setOf(planned.id, olderShipment.id), report.received.orderIDs.toSet())
    }

    @Test
    fun datasEfetivasAusentesNaoUsamPrazoOuAtualizacaoComoFallback() {
        val orders = listOf(
            SalesOrderStatus.Shipped,
            SalesOrderStatus.Completed,
            SalesOrderStatus.Cancelled,
        ).map { status ->
            order(status = status).copy(updatedAt = date(), shippingDueAt = date())
        }
        val report = summary(orders, range = SalesOrderReportRange.preset(SalesReportPeriod.AllTime))
        assertTrue(report.dispatched.isEmpty)
        assertEquals(0, report.completed.orderCount)
        assertEquals(0, report.cancelled.orderCount)
        assertEquals(2, report.received.orderCount)
    }

    @Test
    fun cancelamentoContaNoDiaEfetivoSemSomarValoresPreservados() {
        val recentCancellation = order(status = SalesOrderStatus.Cancelled, day = 1).copy(cancelledAt = date())
        val oldCancellation = order(status = SalesOrderStatus.Cancelled).copy(
            cancelledAt = date(9, 4),
            shippedAt = date(),
        )
        val report = summary(listOf(recentCancellation, oldCancellation))
        assertEquals(listOf(recentCancellation.id), report.cancelled.orderIDs)
        assertDecimal("2", report.cancelled.unitCount)
        assertTrue(report.received.isEmpty)
        assertTrue(report.dispatched.isEmpty)
        assertDecimal("100", recentCancellation.total)
        assertDecimal("50", recentCancellation.netProfit)
    }

    @Test
    fun filasAtuaisIndependemDoPeriodoFinanceiro() {
        val awaiting = order(status = SalesOrderStatus.AwaitingProduction, day = 1).copy(
            productionDueAt = date(9, 4),
            shippingDueAt = date(9, 8),
        )
        val producing = order(status = SalesOrderStatus.InProduction, day = 2, quantity = 3).copy(
            productionDueAt = date(9, 5, hour = 0),
        )
        val ready = order(day = 2).copy(shippingDueAt = date(9, 4))
        val shipped = order(status = SalesOrderStatus.Shipped, day = 2).copy(shippingDueAt = date(9, 4))
        val fresh = order(status = SalesOrderStatus.New, day = 1)
        val orders = listOf(
            awaiting,
            producing,
            ready,
            shipped,
            fresh,
            order(status = SalesOrderStatus.Cancelled, day = 1),
            order(status = SalesOrderStatus.Completed, day = 1),
        )
        val report = summary(orders)
        assertTrue(report.received.isEmpty)
        assertEquals(
            summary(orders, range = SalesOrderReportRange.preset(SalesReportPeriod.AllTime)).operations,
            report.operations,
        )
        assertEquals(listOf(fresh.id), report.operations.newOrders.orderIDs)
        assertEquals(listOf(awaiting.id), report.operations.awaitingProduction.orderIDs)
        assertEquals(listOf(producing.id), report.operations.inProduction.orderIDs)
        assertEquals(2, report.operations.toProduce.orderCount)
        assertDecimal("5", report.operations.toProduce.unitCount)
        assertEquals(listOf(ready.id), report.operations.readyToShip.orderIDs)
        assertEquals(listOf(shipped.id), report.operations.shipped.orderIDs)
        assertEquals(setOf(awaiting.id, ready.id), report.operations.overdue.orderIDs.toSet())
    }

    @Test
    fun rankingAgrupaVariacoesPorIDEMantemNomeMaisRecente() {
        val productID = UUID.randomUUID()
        val old = order(day = 4, productID = productID, name = "Antigo")
        val baseRecent = order(productID = productID, name = "Atual")
        val recent = baseRecent.copy(
            items = baseRecent.items + SalesOrderItem(
                storeID = storeID,
                productID = productID,
                productName = "Atual",
                variantName = "Outra",
                unitPrice = BigDecimal(20),
                unitCost = BigDecimal(5),
                quantity = 1,
                position = 1,
            ),
        )
        val sameName = order(quantity = 1, name = "Atual")
        val report = summary(
            listOf(old, recent, sameName),
            range = SalesOrderReportRange.preset(SalesReportPeriod.LastSevenDays),
        )
        val ranking = report.bestSellingProducts().first()
        assertEquals(productID, ranking.productID)
        assertEquals("Atual", ranking.productName)
        assertDecimal("5", ranking.quantity)
        assertDecimal("220", ranking.revenue)
        assertDecimal("85", ranking.cost)
        assertDecimal("135", ranking.grossProfit)
        assertEquals(2, report.bestSellingProducts().size)
    }

    @Test
    fun rankingRespeitaQuantidadeReceitaNomeIDELimite() {
        val values = listOf(
            order(quantity = 2, price = BigDecimal(50), name = "B"),
            order(quantity = 2, price = BigDecimal(50), name = "A"),
            order(quantity = 3, price = BigDecimal(1), name = "C"),
            order(quantity = 2, price = BigDecimal(60), name = "D"),
            order(quantity = 2, price = BigDecimal(50), name = "A"),
        )
        val report = summary(values)
        assertEquals(listOf("C", "D", "A", "A", "B"), report.bestSellingProducts(limit = 99).map { it.productName })
        assertEquals(3, report.bestSellingProducts().size)
        assertTrue(report.bestSellingProducts(limit = 0).isEmpty())
        assertTrue(report.bestSellingProducts(limit = -1).isEmpty())
        assertEquals(
            summary(values.reversed()).bestSellingProducts(limit = 99),
            report.bestSellingProducts(limit = 99),
        )
    }

    @Test
    fun nomesEmpatadosNaDataUsamIDEstavelDoPedido() {
        val first = order(name = "Primeiro").copy(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        )
        val second = order(
            productID = first.items.first().productID,
            name = "Segundo",
        ).copy(
            id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        )
        assertEquals("Primeiro", summary(listOf(second, first)).bestSellingProducts().first().productName)
    }

    @Test
    fun canaisSeparamCustomizadosEDesconhecidosSemPerderTotais() {
        val direct = order().copy(customChannelName = "Ignorar")
        val shopee = order(channel = SalesChannel.Shopee)
        val mercadoLivre = order(channel = SalesChannel.MercadoLivre)
        val otherA = order(channel = SalesChannel.Other).copy(customChannelName = " Feira ")
        val otherB = order(channel = SalesChannel.Other).copy(customChannelName = "Feira")
        val otherC = order(channel = SalesChannel.Other).copy(customChannelName = "Site")
        val unknown = order().copy(channelRawValue = "future-channel")
        val report = summary(listOf(direct, shopee, mercadoLivre, otherA, otherB, otherC, unknown))
        assertEquals(6, report.channelGroups.size)
        // O texto localizado de canais padrao/desconhecidos fica no :app; aqui conferimos o nome customizado.
        assertEquals("Feira", report.channelGroups.first().customDisplayName)
        assertEquals(2, report.channelGroups.first().totals.orderCount)
        assertTrue(report.channelGroups.any { it.id.rawValue == "future-channel" && it.channel == null })
        assertEquals(
            0,
            report.received.revenue.compareTo(report.channelGroups.sumOf { it.totals.revenue }),
        )
        assertEquals(
            0,
            report.received.netProfit.compareTo(report.channelGroups.sumOf { it.totals.netProfit }),
        )
    }

    @Test
    fun agrupamentoDiarioUsaDiaLocalEReconciliaTotais() {
        val late = order().copy(orderedAt = date(9, 5, hour = 23))
        val early = order().copy(orderedAt = date(9, 5, hour = 0))
        val old = order(day = 4)
        val report = summary(
            listOf(old, late, early),
            range = SalesOrderReportRange.preset(SalesReportPeriod.LastSevenDays),
        )
        assertEquals(listOf(date(9, 5, hour = 0), date(9, 4, hour = 0)), report.dayGroups.map { it.day })
        assertEquals(2, report.dayGroups.first().totals.orderCount)
        assertEquals(0, report.received.revenue.compareTo(report.dayGroups.sumOf { it.totals.revenue }))
        assertEquals(0, report.received.netProfit.compareTo(report.dayGroups.sumOf { it.totals.netProfit }))
    }

    @Test
    fun recortesRapidosFiltramPedidosNosLimites() {
        val today = order()
        val tomorrow = order(day = 6).copy(orderedAt = date(9, 6, hour = 0))
        val monthStart = order(day = 1).copy(orderedAt = date(9, 1, hour = 0))
        val sixDaysAgo = order().copy(orderedAt = date(8, 30, hour = 0))
        val sevenDaysAgo = order().copy(orderedAt = date(8, 29, hour = 23))
        val nextMonth = order().copy(orderedAt = date(10, 1, hour = 0))
        val values = listOf(today, tomorrow, monthStart, sixDaysAgo, sevenDaysAgo, nextMonth)

        assertEquals(listOf(today.id), summary(values).received.orderIDs)
        assertEquals(
            setOf(today.id, monthStart.id, sixDaysAgo.id),
            summary(values, range = SalesOrderReportRange.preset(SalesReportPeriod.LastSevenDays))
                .received.orderIDs.toSet(),
        )
        assertEquals(
            setOf(today.id, tomorrow.id, monthStart.id),
            summary(values, range = SalesOrderReportRange.preset(SalesReportPeriod.CurrentMonth))
                .received.orderIDs.toSet(),
        )
        assertEquals(
            6,
            summary(values, range = SalesOrderReportRange.preset(SalesReportPeriod.AllTime)).received.orderCount,
        )
    }

    @Test
    fun personalizadoIncluiDiasCompletosENaoIncluiProximaMeiaNoite() {
        val range = SalesOrderReportRange.custom(start = date(9, 4, hour = 22), end = date(9, 5, hour = 1))
        assertTrue(range.contains(date(9, 4, hour = 0), zone = zone))
        assertTrue(range.contains(date(9, 5, hour = 23), zone = zone))
        assertFalse(range.contains(date(9, 6, hour = 0), zone = zone))
        assertFalse(range.contains(date(9, 3, hour = 23), zone = zone))
        assertEquals(
            2,
            summary(listOf(order(day = 3), order(day = 4), order(day = 5), order(day = 6)), range = range)
                .received.orderCount,
        )
    }

    @Test
    fun personalizadoInvertidoFicaVazioMasMesmoDiaEhValido() {
        val invalid = SalesOrderReportRange.custom(start = date(9, 6), end = date(9, 4))
        assertNull(invalid.dateInterval(zone = zone))
        assertTrue(summary(listOf(order()), range = invalid).received.isEmpty)

        val sameDay = SalesOrderReportRange.custom(start = date(9, 5, hour = 23), end = date(9, 5, hour = 0))
        assertEquals(1, summary(listOf(order()), range = sameDay).received.orderCount)
    }

    @Test
    fun personalizadoRespeitaHorarioDeVeraoEAnoBissexto() {
        val spring = SalesOrderReportRange.custom(start = date(3, 8), end = date(3, 8))
        val autumn = SalesOrderReportRange.custom(start = date(11, 1), end = date(11, 1))
        assertEquals(23L * 60 * 60, spring.dateInterval(zone = zone)!!.durationSeconds)
        assertEquals(25L * 60 * 60, autumn.dateInterval(zone = zone)!!.durationSeconds)

        val leap = SalesOrderReportRange.custom(
            start = date(2, 28, year = 2028),
            end = date(2, 29, year = 2028),
        )
        assertTrue(leap.contains(date(2, 29, hour = 23, year = 2028), zone = zone))
        assertFalse(leap.contains(date(3, 1, hour = 0, year = 2028), zone = zone))
    }
}
