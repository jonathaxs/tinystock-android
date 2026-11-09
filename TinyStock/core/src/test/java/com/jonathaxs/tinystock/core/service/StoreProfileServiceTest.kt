package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.model.StoreLifecycleState
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.model.StoreScope
import com.jonathaxs.tinystock.core.support.expectError
import com.jonathaxs.tinystock.core.support.withStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Porte dos testes de cadastro, arquivamento e ordenacao de `StoreProfileTests.swift`. */
class StoreProfileServiceTest {

    @Test
    fun criacaoRemoveEspacosDoNome() = withStore { store ->
        val created = StoreProfileService.create(name = "  Loja Principal  ", store = store)

        val saved = store.store(created.id)!!
        assertEquals("Loja Principal", saved.name)
        assertFalse(saved.isArchived)
    }

    @Test
    fun criacaoAtribuiOrdemSequencial() = withStore { store ->
        val first = StoreProfileService.create(name = "Primeira", store = store)
        val second = StoreProfileService.create(name = "Segunda", store = store)
        val third = StoreProfileService.create(name = "Terceira", store = store)

        val positions = listOf(
            store.store(first.id)!!.sortOrder,
            store.store(second.id)!!.sortOrder,
            store.store(third.id)!!.sortOrder,
        )
        assertEquals(listOf(0, 1, 2), positions)
    }

    @Test
    fun reordenacaoPersistePosicoesConsecutivas() = withStore { store ->
        val first = StoreProfileService.create(name = "Primeira", store = store)
        val second = StoreProfileService.create(name = "Segunda", store = store)
        val third = StoreProfileService.create(name = "Terceira", store = store)
        val reorderedAt = Instant.ofEpochSecond(1_800_000_000)

        StoreProfileService.setDisplayOrder(
            listOf(third, first, second),
            date = reorderedAt,
            store = store,
        )

        val savedFirst = store.store(first.id)!!
        val savedSecond = store.store(second.id)!!
        val savedThird = store.store(third.id)!!
        assertEquals(0, savedThird.sortOrder)
        assertEquals(1, savedFirst.sortOrder)
        assertEquals(2, savedSecond.sortOrder)
        assertEquals(
            listOf(third.id, first.id, second.id),
            StoreProfileService.orderedForDisplay(listOf(savedFirst, savedSecond, savedThird)).map { it.id },
        )
        assertTrue(listOf(savedFirst, savedSecond, savedThird).all { it.updatedAt == reorderedAt })
    }

    @Test
    fun nomeVazioEhRecusado() = withStore { store ->
        expectError(StoreProfileError.EmptyName) {
            StoreProfileService.create(name = "   \n", store = store)
        }
    }

    @Test
    fun nomeDuplicadoIgnoraMaiusculasEAcentos() = withStore { store ->
        StoreProfileService.create(name = "Loja Secundária", store = store)

        expectError(StoreProfileError.DuplicateName) {
            StoreProfileService.create(name = "LOJA SECUNDARIA", store = store)
        }
    }

    @Test
    fun lojaPadraoEhCriadaUmaUnicaVez() = withStore { store ->
        val first = StoreProfileService.ensureDefaultStore(
            name = "Minha loja",
            store = store,
        )
        val second = StoreProfileService.ensureDefaultStore(
            name = "Outra loja",
            store = store,
        )
        val stores = store.stores()

        assertEquals(1, stores.size)
        assertEquals(first.id, second.id)
        assertEquals(StoreScope.PRIMARY_STORE_ID, first.id)
        assertEquals("Minha loja", store.store(second.id)!!.name)
    }

    // Os testes "preparacaoDoCloudKitRemapeiaLojaInicialETodoOEscopo",
    // "reconciliacaoDoCloudKitConsolidaCopiasDaLojaInicial", "reconciliacaoSemCopiasNaoAlteraALojaInicial"
    // e "reconciliacaoDoCloudKitPreservaCicloDeVidaMaisRecente" sao exclusivos do CloudKit no iOS.

