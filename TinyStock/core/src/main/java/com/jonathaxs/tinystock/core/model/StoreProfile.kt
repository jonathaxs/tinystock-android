package com.jonathaxs.tinystock.core.model

import java.time.Instant
import java.util.UUID

/** Identificadores reservados de loja. Mesmos valores do iOS, por causa do backup. */
object StoreScope {
    /** Objetos criados fora de uma loja, como dados de teste antigos. */
    val UNASSIGNED_STORE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    /** Loja criada na primeira abertura. O UUID fixo permite juntar copias vindas de outro aparelho. */
    val PRIMARY_STORE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
}

enum class StoreLifecycleState(val rawValue: String) {
    Active("active"),
    Archived("archived"),
    Trashed("trashed"),
}

/**
 * Uma loja independente. Produtos, pedidos e relatorios usam o [id] dela para ficar isolados.
 * Porte do `StoreProfile` do iOS. A foto fica num arquivo do app; aqui so o nome do arquivo.
 */
data class StoreProfile(
    val id: UUID = UUID.randomUUID(),
    val name: String = "",
    val imageFileName: String? = null,
    /** Lojas arquivadas guardam o historico, mas saem do uso do dia a dia. */
    val isArchived: Boolean = false,
    val archivedAt: Instant? = null,
    /** Entrada na lixeira; a exclusao definitiva acontece 30 dias depois. */
    val trashedAt: Instant? = null,
    /** Permite restaurar da lixeira para o estado de antes. */
    val wasArchivedBeforeTrash: Boolean = false,
    /** Posicao escolhida pelo usuario nas listas e no seletor. */
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    val lifecycleState: StoreLifecycleState
        get() = when {
            trashedAt != null -> StoreLifecycleState.Trashed
            isArchived -> StoreLifecycleState.Archived
            else -> StoreLifecycleState.Active
        }

    val isActive: Boolean get() = lifecycleState == StoreLifecycleState.Active
    val isTrashed: Boolean get() = lifecycleState == StoreLifecycleState.Trashed
}
