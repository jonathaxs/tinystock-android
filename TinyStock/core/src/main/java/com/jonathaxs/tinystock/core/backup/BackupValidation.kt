package com.jonathaxs.tinystock.core.backup

import com.jonathaxs.tinystock.core.model.StoreScope
import java.util.UUID

/**
 * Coerencia do backup v2, porte do `isValidV2` do iOS. As conferencias de data finita e de Decimal NaN do
 * iOS nao entram: `Instant` e `BigDecimal` nao representam esses valores e a leitura ja recusa.
 */
internal fun isValidV2(payload: BackupPayload): Boolean {
    if (payload.version != BackupManager.CURRENT_VERSION || payload.sales.isNotEmpty() || payload.stores.isEmpty()) {
        return false
    }

    val storeIDs = payload.stores.map { it.id }.toSet()
    val activeIDs = payload.stores.filter { !it.isArchived && it.trashedAt == null }.map { it.id }.toSet()
    val selectedStoreID = payload.selectedStoreID ?: return false
    if (!hasUniqueIDs(payload.stores.map { it.id }) ||
        activeIDs.isEmpty() ||
        selectedStoreID !in activeIDs ||
        StoreScope.UNASSIGNED_STORE_ID in storeIDs
    ) {
        return false
    }
    val storesAreCoherent = payload.stores.all {
        it.sortOrder >= 0 &&
            // Lixeira so a partir de arquivada; a marca de "estava arquivada" so existe na lixeira.
            (it.trashedAt == null || it.isArchived) &&
            (it.trashedAt != null || !it.wasArchivedBeforeTrash)
    }
    if (!storesAreCoherent) return false

    if (!hasUniqueIDs(payload.products.map { it.id })) return false
    val productsByID = payload.products.associateBy { it.id }
    if (!payload.products.all { it.storeID != null && it.storeID in storeIDs }) return false

    if (!hasUniqueIDs(payload.variants.map { it.id })) return false
    val variantsByID = payload.variants.associateBy { it.id }
    if (!payload.variants.all { it.quantity >= 0 && productsByID[it.productID]?.storeID == it.storeID }) {
        return false
    }
    // A variacao interna e a unica do produto.
    val defaultsAreCoherent = payload.variants.groupBy { it.productID }.values.all { variants ->
        val defaults = variants.count { it.isDefault }
        defaults == 0 || (defaults == 1 && variants.size == 1)
    }
    if (!defaultsAreCoherent) return false

    val movementIDs = payload.stockMovements.map { it.id }.toSet()
    if (movementIDs.size != payload.stockMovements.size) return false
    val movementsAreCoherent = payload.stockMovements.all { movement ->
        val variant = variantsByID[movement.variantID]
        productsByID[movement.productID]?.storeID == movement.storeID &&
            variant?.productID == movement.productID &&
            variant.storeID == movement.storeID &&
            movement.reversedMovementID.let { it == null || (it != movement.id && it in movementIDs) }
    }
    if (!movementsAreCoherent) return false

    return hasUniqueIDs(payload.orders.map { it.id }) &&
        hasUniqueIDs(payload.orders.flatMap { order -> order.items.map { it.id } }) &&
        payload.orders.all { order ->
            order.storeID in storeIDs &&
                order.items.all { it.storeID == order.storeID && it.quantity > 0 }
        }
}

private fun hasUniqueIDs(values: List<UUID>): Boolean = values.toSet().size == values.size
