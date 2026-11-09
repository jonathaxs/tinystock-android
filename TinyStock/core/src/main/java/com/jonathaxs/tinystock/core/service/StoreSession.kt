package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.util.ensure
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Leitura e gravacao do id da loja selecionada (substitui o `UserDefaults` do iOS). A versao real usa
 * DataStore (`AppPreferences`), por isso as funcoes sao suspend.
 */
interface SelectedStoreStorage {
    suspend fun load(): UUID?
    suspend fun save(id: UUID)
}

/** Armazenamento em memoria da loja selecionada para testes e previas. */
class InMemorySelectedStoreStorage(
    initialStoreID: UUID? = null,
) : SelectedStoreStorage {

    private var selectedStoreID: UUID? = initialStoreID

    override suspend fun load(): UUID? = selectedStoreID

    override suspend fun save(id: UUID) {
        selectedStoreID = id
    }
}

/**
 * Mantem e grava a loja selecionada. Porte do `StoreSession` do iOS, sem `bootstrapForCloudSync` e
 * `reconcileCloudChanges` (exclusivos do CloudKit). Diferente do iOS, o construtor nao grava (gravar e
 * suspend); quem grava a selecao inicial e o [bootstrap].
 */
class StoreSession(
    selectedStoreID: UUID,
    private val storage: SelectedStoreStorage = InMemorySelectedStoreStorage(),
) {

    var selectedStoreID: UUID = selectedStoreID
        private set

    /** Troca a selecao somente para uma loja ativa. */
    suspend fun select(store: StoreProfile) {
        ensure(store.isActive) { StoreProfileError.ArchivedStore }
        selectedStoreID = store.id
        storage.save(selectedStoreID)
    }

    companion object {
        /** Mesma chave do `UserDefaults` no iOS. */
        const val SELECTED_STORE_KEY = "app.selectedStoreID"

        /**
         * Garante a loja inicial, limpa a lixeira vencida e recupera uma selecao ativa valida.
         * [defaultStoreName] e o nome traduzido da loja inicial, usado so se ela for criada agora.
         */
        suspend fun bootstrap(
            defaultStoreName: String,
            storage: SelectedStoreStorage = InMemorySelectedStoreStorage(),
            date: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault(),
            store: TinyStockStore,
        ): StoreSession = store.inTransaction {
            val defaultStore = StoreProfileService.ensureDefaultStore(name = defaultStoreName, date = date, store = store)
            StoreDeletionService.purgeExpiredTrash(date = date, zone = zone, store = store)
            val activeStores = StoreProfileService.orderedForDisplay(
                store.stores().filter { it.isActive },
            )

            val storedID = storage.load()
            val selectedStore = activeStores.firstOrNull { it.id == storedID }
                ?: activeStores.firstOrNull()
                ?: defaultStore

            storage.save(selectedStore.id)
            StoreSession(selectedStoreID = selectedStore.id, storage = storage)
        }
    }
}
