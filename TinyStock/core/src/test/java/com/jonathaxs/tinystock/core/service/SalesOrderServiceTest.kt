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
import com.jonathaxs.tinystock.core.model.StoreScope
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

/** Porte de `SalesOrderServiceTests.swift`. */
class SalesOrderServiceTest {

    private val now: Instant = Instant.ofEpochSecond(1_800_000_000)
    private val zone = ZoneOffset.UTC

    private class Fixture(
        val store: InMemoryTinyStockStore,
        val storeProfile: StoreProfile,
        val product: Product,
        val variant: ProductVariant,
    ) {
        fun line(quantity: Int): SalesOrderLine =
            SalesOrderLine(productID = product.id, variantID = variant.id, quantity = quantity)
    }

    private suspend fun fixture(store: InMemoryTinyStockStore, quantity: Int = 5): Fixture {
        val storeProfile = StoreProfile(name = "Loja de teste")
        store.saveStore(storeProfile)
        val product = Product(
            storeID = storeProfile.id,
            name = "Caneca",
            quantity = 99,
            costPrice = BigDecimal(10),
            salePrice = BigDecimal(25),
        )
        store.saveProduct(product)
        val variant = ProductVariantService.create(
            product,
            "Verde",
            initialQuantity = quantity,
            store = store,
        )
        return Fixture(store, storeProfile, product, variant)
    }

    private suspend fun register(
        f: Fixture,
        lines: List<SalesOrderLine>? = null,
        fulfillment: OrderFulfillment = OrderFulfillment.ReadyStock,
        fee: BigDecimal = BigDecimal.ZERO,
        id: UUID = UUID.randomUUID(),
    ): SalesOrder = SalesOrderService.register(
        id = id,
        storeID = f.storeProfile.id,
        lines = lines ?: listOf(f.line(2)),
        fulfillment = fulfillment,
        orderedAt = now,
        productionDueAt = if (fulfillment == OrderFulfillment.Production) now else null,
        shippingDueAt = now.plusSeconds(86_400),
        channelFeePercentage = fee,
        now = now,
        zone = zone,
        store = f.store,
    )

    private suspend fun expectNoOrder(f: Fixture, quantity: Int = 5) {
        assertTrue(f.store.orders(f.storeProfile.id).isEmpty())
        assertEquals(quantity, f.store.variant(f.variant.id)!!.quantity)
        assertEquals(99, f.store.product(f.product.id)!!.quantity)
    }

    private class SimulatedSaveFailure : RuntimeException("simulated")

    private class FailingOrderSaveStore(
        private val inner: InMemoryTinyStockStore,
    ) : TinyStockStore by inner {
        override suspend fun saveOrder(order: SalesOrder) {
            throw SimulatedSaveFailure()
        }
    }

    @Test
    fun prontaEntregaGravaPedidoItensETaxaComBaixaAuditavel() = withStore { rawStore ->
        val f = fixture(rawStore)

        val order = SalesOrderService.register(
            storeID = f.storeProfile.id,
            lines = listOf(f.line(2)),
            fulfillment = OrderFulfillment.ReadyStock,
            channel = SalesChannel.Other,
            customChannelName = " Minha plataforma ",
            buyerName = " Comprador ",
            externalReference = " REF-01 ",
            orderedAt = now,
            shippingDueAt = now,
            note = " Observacao ",
            channelFeePercentage = BigDecimal(10),
            now = now,
            zone = zone,
            store = f.store,
        )

        assertEquals(SalesOrderStatus.ReadyToShip, order.status)
        assertDecimal("50", order.total)
        assertDecimal("20", order.totalCost)
        assertDecimal("5", order.channelFeeAmount)
        assertDecimal("25", order.netProfit)
        assertEquals("Minha plataforma", order.customChannelDisplayName)
        assertEquals("Comprador", order.buyerName)
        assertEquals("REF-01", order.externalReference)
        assertEquals("Observacao", order.note)
        assertEquals(now, order.createdAt)
        assertEquals(now, order.updatedAt)

        val updatedVariant = f.store.variant(f.variant.id)!!
        assertEquals(3, updatedVariant.quantity)
        assertEquals(now, updatedVariant.updatedAt)
        assertEquals(99, f.store.product(f.product.id)!!.quantity)

        val entry = StockService.movements(f.variant, f.product, f.store)
            .first { it.kind == StockMovementKind.OrderWithdrawal }
        assertEquals(order.id, entry.referenceID)
        assertEquals(-2, entry.quantityDelta)
        assertEquals(3, entry.balanceAfter)
        assertEquals(now, entry.createdAt)

        val persisted = f.store.order(order.id)!!
        assertEquals(1, persisted.itemList.size)
        assertEquals(f.variant.id, persisted.itemList.first().variantID)
        assertEquals(f.storeProfile.id, persisted.itemList.first().storeID)
        assertDecimal("50", persisted.total)
    }