    @Test
    fun edicaoPreservaDataDeCriacao() = withStore { store ->
        val createdAt = Instant.ofEpochSecond(1_700_000_000)
        val updatedAt = Instant.ofEpochSecond(1_800_000_000)
        val initial = StoreProfile(name = "Loja 1", createdAt = createdAt, updatedAt = createdAt)
        store.saveStore(initial)

        StoreProfileService.update(
            profile = initial,
            name = "  Loja principal ",
            imageFileName = null,
            date = updatedAt,
            store = store,
        )

        val saved = store.store(initial.id)!!
        assertEquals("Loja principal", saved.name)
        assertEquals(createdAt, saved.createdAt)
        assertEquals(updatedAt, saved.updatedAt)
    }

    @Test
    fun lojaPadraoReativaLojaArquivadaEmBancoInconsistente() = withStore { store ->
        val archived = StoreProfile(name = "Loja Principal", isArchived = true)
        store.saveStore(archived)

        val recovered = StoreProfileService.ensureDefaultStore(name = "Minha loja", store = store)
        val stores = store.stores()

        assertEquals(1, stores.size)
        assertEquals(archived.id, recovered.id)
        assertFalse(store.store(archived.id)!!.isArchived)
    }

    @Test
    fun ultimaLojaAtivaNaoPodeSerArquivada() = withStore { store ->
        val created = StoreProfileService.create(name = "Minha loja", store = store)

        expectError(StoreProfileError.LastActiveStore) {
            StoreProfileService.archive(created, store = store)
        }
        assertFalse(store.store(created.id)!!.isArchived)
    }

    @Test
    fun lojaPodeSerArquivadaQuandoExisteOutraAtiva() = withStore { store ->
        val first = StoreProfileService.create(name = "Loja Principal", store = store)
        StoreProfileService.create(name = "Loja Secundária", store = store)
        val archivedAt = Instant.ofEpochSecond(1_800_000_000)

        StoreProfileService.archive(first, date = archivedAt, store = store)

        val saved = store.store(first.id)!!
        assertTrue(saved.isArchived)
        assertEquals(archivedAt, saved.archivedAt)
        assertEquals(StoreLifecycleState.Archived, saved.lifecycleState)
        assertEquals(archivedAt, saved.updatedAt)
    }

    @Test
    fun lojaArquivadaPodeSerRestaurada() = withStore { store ->
        val restoredAt = Instant.ofEpochSecond(1_800_000_000)
        val archived = StoreProfile(
            name = "Loja Principal",
            isArchived = true,
            archivedAt = Instant.ofEpochSecond(1_700_000_000),
        )
        store.saveStore(archived)

        StoreProfileService.restore(archived, date = restoredAt, store = store)

        val saved = store.store(archived.id)!!
        assertFalse(saved.isArchived)
        assertNull(saved.archivedAt)
        assertEquals(StoreLifecycleState.Active, saved.lifecycleState)
        assertEquals(restoredAt, saved.updatedAt)
    }

    @Test
    fun arquivadasELixeiraUsamOrdemMaisRecentePrimeiro() {
        val older = Instant.ofEpochSecond(100)
        val newer = Instant.ofEpochSecond(200)
        val firstArchived = StoreProfile(name = "A", isArchived = true, archivedAt = older)
        val secondArchived = StoreProfile(name = "B", isArchived = true, archivedAt = newer)
        val firstTrashed = StoreProfile(name = "C", isArchived = true, trashedAt = older)
        val secondTrashed = StoreProfile(name = "D", isArchived = true, trashedAt = newer)
        val all = listOf(firstArchived, secondArchived, firstTrashed, secondTrashed)

        assertEquals(
            listOf(secondArchived.id, firstArchived.id),
            StoreProfileService.orderedArchivedForDisplay(all).map { it.id },
        )
        assertEquals(
            listOf(secondTrashed.id, firstTrashed.id),
            StoreProfileService.orderedTrashedForDisplay(all).map { it.id },
        )
    }

    // O iOS tem "todoErroTemMensagemLocalizada"; no Android o texto fica no :app e o teste vai para la.
}
