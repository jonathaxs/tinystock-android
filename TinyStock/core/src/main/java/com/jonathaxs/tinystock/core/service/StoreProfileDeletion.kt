package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.model.StoreScope
import com.jonathaxs.tinystock.core.util.ensure
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Contagem de registros vinculados a uma loja antes da exclusao definitiva. */
data class StoreDeletionSummary(
    val productCount: Int,
    val variantCount: Int,
    val stockMovementCount: Int,
    val orderCount: Int,
)

/**
 * Lixeira e exclusao definitiva de uma loja e dos dados associados. Porte de
 * `StoreProfileService+Deletion.swift`, sem a contagem de vendas antigas (`Sale`).
 */
object StoreDeletionService {

    const val TRASH_RETENTION_DAYS: Int = 30

    /** Move para a lixeira sem remover os dados e guarda o estado anterior para restauracao. */
    suspend fun moveToTrash(
        profile: StoreProfile,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): StoreProfile = store.inTransaction {
        val current = store.store(profile.id) ?: profile
        if (current.isTrashed) return@inTransaction current
        if (current.isActive) {
            val remainingActiveCount = store.stores().count { it.isActive && it.id != current.id }
            ensure(remainingActiveCount > 0) { StoreProfileError.LastActiveStore }
        }

        val trashed = current.copy(
            wasArchivedBeforeTrash = current.isArchived,
            isArchived = true,
            trashedAt = date,
            updatedAt = date,
        )
        store.saveStore(trashed)
        trashed
    }

    /** Restaura para ativa ou arquivada, conforme o estado anterior a lixeira. */
    suspend fun restoreFromTrash(
        profile: StoreProfile,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): StoreProfile = store.inTransaction {
        val current = store.store(profile.id) ?: profile
        ensure(current.isTrashed) { StoreProfileError.StoreNotInTrash }

        val restoreAsArchived = current.wasArchivedBeforeTrash
        val restored = current.copy(
            isArchived = restoreAsArchived,
            archivedAt = if (restoreAsArchived) (current.archivedAt ?: date) else null,
            trashedAt = null,
            wasArchivedBeforeTrash = false,
            updatedAt = date,
        )
        store.saveStore(restored)
        restored
    }

    fun trashExpirationDate(
        profile: StoreProfile,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Instant? =
        profile.trashedAt?.atZone(zone)?.plusDays(TRASH_RETENTION_DAYS.toLong())?.toInstant()

    /** Remove em lote somente lojas que completaram o prazo na lixeira. */
    suspend fun purgeExpiredTrash(
        date: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        store: TinyStockStore,
    ): Int = store.inTransaction {
        val expired = store.stores().filter { candidate ->
            val expiration = trashExpirationDate(candidate, zone) ?: return@filter false
            !expiration.isAfter(date)
        }
        for (candidate in expired) {
            deletePermanently(candidate, date = date, store = store)
        }
        expired.size
    }

    /** Calcula o impacto antes de apresentar a confirmacao ao usuario. */
    suspend fun deletionSummary(
        profile: StoreProfile,
        store: TinyStockStore,
    ): StoreDeletionSummary {
        val current = store.store(profile.id) ?: profile
        validateDeletion(current, store)
        val storeID = current.id
        return StoreDeletionSummary(
            productCount = store.products(storeID).size,
            variantCount = store.variants(storeID).size,
            stockMovementCount = store.movements(storeID).size,
            orderCount = store.orders(storeID).size,
        )
    }

    /** Exclui a loja e todos os dados do escopo. Devolve a primeira loja ativa restante. */
    suspend fun deletePermanently(
        profile: StoreProfile,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): StoreProfile? = store.inTransaction {
        val current = store.store(profile.id) ?: profile
        ensure(current.isTrashed) { StoreProfileError.StoreNotInTrash }
        val remainingActiveStores = StoreProfileService.orderedForDisplay(
            store.stores().filter { it.id != current.id && it.isActive },
        )
        ensure(remainingActiveStores.isNotEmpty()) { StoreProfileError.LastActiveStore }

        val deletedStoreID = current.id
        deleteData(deletedStoreID, store)
        store.deleteStore(deletedStoreID)

        // A identidade inicial continua existindo para manter o backup consistente.
        val activeAfterPromotion = if (deletedStoreID == StoreScope.PRIMARY_STORE_ID) {
            val replacement = remainingActiveStores.first()
            val previousID = replacement.id
            StoreProfileService.remapStoreScope(
                from = previousID,
                to = StoreScope.PRIMARY_STORE_ID,
                store = store,
            )
            store.deleteStore(previousID)
            val promoted = replacement.copy(
                id = StoreScope.PRIMARY_STORE_ID,
                updatedAt = date,
            )
            store.saveStore(promoted)
            listOf(promoted) + remainingActiveStores.drop(1)
        } else {
            remainingActiveStores
        }

        val reordered = StoreProfileService.setDisplayOrder(
            activeAfterPromotion,
            date = date,
            store = store,
        )
        reordered.firstOrNull()
    }

    private suspend fun validateDeletion(
        profile: StoreProfile,
        store: TinyStockStore,
    ) {
        ensure(profile.isTrashed) { StoreProfileError.StoreNotInTrash }
        val remainingActiveCount = store.stores().count { it.isActive && it.id != profile.id }
        ensure(remainingActiveCount > 0) { StoreProfileError.LastActiveStore }
    }

    private suspend fun deleteData(storeID: UUID, store: TinyStockStore) {
        for (order in store.orders(storeID)) {
            store.deleteOrder(order.id)
        }
        for (movement in store.movements(storeID)) {
            store.deleteMovement(movement.id)
        }
        for (variant in store.variants(storeID)) {
            store.deleteVariant(variant.id)
        }
        for (product in store.products(storeID)) {
            store.deleteProduct(product.id)
        }
    }
}
