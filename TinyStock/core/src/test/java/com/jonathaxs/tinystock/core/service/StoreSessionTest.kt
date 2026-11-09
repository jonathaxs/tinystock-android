package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.support.expectError
import com.jonathaxs.tinystock.core.support.withStore
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Porte dos testes de selecao atual (`StoreSession`) de `StoreProfileTests.swift`. */
class StoreSessionTest {

    @Test
    fun sessaoCriaLojaPadraoEPersisteASelecao() = withStore { store ->
        val storage = InMemorySelectedStoreStorage()

        val session = StoreSession.bootstrap(defaultStoreName = "Minha loja", storage = storage, store = store)
        val stores = store.stores()

        assertEquals(1, stores.size)
        assertEquals(session.selectedStoreID, stores.first().id)
        assertEquals(session.selectedStoreID, storage.load())
    }

    @Test
    fun sessaoRemoveLixeiraVencidaAoIniciar() = withStore { store ->
        val storage = InMemorySelectedStoreStorage()
        val zone = ZoneOffset.UTC
        val now = Instant.ofEpochSecond(1_800_000_000)
        val expiredDate = now.atZone(zone).minusDays(31).toInstant()
        val active = StoreProfileService.create(name = "Ativa", store = store)
        val target = StoreProfileService.create(name = "Temporaria", store = store)
        StoreDeletionService.moveToTrash(target, date = expiredDate, store = store)

        val session = StoreSession.bootstrap(
            defaultStoreName = "Minha loja",
            storage = storage,
            date = now,
            zone = zone,
            store = store,
        )

        assertEquals(active.id, session.selectedStoreID)
        assertEquals(1, store.stores().size)
    }

    @Test
    fun selecaoInvalidaVoltaParaLojaAtiva() = withStore { store ->
        val storage = InMemorySelectedStoreStorage(UUID.randomUUID())
        val created = StoreProfileService.create(name = "Loja Principal", store = store)

        val session = StoreSession.bootstrap(defaultStoreName = "Minha loja", storage = storage, store = store)

        assertEquals(created.id, session.selectedStoreID)
    }

    @Test
    fun sessaoRecusaSelecionarLojaArquivada() = withStore { store ->
        val storage = InMemorySelectedStoreStorage()
        val active = StoreProfileService.create(name = "Loja Principal", store = store)
        val archived = StoreProfile(name = "Loja antiga", isArchived = true)
        store.saveStore(archived)
        val session = StoreSession(selectedStoreID = active.id, storage = storage)

        expectError(StoreProfileError.ArchivedStore) {
            session.select(archived)
        }
        assertEquals(active.id, session.selectedStoreID)
    }

    // O teste "sessaoTrocaSelecaoArquivadaAposImportacaoRemota" nao se aplica: `reconcileCloudChanges`
    // faz parte da sincronizacao com CloudKit no iOS.
}
