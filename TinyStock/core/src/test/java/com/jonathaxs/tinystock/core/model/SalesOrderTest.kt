package com.jonathaxs.tinystock.core.model

import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.service.ProductVariantService
import com.jonathaxs.tinystock.core.support.assertDecimal
import com.jonathaxs.tinystock.core.support.expectError
import com.jonathaxs.tinystock.core.support.withStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Porte de `SalesOrderTests.swift`. */
class SalesOrderTest {

    private suspend fun makeProduct(store: TinyStockStore): Pair<Product, ProductVariant> {
        val product = Product(
            storeID = UUID.randomUUID(),
            name = "Caneca",
            costPrice = BigDecimal(10),
            salePrice = BigDecimal(25),
        )
        store.saveProduct(product)
        val variant = ProductVariantService.create(product, "Verde", initialQuantity = 3, store = store)
        return product to variant
    }

    @Test
    fun atendimentoDefineEstadoInicial() {
        assertEquals(
            SalesOrderStatus.ReadyToShip,
            SalesOrder(fulfillmentRawValue = OrderFulfillment.ReadyStock.rawValue).status,
        )
        assertEquals(
            SalesOrderStatus.AwaitingProduction,
            SalesOrder(fulfillmentRawValue = OrderFulfillment.Production.rawValue).status,
        )
        assertEquals(
            SalesOrderStatus.New,
            SalesOrder(statusRawValue = SalesOrderStatus.New.rawValue).status,
        )
    }

    @Test
    fun matrizDeTransicoesRespeitaAtendimento() {
        val production: Map<SalesOrderStatus, Set<SalesOrderStatus>> = mapOf(
            SalesOrderStatus.New to setOf(SalesOrderStatus.AwaitingProduction, SalesOrderStatus.Cancelled),
            SalesOrderStatus.AwaitingProduction to setOf(
                SalesOrderStatus.InProduction,
                SalesOrderStatus.ReadyToShip,
                SalesOrderStatus.Cancelled,
            ),
            SalesOrderStatus.InProduction to setOf(SalesOrderStatus.ReadyToShip, SalesOrderStatus.Cancelled),
            SalesOrderStatus.ReadyToShip to setOf(SalesOrderStatus.Shipped, SalesOrderStatus.Cancelled),
            SalesOrderStatus.Shipped to setOf(SalesOrderStatus.Completed),
            SalesOrderStatus.Completed to emptySet(),
            SalesOrderStatus.Cancelled to emptySet(),
        )
        val ready: Map<SalesOrderStatus, Set<SalesOrderStatus>> = mapOf(
            SalesOrderStatus.New to setOf(SalesOrderStatus.ReadyToShip, SalesOrderStatus.Cancelled),
            SalesOrderStatus.AwaitingProduction to emptySet(),
            SalesOrderStatus.InProduction to emptySet(),
            SalesOrderStatus.ReadyToShip to setOf(SalesOrderStatus.Shipped, SalesOrderStatus.Cancelled),
            SalesOrderStatus.Shipped to setOf(SalesOrderStatus.Completed),
            SalesOrderStatus.Completed to emptySet(),
            SalesOrderStatus.Cancelled to emptySet(),
        )
        for (status in SalesOrderStatus.entries) {
            for (fulfillment in OrderFulfillment.entries) {
                val expected = (if (fulfillment == OrderFulfillment.Production) production else ready)[status].orEmpty()
                for (next in SalesOrderStatus.entries) {
                    assertEquals(expected.contains(next), status.canTransition(next, fulfillment))
                }
                assertEquals(expected, status.allowedNextStatuses(fulfillment).toSet())
                assertEquals(
                    status == SalesOrderStatus.Completed || status == SalesOrderStatus.Cancelled,
                    status.isTerminal,
                )
            }
        }
    }

    @Test
    fun consultaDeTransicaoNaoMudaEstadoNemDatas() {
        val order = SalesOrder(fulfillmentRawValue = OrderFulfillment.Production.rawValue)

        assertTrue(order.canTransition(SalesOrderStatus.ReadyToShip))
        assertEquals(SalesOrderStatus.AwaitingProduction, order.status)
        assertNull(order.producedAt)
        assertFalse(order.canTransition(SalesOrderStatus.Shipped))
    }

