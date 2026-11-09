package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.StockMovementKind
import com.jonathaxs.tinystock.core.support.expectError
import com.jonathaxs.tinystock.core.support.withStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Porte de `StockServiceTests.swift`. */
class StockServiceTest {

    private suspend fun makeProductAndVariant(
        store: TinyStockStore,
        initialQuantity: Int = 0,
    ): Pair<Product, ProductVariant> {
        val product = Product(storeID = UUID.randomUUID(), name = "Produto principal")
        store.saveProduct(product)
        val variant = ProductVariantService.create(product, "Preta", initialQuantity = initialQuantity, store = store)
        return product to variant
    }

    @Test
    fun estoqueInicialGeraMovimentacao() = withStore { store ->
        val (product, variant) = makeProductAndVariant(store, initialQuantity = 3)

        val movements = StockService.movements(variant, product, store)

        val movement = movements.first()
        assertEquals(1, movements.size)
        assertEquals(StockMovementKind.InitialStock, movement.kind)
        assertEquals(3, movement.quantityDelta)
        assertEquals(3, movement.balanceAfter)
        assertEquals(3, store.variant(variant.id)!!.quantity)
    }

    @Test
    fun variacaoZeradaNaoCriaMovimentacaoInicial() = withStore { store ->
        val (product, variant) = makeProductAndVariant(store)

        assertTrue(StockService.movements(variant, product, store).isEmpty())
        assertEquals(0, store.variant(variant.id)!!.quantity)
    }

    @Test
    fun entradaSomaAoSaldoEGuardaReferencia() = withStore { store ->
        val (product, variant) = makeProductAndVariant(store, initialQuantity = 2)
        val referenceID = UUID.randomUUID()
        val date = Instant.ofEpochSecond(1_800_000_000)

        val movement = StockService.registerEntry(
            4, variant, product, note = "  Nova entrada  ", referenceID = referenceID, date = date, store = store,
        )

        val updated = store.variant(variant.id)!!
        assertEquals(6, updated.quantity)
        assertEquals(date, updated.updatedAt)
        assertEquals(StockMovementKind.Entry, movement.kind)
        assertEquals(4, movement.quantityDelta)
        assertEquals(6, movement.balanceAfter)
        assertEquals("Nova entrada", movement.note)
        assertEquals(referenceID, movement.referenceID)
    }

    @Test
    fun ajustePodeAumentarOuReduzirOSaldo() = withStore { store ->
        val (product, variant) = makeProductAndVariant(store, initialQuantity = 5)

        // O mesmo objeto (com saldo velho) e passado duas vezes: o servico rele o saldo atual.
        val increase = StockService.registerAdjustment(8, variant, product, store = store)
        val decrease = StockService.registerAdjustment(3, variant, product, store = store)

        assertEquals(3, increase.quantityDelta)
        assertEquals(8, increase.balanceAfter)
        assertEquals(-5, decrease.quantityDelta)
        assertEquals(3, decrease.balanceAfter)
        assertEquals(3, store.variant(variant.id)!!.quantity)
    }

    @Test
    fun entradaInvalidaEAjusteSemMudancaSaoRecusados() = withStore { store ->
        val (product, variant) = makeProductAndVariant(store, initialQuantity = 2)

        expectError(StockError.InvalidQuantity) {
            StockService.registerEntry(0, variant, product, store = store)
        }
        expectError(StockError.NoChange) {
            StockService.registerAdjustment(2, variant, product, store = store)
        }
        expectError(StockError.NegativeBalance) {
            StockService.registerAdjustment(-1, variant, product, store = store)
        }
    }