    @Test
    fun produzirNaoBaixaNemReservaEstoqueMesmoSemUnidades() = withStore { rawStore ->
        val f = fixture(rawStore, quantity = 0)

        val order = register(f, lines = listOf(f.line(20)), fulfillment = OrderFulfillment.Production)

        assertEquals(SalesOrderStatus.AwaitingProduction, order.status)
        assertDecimal("20", order.totalQuantity)
        assertEquals(now, order.productionDueAt)
        assertEquals(1, order.itemList.size)
        assertEquals(0, f.store.variant(f.variant.id)!!.quantity)
        assertTrue(f.store.movements(f.storeProfile.id).isEmpty())
    }

    @Test
    fun linhasRepetidasSaoSomadasAntesDaBaixa() = withStore { rawStore ->
        val f = fixture(rawStore)

        val order = register(f, lines = listOf(f.line(2), f.line(3)))

        assertEquals(1, order.itemList.size)
        assertEquals(5, order.itemList.first().quantity)
        assertEquals(0, f.store.variant(f.variant.id)!!.quantity)
        assertEquals(2, f.store.movements(f.storeProfile.id).size)
    }

    @Test
    fun somaRepetidaAcimaDoEstoqueNaoAlteraNada() = withStore { rawStore ->
        val f = fixture(rawStore)

        expectError(
            SalesOrderError.InsufficientStock(
                productName = "Caneca",
                variantName = "Verde",
                available = 5,
                requested = 6,
            ),
        ) {
            register(f, lines = listOf(f.line(3), f.line(3)))
        }
        expectNoOrder(f)
    }

    @Test
    fun falhaNoUltimoItemNaoBaixaOPrimeiro() = withStore { rawStore ->
        val f = fixture(rawStore)
        val other = ProductVariantService.create(f.product, "Branca", initialQuantity = 1, store = f.store)
        val last = SalesOrderLine(productID = f.product.id, variantID = other.id, quantity = 2)

        expectError(
            SalesOrderError.InsufficientStock(
                productName = "Caneca",
                variantName = "Branca",
                available = 1,
                requested = 2,
            ),
        ) {
            register(f, lines = listOf(f.line(1), last))
        }
        expectNoOrder(f)
        assertEquals(1, f.store.variant(other.id)!!.quantity)
    }

    @Test
    fun variasVariacoesPreservamSequenciaERecebemBaixasSeparadas() = withStore { rawStore ->
        val f = fixture(rawStore)
        val other = Product(
            storeID = f.storeProfile.id,
            name = "Suporte",
            costPrice = BigDecimal(5),
            salePrice = BigDecimal(15),
        )
        f.store.saveProduct(other)
        val otherVariant = ProductVariantService.create(other, "Unico", initialQuantity = 4, store = f.store)
        val line = SalesOrderLine(productID = other.id, variantID = otherVariant.id, quantity = 2)

        val order = register(f, lines = listOf(line, f.line(1), line))

        assertEquals(listOf("Suporte", "Caneca"), order.itemList.map { it.productName })
        assertEquals(listOf(4, 1), order.itemList.map { it.quantity })
        assertDecimal("85", order.total)
        assertDecimal("30", order.totalCost)
        assertEquals(0, f.store.variant(otherVariant.id)!!.quantity)
        assertEquals(4, f.store.variant(f.variant.id)!!.quantity)
    }

    @Test
    fun pedidoVazioELojaArquivadaSaoRecusados() = withStore { rawStore ->
        val f = fixture(rawStore)

        expectError(SalesOrderError.EmptyOrder) {
            register(f, lines = emptyList())
        }
        f.store.saveStore(f.storeProfile.copy(isArchived = true))
        expectError(SalesOrderError.StoreUnavailable) {
            register(f)
        }
        expectNoOrder(f)
    }

