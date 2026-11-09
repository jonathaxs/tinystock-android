package com.jonathaxs.tinystock.core.report

import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** Testes de `HomeSummary` (calculo puro do resumo da tela Inicio). */
class HomeSummaryTest {

    private val storeID = UUID.randomUUID()
    private val zone: ZoneId = ZoneId.of("America/New_York")

    private fun date(day: Int, hour: Int = 12): Instant =
        ZonedDateTime.of(2026, 9, day, hour, 0, 0, 0, zone).toInstant()

    @Test
    fun resumoVazioComecaZerado() {
        val summary = HomeSummary(
            products = emptyList(),
            variants = emptyList(),
            orders = emptyList(),
            now = date(5),
            zone = zone,
        )
        assertEquals(HomeSummary(), summary)
        assertEquals(0, summary.products)
        assertEquals(0, summary.units)
        assertEquals(0, summary.outOfStock)
        assertEquals(0, summary.production)
        assertEquals(0, summary.readyToShip)
        assertEquals(0, summary.shipped)
        assertEquals(0, summary.overdue)
        assertEquals(0, summary.openOrders)
    }

    @Test
    fun catalogoSomaUnidadesDasVariacoesEContaProdutosSemEstoque() {
        val withStock = Product(storeID = storeID, name = "Com estoque")
        val emptyProduct = Product(storeID = storeID, name = "Zerado")
        val noVariantsProduct = Product(storeID = storeID, name = "Sem variacao")
        val foreignProduct = Product(storeID = UUID.randomUUID(), name = "Outra loja")

        val variants = listOf(
            ProductVariant(storeID = storeID, productID = withStock.id, name = "A", quantity = 3),
            ProductVariant(storeID = storeID, productID = withStock.id, name = "B", quantity = 4),
            ProductVariant(storeID = storeID, productID = emptyProduct.id, name = "Unica", quantity = 0),
            // Variacao de outra loja ou outro produto nao entra na soma.
            ProductVariant(storeID = foreignProduct.storeID, productID = withStock.id, name = "Estranha", quantity = 99),
            ProductVariant(storeID = storeID, productID = foreignProduct.id, name = "Estranha 2", quantity = 99),
        )

        val summary = HomeSummary(
            products = listOf(withStock, emptyProduct, noVariantsProduct),
            variants = variants,
            orders = emptyList(),
            now = date(5),
            zone = zone,
        )

        assertEquals(3, summary.products)
        assertEquals(7, summary.units)
        assertEquals(2, summary.outOfStock)
    }

    @Test
    fun pedidosContamPorEtapaAtrasoEIgnoramEncerradosOuDesconhecidos() {
        val newProduction = SalesOrder(
            storeID = storeID,
            fulfillmentRawValue = OrderFulfillment.Production.rawValue,
            statusRawValue = SalesOrderStatus.New.rawValue,
        )
        val newReady = SalesOrder(
            storeID = storeID,
            fulfillmentRawValue = OrderFulfillment.ReadyStock.rawValue,
            statusRawValue = SalesOrderStatus.New.rawValue,
        )
        val awaitingOverdue = SalesOrder(
            storeID = storeID,
            fulfillmentRawValue = OrderFulfillment.Production.rawValue,
            statusRawValue = SalesOrderStatus.AwaitingProduction.rawValue,
            productionDueAt = date(4),
            shippingDueAt = date(8),
        )
        val inProductionOnTime = SalesOrder(
            storeID = storeID,
            fulfillmentRawValue = OrderFulfillment.Production.rawValue,
            statusRawValue = SalesOrderStatus.InProduction.rawValue,
            productionDueAt = date(5),
            shippingDueAt = date(7),
        )
        val readyOverdue = SalesOrder(
            storeID = storeID,
            statusRawValue = SalesOrderStatus.ReadyToShip.rawValue,
            shippingDueAt = date(4),
        )
        val shipped = SalesOrder(
            storeID = storeID,
            statusRawValue = SalesOrderStatus.Shipped.rawValue,
            shippingDueAt = date(1),
        )
        val completed = SalesOrder(
            storeID = storeID,
            statusRawValue = SalesOrderStatus.Completed.rawValue,
            shippingDueAt = date(1),
        )
        val cancelled = SalesOrder(
            storeID = storeID,
            statusRawValue = SalesOrderStatus.Cancelled.rawValue,
            shippingDueAt = date(1),
        )
        val unknown = SalesOrder(
            storeID = storeID,
            statusRawValue = "future-status",
            shippingDueAt = date(1),
        )

        val summary = HomeSummary(
            products = emptyList(),
            variants = emptyList(),
            orders = listOf(
                newProduction,
                newReady,
                awaitingOverdue,
                inProductionOnTime,
                readyOverdue,
                shipped,
                completed,
                cancelled,
                unknown,
            ),
            now = date(5),
            zone = zone,
        )

        assertEquals(3, summary.production)
        assertEquals(2, summary.readyToShip)
        assertEquals(1, summary.shipped)
        assertEquals(6, summary.openOrders)
        assertEquals(2, summary.overdue)
    }
}
