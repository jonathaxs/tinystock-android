package com.jonathaxs.tinystock.core.model

import java.time.Instant
import java.util.UUID

enum class StockMovementKind(val rawValue: String) {
    InitialStock("initialStock"),
    Entry("entry"),
    Adjustment("adjustment"),
    Reversal("reversal"),
    OrderWithdrawal("orderWithdrawal");

    companion object {
        fun fromRawValue(raw: String): StockMovementKind? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** Registro auditavel de cada mudanca de saldo de uma variacao. Porte do `StockMovement`. */
data class StockMovement(
    val id: UUID = UUID.randomUUID(),
    val storeID: UUID = StoreScope.UNASSIGNED_STORE_ID,
    val productID: UUID = UUID.randomUUID(),
    val variantID: UUID = UUID.randomUUID(),
    val kindRawValue: String = StockMovementKind.Adjustment.rawValue,
    /** Diferenca aplicada ao saldo: entrada positiva, retirada negativa. */
    val quantityDelta: Int = 0,
    /** Saldo da variacao logo depois desta movimentacao. */
    val balanceAfter: Int = 0,
    val note: String = "",
    /** Pedido ou outra operacao que originou a movimentacao. */
    val referenceID: UUID? = null,
    /** Preenchido so na movimentacao que desfaz outra. */
    val reversedMovementID: UUID? = null,
    /** Quando esta movimentacao foi desfeita. */
    val reversedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
) {
    /** Valor desconhecido vira ajuste, como no iOS. */
    val kind: StockMovementKind
        get() = StockMovementKind.fromRawValue(kindRawValue) ?: StockMovementKind.Adjustment

    fun belongsTo(variant: ProductVariant, product: Product): Boolean =
        storeID == product.storeID &&
            productID == product.id &&
            variantID == variant.id &&
            variant.belongsTo(product)
}