    @Test
    fun lojaInexistenteOuSemEscopoNaoPodeReceberPedido() = withStore { rawStore ->
        val f = fixture(rawStore)

        for (storeID in listOf(UUID.randomUUID(), StoreScope.UNASSIGNED_STORE_ID)) {
            expectError(SalesOrderError.StoreUnavailable) {
                SalesOrderService.register(
                    storeID = storeID,
                    lines = listOf(f.line(1)),
                    fulfillment = OrderFulfillment.ReadyStock,
                    orderedAt = now,
                    shippingDueAt = now,
                    now = now,
                    zone = zone,
                    store = f.store,
                )
            }
        }
        expectNoOrder(f)
    }

    @Test
    fun produtoOuVariacaoDeOutroEscopoSaoRecusados() = withStore { rawStore ->
        val f = fixture(rawStore)
        val other = Product(storeID = UUID.randomUUID(), name = "Outra loja")
        f.store.saveProduct(other)
        val variant = ProductVariantService.create(other, "Outra", store = f.store)

        expectError(SalesOrderError.ProductUnavailable) {
            register(f, lines = listOf(SalesOrderLine(other.id, variant.id, 1)))
        }
        expectError(SalesOrderError.VariantMismatch) {
            register(f, lines = listOf(SalesOrderLine(f.product.id, variant.id, 1)))
        }
        expectError(SalesOrderError.VariantMismatch) {
            register(f, lines = listOf(SalesOrderLine(f.product.id, UUID.randomUUID(), 1)))
        }
        expectNoOrder(f)
    }

    @Test
    fun idsAmbiguosNoCatalogoNaoSaoEscolhidosArbitrariamente() = withStore { rawStore ->
        val f = fixture(rawStore)
        val duplicateVariantStore = object : TinyStockStore by f.store {
            override suspend fun variants(storeID: UUID): List<ProductVariant> =
                f.store.variants(storeID) + ProductVariant(
                    id = f.variant.id,
                    storeID = f.storeProfile.id,
                    productID = UUID.randomUUID(),
                    name = "Duplicada",
                )
        }

        expectError(SalesOrderError.VariantMismatch) {
            SalesOrderService.register(
                storeID = f.storeProfile.id,
                lines = listOf(f.line(2)),
                fulfillment = OrderFulfillment.ReadyStock,
                orderedAt = now,
                shippingDueAt = now,
                now = now,
                zone = zone,
                store = duplicateVariantStore,
            )
        }
        expectNoOrder(f)
    }

    @Test
    fun quantidadeInvalidaNaoCriaPedido() = withStore { rawStore ->
        val f = fixture(rawStore)

        for (quantity in listOf(0, -1)) {
            expectError(SalesOrderError.InvalidQuantity) {
                register(f, lines = listOf(f.line(quantity)))
            }
            expectNoOrder(f)
        }
    }

    @Test
    fun somaDeQuantidadesComOverflowEhRecusadaTambemNaProducao() = withStore { rawStore ->
        val f = fixture(rawStore)

        expectError(SalesOrderError.QuantityOverflow) {
            register(
                f,
                lines = listOf(f.line(Int.MAX_VALUE), f.line(1)),
                fulfillment = OrderFulfillment.Production,
            )
        }
        expectNoOrder(f)
    }

    @Test
    fun valoresMonetariosInvalidosNaoGeramBaixa() = withStore { rawStore ->
        val f = fixture(rawStore)

        f.store.saveProduct(f.product.copy(salePrice = BigDecimal(-1)))
        expectError(SalesOrderError.InvalidMoney) {
            register(f)
        }
        expectNoOrder(f)

        f.store.saveProduct(f.product.copy(salePrice = BigDecimal(25), costPrice = BigDecimal(-1)))
        expectError(SalesOrderError.InvalidMoney) {
            register(f)
        }
        expectNoOrder(f)
    }

    @Test
    fun taxaInvalidaNaoGravaEArredondamentoValidoUsaCentavos() = withStore { rawStore ->
        val f = fixture(rawStore)

        for (rate in listOf(BigDecimal(-1), BigDecimal(101))) {
            expectError(SalesOrderError.InvalidChannelFee) {
                register(f, fee = rate)
            }
        }
        expectNoOrder(f)

        f.store.saveProduct(f.product.copy(salePrice = BigDecimal("2.35")))
        val order = register(f, lines = listOf(f.line(3)), fee = BigDecimal(10))
        assertDecimal("7.05", order.total)
        assertDecimal("0.71", order.channelFeeAmount)
    }

