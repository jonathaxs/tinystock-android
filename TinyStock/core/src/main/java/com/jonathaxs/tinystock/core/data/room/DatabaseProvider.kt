package com.jonathaxs.tinystock.core.data.room

import android.content.Context
import androidx.room.Room

/**
 * Instancia unica do [TinyStockDatabase] no processo, gravada no arquivo `tinystock.db`.
 *
 * Nao usa `fallbackToDestructiveMigration` para preservar os dados reais da loja.
 */
object DatabaseProvider {

    private const val DATABASE_NAME = "tinystock.db"

    @Volatile
    private var instance: TinyStockDatabase? = null

    fun get(context: Context): TinyStockDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TinyStockDatabase::class.java,
                DATABASE_NAME,
            ).build().also { instance = it }
        }
}
