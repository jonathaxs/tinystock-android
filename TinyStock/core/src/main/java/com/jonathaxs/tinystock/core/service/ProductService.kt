package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.util.DomainError
import com.jonathaxs.tinystock.core.util.ensure
import com.jonathaxs.tinystock.core.util.foldedForComparison
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class ProductError : DomainError {
    EmptyName,
    DuplicateName,
    NegativeCostPrice,
    NegativeSalePrice,
    ActiveOrders,
}

/** Regras do cadastro canonico de produtos por loja. Porte do `ProductService`. */
object ProductService {

    /**
     * Cria so os dados que pertencem ao produto. Variacoes e estoque ficam com os servicos de cada
     * responsabilidade.
     */
    suspend fun create(
        storeID: UUID,
        name: String,
        costPrice: BigDecimal = BigDecimal.ZERO,
        salePrice: BigDecimal = BigDecimal.ZERO,
        imageFileName: String? = null,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): Product = store.inTransaction {
        val cleanName = validatedName(
            name = name,
            storeID = storeID,
            excluding = null,
            store = store,
        )
        validatePrices(costPrice = costPrice, salePrice = salePrice)

        // Produtos novos entram no fim da ordem manual do catalogo.
        val nextSortOrder = (store.products(storeID).maxOfOrNull { it.sortOrder } ?: -1) + 1
        val product = Product(
            storeID = storeID,
            name = cleanName,
            costPrice = costPrice,
            salePrice = salePrice,
            imageFileName = imageFileName,
            createdAt = date,
            updatedAt = date,
            sortOrder = nextSortOrder,
        )
        store.saveProduct(product)
        product
    }

    /** Grava a ordem completa recebida da tela sem mexer nos outros dados do produto. */
    suspend fun setDisplayOrder(products: List<Product>, store: TinyStockStore) = store.inTransaction {
        for ((position, product) in products.withIndex()) {
            val current = store.product(product.id)?.takeIf { it.storeID == product.storeID } ?: continue
            if (current.sortOrder != position) {
                store.saveProduct(current.copy(sortOrder = position))
            }
        }
    }

    /** Edita os dados canonicos sem mexer na loja nem na data de criacao. Devolve a versao gravada. */
    suspend fun update(
        product: Product,
        name: String,
        costPrice: BigDecimal,
        salePrice: BigDecimal,
        imageFileName: String?,
        date: Instant = Instant.now(),
        store: TinyStockStore,
    ): Product = store.inTransaction {
        val current = store.product(product.id)?.takeIf { it.storeID == product.storeID } ?: product
        val cleanName = validatedName(
            name = name,
            storeID = current.storeID,
            excluding = current.id,
            store = store,
        )
        validatePrices(costPrice = costPrice, salePrice = salePrice)

        val updated = current.copy(
            name = cleanName,
            costPrice = costPrice,
            salePrice = salePrice,
            imageFileName = imageFileName,
            updatedAt = date,
        )
        store.saveProduct(updated)
        updated
    }

    /** Remove o produto e o estoque dele sem tocar nos retratos financeiros dos pedidos. */
    suspend fun delete(product: Product, store: TinyStockStore) = store.inTransaction {
        val current = store.product(product.id)?.takeIf { it.storeID == product.storeID } ?: product
        val productID = current.id
        val storeID = current.storeID
        val orders = store.orders(storeID)
        // Estados desconhecidos tambem bloqueiam a exclusao: nao podemos presumir que o pedido terminou.
        val hasActiveOrders = orders.any { order ->
            order.status?.isTerminal != true && order.itemList.any { it.productID == productID }
        }
        ensure(!hasActiveOrders) { ProductError.ActiveOrders }

        val variants = ProductVariantService.variants(current, store)
        val movements = store.movements(storeID).filter { it.productID == productID }

        for (movement in movements) {
            store.deleteMovement(movement.id)
        }
        for (variant in variants) {
            store.deleteVariant(variant.id)
        }
        store.deleteProduct(productID)
    }

    private suspend fun validatedName(
        name: String,
        storeID: UUID,
        excluding: UUID?,
        store: TinyStockStore,
    ): String {
        val cleanName = name.trim()
        ensure(cleanName.isNotEmpty()) { ProductError.EmptyName }

        val candidate = comparableName(cleanName)
        val isDuplicate = store.products(storeID).any {
            it.id != excluding && comparableName(it.name) == candidate
        }
        ensure(!isDuplicate) { ProductError.DuplicateName }
        return cleanName
    }

    private fun validatePrices(costPrice: BigDecimal, salePrice: BigDecimal) {
        ensure(costPrice >= BigDecimal.ZERO) { ProductError.NegativeCostPrice }
        ensure(salePrice >= BigDecimal.ZERO) { ProductError.NegativeSalePrice }
    }

    private fun comparableName(name: String): String = name.foldedForComparison()
}
