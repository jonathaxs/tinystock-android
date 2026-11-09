package com.jonathaxs.tinystock.core.data

import com.jonathaxs.tinystock.core.model.StoreProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Comportamento proprio do armazenamento em memoria (os servicos sao testados nos outros arquivos). */
class InMemoryTinyStockStoreTest {

    @Test
    fun observarEmiteSoMudancasConfirmadas() = runBlocking {
        val store = InMemoryTinyStockStore()
        val seen = mutableListOf<Int>()
        val job = launch(Dispatchers.Unconfined) { store.observeStores().collect { seen.add(it.size) } }

        store.saveStore(StoreProfile(name = "A"))
        store.inTransaction {
            store.saveStore(StoreProfile(name = "B"))
            store.saveStore(StoreProfile(name = "C"))
        }
        runCatching {
            store.inTransaction {
                store.saveStore(StoreProfile(name = "D"))
                error("falha simulada")
            }
        }
        job.cancel()

        // Uma emissao inicial, uma pela gravacao solta e uma so pela transacao inteira. A desfeita nao emite.
        assertEquals(listOf(0, 1, 3), seen)
        assertEquals(3, store.stores().size)
    }
}
