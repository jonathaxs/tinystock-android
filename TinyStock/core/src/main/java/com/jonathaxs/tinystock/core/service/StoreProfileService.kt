package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.StoreLifecycleState
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.model.StoreScope
import com.jonathaxs.tinystock.core.util.DomainError
import com.jonathaxs.tinystock.core.util.ensure
import com.jonathaxs.tinystock.core.util.foldedForComparison
import java.time.Instant
import java.util.UUID

enum class StoreProfileError : DomainError {
    EmptyName,
    DuplicateName,
    LastActiveStore,
    ArchivedStore,
    TrashedStore,
    StoreNotInTrash,
}

/**
 * Criacao, edicao, arquivamento e ordenacao das lojas. Porte do `StoreProfileService` do iOS, sem as
 * rotinas de reconciliacao do CloudKit.
 */
object StoreProfileService {

    /** Cria uma loja depois de limpar e validar o nome. */
    suspend fun create(
        name: String,
        imageFileName: String? = null,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): StoreProfile = store.inTransaction {
        val cleanName = sanitized(name)
        ensure(cleanName.isNotEmpty()) { StoreProfileError.EmptyName }
        ensure(!contains(cleanName, store = store)) { StoreProfileError.DuplicateName }

        val existingStores = store.stores()
        val created = StoreProfile(
            name = cleanName,
            imageFileName = imageFileName,
            sortOrder = nextSortOrder(existingStores),
            createdAt = date,
            updatedAt = date,
        )
        store.saveStore(created)
        created
    }

    /**
     * Devolve uma loja ativa existente ou garante a loja inicial uma unica vez. [name] e o nome padrao ja
     * traduzido pela UI ("Minha loja"), usado so se a loja for criada agora.
     */
    suspend fun ensureDefaultStore(
        name: String,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): StoreProfile = store.inTransaction {
        val stores = store.stores()
        orderedForDisplay(stores.filter { it.isActive }).firstOrNull()?.let {
            return@inTransaction it
        }

        // Um backup inconsistente pode chegar so com lojas arquivadas ou na lixeira; reativar preserva os dados.
        orderedArchivedForDisplay(stores).firstOrNull()?.let { archived ->
            val reactivated = archived.copy(
                isArchived = false,
                archivedAt = null,
                updatedAt = date,
            )
            store.saveStore(reactivated)
            return@inTransaction reactivated
        }

        orderedTrashedForDisplay(stores).firstOrNull()?.let { trashed ->
            val reactivated = trashed.copy(
                isArchived = false,
                archivedAt = null,
                trashedAt = null,
                wasArchivedBeforeTrash = false,
                updatedAt = date,
            )
            store.saveStore(reactivated)
            return@inTransaction reactivated
        }

        val created = StoreProfile(
            id = StoreScope.PRIMARY_STORE_ID,
            name = sanitized(name),
            createdAt = date,
            updatedAt = date,
        )
        store.saveStore(created)
        created
    }

    /** Atualiza nome e imagem sem mudar a data original de criacao. */
    suspend fun update(
        profile: StoreProfile,
        name: String,
        imageFileName: String?,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): StoreProfile = store.inTransaction {
        val current = store.store(profile.id) ?: profile
        ensure(!current.isTrashed) { StoreProfileError.TrashedStore }
        val cleanName = sanitized(name)
        ensure(cleanName.isNotEmpty()) { StoreProfileError.EmptyName }
        ensure(!contains(cleanName, excluding = current.id, store = store)) {
            StoreProfileError.DuplicateName
        }

        val updated = current.copy(
            name = cleanName,
            imageFileName = imageFileName,
            updatedAt = date,
        )
        store.saveStore(updated)
        updated
    }

