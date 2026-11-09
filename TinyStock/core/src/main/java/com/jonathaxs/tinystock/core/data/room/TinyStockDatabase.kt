package com.jonathaxs.tinystock.core.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Banco local do app em Room.
 *
 * Mudar qualquer entidade exige subir [version] e escrever a migracao correspondente. Nunca usar
 * `fallbackToDestructiveMigration`: sao dados reais da loja.
 */
@Database(
    entities = [
        StoreEntity::class,
        ProductEntity::class,
        VariantEntity::class,
        StockMovementEntity::class,
        SalesOrderEntity::class,
        SalesOrderItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TinyStockDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun productDao(): ProductDao
    abstract fun variantDao(): VariantDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun salesOrderDao(): SalesOrderDao
}