    @Test
    fun retratoCopiaIdentidadeEValoresSemMovimentarEstoque() = withStore { store ->
        val (product, variant) = makeProduct(store)

        val item = SalesOrderItem.snapshot(product, variant, quantity = 5, position = 2)

        assertEquals(product.storeID, item.storeID)
        assertEquals(product.id, item.productID)
        assertEquals(variant.id, item.variantID)
        assertEquals("Caneca", item.productName)
        assertEquals("Verde", item.variantName)
        assertDecimal("25", item.unitPrice)
        assertDecimal("10", item.unitCost)
        assertEquals(5, item.quantity)
        assertEquals(2, item.position)
        assertDecimal("125", item.subtotal)
        assertDecimal("50", item.subtotalCost)
        assertDecimal("75", item.grossProfit)
        assertEquals(3, store.variant(variant.id)!!.quantity)
        assertEquals(1, store.movements(product.storeID).size)
    }

    @Test
    fun retratoNaoExpoeNomeDaVariacaoInterna() = withStore { store ->
        val product = Product(storeID = UUID.randomUUID(), name = "Produto", salePrice = BigDecimal(20))
        val variant = ProductVariant(
            storeID = product.storeID,
            productID = product.id,
            name = ProductVariant.INTERNAL_DEFAULT_NAME,
            isDefault = true,
        )
        store.saveProduct(product)
        store.saveVariant(variant)

        val item = SalesOrderItem.snapshot(product, variant, quantity = 1)

        assertTrue(item.variantName.isEmpty())
        assertEquals(variant.id, item.variantID)
    }

    @Test
    fun retratoNaoMudaQuandoCatalogoEhEditado() = withStore { store ->
        val (product, variant) = makeProduct(store)
        val item = SalesOrderItem.snapshot(product, variant, quantity = 2)

        store.saveProduct(
            product.copy(
                name = "Nome novo",
                salePrice = BigDecimal(100),
                costPrice = BigDecimal(50),
            ),
        )
        store.saveVariant(variant.copy(name = "Outra cor"))

        assertEquals("Caneca", item.productName)
        assertEquals("Verde", item.variantName)
        assertDecimal("50", item.subtotal)
        assertDecimal("20", item.subtotalCost)
    }

    @Test
    fun retratoRecusaQuantidadeInvalida() = withStore { store ->
        val (product, variant) = makeProduct(store)

        for (quantity in listOf(0, -1)) {
            expectError(SalesOrderItemError.InvalidQuantity) {
                SalesOrderItem.snapshot(product, variant, quantity = quantity)
            }
        }
    }

    @Test
    fun retratoRecusaVariacaoDeOutroProdutoOuLoja() = withStore { store ->
        val (product, variant) = makeProduct(store)

        expectError(SalesOrderItemError.VariantMismatch) {
            SalesOrderItem.snapshot(product, variant.copy(storeID = UUID.randomUUID()), quantity = 1)
        }
        expectError(SalesOrderItemError.VariantMismatch) {
            SalesOrderItem.snapshot(product, variant.copy(productID = UUID.randomUUID()), quantity = 1)
        }
    }

    @Test
    fun retratoRecusaPrecosNegativos() = withStore { store ->
        val (product, variant) = makeProduct(store)

        expectError(SalesOrderItemError.InvalidPrices) {
            SalesOrderItem.snapshot(product.copy(salePrice = BigDecimal(-1)), variant, quantity = 1)
        }
        expectError(SalesOrderItemError.InvalidPrices) {
            SalesOrderItem.snapshot(
                product.copy(salePrice = BigDecimal.ZERO, costPrice = BigDecimal(-1)),
                variant,
                quantity = 1,
            )
        }
    }

