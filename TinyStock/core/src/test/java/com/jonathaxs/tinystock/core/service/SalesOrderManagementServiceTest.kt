package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.InMemoryTinyStockStore
import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import com.jonathaxs.tinystock.core.model.StockMovementKind
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.support.assertDecimal
import com.jonathaxs.tinystock.core.support.expectError
import com.jonathaxs.tinystock.core.support.withStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Porte de `SalesOrderManagementServiceTests.swift`. */
class SalesOrderManagementServiceTest {

    private val now: Instant = Instant.ofEpochSecond(1_800_000_000)
    private val zone = ZoneOffset.UTC

    private class Fixture(
        val store: InMemoryTinyStockStore,
        val storeProfile: StoreProfile,
        val product: Product,
        val variant: ProductVariant,
        val order: SalesOrder,
    )

    private suspend fun fixture(
        store: InMemoryTinyStockStore,
        fulfillment: OrderFulfillment = OrderFulfillment.ReadyStock,
        stock: Int = 5,
        sold: Int = 2,
    ): Fixture {
        val storeProfile = StoreProfile(name = "Loja")
        store.saveStore(storeProfile)
        val product = Product(
            storeID = storeProfile.id,
            name = "Caneca",
            costPrice = BigDecimal(10),
            salePrice = BigDecimal(25),
        )
        store.saveProduct(product)
        val variant = ProductVariantService.create(
            product,
            "Verde",
            initialQuantity = stock,
            store = store,
        )
        val order = SalesOrderService.register(
            storeID = storeProfile.id,
            lines = listOf(SalesOrderLine(productID = product.id, variantID = variant.id, quantity = sold)),
            fulfillment = fulfillment,
            orderedAt = now,
            productionDueAt = if (fulfillment == OrderFulfillment.Production) now else null,
            shippingDueAt = now.plusSeconds(86_400),
            now = now,
            zone = zone,
            store = store,
        )
        return Fixture(store, storeProfile, product, variant, order)
    }

    private fun details(
        fulfillment: OrderFulfillment,
        fee: BigDecimal = BigDecimal("12.5"),
        orderedAt: Instant? = null,
        productionDueAt: Instant? = null,
        shippingDueAt: Instant? = null,
    ): SalesOrderDetails = SalesOrderDetails(
        channel = SalesChannel.Other,
        customChannelName = "  Minha loja  ",
        buyerName = "  Maria  ",
        externalReference = "  PED-10  ",
        orderedAt = orderedAt ?: now,
        productionDueAt = if (fulfillment == OrderFulfillment.Production) (productionDueAt ?: now) else productionDueAt,
        shippingDueAt = shippingDueAt ?: now.plusSeconds(86_400),
        trackingCode = "  BR123  ",
        note = "  Entregar pela manha  ",
        channelFeePercentage = fee,
    )

    private class SimulatedSaveFailure : RuntimeException("simulated")

    private class FailingOrderSaveStore(
        private val inner: InMemoryTinyStockStore,
    ) : TinyStockStore by inner {
        override suspend fun saveOrder(order: SalesOrder) {
            throw SimulatedSaveFailure()
        }
    }

    @Test
    fun edicaoAtualizaDadosETaxaSemAlterarItemEstoqueOuEstado() = withStore { rawStore ->
        val f = fixture(rawStore)
        val previousItem = f.order.itemList.first()
        val previousMovementCount = f.store.movements(f.storeProfile.id).size
        val changedAt = now.plusSeconds(3_600)

        val order = SalesOrderService.update(
            id = f.order.id,
            details = details(OrderFulfillment.ReadyStock),
            now = changedAt,
            zone = zone,
            store = f.store,
        )

        assertEquals(SalesChannel.Other, order.channel)
        assertEquals("Minha loja", order.customChannelDisplayName)
        assertEquals("Maria", order.buyerName)
        assertEquals("PED-10", order.externalReference)
        assertEquals("Entregar pela manha", order.note)
        assertDecimal("12.5", order.channelFeePercentage)
        assertEquals("BR123", order.trackingCode)
        assertDecimal("6.25", order.channelFeeAmount)
        assertDecimal("23.75", order.netProfit)
        assertNull(order.productionDueAt)
        assertEquals(changedAt, order.updatedAt)
        assertEquals(SalesOrderStatus.ReadyToShip, order.status)
        assertEquals(previousItem, order.itemList.first())
        assertEquals(order, f.store.order(f.order.id))
        assertEquals(3, f.store.variant(f.variant.id)!!.quantity)
        assertEquals(previousMovementCount, f.store.movements(f.storeProfile.id).size)
    }