    @Test
    fun datasInvalidasSaoRecusadasAntesDaGravacao() = withStore { rawStore ->
        val f = fixture(rawStore)
        val tomorrow = now.plusSeconds(86_400)

        expectError(SalesOrderError.FutureOrderDate) {
            SalesOrderService.register(
                storeID = f.storeProfile.id,
                lines = listOf(f.line(1)),
                fulfillment = OrderFulfillment.ReadyStock,
                orderedAt = tomorrow,
                shippingDueAt = tomorrow,
                now = now,
                zone = zone,
                store = f.store,
            )
        }
        expectError(SalesOrderError.ShippingBeforeOrder) {
            SalesOrderService.register(
                storeID = f.storeProfile.id,
                lines = listOf(f.line(1)),
                fulfillment = OrderFulfillment.ReadyStock,
                orderedAt = now,
                shippingDueAt = now.minusSeconds(86_400),
                now = now,
                zone = zone,
                store = f.store,
            )
        }
        // Instant em Kotlin nao aceita valores infinitos ou NaN como o Date do iOS.
        expectNoOrder(f)
    }

    @Test
    fun producaoExigeDataEntreVendaEDespacho() = withStore { rawStore ->
        val f = fixture(rawStore)

        expectError(SalesOrderError.ProductionDateRequired) {
            SalesOrderService.register(
                storeID = f.storeProfile.id,
                lines = listOf(f.line(1)),
                fulfillment = OrderFulfillment.Production,
                orderedAt = now,
                shippingDueAt = now,
                now = now,
                zone = zone,
                store = f.store,
            )
        }
        for (date in listOf(now.minusSeconds(86_400), now.plusSeconds(86_400))) {
            expectError(SalesOrderError.InvalidProductionDate) {
                SalesOrderService.register(
                    storeID = f.storeProfile.id,
                    lines = listOf(f.line(1)),
                    fulfillment = OrderFulfillment.Production,
                    orderedAt = now,
                    productionDueAt = date,
                    shippingDueAt = now,
                    now = now,
                    zone = zone,
                    store = f.store,
                )
            }
        }
        expectNoOrder(f)
    }

    @Test
    fun selecaoPorDiaAceitaHorariosDiferentesETrocaDeAtendimentoLimpaProducao() = withStore { rawStore ->
        val f = fixture(rawStore)
        val start = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val afternoon = start.plusSeconds(15 * 3_600)

        val order = SalesOrderService.register(
            storeID = f.storeProfile.id,
            lines = listOf(f.line(1)),
            fulfillment = OrderFulfillment.ReadyStock,
            orderedAt = afternoon,
            productionDueAt = afternoon.plusSeconds(86_400),
            shippingDueAt = start,
            now = afternoon,
            zone = zone,
            store = f.store,
        )

        assertNull(order.productionDueAt)
        assertEquals(start, order.shippingDueAt)
    }

    @Test
    fun comparacaoDosDiasRespeitaOFusoInformado() = withStore { rawStore ->
        val f = fixture(rawStore)
        val utcMidnight = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val localZone = ZoneOffset.ofHours(-3)
        val sale = utcMidnight.plusSeconds(1_800)
        val shipping = utcMidnight.minusSeconds(3_600)

        val order = SalesOrderService.register(
            storeID = f.storeProfile.id,
            lines = listOf(f.line(1)),
            fulfillment = OrderFulfillment.ReadyStock,
            orderedAt = sale,
            shippingDueAt = shipping,
            now = sale,
            zone = localZone,
            store = f.store,
        )

        assertEquals(SalesOrderStatus.ReadyToShip, order.status)
    }