    @Test
    fun totaisSomamItensEDescontamTaxaSalvaUmaVez() {
        val first = SalesOrderItem(
            unitPrice = BigDecimal("2.35"),
            unitCost = BigDecimal("1.10"),
            quantity = 3,
        )
        val second = SalesOrderItem(
            unitPrice = BigDecimal("5.25"),
            unitCost = BigDecimal("2.15"),
            quantity = 1,
        )
        val order = SalesOrder(
            channelRawValue = SalesChannel.Shopee.rawValue,
            channelFeePercentage = BigDecimal(10),
            channelFeeAmount = BigDecimal("1.23"),
            items = listOf(first, second),
        )

        assertDecimal("4", order.totalQuantity)
        assertDecimal("12.30", order.total)
        assertDecimal("5.45", order.totalCost)
        assertDecimal("6.85", order.grossProfit)
        assertDecimal("5.62", order.netProfit)
        assertDecimal("5.62", order.copy(channelFeePercentage = BigDecimal(50)).netProfit)
    }

    @Test
    fun pedidoVazioETotalGrandeNaoCausamOverflow() {
        val empty = SalesOrder()
        assertTrue(empty.itemList.isEmpty())
        assertDecimal("0", empty.total)
        assertDecimal("0", empty.totalQuantity)

        val first = SalesOrderItem(quantity = Int.MAX_VALUE)
        val second = SalesOrderItem(quantity = 1)
        val order = empty.copy(items = listOf(first, second))

        assertEquals(BigDecimal.valueOf(Int.MAX_VALUE.toLong()).add(BigDecimal.ONE), order.totalQuantity)
    }

    @Test
    fun ordenacaoDosItensIndependeDaOrdemDaRelacao() {
        val first = SalesOrderItem(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            position = 0,
        )
        val second = SalesOrderItem(
            id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            position = 0,
        )
        val last = SalesOrderItem(position = 1)
        val order = SalesOrder(items = listOf(last, second, first))

        assertEquals(listOf(first.id, second.id, last.id), order.itemList.map { it.id })
    }

    @Test
    fun persistenciaPreservaCamposRelacaoEEscopoDaLoja() = withStore { store ->
        val (product, variant) = makeProduct(store)
        val date = Instant.ofEpochSecond(1_800_000_000)
        val item = SalesOrderItem.snapshot(product, variant, quantity = 2)
        val order = SalesOrder(
            storeID = product.storeID,
            channelRawValue = SalesChannel.Other.rawValue,
            customChannelName = "Minha plataforma",
            fulfillmentRawValue = OrderFulfillment.Production.rawValue,
            buyerName = "Comprador de teste",
            externalReference = "TEST-01",
            orderedAt = date,
            productionDueAt = date.plusSeconds(86_400),
            shippingDueAt = date.plusSeconds(172_800),
            productionStartedAt = date,
            producedAt = date,
            shippedAt = date,
            completedAt = date,
            cancelledAt = date,
            trackingCode = "TRACK-01",
            note = "Teste",
            channelFeePercentage = BigDecimal(10),
            channelFeeAmount = BigDecimal(5),
            createdAt = date,
            updatedAt = date,
            items = listOf(item),
        )
        store.saveOrder(order)
        store.saveOrder(SalesOrder(storeID = UUID.randomUUID()))

        val saved = store.orders(product.storeID)
        val fetched = saved.single()

        assertEquals(order.id, fetched.id)
        assertEquals(SalesChannel.Other, fetched.channel)
        assertEquals("Minha plataforma", fetched.customChannelDisplayName)
        assertEquals(OrderFulfillment.Production, fetched.fulfillment)
        assertEquals(SalesOrderStatus.AwaitingProduction, fetched.status)
        assertEquals("Comprador de teste", fetched.buyerName)
        assertEquals("TEST-01", fetched.externalReference)
        assertEquals(date, fetched.orderedAt)
        assertEquals(date, fetched.createdAt)
        assertEquals(date, fetched.updatedAt)
        assertEquals(date.plusSeconds(86_400), fetched.productionDueAt)
        assertEquals(date.plusSeconds(172_800), fetched.shippingDueAt)
        assertEquals(date, fetched.productionStartedAt)
        assertEquals(date, fetched.producedAt)
        assertEquals(date, fetched.shippedAt)
        assertEquals(date, fetched.completedAt)
        assertEquals(date, fetched.cancelledAt)
        assertEquals("TRACK-01", fetched.trackingCode)
        assertEquals("Teste", fetched.note)
        assertDecimal("5", fetched.channelFeeAmount)
        assertDecimal("10", fetched.channelFeePercentage)
        assertDecimal("50", fetched.total)
        assertDecimal("25", fetched.netProfit)
        assertEquals(product.storeID, fetched.itemList.first().storeID)
    }

