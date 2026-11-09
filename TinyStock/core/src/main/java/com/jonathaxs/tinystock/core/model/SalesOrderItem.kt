package com.jonathaxs.tinystock.core.model

import com.jonathaxs.tinystock.core.util.DomainError
import com.jonathaxs.tinystock.core.util.ensure
import java.math.BigDecimal
import java.util.UUID

enum class SalesOrderItemError : DomainError {
    InvalidQuantity,
    VariantMismatch,
    InvalidPrices,
}

/**
 * Linha de um pedido. Guarda nome e precos do momento da venda: renomear ou apagar o produto nao muda
 * o pedido. Porte do `SalesOrderItem`.
 */
data class SalesOrderItem(
    val id: UUID = UUID.randomUUID(),
    val storeID: UUID = StoreScope.UNASSIGNED_STORE_ID,
    val productID: UUID = UUID.randomUUID(),
    val variantID: UUID = UUID.randomUUID(),
    val productName: String = "",
    val variantName: String = "",
    val unitPrice: BigDecimal = BigDecimal.ZERO,
    val unitCost: BigDecimal = BigDecimal.ZERO,
    val quantity: Int = 0,
    /** Ordem da linha dentro do pedido. */
    val position: Int = 0,
) {
    val subtotal: BigDecimal get() = unitPrice * quantity.toBigDecimal()
    val subtotalCost: BigDecimal get() = unitCost * quantity.toBigDecimal()
    val grossProfit: BigDecimal get() = subtotal - subtotalCost

    companion object {
        /**
         * Retrato do produto e da variacao no momento da venda. Nao reserva nem baixa estoque; isso e
         * do servico de pedidos.
         */
        fun snapshot(product: Product, variant: ProductVariant, quantity: Int, position: Int = 0): SalesOrderItem {
            ensure(variant.belongsTo(product)) { SalesOrderItemError.VariantMismatch }
            ensure(quantity > 0) { SalesOrderItemError.InvalidQuantity }
            ensure(product.salePrice.signum() >= 0 && product.costPrice.signum() >= 0) {
                SalesOrderItemError.InvalidPrices
            }
            return SalesOrderItem(
                storeID = product.storeID,
                productID = product.id,
                variantID = variant.id,
                productName = product.name,
                variantName = if (variant.isDefault) "" else variant.name,
                unitPrice = product.salePrice,
                unitCost = product.costPrice,
                quantity = quantity,
                position = position,
            )
        }
    }
}
