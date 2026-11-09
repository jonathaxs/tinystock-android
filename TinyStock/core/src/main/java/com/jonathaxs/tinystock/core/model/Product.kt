package com.jonathaxs.tinystock.core.model

import com.jonathaxs.tinystock.core.util.containsFolded
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Um item do catalogo de uma loja. Porte do `Product` do iOS.
 *
 * O saldo de verdade fica nas variacoes ([ProductVariant.quantity]). [category], [quantity] e
 * [minimumStock] sao campos antigos, guardados so para o backup continuar igual ao do iOS.
 */
data class Product(
    val id: UUID = UUID.randomUUID(),
    val storeID: UUID = StoreScope.UNASSIGNED_STORE_ID,
    val name: String = "",
    val category: String = "",
    val quantity: Int = 0,
    val minimumStock: Int = 0,
    /** Custo unitario. BigDecimal evita erro de arredondamento com dinheiro. */
    val costPrice: BigDecimal = BigDecimal.ZERO,
    val salePrice: BigDecimal = BigDecimal.ZERO,
    val imageFileName: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    /** Posicao escolhida no catalogo. Empates seguem a ordem alfabetica. */
    val sortOrder: Int = 0,
) {
    /** Estoque (campo antigo) no minimo configurado ou abaixo dele. */
    val isLowStock: Boolean get() = minimumStock > 0 && quantity <= minimumStock

    /** Lucro unitario estimado. */
    val unitProfit: BigDecimal get() = salePrice - costPrice

    /** Quanto o estoque (campo antigo) renderia se tudo fosse vendido. */
    val potentialProfit: BigDecimal get() = unitProfit * quantity.toBigDecimal()

    /** Busca no nome e na categoria antiga. Texto vazio aceita tudo. */
    fun matches(searchText: String): Boolean {
        val query = searchText.trim()
        if (query.isEmpty()) return true
        return name.containsFolded(query) || category.containsFolded(query)
    }

    /** Busca no nome do produto e no das variacoes visiveis dele. Texto vazio aceita tudo. */
    fun matches(searchText: String, variants: List<ProductVariant>): Boolean {
        val query = searchText.trim()
        return query.isEmpty() || name.containsFolded(query) || variants.any {
            it.belongsTo(this) && !it.isDefault && it.name.containsFolded(query)
        }
    }
}
