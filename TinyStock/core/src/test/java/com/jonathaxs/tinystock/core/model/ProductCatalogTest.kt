package com.jonathaxs.tinystock.core.model

import com.jonathaxs.tinystock.core.data.InMemoryTinyStockStore
import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.service.ProductService
import com.jonathaxs.tinystock.core.service.ProductVariantError
import com.jonathaxs.tinystock.core.service.ProductVariantService
import com.jonathaxs.tinystock.core.service.StockError
import com.jonathaxs.tinystock.core.service.StockService
import com.jonathaxs.tinystock.core.support.assertDecimal
import com.jonathaxs.tinystock.core.support.expectError
import com.jonathaxs.tinystock.core.support.withStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

/** Porte de `ProductCatalogTests.swift`. */
class ProductCatalogTest {

    @Test
    fun buscaEncontraNomeOuVariacaoSemCategoriaLegada() {
        val product = Product(storeID = UUID.randomUUID(), name = "Caneca", category = "Categoria antiga")
        val variant = ProductVariant(storeID = product.storeID, productID = product.id, name = "Única")

        assertTrue(product.matches("  ", emptyList()))
        assertTrue(product.matches("CANECA", emptyList()))
        assertTrue(product.matches(" unica ", listOf(variant)))
        assertFalse(product.matches("Categoria antiga", listOf(variant)))
    }

    @Test
    fun buscaESomaIgnoramVariacoesDeOutroProdutoOuLoja() {
        val product = Product(storeID = UUID.randomUUID(), name = "Caneca")
        val own = ProductVariant(storeID = product.storeID, productID = product.id, name = "Branca", quantity = 2)
        val otherProduct = ProductVariant(
            storeID = product.storeID,
            productID = UUID.randomUUID(),
            name = "Verde",
            quantity = 7,
        )
        val otherStore = ProductVariant(
            storeID = UUID.randomUUID(),
            productID = product.id,
            name = "Azul",
            quantity = 9,
        )
        val variants = listOf(own, otherProduct, otherStore)

        assertDecimal("2", ProductVariantService.displayedQuantity(product, variants))
        assertFalse(product.matches("Verde", variants))
        assertFalse(product.matches("Azul", variants))
    }

    @Test
    fun somaVisivelNaoUsaEstoqueLegadoENaoEstouraInt() = withStore { store ->
        val product = Product(storeID = UUID.randomUUID(), name = "Produto", quantity = 100)
        store.saveProduct(product)

        assertDecimal("0", ProductVariantService.displayedQuantity(product, emptyList()))
        val first = ProductVariantService.create(
            product = product,
            name = "A",
            initialQuantity = Int.MAX_VALUE,
            store = store,
        )
        val second = ProductVariantService.create(product = product, name = "B", initialQuantity = 1, store = store)
        val expected = Int.MAX_VALUE.toBigDecimal() + BigDecimal.ONE

        assertEquals(0, expected.compareTo(ProductVariantService.displayedQuantity(product, listOf(first, second))))
        expectError(StockError.QuantityOverflow) {
            ProductVariantService.totalQuantity(product, store)
        }
    }

    @Test
    fun exclusaoRemoveSomenteEstoqueDoProdutoEPreservaVenda() = withStore { store ->
        val first = ProductService.create(
            storeID = UUID.randomUUID(),
            name = "Caneca",
            costPrice = BigDecimal("10"),
            salePrice = BigDecimal("25"),
            store = store,
        )
        val second = ProductService.create(storeID = UUID.randomUUID(), name = "Caneca", store = store)
        val firstVariant = ProductVariantService.create(
            product = first,
            name = "Branca",
            initialQuantity = 2,
            store = store,
        )
        val keptVariant = ProductVariantService.create(
            product = second,
            name = "Branca",
            initialQuantity = 3,
            store = store,
        )
        // Sale e SaleItem legados ficaram de fora do Android; validamos a preservacao com SalesOrder concluido.
        val item = SalesOrderItem.snapshot(first, firstVariant, quantity = 1)
        val order = SalesOrder(
            storeID = first.storeID,
            statusRawValue = SalesOrderStatus.Completed.rawValue,
            items = listOf(item),
        )
        store.saveOrder(order)

        ProductService.delete(first, store)

        assertTrue(store.products(first.storeID).isEmpty())
        assertEquals(listOf(second.id), store.products(second.storeID).map { it.id })
        assertTrue(store.variants(first.storeID).isEmpty())
        assertEquals(listOf(keptVariant.id), store.variants(second.storeID).map { it.id })
        assertTrue(store.movements(first.storeID).isEmpty())
        val secondMovements = store.movements(second.storeID)
        assertEquals(1, secondMovements.size)
        assertEquals(second.id, secondMovements.first().productID)
        val savedOrder = store.order(order.id)!!
        assertDecimal("25", savedOrder.total)
        assertDecimal("15", savedOrder.grossProfit)
        assertEquals("Caneca", savedOrder.items.first().productName)
    }

