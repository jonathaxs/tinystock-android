package com.jonathaxs.tinystock.core.model

import java.time.Instant
import java.util.UUID

/**
 * Opcao vendavel de um produto (Azul, Tamanho M...) com estoque proprio. Porte do `ProductVariant`.
 * Produto sem opcoes tem uma variacao interna ([isDefault]) que nao aparece na tela.
 */
data class ProductVariant(
    val id: UUID = UUID.randomUUID(),
    /** Loja herdada do produto, para consultar por loja. */
    val storeID: UUID = StoreScope.UNASSIGNED_STORE_ID,
    val productID: UUID = UUID.randomUUID(),
    val name: String = "",
    val isDefault: Boolean = false,
    /** Saldo atual. So os servicos de estoque e de pedidos mudam este valor. */
    val quantity: Int = 0,
    val imageFileName: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    /** Confere produto e loja juntos, para nao misturar dados de lojas diferentes. */
    fun belongsTo(product: Product): Boolean = productID == product.id && storeID == product.storeID

    companion object {
        /** Nome gravado so na variacao interna dos produtos sem opcoes. */
        const val INTERNAL_DEFAULT_NAME = "__tinystock_default__"
    }
}
