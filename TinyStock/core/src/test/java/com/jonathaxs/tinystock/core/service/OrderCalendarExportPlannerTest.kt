package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.model.OrderCalendarEventKind
import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderItem
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Porte de `OrderCalendarExportPlannerTests.swift`. */
class OrderCalendarExportPlannerTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")

    private fun date(month: Int, day: Int, hour: Int = 12): Instant =
        ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, zone).toInstant()

    @Test
    fun pedidoEmProducaoOfereceOsDoisEventos() {
        val order = makeOrder(SalesOrderStatus.AwaitingProduction)
        val drafts = OrderCalendarExportPlanner.drafts(order, storeName = "Loja Teste", zone = zone)

        assertEquals(
            listOf(OrderCalendarEventKind.Production, OrderCalendarEventKind.Shipping),
            drafts.map { it.kind },
        )
        assertEquals(
            listOf(
                SalesOrderSchedule.startOfDay(date(3, 8), zone),
                SalesOrderSchedule.startOfDay(date(3, 10), zone),
            ),
            drafts.map { it.startDate },
        )
    }

    @Test
    fun pedidoProntoOfereceSomenteDespacho() {
        val order = makeOrder(SalesOrderStatus.ReadyToShip)
        val drafts = OrderCalendarExportPlanner.drafts(order, storeName = "Loja Teste", zone = zone)

        assertEquals(listOf(OrderCalendarEventKind.Shipping), drafts.map { it.kind })
    }

    @Test
    fun estadosSemPrazoOperacionalNaoOferecemEventos() {
        for (status in listOf(
            SalesOrderStatus.New,
            SalesOrderStatus.Shipped,
            SalesOrderStatus.Completed,
            SalesOrderStatus.Cancelled,
        )) {
            val order = makeOrder(status)
            assertTrue(OrderCalendarExportPlanner.drafts(order, storeName = "Loja", zone = zone).isEmpty())
        }

        val unknown = makeOrder(SalesOrderStatus.AwaitingProduction).copy(statusRawValue = "future-status")
        assertTrue(OrderCalendarExportPlanner.drafts(unknown, storeName = "Loja", zone = zone).isEmpty())
    }

    @Test
    fun prazoAusenteNaoCriaEventoIncompleto() {
        val order = makeOrder(SalesOrderStatus.InProduction).copy(productionDueAt = null)
        val drafts = OrderCalendarExportPlanner.drafts(order, storeName = "Loja", zone = zone)

        assertEquals(listOf(OrderCalendarEventKind.Shipping), drafts.map { it.kind })
    }

    @Test
    fun eventoUsaDiaLocalMesmoNaMudancaDeHorario() {
        val order = makeOrder(SalesOrderStatus.AwaitingProduction).copy(shippingDueAt = null)
        val draft = OrderCalendarExportPlanner.drafts(order, storeName = "Loja", zone = zone).first()

        assertEquals(0, draft.startDate.atZone(zone).hour)
        assertEquals(8, draft.startDate.atZone(zone).dayOfMonth)
        assertEquals(9, draft.endDate.atZone(zone).dayOfMonth)
        assertEquals(23L * 60 * 60, Duration.between(draft.startDate, draft.endDate).seconds)
    }

    // No Android a formatacao localizada de titulo e notas fica no :app; aqui conferimos os dados extraidos.
    @Test
    fun conteudoIdentificaPedidoELoja() {
        val order = makeOrder(SalesOrderStatus.AwaitingProduction)
        val draft = OrderCalendarExportPlanner.drafts(
            order,
            storeName = "Loja Principal",
            zone = zone,
        ).first()

        assertEquals("${order.id}.production", draft.id)
        assertEquals("Produto Premium", draft.primaryProductName)
        assertEquals(1, draft.additionalProductCount)
        assertEquals("Loja Principal", draft.storeName)
        assertEquals("Ana", draft.buyerName)
        assertEquals("PED-42", draft.externalReference)
        assertEquals(SalesChannel.Shopee, draft.channel)
    }

    private fun makeOrder(status: SalesOrderStatus): SalesOrder = SalesOrder(
        channelRawValue = SalesChannel.Shopee.rawValue,
        fulfillmentRawValue = OrderFulfillment.Production.rawValue,
        statusRawValue = status.rawValue,
        buyerName = "Ana",
        externalReference = "PED-42",
        productionDueAt = date(3, 8),
        shippingDueAt = date(3, 10),
        items = listOf(
            SalesOrderItem(productName = "Produto Premium", variantName = "Preta", quantity = 1),
            SalesOrderItem(
                productName = "Produto Secundario",
                variantName = "Edicao especial",
                quantity = 1,
                position = 1,
            ),
        ),
    )
}
