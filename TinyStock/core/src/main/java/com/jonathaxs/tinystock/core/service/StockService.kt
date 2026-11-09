package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.model.StockMovementKind
import com.jonathaxs.tinystock.core.util.DomainError
import com.jonathaxs.tinystock.core.util.DomainException
import com.jonathaxs.tinystock.core.util.addOrNull
import com.jonathaxs.tinystock.core.util.ensure
import com.jonathaxs.tinystock.core.util.negateOrNull
import com.jonathaxs.tinystock.core.util.subtractOrNull
import java.time.Instant
import java.util.UUID

enum class StockError : DomainError {
    InvalidQuantity,
    NegativeBalance,
    NoChange,
    QuantityOverflow,
    ProductMismatch,
    MovementMismatch,
    AlreadyReversed,
    ReversalNotAllowed,
    OrderManagedMovement,
}

/**
 * Entradas, ajustes e reversoes de estoque, sempre com movimentacao auditavel. Porte do `StockService`.
 *
 * Diferenca do iOS: os modelos sao imutaveis. O servico rele do armazenamento a versao atual da variacao
 * e da movimentacao pelo id (o objeto recebido pode estar velho), grava as novas versoes e devolve a
 * movimentacao criada. Quem precisa do saldo novo le de novo do armazenamento.
 */
object StockService {

    /** Entrada pela tela do produto, numa variacao existente ou numa opcao nova. */
    suspend fun registerEntry(
        quantity: Int,
        product: Product,
        variantID: UUID?,
        newVariantName: String = "",
        note: String = "",
        store: TinyStockStore,
    ): StockMovement = store.inTransaction {
        ensure(quantity > 0) { StockError.InvalidQuantity }
        val variant = if (variantID != null) {
            ProductVariantService.variants(product, store).firstOrNull { it.id == variantID }
                ?: throw DomainException(StockError.ProductMismatch)
        } else {
            // A opcao nasce zerada: o recebimento entra como entrada, nao como saldo inicial.
            ProductVariantService.create(product, newVariantName, store = store)
        }
        registerEntry(quantity, variant, product, note = note, store = store)
    }

    /** Soma unidades recebidas ao saldo atual. */
    suspend fun registerEntry(
        quantity: Int,
        variant: ProductVariant,
        product: Product,
        note: String = "",
        referenceID: UUID? = null,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): StockMovement {
        ensure(quantity > 0) { StockError.InvalidQuantity }
        return apply(quantity, StockMovementKind.Entry, variant, product, note, referenceID, null, date, store)
    }

    /** Define o saldo contado pelo comerciante e registra so a diferenca. */
    suspend fun registerAdjustment(
        newQuantity: Int,
        variant: ProductVariant,
        product: Product,
        note: String = "",
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): StockMovement = store.inTransaction {
        ensure(newQuantity >= 0) { StockError.NegativeBalance }
        val current = currentVariant(variant, product, store)
        ensure(newQuantity != current.quantity) { StockError.NoChange }
        val difference = newQuantity.subtractOrNull(current.quantity)
            ?: throw DomainException(StockError.QuantityOverflow)
        apply(difference, StockMovementKind.Adjustment, current, product, note, null, null, date, store)
    }

    /** Desfaz o efeito de uma movimentacao sem apagar o historico original. */
    suspend fun reverse(
        movement: StockMovement,
        variant: ProductVariant,
        product: Product,
        note: String = "",
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): StockMovement = store.inTransaction {
        val current = store.movement(movement.id) ?: throw DomainException(StockError.MovementMismatch)
        ensure(current.kind != StockMovementKind.Reversal) { StockError.ReversalNotAllowed }
        ensure(current.kind != StockMovementKind.OrderWithdrawal) { StockError.OrderManagedMovement }
        ensure(current.reversedAt == null) { StockError.AlreadyReversed }
        ensure(current.belongsTo(variant, product)) { StockError.MovementMismatch }
        val opposite = current.quantityDelta.negateOrNull() ?: throw DomainException(StockError.QuantityOverflow)

        val reversal = apply(
            opposite, StockMovementKind.Reversal, variant, product, note,
            referenceID = current.referenceID, reversedMovementID = current.id, date = date, store = store,
        )
        store.saveMovement(current.copy(reversedAt = date))
        reversal
    }

