package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderItem
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.model.StockMovementKind
import com.jonathaxs.tinystock.core.model.StoreLifecycleState
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.model.StoreScope
import com.jonathaxs.tinystock.core.support.expectError
import com.jonathaxs.tinystock.core.support.withStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Porte dos testes de lixeira e exclusao definitiva de `StoreProfileTests.swift`. */
class StoreDeletionServiceTest {

    @Test
    fun lixeiraRestauraOEstadoAnteriorSemAlterarDados() = withStore { store ->
        val active = StoreProfileService.create(name = "Ativa", store = store)
        val target = StoreProfileService.create(name = "Temporaria", store = store)
        val archivedAt = Instant.ofEpochSecond(1_800_000_000)
        val trashedAt = archivedAt.plusSeconds(100)
        val restoredAt = trashedAt.plusSeconds(100)
        StoreProfileService.archive(target, date = archivedAt, store = store)

        StoreDeletionService.moveToTrash(target, date = trashedAt, store = store)
        val trashed = store.store(target.id)!!
        assertEquals(StoreLifecycleState.Trashed, trashed.lifecycleState)
        assertTrue(trashed.wasArchivedBeforeTrash)
        assertEquals(archivedAt, trashed.archivedAt)

        StoreDeletionService.restoreFromTrash(target, date = restoredAt, store = store)
        val restored = store.store(target.id)!!
        assertEquals(StoreLifecycleState.Archived, restored.lifecycleState)
        assertEquals(archivedAt, restored.archivedAt)
        assertNull(restored.trashedAt)
        assertFalse(restored.wasArchivedBeforeTrash)
        assertEquals(StoreLifecycleState.Active, store.store(active.id)!!.lifecycleState)
    }

    @Test
    fun lojaAtivaVoltaAtivaDepoisDaLixeira() = withStore { store ->
        val target = StoreProfileService.create(name = "Temporaria", store = store)
        StoreProfileService.create(name = "Mantida", store = store)

        StoreDeletionService.moveToTrash(target, store = store)
        StoreDeletionService.restoreFromTrash(target, store = store)

        val restored = store.store(target.id)!!
        assertEquals(StoreLifecycleState.Active, restored.lifecycleState)
        assertNull(restored.archivedAt)
        assertNull(restored.trashedAt)
    }

    @Test
    fun exclusaoPermanenteRemoveEscopoCompletoEPreservaOutraLoja() = withStore { store ->
        val target = StoreProfileService.create(name = "Loja temporaria", store = store)
        val remaining = StoreProfileService.create(name = "Loja mantida", store = store)
        val keptProduct = Product(storeID = remaining.id, name = "Produto mantido")
        val product = Product(storeID = target.id, name = "Produto")
        val variant = ProductVariant(
            storeID = target.id,
            productID = product.id,
            name = "Padrao",
            quantity = 2,
        )
        val movement = StockMovement(
            storeID = target.id,
            productID = product.id,
            variantID = variant.id,
            kindRawValue = StockMovementKind.InitialStock.rawValue,
            quantityDelta = 2,
            balanceAfter = 2,
        )
        val orderItem = SalesOrderItem(
            storeID = target.id,
            productID = product.id,
            variantID = variant.id,
            productName = product.name,
            variantName = variant.name,
            quantity = 1,
        )
        val order = SalesOrder(storeID = target.id, items = listOf(orderItem))

        store.saveProduct(keptProduct)
        store.saveProduct(product)
        store.saveVariant(variant)
        store.saveMovement(movement)
        store.saveOrder(order)

        StoreDeletionService.moveToTrash(target, store = store)

        // Sem Sale/SaleItem legados no Android: orderCount conta apenas SalesOrder.
        val summary = StoreDeletionService.deletionSummary(target, store = store)
        assertEquals(
            StoreDeletionSummary(
                productCount = 1,
                variantCount = 1,
                stockMovementCount = 1,
                orderCount = 1,
            ),
            summary,
        )

        val targetID = target.id
        val replacement = StoreDeletionService.deletePermanently(target, store = store)

        assertEquals(remaining.id, replacement?.id)
        assertEquals(1, store.stores().size)
        assertTrue(store.products(targetID).isEmpty())
        assertEquals(1, store.products(remaining.id).size)
        assertTrue(store.variants(targetID).isEmpty())
        assertTrue(store.movements(targetID).isEmpty())
        assertTrue(store.orders(targetID).isEmpty())
        assertNull(store.order(order.id))
    }

