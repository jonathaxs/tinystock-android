package com.jonathaxs.tinystock.core.data

import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.model.StoreProfile
import java.util.UUID

/**
 * Armazenamento em memoria para testes e previas. Nao e seguro entre threads.
 *
 * A transacao copia as tabelas ao comecar e devolve a copia se der erro, imitando o `rollback()` do
 * iOS. Os modelos sao imutaveis, entao a copia rasa basta.
 */
class InMemoryTinyStockStore : TinyStockStore {

    private var stores = LinkedHashMap<UUID, StoreProfile>()
    private var products = LinkedHashMap<UUID, Product>()
    private var variants = LinkedHashMap<UUID, ProductVariant>()
    private var movements = LinkedHashMap<UUID, StockMovement>()
    private var orders = LinkedHashMap<UUID, SalesOrder>()
    private var transactionDepth = 0

    override suspend fun <R> inTransaction(block: suspend () -> R): R {
        if (transactionDepth > 0) {
            transactionDepth++
            try {
                return block()
            } finally {
                transactionDepth--
            }
        }
        val backup = Snapshot(
            LinkedHashMap(stores), LinkedHashMap(products), LinkedHashMap(variants),
            LinkedHashMap(movements), LinkedHashMap(orders),
        )
        transactionDepth = 1
        try {
            return block()
        } catch (error: Throwable) {
            stores = backup.stores
            products = backup.products
            variants = backup.variants
            movements = backup.movements
            orders = backup.orders
            throw error
        } finally {
            transactionDepth = 0
        }
    }

    override suspend fun stores(): List<StoreProfile> = stores.values.toList()
    override suspend fun store(id: UUID): StoreProfile? = stores[id]
    override suspend fun saveStore(store: StoreProfile) { stores[store.id] = store }
    override suspend fun deleteStore(id: UUID) { stores.remove(id) }

    override suspend fun products(storeID: UUID): List<Product> = products.values.filter { it.storeID == storeID }
    override suspend fun product(id: UUID): Product? = products[id]
    override suspend fun saveProduct(product: Product) { products[product.id] = product }
    override suspend fun deleteProduct(id: UUID) { products.remove(id) }

    override suspend fun variants(storeID: UUID): List<ProductVariant> =
        variants.values.filter { it.storeID == storeID }
    override suspend fun variant(id: UUID): ProductVariant? = variants[id]
    override suspend fun saveVariant(variant: ProductVariant) { variants[variant.id] = variant }
    override suspend fun deleteVariant(id: UUID) { variants.remove(id) }

    override suspend fun movements(storeID: UUID): List<StockMovement> =
        movements.values.filter { it.storeID == storeID }
    override suspend fun movement(id: UUID): StockMovement? = movements[id]
    override suspend fun saveMovement(movement: StockMovement) { movements[movement.id] = movement }
    override suspend fun deleteMovement(id: UUID) { movements.remove(id) }

    override suspend fun orders(storeID: UUID): List<SalesOrder> = orders.values.filter { it.storeID == storeID }
    override suspend fun order(id: UUID): SalesOrder? = orders[id]
    override suspend fun saveOrder(order: SalesOrder) { orders[order.id] = order }
    override suspend fun deleteOrder(id: UUID) { orders.remove(id) }

    private class Snapshot(
        val stores: LinkedHashMap<UUID, StoreProfile>,
        val products: LinkedHashMap<UUID, Product>,
        val variants: LinkedHashMap<UUID, ProductVariant>,
        val movements: LinkedHashMap<UUID, StockMovement>,
        val orders: LinkedHashMap<UUID, SalesOrder>,
    )
}