    /** Historico da variacao, mais recente primeiro, sempre dentro do escopo completo. */
    suspend fun movements(variant: ProductVariant, product: Product, store: TinyStockStore): List<StockMovement> {
        ensure(variant.belongsTo(product)) { StockError.ProductMismatch }
        return store.movements(product.storeID)
            .filter { it.productID == product.id && it.variantID == variant.id }
            .sortedByDescending { it.createdAt }
    }

    // region Uso interno do :core

    /** So o servico de pedidos registra a baixa ligada ao atendimento. */
    internal suspend fun registerOrderWithdrawal(
        quantity: Int,
        variant: ProductVariant,
        product: Product,
        orderID: UUID,
        date: Instant,
        store: TinyStockStore,
    ): StockMovement {
        ensure(quantity > 0) { StockError.InvalidQuantity }
        return apply(-quantity, StockMovementKind.OrderWithdrawal, variant, product, "", orderID, null, date, store)
    }

    /** So o cancelamento coordenado devolve ao estoque uma baixa de pedido. */
    internal suspend fun reverseOrderWithdrawal(
        movement: StockMovement,
        orderID: UUID,
        variant: ProductVariant,
        product: Product,
        note: String,
        date: Instant,
        store: TinyStockStore,
    ): StockMovement = store.inTransaction {
        val current = store.movement(movement.id) ?: throw DomainException(StockError.MovementMismatch)
        ensure(current.kind == StockMovementKind.OrderWithdrawal && current.referenceID == orderID) {
            StockError.OrderManagedMovement
        }
        ensure(current.reversedAt == null) { StockError.AlreadyReversed }
        ensure(current.belongsTo(variant, product)) { StockError.MovementMismatch }
        val opposite = current.quantityDelta.negateOrNull() ?: throw DomainException(StockError.QuantityOverflow)

        val reversal = apply(
            opposite, StockMovementKind.Reversal, variant, product, note,
            referenceID = orderID, reversedMovementID = current.id, date = date, store = store,
        )
        store.saveMovement(current.copy(reversedAt = date))
        reversal
    }

    /** Saldo informado junto com a criacao de uma variacao. */
    internal suspend fun registerInitialStock(
        quantity: Int,
        variant: ProductVariant,
        product: Product,
        date: Instant,
        store: TinyStockStore,
    ): StockMovement {
        ensure(quantity > 0) { StockError.InvalidQuantity }
        return apply(quantity, StockMovementKind.InitialStock, variant, product, "", null, null, date, store)
    }

    // endregion

    /** Versao atual da variacao no armazenamento, conferindo produto e loja. */
    private suspend fun currentVariant(variant: ProductVariant, product: Product, store: TinyStockStore): ProductVariant {
        ensure(variant.belongsTo(product)) { StockError.ProductMismatch }
        return store.variant(variant.id)?.takeIf { it.belongsTo(product) }
            ?: throw DomainException(StockError.ProductMismatch)
    }

    private suspend fun apply(
        delta: Int,
        kind: StockMovementKind,
        variant: ProductVariant,
        product: Product,
        note: String,
        referenceID: UUID?,
        reversedMovementID: UUID?,
        date: Instant,
        store: TinyStockStore,
    ): StockMovement = store.inTransaction {
        val current = currentVariant(variant, product, store)
        ensure(delta != 0) { StockError.NoChange }
        val balance = current.quantity.addOrNull(delta) ?: throw DomainException(StockError.QuantityOverflow)
        ensure(balance >= 0) { StockError.NegativeBalance }

        val movement = StockMovement(
            storeID = product.storeID,
            productID = product.id,
            variantID = current.id,
            kindRawValue = kind.rawValue,
            quantityDelta = delta,
            balanceAfter = balance,
            note = note.trim(),
            referenceID = referenceID,
            reversedMovementID = reversedMovementID,
            createdAt = date,
        )
        store.saveVariant(current.copy(quantity = balance, updatedAt = date))
        store.saveMovement(movement)
        movement
    }
}