    @Test
    fun edicaoDeProducaoPreservaAtendimentoEAtualizaPrazos() = withStore { rawStore ->
        val f = fixture(rawStore, fulfillment = OrderFulfillment.Production, stock = 0, sold = 4)
        val production = now.plusSeconds(86_400)
        val shipping = now.plusSeconds(2 * 86_400)

        val order = SalesOrderService.update(
            id = f.order.id,
            details = details(
                fulfillment = OrderFulfillment.Production,
                productionDueAt = production,
                shippingDueAt = shipping,
            ),
            now = now,
            zone = zone,
            store = f.store,
        )

        assertEquals(OrderFulfillment.Production, order.fulfillment)
        assertEquals(SalesOrderStatus.AwaitingProduction, order.status)
        assertEquals(production, order.productionDueAt)
        assertEquals(shipping, order.shippingDueAt)
        assertEquals(0, f.store.variant(f.variant.id)!!.quantity)
    }

    @Test
    fun edicaoRecusaTaxaEDatasInvalidasSemMudarPedido() = withStore { rawStore ->
        val f = fixture(rawStore)
        val originalBuyer = f.order.buyerName

        expectError(SalesOrderError.InvalidChannelFee) {
            SalesOrderService.update(
                id = f.order.id,
                details = details(OrderFulfillment.ReadyStock, fee = BigDecimal(101)),
                now = now,
                zone = zone,
                store = f.store,
            )
        }
        expectError(SalesOrderError.FutureOrderDate) {
            SalesOrderService.update(
                id = f.order.id,
                details = details(OrderFulfillment.ReadyStock, orderedAt = now.plusSeconds(86_400)),
                now = now,
                zone = zone,
                store = f.store,
            )
        }

        val current = f.store.order(f.order.id)!!
        assertEquals(originalBuyer, current.buyerName)
        assertEquals(SalesChannel.Direct, current.channel)
        assertEquals(3, f.store.variant(f.variant.id)!!.quantity)
    }

    @Test
    fun pedidoEncerradoOuDespachadoNaoPodeSerEditado() = withStore { rawStore ->
        val f = fixture(rawStore)

        for (status in listOf(SalesOrderStatus.Shipped, SalesOrderStatus.Completed, SalesOrderStatus.Cancelled)) {
            f.store.saveOrder(f.order.copy(statusRawValue = status.rawValue))
            expectError(SalesOrderManagementError.EditingNotAllowed) {
                SalesOrderService.update(
                    id = f.order.id,
                    details = details(OrderFulfillment.ReadyStock),
                    now = now,
                    zone = zone,
                    store = f.store,
                )
            }
            assertEquals(3, f.store.variant(f.variant.id)!!.quantity)
        }
    }

    @Test
    fun dadosDesconhecidosEIDInexistenteNaoSaoAlterados() = withStore { rawStore ->
        val f = fixture(rawStore)
        f.store.saveOrder(f.order.copy(statusRawValue = "future-status"))

        expectError(SalesOrderManagementError.InvalidOrderData) {
            SalesOrderService.update(
                id = f.order.id,
                details = details(OrderFulfillment.ReadyStock),
                now = now,
                zone = zone,
                store = f.store,
            )
        }
        expectError(SalesOrderManagementError.OrderUnavailable) {
            SalesOrderService.cancel(id = UUID.randomUUID(), date = now, store = f.store)
        }
    }