    @Test
    fun registroRetroativoMantemPrazosMasMovimentaNaDataDeRegistro() = withStore { rawStore ->
        val f = fixture(rawStore)
        val past = now.minusSeconds(3 * 86_400)

        val order = SalesOrderService.register(
            storeID = f.storeProfile.id,
            lines = listOf(f.line(1)),
            fulfillment = OrderFulfillment.ReadyStock,
            orderedAt = past,
            shippingDueAt = past,
            now = now,
            zone = zone,
            store = f.store,
        )

        assertEquals(past, order.orderedAt)
        assertEquals(past, order.shippingDueAt)
        assertEquals(now, order.createdAt)
        val withdrawal = StockService.movements(f.variant, f.product, f.store)
            .first { it.kind == StockMovementKind.OrderWithdrawal }
        assertEquals(now, withdrawal.createdAt)
    }

    @Test
    fun mesmoRascunhoNaoPodeBaixarEstoqueDuasVezes() = withStore { rawStore ->
        val f = fixture(rawStore)
        val id = UUID.randomUUID()

        register(f, id = id)
        expectError(SalesOrderError.DuplicateOrder) {
            register(f, id = id)
        }

        assertEquals(3, f.store.variant(f.variant.id)!!.quantity)
        assertEquals(1, f.store.orders(f.storeProfile.id).size)
        assertEquals(2, f.store.movements(f.storeProfile.id).size)
    }

    @Test
    fun falhaNoSaveDesfazPedidoItensEBaixaSemApagarDadosAnteriores() = withStore { rawStore ->
        val f = fixture(rawStore)
        val previousDate = f.store.variant(f.variant.id)!!.updatedAt
        val failingStore = FailingOrderSaveStore(f.store)

        try {
            SalesOrderService.register(
                storeID = f.storeProfile.id,
                lines = listOf(f.line(2)),
                fulfillment = OrderFulfillment.ReadyStock,
                orderedAt = now,
                shippingDueAt = now,
                now = now,
                zone = zone,
                store = failingStore,
            )
            fail("Esperava SimulatedSaveFailure")
        } catch (_: SimulatedSaveFailure) {
            // Falha simulada esperada.
        }

        expectNoOrder(f)
        assertEquals(previousDate, f.store.variant(f.variant.id)!!.updatedAt)
        assertEquals(1, f.store.movements(f.storeProfile.id).size)
    }

    @Test
    fun baixaDoPedidoNaoPodeSerEstornadaIsoladamente() = withStore { rawStore ->
        val f = fixture(rawStore)
        val order = register(f)
        val withdrawal = StockService.movements(f.variant, f.product, f.store)
            .first { it.kind == StockMovementKind.OrderWithdrawal }

        expectError(StockError.OrderManagedMovement) {
            StockService.reverse(withdrawal, f.variant, f.product, store = f.store)
        }
        assertEquals(3, f.store.variant(f.variant.id)!!.quantity)
        assertEquals(SalesOrderStatus.ReadyToShip, f.store.order(order.id)!!.status)
        assertNull(f.store.movement(withdrawal.id)!!.reversedAt)
    }

    // No iOS e um teste com argumentos (um caso por status); aqui o laco cobre todos.
    @Test
    fun exclusaoDoProdutoDependeDoEncerramentoDosPedidos() {
        for (status in SalesOrderStatus.entries) withStore { store ->
            val f = fixture(store)
            val order = register(f)
            store.saveOrder(order.copy(statusRawValue = status.rawValue))
            if (status.isTerminal) {
                ProductService.delete(f.product, store)
                assertEquals("Caneca", store.order(order.id)!!.itemList.first().productName)
            } else {
                expectError(ProductError.ActiveOrders) { ProductService.delete(f.product, store) }
                assertEquals(3, store.variant(f.variant.id)!!.quantity)
                assertTrue(store.product(f.product.id) != null)
            }
            assertEquals(1, store.orders(f.storeProfile.id).size)
        }
    }

    @Test
    fun pedidoDeOutraLojaNaoBloqueiaProdutoMasEstadoDesconhecidoBloqueia() = withStore { store ->
        val f = fixture(store)
        val order = register(f).copy(statusRawValue = "future-status")
        store.saveOrder(order)
        expectError(ProductError.ActiveOrders) { ProductService.delete(f.product, store) }

        val movedOrder = order.copy(storeID = UUID.randomUUID())
        store.saveOrder(movedOrder)
        ProductService.delete(f.product, store)
        assertEquals(1, store.order(order.id)!!.itemList.size)
    }
    // O teste "mensagensDeErroEstaoLocalizadas" vai para o :app (A5).
}
