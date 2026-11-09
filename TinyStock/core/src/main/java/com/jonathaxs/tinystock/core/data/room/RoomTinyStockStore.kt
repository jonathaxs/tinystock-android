package com.jonathaxs.tinystock.core.data.room

import androidx.room.withTransaction
import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.model.StoreProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Implementacao de [TinyStockStore] sobre o [TinyStockDatabase].
 *
 * Pedidos e linhas sao gravados e apagados juntos na mesma transacao, e reconstruidos por `orderID`
 * na leitura e na observacao.
 */
class RoomTinyStockStore(
    private val db: TinyStockDatabase,
) : TinyStockStore {

    private val storeDao = db.storeDao()
    private val productDao = db.productDao()
    private val variantDao = db.variantDao()
    private val stockMovementDao = db.stockMovementDao()
    private val salesOrderDao = db.salesOrderDao()

    override suspend fun <R> inTransaction(block: suspend () -> R): R =
        db.withTransaction { block() }

    override suspend fun stores(): List<StoreProfile> =
        storeDao.all().map { it.toModel() }

    override suspend fun store(id: UUID): StoreProfile? =
        storeDao.byId(id)?.toModel()

    override suspend fun saveStore(store: StoreProfile) {
        storeDao.upsert(store.toEntity())
    }

    override suspend fun deleteStore(id: UUID) {
        storeDao.delete(id)
    }

    override suspend fun products(storeID: UUID): List<Product> =
        productDao.byStore(storeID).map { it.toModel() }

    override suspend fun product(id: UUID): Product? =
        productDao.byId(id)?.toModel()

    override suspend fun saveProduct(product: Product) {
        productDao.upsert(product.toEntity())
    }

    override suspend fun deleteProduct(id: UUID) {
        productDao.delete(id)
    }

    override suspend fun variants(storeID: UUID): List<ProductVariant> =
        variantDao.byStore(storeID).map { it.toModel() }

    override suspend fun variant(id: UUID): ProductVariant? =
        variantDao.byId(id)?.toModel()

    override suspend fun saveVariant(variant: ProductVariant) {
        variantDao.upsert(variant.toEntity())
    }

    override suspend fun deleteVariant(id: UUID) {
        variantDao.delete(id)
    }

    override suspend fun movements(storeID: UUID): List<StockMovement> =
        stockMovementDao.byStore(storeID).map { it.toModel() }

    override suspend fun movement(id: UUID): StockMovement? =
        stockMovementDao.byId(id)?.toModel()

    override suspend fun saveMovement(movement: StockMovement) {
        stockMovementDao.upsert(movement.toEntity())
    }

    override suspend fun deleteMovement(id: UUID) {
        stockMovementDao.delete(id)
    }

    override suspend fun orders(storeID: UUID): List<SalesOrder> = db.withTransaction {
        val orders = salesOrderDao.byStore(storeID)
        val itemsByOrder = salesOrderDao.itemsByStore(storeID).groupBy { it.orderID }
        orders.map { entity ->
            val items = itemsByOrder[entity.id].orEmpty().map { it.toModel() }
            entity.toModel(items)
        }
    }

    override suspend fun order(id: UUID): SalesOrder? = db.withTransaction {
        val entity = salesOrderDao.byId(id) ?: return@withTransaction null
        val items = salesOrderDao.itemsByOrder(id).map { it.toModel() }
        entity.toModel(items)
    }

    override suspend fun saveOrder(order: SalesOrder) {
        db.withTransaction {
            salesOrderDao.upsert(order.toEntity())
            salesOrderDao.deleteItemsOfOrder(order.id)
            if (order.items.isNotEmpty()) {
                salesOrderDao.insertItems(order.items.map { it.toEntity(order.id) })
            }
        }
    }

    override suspend fun deleteOrder(id: UUID) {
        db.withTransaction {
            salesOrderDao.deleteItemsOfOrder(id)
            salesOrderDao.delete(id)
        }
    }

    override fun observeStores(): Flow<List<StoreProfile>> =
        storeDao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observeProducts(storeID: UUID): Flow<List<Product>> =
        productDao.observeByStore(storeID).map { list -> list.map { it.toModel() } }

    override fun observeVariants(storeID: UUID): Flow<List<ProductVariant>> =
        variantDao.observeByStore(storeID).map { list -> list.map { it.toModel() } }

    override fun observeMovements(storeID: UUID): Flow<List<StockMovement>> =
        stockMovementDao.observeByStore(storeID).map { list -> list.map { it.toModel() } }

    /**
     * Um so gatilho para as duas tabelas: a cada mudanca confirmada em pedidos ou linhas, rele tudo numa
     * transacao. Juntar dois Flows separados podia mostrar por um instante o pedido novo com as linhas velhas.
     */
    override fun observeOrders(storeID: UUID): Flow<List<SalesOrder>> =
        db.invalidationTracker
            .createFlow("sales_orders", "sales_order_items")
            .map { orders(storeID) }
}