    @Test
    fun rollbackDaExclusaoRestauraProdutoEEstoque() = withStore { store ->
        val product = ProductService.create(storeID = UUID.randomUUID(), name = "Produto", store = store)
        ProductVariantService.create(product = product, name = "Unica", initialQuantity = 2, store = store)
        val failingStore = FailingOnDeleteProductStore(store)

        try {
            ProductService.delete(product, failingStore)
            fail("Esperava falha ao excluir produto")
        } catch (_: IllegalStateException) {
            // Esperado.
        }

        assertEquals(1, store.products(product.storeID).size)
        assertEquals(1, store.variants(product.storeID).size)
        assertEquals(1, store.movements(product.storeID).size)
    }

    @Test
    fun exclusaoTambemLimpaMovimentacaoSemVariacao() = withStore { store ->
        val product = ProductService.create(storeID = UUID.randomUUID(), name = "Produto", store = store)
        store.saveMovement(StockMovement(storeID = product.storeID, productID = product.id))

        ProductService.delete(product, store)

        assertEquals(0, store.movements(product.storeID).size)
    }

    @Test
    fun entradaEmNovaVariacaoRegistraRecebimentoNaoSaldoInicial() = withStore { store ->
        val product = ProductService.create(storeID = UUID.randomUUID(), name = "Caneca", store = store)
        val entry = StockService.registerEntry(
            quantity = 3,
            product = product,
            variantID = null,
            newVariantName = " Verde ",
            note = " Recebido ",
            store = store,
        )

        val variant = ProductVariantService.variants(product, store).first()
        assertEquals("Verde", variant.name)
        assertEquals(3, variant.quantity)
        assertEquals(StockMovementKind.Entry, entry.kind)
        assertEquals(3, entry.quantityDelta)
        assertEquals("Recebido", entry.note)
        assertEquals(1, store.movements(product.storeID).size)
    }

    @Test
    fun entradaExistenteReconsultaSaldoERecusaOpcaoDeOutraLoja() = withStore { store ->
        val product = ProductService.create(storeID = UUID.randomUUID(), name = "Caneca", store = store)
        val other = ProductService.create(storeID = UUID.randomUUID(), name = "Caneca", store = store)
        val variant = ProductVariantService.create(product = product, name = "Azul", initialQuantity = 2, store = store)

        StockService.registerEntry(quantity = 3, product = product, variantID = variant.id, store = store)
        StockService.registerEntry(quantity = 4, product = product, variantID = variant.id, store = store)
        assertEquals(9, store.variant(variant.id)!!.quantity)

        expectError(StockError.ProductMismatch) {
            StockService.registerEntry(quantity = 1, product = other, variantID = variant.id, store = store)
        }
        expectError(StockError.ProductMismatch) {
            StockService.registerEntry(quantity = 1, product = product, variantID = UUID.randomUUID(), store = store)
        }
        assertEquals(9, store.variant(variant.id)!!.quantity)
    }

    @Test
    fun entradasInvalidasNaoCriamVariacaoNemMovimentacao() = withStore { store ->
        val product = ProductService.create(storeID = UUID.randomUUID(), name = "Caneca", store = store)
        val variant = ProductVariantService.create(
            product = product,
            name = "Azul",
            initialQuantity = Int.MAX_VALUE,
            store = store,
        )

        expectError(StockError.InvalidQuantity) {
            StockService.registerEntry(
                quantity = 0,
                product = product,
                variantID = null,
                newVariantName = "Verde",
                store = store,
            )
        }
        expectError(ProductVariantError.DuplicateName) {
            StockService.registerEntry(
                quantity = 1,
                product = product,
                variantID = null,
                newVariantName = "azul",
                store = store,
            )
        }
        expectError(StockError.QuantityOverflow) {
            StockService.registerEntry(
                quantity = 1,
                product = product,
                variantID = variant.id,
                store = store,
            )
        }
        assertEquals(Int.MAX_VALUE, store.variant(variant.id)!!.quantity)
        assertEquals(1, store.variants(product.storeID).size)
        assertEquals(1, store.movements(product.storeID).size)
    }

    private class FailingOnDeleteProductStore(
        private val inner: InMemoryTinyStockStore,
    ) : TinyStockStore by inner {
        override suspend fun deleteProduct(id: UUID) {
            throw IllegalStateException("Falha simulada na exclusao")
        }
    }
}