    // No Android nao ha Sale legado; o teste confere que apagar o pedido preserva catalogo e estoque.
    @Test
    fun excluirPedidoRemoveItensMasPreservaCatalogoEstoqueEVendasLegadas() = withStore { store ->
        val (product, variant) = makeProduct(store)
        val item = SalesOrderItem.snapshot(product, variant, quantity = 1)
        val order = SalesOrder(storeID = product.storeID, items = listOf(item))
        store.saveOrder(order)

        store.deleteOrder(order.id)

        assertTrue(store.orders(product.storeID).isEmpty())
        assertEquals(1, store.products(product.storeID).size)
        assertEquals(1, store.variants(product.storeID).size)
        assertEquals(1, store.movements(product.storeID).size)
        assertEquals(3, store.variant(variant.id)!!.quantity)
    }

    @Test
    fun excluirCatalogoNaoApagaRetratosDoPedido() = withStore { store ->
        val (product, variant) = makeProduct(store)
        val item = SalesOrderItem.snapshot(product, variant, quantity = 2)
        val order = SalesOrder(
            storeID = product.storeID,
            statusRawValue = SalesOrderStatus.Completed.rawValue,
            items = listOf(item),
        )
        store.saveOrder(order)

        store.deleteVariant(variant.id)
        store.deleteProduct(product.id)

        val fetched = store.orders(product.storeID).single()
        assertEquals(1, fetched.itemList.size)
        assertDecimal("50", fetched.total)
        assertEquals("Caneca", fetched.itemList.first().productName)
        assertEquals("Verde", fetched.itemList.first().variantName)
    }

    @Test
    fun valoresDesconhecidosNaoSaoConvertidosParaEstadosValidos() = withStore { store ->
        val storeID = UUID.randomUUID()
        val order = SalesOrder(
            storeID = storeID,
            statusRawValue = "future-status",
            fulfillmentRawValue = "future-fulfillment",
            channelRawValue = "future-channel",
        )
        store.saveOrder(order)

        val fetched = store.orders(storeID).single()
        assertNull(fetched.status)
        assertNull(fetched.fulfillment)
        assertNull(fetched.channel)
        assertFalse(fetched.canTransition(SalesOrderStatus.Shipped))
        assertEquals("future-status", fetched.statusRawValue)
        assertNull(fetched.customChannelDisplayName)
    }

    @Test
    fun canaisNaoDependemDePagamentoENomesCustomizadosSaoOpcionais() {
        val order = SalesOrder(
            channelRawValue = SalesChannel.Other.rawValue,
            customChannelName = "  Minha loja virtual  ",
        )
        assertEquals("Minha loja virtual", order.customChannelDisplayName)
        assertNull(order.copy(customChannelName = "  ").customChannelDisplayName)
        assertNull(order.copy(channelRawValue = SalesChannel.Shopee.rawValue).customChannelDisplayName)
        assertFalse("pix" in SalesChannel.entries.map { it.rawValue })
    }

    // No Android os textos localizados ficam no :app (A5); aqui conferimos o round-trip de rawValue.
    @Test
    fun enumsTemRoundTripCodableETextosLocalizados() {
        for (channel in SalesChannel.entries) {
            assertEquals(channel, SalesChannel.fromRawValue(channel.rawValue))
        }
        for (status in SalesOrderStatus.entries) {
            assertEquals(status, SalesOrderStatus.fromRawValue(status.rawValue))
        }
        for (fulfillment in OrderFulfillment.entries) {
            assertEquals(fulfillment, OrderFulfillment.fromRawValue(fulfillment.rawValue))
        }
    }
}