    @Test
    fun cancelamentoDeProntaEntregaDevolveEstoqueComReversaoAuditavel() = withStore { rawStore ->
        val f = fixture(rawStore)
        val original = StockService.movements(f.variant, f.product, f.store)
            .first { it.kind == StockMovementKind.OrderWithdrawal }
        val cancelledAt = now.plusSeconds(7_200)

        val order = SalesOrderService.cancel(
            id = f.order.id,
            reason = "  Cliente desistiu  ",
            date = cancelledAt,
            store = f.store,
        )

        assertEquals(SalesOrderStatus.Cancelled, order.status)
        assertEquals(cancelledAt, order.cancelledAt)
        assertEquals(cancelledAt, order.updatedAt)
        assertEquals("Cliente desistiu", order.cancellationReason)

        val updatedVariant = f.store.variant(f.variant.id)!!
        assertEquals(5, updatedVariant.quantity)
        assertEquals(cancelledAt, updatedVariant.updatedAt)
        assertEquals(cancelledAt, f.store.movement(original.id)!!.reversedAt)

        val movements = StockService.movements(f.variant, f.product, f.store)
        val reversal = movements.first { it.reversedMovementID == original.id }
        assertEquals(StockMovementKind.Reversal, reversal.kind)
        assertEquals(2, reversal.quantityDelta)
        assertEquals(5, reversal.balanceAfter)
        assertEquals(order.id, reversal.referenceID)
        assertEquals("Cliente desistiu", reversal.note)
        assertDecimal("50", order.total)
        assertEquals(2, order.itemList.first().quantity)
    }

    @Test
    fun cancelamentoDeProducaoNaoCriaMovimentoDeEstoque() = withStore { rawStore ->
        val f = fixture(rawStore, fulfillment = OrderFulfillment.Production, stock = 0, sold = 8)

        val order = SalesOrderService.cancel(
            id = f.order.id,
            reason = "Sem material",
            date = now,
            store = f.store,
        )

        assertEquals(SalesOrderStatus.Cancelled, order.status)
        assertEquals("Sem material", order.cancellationReason)
        assertEquals(0, f.store.variant(f.variant.id)!!.quantity)
        assertTrue(f.store.movements(f.storeProfile.id).isEmpty())
    }

    // O iOS tem "dataDeCancelamentoInvalidaNaoAlteraPedido" para Date.infinity; Instant em Kotlin e sempre finito.

    @Test
    fun pedidoDespachadoOuTerminalNaoPodeSerCancelado() = withStore { rawStore ->
        val f = fixture(rawStore)

        for (status in listOf(SalesOrderStatus.Shipped, SalesOrderStatus.Completed, SalesOrderStatus.Cancelled)) {
            f.store.saveOrder(f.order.copy(statusRawValue = status.rawValue))
            expectError(SalesOrderManagementError.CancellationNotAllowed) {
                SalesOrderService.cancel(id = f.order.id, date = now, store = f.store)
            }
            assertEquals(3, f.store.variant(f.variant.id)!!.quantity)
            assertEquals(2, f.store.movements(f.storeProfile.id).size)
        }
    }

    @Test
    fun segundoCancelamentoNaoDevolveEstoqueNovamente() = withStore { rawStore ->
        val f = fixture(rawStore)
        SalesOrderService.cancel(id = f.order.id, date = now, store = f.store)

        expectError(SalesOrderManagementError.CancellationNotAllowed) {
            SalesOrderService.cancel(id = f.order.id, date = now, store = f.store)
        }
        assertEquals(5, f.store.variant(f.variant.id)!!.quantity)
        assertEquals(3, f.store.movements(f.storeProfile.id).size)
    }

    @Test
    fun historicoInconsistenteBloqueiaCancelamentoAntesDeQualquerMudanca() = withStore { rawStore ->
        val f = fixture(rawStore)
        val withdrawal = StockService.movements(f.variant, f.product, f.store)
            .first { it.kind == StockMovementKind.OrderWithdrawal }
        f.store.saveMovement(withdrawal.copy(quantityDelta = -1))

        expectError(SalesOrderManagementError.StockHistoryMismatch) {
            SalesOrderService.cancel(id = f.order.id, date = now, store = f.store)
        }

        val currentOrder = f.store.order(f.order.id)!!
        assertEquals(SalesOrderStatus.ReadyToShip, currentOrder.status)
        assertNull(currentOrder.cancelledAt)
        assertTrue(currentOrder.cancellationReason.isEmpty())
        assertEquals(3, f.store.variant(f.variant.id)!!.quantity)
        assertNull(f.store.movement(withdrawal.id)!!.reversedAt)
        assertEquals(2, f.store.movements(f.storeProfile.id).size)
    }