    /** Arquiva sem apagar o historico. A ultima loja ativa precisa continuar disponivel. */
    suspend fun archive(
        profile: StoreProfile,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): StoreProfile = store.inTransaction {
        val current = store.store(profile.id) ?: profile
        ensure(!current.isTrashed) { StoreProfileError.TrashedStore }
        if (current.isArchived) return@inTransaction current

        val activeCount = store.stores().count { it.isActive }
        ensure(activeCount > 1) { StoreProfileError.LastActiveStore }

        val archived = current.copy(
            isArchived = true,
            archivedAt = date,
            updatedAt = date,
        )
        store.saveStore(archived)
        archived
    }

    /** Traz uma loja arquivada de volta sem mexer nos produtos nem no historico. */
    suspend fun restore(
        profile: StoreProfile,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): StoreProfile = store.inTransaction {
        val current = store.store(profile.id) ?: profile
        if (!current.isArchived || current.isTrashed) return@inTransaction current

        val restored = current.copy(
            isArchived = false,
            archivedAt = null,
            updatedAt = date,
        )
        store.saveStore(restored)
        restored
    }

    /** Ordena com desempate fixo para bancos anteriores ao campo de posicao. */
    fun orderedForDisplay(stores: List<StoreProfile>): List<StoreProfile> =
        stores.sortedWith(
            compareBy<StoreProfile> { it.sortOrder }
                .thenBy { it.createdAt }
                .thenBy { it.id.toString() },
        )

    /** Arquivadas e itens da lixeira usam a data da acao, sem depender da ordem manual. */
    fun orderedArchivedForDisplay(stores: List<StoreProfile>): List<StoreProfile> =
        stores.filter { it.lifecycleState == StoreLifecycleState.Archived }
            .sortedWith(
                compareByDescending<StoreProfile> { it.archivedAt ?: it.updatedAt }
                    .thenBy { it.id.toString() },
            )

    fun orderedTrashedForDisplay(stores: List<StoreProfile>): List<StoreProfile> =
        stores.filter { it.isTrashed }
            .sortedWith(
                compareByDescending<StoreProfile> { it.trashedAt ?: it.updatedAt }
                    .thenBy { it.id.toString() },
            )

    /** Grava a ordem completa recebida da tela sem mudar outros dados da loja. */
    suspend fun setDisplayOrder(
        stores: List<StoreProfile>,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): List<StoreProfile> = store.inTransaction {
        val currentStores = stores.map { store.store(it.id) ?: it }
        ensure(currentStores.all { it.isActive }) { StoreProfileError.ArchivedStore }

        currentStores.mapIndexed { position, current ->
            if (current.sortOrder != position) {
                val updated = current.copy(sortOrder = position, updatedAt = date)
                store.saveStore(updated)
                updated
            } else {
                current
            }
        }
    }

    /** Troca o id da loja em todos os registros ligados a ela. */
    internal suspend fun remapStoreScope(
        from: UUID,
        to: UUID,
        store: TinyStockStore,
    ) = store.inTransaction {
        for (product in store.products(from)) {
            store.saveProduct(product.copy(storeID = to))
        }
        for (variant in store.variants(from)) {
            store.saveVariant(variant.copy(storeID = to))
        }
        for (movement in store.movements(from)) {
            store.saveMovement(movement.copy(storeID = to))
        }
        for (order in store.orders(from)) {
            store.saveOrder(
                order.copy(
                    storeID = to,
                    items = order.items.map { item ->
                        if (item.storeID == from) item.copy(storeID = to) else item
                    },
                ),
            )
        }
    }

    private fun sanitized(name: String): String = name.trim()

    private fun nextSortOrder(stores: List<StoreProfile>): Int {
        val last = stores.maxOfOrNull { it.sortOrder }
        return if (last != null && last < Int.MAX_VALUE) last + 1 else stores.size
    }

    private suspend fun contains(
        name: String,
        excluding: UUID? = null,
        store: TinyStockStore,
    ): Boolean {
        val candidate = comparableName(name)
        return store.stores().any { it.id != excluding && comparableName(it.name) == candidate }
    }

    private fun comparableName(name: String): String = sanitized(name).foldedForComparison()
}
