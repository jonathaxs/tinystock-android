package com.jonathaxs.tinystock.core.data.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Acesso a tabela `stores`. */
@Dao
interface StoreDao {
    @Query("SELECT * FROM stores")
    suspend fun all(): List<StoreEntity>

    @Query("SELECT * FROM stores")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun byId(id: UUID): StoreEntity?

    @Upsert
    suspend fun upsert(entity: StoreEntity)

    @Query("DELETE FROM stores WHERE id = :id")
    suspend fun delete(id: UUID)
}

/** Acesso a tabela `products`. */
@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE storeID = :storeID")
    suspend fun byStore(storeID: UUID): List<ProductEntity>

    @Query("SELECT * FROM products WHERE storeID = :storeID")
    fun observeByStore(storeID: UUID): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun byId(id: UUID): ProductEntity?

    @Upsert
    suspend fun upsert(entity: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: UUID)
}

/** Acesso a tabela `variants`. */
@Dao
interface VariantDao {
    @Query("SELECT * FROM variants WHERE storeID = :storeID")
    suspend fun byStore(storeID: UUID): List<VariantEntity>

    @Query("SELECT * FROM variants WHERE storeID = :storeID")
    fun observeByStore(storeID: UUID): Flow<List<VariantEntity>>

    @Query("SELECT * FROM variants WHERE id = :id")
    suspend fun byId(id: UUID): VariantEntity?

    @Upsert
    suspend fun upsert(entity: VariantEntity)

    @Query("DELETE FROM variants WHERE id = :id")
    suspend fun delete(id: UUID)
}

/** Acesso a tabela `stock_movements`. */
@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements WHERE storeID = :storeID")
    suspend fun byStore(storeID: UUID): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE storeID = :storeID")
    fun observeByStore(storeID: UUID): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE id = :id")
    suspend fun byId(id: UUID): StockMovementEntity?

    @Upsert
    suspend fun upsert(entity: StockMovementEntity)

    @Query("DELETE FROM stock_movements WHERE id = :id")
    suspend fun delete(id: UUID)
}

/** Acesso as tabelas `sales_orders` e `sales_order_items`. A observacao fica no RoomTinyStockStore. */
@Dao
interface SalesOrderDao {
    @Query("SELECT * FROM sales_orders WHERE storeID = :storeID")
    suspend fun byStore(storeID: UUID): List<SalesOrderEntity>

    @Query("SELECT * FROM sales_orders WHERE id = :id")
    suspend fun byId(id: UUID): SalesOrderEntity?

    @Upsert
    suspend fun upsert(entity: SalesOrderEntity)

    @Query("DELETE FROM sales_orders WHERE id = :id")
    suspend fun delete(id: UUID)

    @Query("SELECT * FROM sales_order_items WHERE storeID = :storeID")
    suspend fun itemsByStore(storeID: UUID): List<SalesOrderItemEntity>

    @Query("SELECT * FROM sales_order_items WHERE orderID = :orderID")
    suspend fun itemsByOrder(orderID: UUID): List<SalesOrderItemEntity>

    @Upsert
    suspend fun insertItems(items: List<SalesOrderItemEntity>)

    @Query("DELETE FROM sales_order_items WHERE orderID = :orderID")
    suspend fun deleteItemsOfOrder(orderID: UUID)
}