    @Test
    fun cancelamentoComDoisItensDevolveCadaVariacaoUmaVez() = withStore { rawStore ->
        val f = fixture(rawStore, fulfillment = OrderFulfillment.Production, stock = 5, sold = 1)
        val other = ProductVariantService.create(f.product, "Branca", initialQuantity = 4, store = f.store)
        // Substitui o pedido de producao por um pedido de pronta entrega com duas linhas.
        f.store.deleteOrder(f.order.id)
        val order = SalesOrderService.register(
            storeID = f.storeProfile.id,
            lines = listOf(
                SalesOrderLine(productID = f.product.id, variantID = f.variant.id, quantity = 2),
                SalesOrderLine(productID = f.product.id, variantID = other.id, quantity = 3),
            ),
            fulfillment = OrderFulfillment.ReadyStock,
            orderedAt = now,
            shippingDueAt = now,
            now = now,
            zone = zone,
            store = f.store,
        )
        assertEquals(3, f.store.variant(f.variant.id)!!.quantity)
        assertEquals(1, f.store.variant(other.id)!!.quantity)

        SalesOrderService.cancel(id = order.id, date = now, store = f.store)

        assertEquals(5, f.store.variant(f.variant.id)!!.quantity)
        assertEquals(4, f.store.variant(other.id)!!.quantity)
        assertEquals(2, f.store.movements(f.storeProfile.id).count { it.reversedMovementID != null })
    }

    @Test
    fun falhaNoSaveRestauraPedidoMovimentoESaldoEmMemoriaENoBanco() = withStore { rawStore ->
        val f = fixture(rawStore)
        val previousUpdatedAt = f.store.variant(f.variant.id)!!.updatedAt
        val withdrawal = StockService.movements(f.variant, f.product, f.store)
            .first { it.kind == StockMovementKind.OrderWithdrawal }
        val failingStore = FailingOrderSaveStore(f.store)

        try {
            SalesOrderService.cancel(
                id = f.order.id,
                reason = "Teste",
                date = now.plusSeconds(1),
                store = failingStore,
            )
            fail("Esperava SimulatedSaveFailure")
        } catch (_: SimulatedSaveFailure) {
            // Falha simulada esperada.
        }

        val currentOrder = f.store.order(f.order.id)!!
        assertEquals(SalesOrderStatus.ReadyToShip, currentOrder.status)
        assertNull(currentOrder.cancelledAt)
        val currentVariant = f.store.variant(f.variant.id)!!
        assertEquals(3, currentVariant.quantity)
        assertEquals(previousUpdatedAt, currentVariant.updatedAt)
        assertNull(f.store.movement(withdrawal.id)!!.reversedAt)
        assertEquals(2, f.store.movements(f.storeProfile.id).size)
    }

    @Test
    fun prontaEntregaAvancaParaDespachadoEConcluidoSemMudarEstoque() = withStore { rawStore ->
        val f = fixture(rawStore)
        val shippedAt = now.plusSeconds(3_600)
        val completedAt = shippedAt.plusSeconds(3_600)

        SalesOrderService.transition(f.order.id, SalesOrderStatus.Shipped, date = shippedAt, store = f.store)
        val afterShipped = f.store.order(f.order.id)!!
        assertEquals(SalesOrderStatus.Shipped, afterShipped.status)
        assertEquals(shippedAt, afterShipped.shippedAt)
        assertNull(afterShipped.completedAt)
        assertEquals(3, f.store.variant(f.variant.id)!!.quantity)

        SalesOrderService.transition(f.order.id, SalesOrderStatus.Completed, date = completedAt, store = f.store)
        val afterCompleted = f.store.order(f.order.id)!!
        assertEquals(SalesOrderStatus.Completed, afterCompleted.status)
        assertEquals(completedAt, afterCompleted.completedAt)
        assertEquals(3, f.store.variant(f.variant.id)!!.quantity)
        assertEquals(2, f.store.movements(f.storeProfile.id).size)
    }

