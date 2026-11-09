package com.jonathaxs.tinystock.core.backup

import com.jonathaxs.tinystock.core.data.ImageFileStore
import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderItem
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.util.DomainException
import com.jonathaxs.tinystock.core.util.ensure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Exporta e restaura o backup JSON v2. Porte do `BackupManager` do iOS, sem a restauracao da v1 (anterior
 * as lojas) e sem iCloud: no Android o arquivo vai e volta pelo seletor de arquivos do sistema.
 */
object BackupManager {
    const val CURRENT_VERSION = 2

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        // Campos novos de versoes futuras do app nao impedem a leitura.
        ignoreUnknownKeys = true
        // Opcional vazio sai do arquivo, como no iOS.
        explicitNulls = false
        encodeDefaults = true
        // null em campo com valor padrao vira o padrao, como o `decodeIfPresent ?? padrao` do iOS.
        coerceInputValues = true
    }

    // region Exportacao

    /**
     * Banco inteiro (todas as lojas, ate as arquivadas e as da lixeira) em JSON. A loja aberta vai junto
     * para o outro aparelho abrir nela; se ela nao estiver ativa, vai a primeira ativa.
     */
    suspend fun export(
        selectedStoreID: UUID,
        images: ImageFileStore,
        store: TinyStockStore,
        exportedAt: Instant = Instant.now(),
    ): ByteArray {
        // Le tudo numa transacao so, para nao misturar dados de antes e depois de uma gravacao.
        val data = store.inTransaction {
            ExportData(
                stores = store.stores().sortedBy { it.id.toString() },
                products = store.allProducts().sortedBy { it.id.toString() },
                variants = store.allVariants().sortedBy { it.id.toString() },
                movements = store.allMovements().sortedBy { it.id.toString() },
                orders = store.allOrders().sortedBy { it.id.toString() },
            )
        }
        val activeStores = data.stores.filter { it.isActive }
        val selectedID = activeStores.firstOrNull { it.id == selectedStoreID }?.id
            ?: activeStores.firstOrNull()?.id
            ?: throw DomainException(BackupError.InvalidFile)

        val payload = BackupPayload(
            version = CURRENT_VERSION,
            exportedAt = exportedAt,
            products = data.products.map { it.snapshot(images.readOptional(it.imageFileName)) },
            sales = emptyList(),
            selectedStoreID = selectedID,
            stores = data.stores.map { it.snapshot(images.readOptional(it.imageFileName)) },
            variants = data.variants.map { it.snapshot(images.readOptional(it.imageFileName)) },
            stockMovements = data.movements.map { it.snapshot() },
            orders = data.orders.map { it.snapshot() },
        )
        ensure(isValidV2(payload)) { BackupError.InvalidFile }
        return json.encodeToString(BackupPayload.serializer(), payload).encodeToByteArray()
    }

    private class ExportData(
        val stores: List<StoreProfile>,
        val products: List<Product>,
        val variants: List<ProductVariant>,
        val movements: List<StockMovement>,
        val orders: List<SalesOrder>,
    )

    // endregion

    // region Leitura

    /** Le e confere o arquivo antes de qualquer mudanca no banco. */
    fun decode(data: ByteArray): BackupPayload {
        val root = try {
            json.parseToJsonElement(data.decodeToString(throwOnInvalidSequence = true).removePrefix("﻿"))
        } catch (error: Exception) {
            throw DomainException(BackupError.InvalidFile)
        }
        val version = ((root as? JsonObject)?.get("version") as? JsonPrimitive)
            ?.takeUnless { it.isString }?.intOrNull
            ?: throw DomainException(BackupError.InvalidFile)
        ensure(version == CURRENT_VERSION) { BackupError.UnsupportedVersion(version) }

        val payload = try {
            json.decodeFromJsonElement(BackupPayload.serializer(), root)
        } catch (error: Exception) {
            throw DomainException(BackupError.InvalidFile)
        }
        ensure(isValidV2(payload)) { BackupError.InvalidFile }
        return payload
    }

    // endregion

    // region Restauracao

    /**
     * Troca o banco inteiro pelo do backup, mantendo o id de cada registro. Registros que nao estao no
     * arquivo sao apagados. Tudo ou nada: se falhar, o banco e as fotos ficam como estavam.
     */
    suspend fun apply(payload: BackupPayload, images: ImageFileStore, store: TinyStockStore): BackupRestoreResult {
        ensure(payload.version == CURRENT_VERSION) { BackupError.UnsupportedVersion(payload.version) }
        ensure(isValidV2(payload)) { BackupError.InvalidFile }
        val selectedID = payload.selectedStoreID ?: throw DomainException(BackupError.InvalidFile)

        // As fotos novas sao gravadas antes, fora da transacao do banco; se algo falhar, saem de novo.
        val written = mutableListOf<String>()
        val replacedFiles: Set<String>
        try {
            suspend fun save(bytes: ByteArray?): String? = bytes?.let { images.write(it).also(written::add) }
            val storeImages = payload.stores.associate { it.id to save(it.imageData) }
            val productImages = payload.products.associate { it.id to save(it.imageData) }
            val variantImages = payload.variants.associate { it.id to save(it.imageData) }

            replacedFiles = store.inTransaction {
                val previousFiles = currentImageFiles(store)
                merge(payload, storeImages, productImages, variantImages, store)
                previousFiles
            }
        } catch (error: Throwable) {
            for (name in written) runCatching { images.delete(name) }
            throw error
        }

        // Depois de confirmar, as fotos antigas ficam sem dono. Falha aqui so deixa arquivo sobrando.
        for (name in replacedFiles - written.toSet()) {
            try {
                images.delete(name)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
        return BackupRestoreResult(selectedStoreID = selectedID, summary = payload.summary)
    }

    private suspend fun currentImageFiles(store: TinyStockStore): Set<String> = buildSet {
        store.stores().mapNotNullTo(this) { it.imageFileName }
        store.allProducts().mapNotNullTo(this) { it.imageFileName }
        store.allVariants().mapNotNullTo(this) { it.imageFileName }
    }

    private suspend fun merge(
        payload: BackupPayload,
        storeImages: Map<UUID, String?>,
        productImages: Map<UUID, String?>,
        variantImages: Map<UUID, String?>,
        store: TinyStockStore,
    ) {
        val existingProducts = store.allProducts().associateBy { it.id }

        // O formato v2 substitui o banco inteiro: some o que nao esta no arquivo.
        val orderIDs = payload.orders.map { it.id }.toSet()
        val movementIDs = payload.stockMovements.map { it.id }.toSet()
        val variantIDs = payload.variants.map { it.id }.toSet()
        val productIDs = payload.products.map { it.id }.toSet()
        val storeIDs = payload.stores.map { it.id }.toSet()
        store.allOrders().filter { it.id !in orderIDs }.forEach { store.deleteOrder(it.id) }
        store.allMovements().filter { it.id !in movementIDs }.forEach { store.deleteMovement(it.id) }
        store.allVariants().filter { it.id !in variantIDs }.forEach { store.deleteVariant(it.id) }
        existingProducts.values.filter { it.id !in productIDs }.forEach { store.deleteProduct(it.id) }
        store.stores().filter { it.id !in storeIDs }.forEach { store.deleteStore(it.id) }

        for (snapshot in payload.stores) {
            store.saveStore(snapshot.toModel(storeImages[snapshot.id]))
        }
        for (snapshot in payload.products) {
            val sortOrder = existingProducts[snapshot.id]?.sortOrder ?: 0
            store.saveProduct(snapshot.toModel(productImages[snapshot.id], sortOrder))
        }
        for (snapshot in payload.variants) {
            store.saveVariant(snapshot.toModel(variantImages[snapshot.id]))
        }
        for (snapshot in payload.stockMovements) {
            store.saveMovement(snapshot.toModel())
        }
        for (snapshot in payload.orders) {
            store.saveOrder(snapshot.toModel())
        }
    }

    // endregion

    /** Nome sugerido ao salvar, com a data em UTC, igual ao do iOS. */
    fun suggestedFilename(date: Instant = Instant.now()): String =
        "tinystock-backup-${DateTimeFormatter.ISO_LOCAL_DATE.format(date.atOffset(ZoneOffset.UTC))}.json"

    private suspend fun ImageFileStore.readOptional(fileName: String?): ByteArray? =
        fileName?.let { read(it) }
}

// region Conversao entre modelo e retrato

private fun StoreProfile.snapshot(imageData: ByteArray?) = BackupPayload.StoreSnapshot(
    archivedAt = archivedAt,
    createdAt = createdAt,
    id = id,
    imageData = imageData,
    isArchived = isArchived,
    name = name,
    sortOrder = sortOrder,
    trashedAt = trashedAt,
    updatedAt = updatedAt,
    wasArchivedBeforeTrash = wasArchivedBeforeTrash,
)

private fun BackupPayload.StoreSnapshot.toModel(imageFileName: String?) = StoreProfile(
    id = id,
    name = name,
    imageFileName = imageFileName,
    isArchived = isArchived,
    archivedAt = archivedAt,
    trashedAt = trashedAt,
    wasArchivedBeforeTrash = wasArchivedBeforeTrash,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun Product.snapshot(imageData: ByteArray?) = BackupPayload.ProductSnapshot(
    category = category,
    costPrice = costPrice,
    createdAt = createdAt,
    id = id,
    imageData = imageData,
    minimumStock = minimumStock,
    name = name,
    quantity = quantity,
    salePrice = salePrice,
    storeID = storeID,
    updatedAt = updatedAt,
)

private fun BackupPayload.ProductSnapshot.toModel(imageFileName: String?, sortOrder: Int): Product {
    val storeID = storeID ?: throw DomainException(BackupError.InvalidFile)
    return Product(
        id = id,
        storeID = storeID,
        name = name,
        category = category,
        quantity = quantity,
        minimumStock = minimumStock,
        costPrice = costPrice,
        salePrice = salePrice,
        imageFileName = imageFileName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sortOrder = sortOrder,
    )
}

private fun ProductVariant.snapshot(imageData: ByteArray?) = BackupPayload.ProductVariantSnapshot(
    createdAt = createdAt,
    id = id,
    imageData = imageData,
    isDefault = isDefault,
    name = name,
    productID = productID,
    quantity = quantity,
    storeID = storeID,
    updatedAt = updatedAt,
)

private fun BackupPayload.ProductVariantSnapshot.toModel(imageFileName: String?) = ProductVariant(
    id = id,
    storeID = storeID,
    productID = productID,
    name = name,
    isDefault = isDefault,
    quantity = quantity,
    imageFileName = imageFileName,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun StockMovement.snapshot() = BackupPayload.StockMovementSnapshot(
    balanceAfter = balanceAfter,
    createdAt = createdAt,
    id = id,
    kind = kindRawValue,
    note = note,
    productID = productID,
    quantityDelta = quantityDelta,
    referenceID = referenceID,
    reversedAt = reversedAt,
    reversedMovementID = reversedMovementID,
    storeID = storeID,
    variantID = variantID,
)

private fun BackupPayload.StockMovementSnapshot.toModel() = StockMovement(
    id = id,
    storeID = storeID,
    productID = productID,
    variantID = variantID,
    kindRawValue = kind,
    quantityDelta = quantityDelta,
    balanceAfter = balanceAfter,
    note = note,
    referenceID = referenceID,
    reversedMovementID = reversedMovementID,
    reversedAt = reversedAt,
    createdAt = createdAt,
)

private fun SalesOrder.snapshot() = BackupPayload.SalesOrderSnapshot(
    buyerName = buyerName,
    cancellationReason = cancellationReason,
    cancelledAt = cancelledAt,
    channel = channelRawValue,
    channelFeeAmount = channelFeeAmount,
    channelFeePercentage = channelFeePercentage,
    completedAt = completedAt,
    createdAt = createdAt,
    customChannelName = customChannelName,
    externalReference = externalReference,
    fulfillment = fulfillmentRawValue,
    id = id,
    items = itemList.map { item ->
        BackupPayload.SalesOrderItemSnapshot(
            id = item.id,
            position = item.position,
            productID = item.productID,
            productName = item.productName,
            quantity = item.quantity,
            storeID = item.storeID,
            unitCost = item.unitCost,
            unitPrice = item.unitPrice,
            variantID = item.variantID,
            variantName = item.variantName,
        )
    },
    note = note,
    orderedAt = orderedAt,
    producedAt = producedAt,
    productionDueAt = productionDueAt,
    productionStartedAt = productionStartedAt,
    shippedAt = shippedAt,
    shippingDueAt = shippingDueAt,
    status = statusRawValue,
    storeID = storeID,
    trackingCode = trackingCode,
    updatedAt = updatedAt,
)

private fun BackupPayload.SalesOrderSnapshot.toModel() = SalesOrder(
    id = id,
    storeID = storeID,
    channelRawValue = channel,
    customChannelName = customChannelName,
    fulfillmentRawValue = fulfillment,
    statusRawValue = status,
    buyerName = buyerName,
    externalReference = externalReference,
    orderedAt = orderedAt,
    productionDueAt = productionDueAt,
    shippingDueAt = shippingDueAt,
    productionStartedAt = productionStartedAt,
    producedAt = producedAt,
    shippedAt = shippedAt,
    completedAt = completedAt,
    cancelledAt = cancelledAt,
    cancellationReason = cancellationReason,
    trackingCode = trackingCode,
    note = note,
    channelFeePercentage = channelFeePercentage,
    channelFeeAmount = channelFeeAmount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    items = items.map { item ->
        SalesOrderItem(
            id = item.id,
            storeID = item.storeID,
            productID = item.productID,
            variantID = item.variantID,
            productName = item.productName,
            variantName = item.variantName,
            unitPrice = item.unitPrice,
            unitCost = item.unitCost,
            quantity = item.quantity,
            position = item.position,
        )
    },
)

// endregion
