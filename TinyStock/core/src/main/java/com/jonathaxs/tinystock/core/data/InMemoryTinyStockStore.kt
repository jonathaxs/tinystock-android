package com.jonathaxs.tinystock.core.data

import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.model.StoreProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Armazenamento em memoria para testes e previas. Nao e seguro entre threads.
 *
 * A transacao copia as tabelas ao comecar e devolve a copia se der erro, imitando o `rollback()` do
 * iOS. Os modelos sao imutaveis, entao a copia rasa basta. Os `observe` so emitem depois que a
 * transacao mais externa confirma.
 */
class InMemoryTinyStockStore : TinyStockStore {

    private var stores = LinkedHashMap<UUID, StoreProfile>()
    private var products = LinkedHashMap<UUID, Product>()
    private var variants = LinkedHashMap<UUID, ProductVariant>()
    private var movements = LinkedHashMap<UUID, StockMovement>()
    private var orders = LinkedHashMap<UUID, SalesOrder>()
    private var transactionDepth = 0

    /** Sobe a cada mudanca confirmada; os `observe` releem a tabela quando ela muda. */
    private val version = MutableStateFlow(0L)
    private var pendingChange = false

    private fun changed() {
        if (transactionDepth > 0) pendingChange = true else version.value++
    }

    private fun <T> observe(query: () -> List<T>): Flow<List<T>> = version.map { query() }.distinctUntilChanged()

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
        pendingChange = false
        try {
            val result = block()
            transactionDepth = 0
            if (pendingChange) version.value++
            return result
        } catch (error: Throwable) {
            stores = backup.stores
            products = backup.products
            variants = backup.variants
            movements = backup.movements
            orders = backup.orders
            throw error
        } finally {
            transactionDepth = 0
            pendingChange = false
        }
    }

    override suspend fun stores(): List<StoreProfile> = stores.values.toList()
    override suspend fun store(id: UUID): StoreProfile? = stores[id]
    override suspend fun saveStore(store: StoreProfile) { stores[store.id] = store; changed() }
    override suspend fun deleteStore(id: UUID) { stores.remove(id); changed() }

    override suspend fun products(storeID: UUID): List<Product> = products.values.filter { it.storeID == storeID }
    override suspend fun product(id: UUID): Product? = products[id]
    override suspend fun saveProduct(product: Product) { products[product.id] = product; changed() }
    override suspend fun deleteProduct(id: UUID) { products.remove(id); changed() }

    override suspend fun variants(storeID: UUID): List<ProductVariant> =
        variants.values.filter { it.storeID == storeID }
    override suspend fun variant(id: UUID): ProductVariant? = variants[id]
    override suspend fun saveVariant(variant: ProductVariant) { variants[variant.id] = variant; changed() }
    override suspend fun deleteVariant(id: UUID) { variants.remove(id); changed() }

    override suspend fun movements(storeID: UUID): List<StockMovement> =
        movements.values.filter { it.storeID == storeID }
    override suspend fun movement(id: UUID): StockMovement? = movements[id]
    override suspend fun saveMovement(movement: StockMovement) { movements[movement.id] = movement; changed() }
    override suspend fun deleteMovement(id: UUID) { movements.remove(id); changed() }

    override suspend fun orders(storeID: UUID): List<SalesOrder> = orders.values.filter { it.storeID == storeID }
    override suspend fun order(id: UUID): SalesOrder? = orders[id]
    override suspend fun saveOrder(order: SalesOrder) { orders[order.id] = order; changed() }
    override suspend fun deleteOrder(id: UUID) { orders.remove(id); changed() }

    override fun observeStores(): Flow<List<StoreProfile>> = observe { stores.values.toList() }
    override fun observeProducts(storeID: UUID): Flow<List<Product>> =
        observe { products.values.filter { it.storeID == storeID } }
    override fun observeVariants(storeID: UUID): Flow<List<ProductVariant>> =
        observe { variants.values.filter { it.storeID == storeID } }
    override fun observeMovements(storeID: UUID): Flow<List<StockMovement>> =
        observe { movements.values.filter { it.storeID == storeID } }
    override fun observeOrders(storeID: UUID): Flow<List<SalesOrder>> =
        observe { orders.values.filter { it.storeID == storeID } }

    private class Snapshot(
        val stores: LinkedHashMap<UUID, StoreProfile>,
        val products: LinkedHashMap<UUID, Product>,
        val variants: LinkedHashMap<UUID, ProductVariant>,
        val movements: LinkedHashMap<UUID, StockMovement>,
        val orders: LinkedHashMap<UUID, SalesOrder>,
    )
}