    @Test
    fun ultimaLojaAtivaNaoPodeIrParaLixeira() = withStore { store ->
        val created = StoreProfileService.create(name = "Loja principal", store = store)

        expectError(StoreProfileError.LastActiveStore) {
            StoreDeletionService.moveToTrash(created, store = store)
        }
        expectError(StoreProfileError.StoreNotInTrash) {
            StoreDeletionService.deletePermanently(created, store = store)
        }
        assertEquals(1, store.stores().size)
    }

    @Test
    fun lojaArquivadaPodeSerExcluidaComApenasUmaLojaAtiva() = withStore { store ->
        val active = StoreProfile(name = "Ativa")
        val archived = StoreProfile(name = "Arquivada", isArchived = true)
        store.saveStore(active)
        store.saveStore(archived)

        StoreDeletionService.moveToTrash(archived, store = store)
        StoreDeletionService.deletePermanently(archived, store = store)

        assertEquals(listOf(active.id), store.stores().map { it.id })
    }

    @Test
    fun exclusaoDaLojaInicialTransfereIdentidadeParaSubstituta() = withStore { store ->
        val initial = StoreProfile(id = StoreScope.PRIMARY_STORE_ID, name = "Inicial", sortOrder = 0)
        val replacement = StoreProfile(name = "Substituta", sortOrder = 1)
        val product = Product(storeID = replacement.id, name = "Produto mantido")
        store.saveStore(initial)
        store.saveStore(replacement)
        store.saveProduct(product)

        StoreDeletionService.moveToTrash(initial, store = store)
        val selected = StoreDeletionService.deletePermanently(initial, store = store)

        assertEquals(StoreScope.PRIMARY_STORE_ID, selected?.id)
        assertEquals(1, store.stores().size)
        val savedReplacement = store.store(StoreScope.PRIMARY_STORE_ID)!!
        assertEquals("Substituta", savedReplacement.name)
        assertEquals(0, savedReplacement.sortOrder)
        assertEquals(StoreScope.PRIMARY_STORE_ID, store.product(product.id)!!.storeID)
    }

    @Test
    fun lixeiraExpiraSomenteDepoisDeTrintaDiasCompletos() = withStore { store ->
        val trashedAt = Instant.ofEpochSecond(1_800_000_000)
        val zone = ZoneOffset.UTC
        val target = StoreProfileService.create(name = "Temporaria", store = store)
        StoreProfileService.create(name = "Mantida", store = store)
        val trashed = StoreDeletionService.moveToTrash(target, date = trashedAt, store = store)
        val expiration = StoreDeletionService.trashExpirationDate(trashed, zone = zone)!!

        val beforeExpiration = expiration.minusSeconds(1)
        assertEquals(
            0,
            StoreDeletionService.purgeExpiredTrash(date = beforeExpiration, zone = zone, store = store),
        )
        assertEquals(2, store.stores().size)

        assertEquals(
            1,
            StoreDeletionService.purgeExpiredTrash(date = expiration, zone = zone, store = store),
        )
        assertEquals(1, store.stores().size)
    }

    @Test
    fun lojaNaLixeiraNaoPodeSerEditadaOuSelecionada() = withStore { store ->
        val target = StoreProfileService.create(name = "Temporaria", store = store)
        val active = StoreProfileService.create(name = "Ativa", store = store)
        val trashed = StoreDeletionService.moveToTrash(target, store = store)

        expectError(StoreProfileError.TrashedStore) {
            StoreProfileService.update(
                profile = target,
                name = "Outro nome",
                imageFileName = null,
                store = store,
            )
        }
        val session = StoreSession(selectedStoreID = active.id, storage = InMemorySelectedStoreStorage())
        expectError(StoreProfileError.ArchivedStore) {
            session.select(trashed)
        }
    }

    // Extra do Android: confere que falha na gravacao desfaz a exclusao inteira pela transacao.
    @Test
    fun falhaAoExcluirLojaDesfazRemocaoDosDados() = withStore { inner ->
        val target = StoreProfileService.create(name = "Temporaria", store = inner)
        StoreProfileService.create(name = "Mantida", store = inner)
        val product = Product(storeID = target.id, name = "Produto")
        inner.saveProduct(product)
        StoreDeletionService.moveToTrash(target, store = inner)

        val failingStore = object : TinyStockStore by inner {
            override suspend fun deleteStore(id: UUID) {
                throw IllegalStateException("Falha simulada")
            }
        }

        try {
            StoreDeletionService.deletePermanently(target, store = failingStore)
            fail("Esperava falha simulada")
        } catch (_: IllegalStateException) {
            // Esperado.
        }

        assertNotNull(inner.store(target.id))
        assertNotNull(inner.product(product.id))
    }
}