    @Test
    fun producaoRegistraTodasAsDatasEfetivasEmOrdem() = withStore { rawStore ->
        val f = fixture(rawStore, fulfillment = OrderFulfillment.Production, stock = 0, sold = 3)
        val startedAt = now.plusSeconds(1_000)
        val producedAt = startedAt.plusSeconds(1_000)
        val shippedAt = producedAt.plusSeconds(1_000)
        val completedAt = shippedAt.plusSeconds(1_000)

        SalesOrderService.transition(f.order.id, SalesOrderStatus.InProduction, date = startedAt, store = f.store)
        SalesOrderService.transition(f.order.id, SalesOrderStatus.ReadyToShip, date = producedAt, store = f.store)
        SalesOrderService.transition(f.order.id, SalesOrderStatus.Shipped, date = shippedAt, store = f.store)
        SalesOrderService.transition(f.order.id, SalesOrderStatus.Completed, date = completedAt, store = f.store)

        val currentOrder = f.store.order(f.order.id)!!
        assertEquals(startedAt, currentOrder.productionStartedAt)
        assertEquals(producedAt, currentOrder.producedAt)
        assertEquals(shippedAt, currentOrder.shippedAt)
        assertEquals(completedAt, currentOrder.completedAt)
        assertEquals(SalesOrderStatus.Completed, currentOrder.status)
        assertEquals(0, f.store.variant(f.variant.id)!!.quantity)
    }

    @Test
    fun acaoProduzidoPodePularInicioDaProducao() = withStore { rawStore ->
        val f = fixture(rawStore, fulfillment = OrderFulfillment.Production, stock = 0, sold = 1)
        val producedAt = now.plusSeconds(1_000)

        SalesOrderService.transition(f.order.id, SalesOrderStatus.ReadyToShip, date = producedAt, store = f.store)

        val currentOrder = f.store.order(f.order.id)!!
        assertEquals(SalesOrderStatus.ReadyToShip, currentOrder.status)
        assertNull(currentOrder.productionStartedAt)
        assertEquals(producedAt, currentOrder.producedAt)
    }

    @Test
    fun transicoesInvalidasEDatasForaDeOrdemNaoAlteramPedido() = withStore { rawStore ->
        val f = fixture(rawStore, fulfillment = OrderFulfillment.Production, stock = 0, sold = 1)

        expectError(SalesOrderManagementError.TransitionNotAllowed) {
            SalesOrderService.transition(f.order.id, SalesOrderStatus.Shipped, date = now, store = f.store)
        }
        expectError(SalesOrderManagementError.TransitionNotAllowed) {
            SalesOrderService.transition(f.order.id, SalesOrderStatus.Cancelled, date = now, store = f.store)
        }
        expectError(SalesOrderManagementError.InvalidTransitionDate) {
            SalesOrderService.transition(
                f.order.id,
                SalesOrderStatus.ReadyToShip,
                date = now.minusSeconds(1),
                store = f.store,
            )
        }

        val currentOrder = f.store.order(f.order.id)!!
        assertEquals(SalesOrderStatus.AwaitingProduction, currentOrder.status)
        assertNull(currentOrder.producedAt)
    }

    @Test
    fun dataEfetivaNaoPodeAntecederAOperacaoAnterior() = withStore { rawStore ->
        val f = fixture(rawStore, fulfillment = OrderFulfillment.Production, stock = 0, sold = 1)
        val startedAt = now.plusSeconds(2_000)
        SalesOrderService.transition(f.order.id, SalesOrderStatus.InProduction, date = startedAt, store = f.store)

        expectError(SalesOrderManagementError.InvalidTransitionDate) {
            SalesOrderService.transition(
                f.order.id,
                SalesOrderStatus.ReadyToShip,
                date = startedAt.minusSeconds(1),
                store = f.store,
            )
        }

        val currentOrder = f.store.order(f.order.id)!!
        assertEquals(SalesOrderStatus.InProduction, currentOrder.status)
        assertNull(currentOrder.producedAt)
    }

    @Test
    fun falhaNoSaveDaTransicaoRestauraEstadoEDatas() = withStore { rawStore ->
        val f = fixture(rawStore)
        val previousUpdatedAt = f.order.updatedAt
        val failingStore = FailingOrderSaveStore(f.store)

        try {
            SalesOrderService.transition(
                f.order.id,
                SalesOrderStatus.Shipped,
                date = now.plusSeconds(1),
                store = failingStore,
            )
            fail("Esperava SimulatedSaveFailure")
        } catch (_: SimulatedSaveFailure) {
            // Falha simulada esperada.
        }

        val currentOrder = f.store.order(f.order.id)!!
        assertEquals(SalesOrderStatus.ReadyToShip, currentOrder.status)
        assertNull(currentOrder.shippedAt)
        assertEquals(previousUpdatedAt, currentOrder.updatedAt)
    }

    // O iOS tem "errosDeGerenciamentoPossuemMensagensLocalizadas"; no Android o texto fica no :app (A5).
}