    @Test
    fun reversaoDesfazEntradaSemApagarHistorico() = withStore { store ->
        val (product, variant) = makeProductAndVariant(store, initialQuantity = 2)
        val entry = StockService.registerEntry(3, variant, product, store = store)
        val reversedAt = Instant.ofEpochSecond(1_800_000_000)

        val reversal = StockService.reverse(
            entry, variant, product, note = "Entrada duplicada", date = reversedAt, store = store,
        )

        assertEquals(2, store.variant(variant.id)!!.quantity)
        assertEquals(reversedAt, store.movement(entry.id)!!.reversedAt)
        assertEquals(StockMovementKind.Reversal, reversal.kind)
        assertEquals(-3, reversal.quantityDelta)
        assertEquals(2, reversal.balanceAfter)
        assertEquals(entry.id, reversal.reversedMovementID)
        assertEquals("Entrada duplicada", reversal.note)
        assertEquals(3, StockService.movements(variant, product, store).size)
        expectError(StockError.ReversalNotAllowed) {
            StockService.reverse(reversal, variant, product, store = store)
        }
    }

    @Test
    fun mesmaMovimentacaoNaoPodeSerRevertidaDuasVezes() = withStore { store ->
        val (product, variant) = makeProductAndVariant(store)
        val entry = StockService.registerEntry(2, variant, product, store = store)
        StockService.reverse(entry, variant, product, store = store)

        expectError(StockError.AlreadyReversed) {
            StockService.reverse(entry, variant, product, store = store)
        }
    }

    @Test
    fun reversaoQueDeixariaSaldoNegativoEhBloqueada() = withStore { store ->
        val (product, variant) = makeProductAndVariant(store)
        val entry = StockService.registerEntry(5, variant, product, store = store)
        StockService.registerAdjustment(2, variant, product, store = store)

        expectError(StockError.NegativeBalance) {
            StockService.reverse(entry, variant, product, store = store)
        }
        assertEquals(2, store.variant(variant.id)!!.quantity)
        assertNull(store.movement(entry.id)!!.reversedAt)
    }

    @Test
    fun produtoOuMovimentacaoDeOutroEscopoSaoRecusados() = withStore { store ->
        val (firstProduct, firstVariant) = makeProductAndVariant(store)
        val (secondProduct, secondVariant) = makeProductAndVariant(store)
        val entry = StockService.registerEntry(1, firstVariant, firstProduct, store = store)

        expectError(StockError.ProductMismatch) {
            StockService.registerEntry(1, firstVariant, secondProduct, store = store)
        }
        expectError(StockError.MovementMismatch) {
            StockService.reverse(entry, secondVariant, secondProduct, store = store)
        }
    }

    @Test
    fun historicoFicaIsoladoEOrdenadoDoMaisRecente() = withStore { store ->
        val (product, variant) = makeProductAndVariant(store)
        val (otherProduct, otherVariant) = makeProductAndVariant(store)
        val older = Instant.ofEpochSecond(1_700_000_000)
        val newer = Instant.ofEpochSecond(1_800_000_000)

        val first = StockService.registerEntry(1, variant, product, date = older, store = store)
        val second = StockService.registerEntry(2, variant, product, date = newer, store = store)
        StockService.registerEntry(9, otherVariant, otherProduct, date = newer, store = store)

        val movements = StockService.movements(variant, product, store)

        assertEquals(listOf(second.id, first.id), movements.map { it.id })
        assertTrue(movements.all { it.variantID != otherVariant.id })
    }

    // Extra do Android: a transacao desfaz tudo quando o servico falha no meio.
    @Test
    fun falhaNoMeioNaoDeixaGravacaoPelaMetade() = withStore { store ->
        val (product, variant) = makeProductAndVariant(store, initialQuantity = 1)

        expectError(StockError.NegativeBalance) {
            store.inTransaction {
                StockService.registerEntry(4, variant, product, store = store)
                StockService.registerAdjustment(-1, variant, product, store = store)
            }
        }

        assertEquals(1, store.variant(variant.id)!!.quantity)
        assertEquals(1, StockService.movements(variant, product, store).size)
    }

    // O iOS tem "todoErroTemMensagemLocalizada"; no Android o texto fica no :app e o teste vai para la (A5).
}
