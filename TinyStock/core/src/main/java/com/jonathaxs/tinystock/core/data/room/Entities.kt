package com.jonathaxs.tinystock.core.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderItem
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.model.StoreProfile
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Linha da tabela `stores`. Sem chaves estrangeiras nem indices unicos extras: o app troca nomes
 * de variacoes entre si e muda `storeID` em lote.
 */
@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val imageFileName: String?,
    val isArchived: Boolean,
    val archivedAt: Instant?,
    val trashedAt: Instant?,
    val wasArchivedBeforeTrash: Boolean,
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun toModel(): StoreProfile = StoreProfile(
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
}

fun StoreProfile.toEntity(): StoreEntity = StoreEntity(
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

/** Linha da tabela `products`. */
@Entity(
    tableName = "products",
    indices = [Index(value = ["storeID"])],
)
data class ProductEntity(
    @PrimaryKey val id: UUID,
    val storeID: UUID,
    val name: String,
    val category: String,
    val quantity: Int,
    val minimumStock: Int,
    val costPrice: BigDecimal,
    val salePrice: BigDecimal,
    val imageFileName: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val sortOrder: Int,
) {
    fun toModel(): Product = Product(
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

fun Product.toEntity(): ProductEntity = ProductEntity(
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

/** Linha da tabela `variants`. Sem indice unico no nome para permitir troca de nomes em lote. */
@Entity(
    tableName = "variants",
    indices = [
        Index(value = ["storeID"]),
        Index(value = ["productID"]),
    ],
)
data class VariantEntity(
    @PrimaryKey val id: UUID,
    val storeID: UUID,
    val productID: UUID,
    val name: String,
    val isDefault: Boolean,
    val quantity: Int,
    val imageFileName: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun toModel(): ProductVariant = ProductVariant(
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
}

fun ProductVariant.toEntity(): VariantEntity = VariantEntity(
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

/** Linha da tabela `stock_movements`. */
@Entity(
    tableName = "stock_movements",
    indices = [
        Index(value = ["storeID"]),
        Index(value = ["variantID"]),
        Index(value = ["referenceID"]),
    ],
)
data class StockMovementEntity(
    @PrimaryKey val id: UUID,
    val storeID: UUID,
    val productID: UUID,
    val variantID: UUID,
    val kindRawValue: String,
    val quantityDelta: Int,
    val balanceAfter: Int,
    val note: String,
    val referenceID: UUID?,
    val reversedMovementID: UUID?,
    val reversedAt: Instant?,
    val createdAt: Instant,
) {
    fun toModel(): StockMovement = StockMovement(
        id = id,
        storeID = storeID,
        productID = productID,
        variantID = variantID,
        kindRawValue = kindRawValue,
        quantityDelta = quantityDelta,
        balanceAfter = balanceAfter,
        note = note,
        referenceID = referenceID,
        reversedMovementID = reversedMovementID,
        reversedAt = reversedAt,
        createdAt = createdAt,
    )
}

fun StockMovement.toEntity(): StockMovementEntity = StockMovementEntity(
    id = id,
    storeID = storeID,
    productID = productID,
    variantID = variantID,
    kindRawValue = kindRawValue,
    quantityDelta = quantityDelta,
    balanceAfter = balanceAfter,
    note = note,
    referenceID = referenceID,
    reversedMovementID = reversedMovementID,
    reversedAt = reversedAt,
    createdAt = createdAt,
)

/** Dados escalares do pedido na tabela `sales_orders`; as linhas ficam em `sales_order_items`. */
@Entity(
    tableName = "sales_orders",
    indices = [Index(value = ["storeID"])],
)
data class SalesOrderEntity(
    @PrimaryKey val id: UUID,
    val storeID: UUID,
    val channelRawValue: String,
    val customChannelName: String,
    val fulfillmentRawValue: String,
    val statusRawValue: String,
    val buyerName: String,
    val externalReference: String,
    val orderedAt: Instant,
    val productionDueAt: Instant?,
    val shippingDueAt: Instant?,
    val productionStartedAt: Instant?,
    val producedAt: Instant?,
    val shippedAt: Instant?,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val cancellationReason: String,
    val trackingCode: String,
    val note: String,
    val channelFeePercentage: BigDecimal,
    val channelFeeAmount: BigDecimal,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun toModel(items: List<SalesOrderItem>): SalesOrder = SalesOrder(
        id = id,
        storeID = storeID,
        channelRawValue = channelRawValue,
        customChannelName = customChannelName,
        fulfillmentRawValue = fulfillmentRawValue,
        statusRawValue = statusRawValue,
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
        items = items,
    )
}

fun SalesOrder.toEntity(): SalesOrderEntity = SalesOrderEntity(
    id = id,
    storeID = storeID,
    channelRawValue = channelRawValue,
    customChannelName = customChannelName,
    fulfillmentRawValue = fulfillmentRawValue,
    statusRawValue = statusRawValue,
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
)

/** Linha de um pedido na tabela `sales_order_items`, ligada ao pedido por [orderID]. */
@Entity(
    tableName = "sales_order_items",
    indices = [
        Index(value = ["orderID"]),
        Index(value = ["storeID"]),
    ],
)
data class SalesOrderItemEntity(
    @PrimaryKey val id: UUID,
    val orderID: UUID,
    val storeID: UUID,
    val productID: UUID,
    val variantID: UUID,
    val productName: String,
    val variantName: String,
    val unitPrice: BigDecimal,
    val unitCost: BigDecimal,
    val quantity: Int,
    val position: Int,
) {
    fun toModel(): SalesOrderItem = SalesOrderItem(
        id = id,
        storeID = storeID,
        productID = productID,
        variantID = variantID,
        productName = productName,
        variantName = variantName,
        unitPrice = unitPrice,
        unitCost = unitCost,
        quantity = quantity,
        position = position,
    )
}

fun SalesOrderItem.toEntity(orderID: UUID): SalesOrderItemEntity = SalesOrderItemEntity(
    id = id,
    orderID = orderID,
    storeID = storeID,
    productID = productID,
    variantID = variantID,
    productName = productName,
    variantName = variantName,
    unitPrice = unitPrice,
    unitCost = unitCost,
    quantity = quantity,
    position = position,
)
