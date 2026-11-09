package com.jonathaxs.tinystock.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jonathaxs.tinystock.core.backup.BackupManager
import com.jonathaxs.tinystock.core.data.room.RoomTinyStockStore
import com.jonathaxs.tinystock.core.data.room.TinyStockDatabase
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderItem
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.model.StoreProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.Instant

/** Backup de ponta a ponta sobre o Room e fotos em arquivo de verdade. */
@RunWith(AndroidJUnit4::class)
class BackupOnRoomTest {

    private val now: Instant = Instant.ofEpochMilli(1_800_000_123_456L)

    private lateinit var source: TinyStockDatabase
    private lateinit var target: TinyStockDatabase
    private lateinit var sourceImages: DirectoryImageFileStore
    private lateinit var targetImages: DirectoryImageFileStore
    private lateinit var targetDirectory: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        source = Room.inMemoryDatabaseBuilder(context, TinyStockDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, TinyStockDatabase::class.java).build()
        val root = File(context.cacheDir, "backup-test-${System.nanoTime()}")
        sourceImages = DirectoryImageFileStore(File(root, "source"))
        targetDirectory = File(root, "target")
        targetImages = DirectoryImageFileStore(targetDirectory)
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun exportarDeUmBancoERestaurarNoutroTrazTudo() = runBlocking {
        val from = RoomTinyStockStore(source)
        val to = RoomTinyStockStore(target)

        val shop = StoreProfile(name = "Loja", imageFileName = sourceImages.write(byteArrayOf(0x01, 0x02)),
            createdAt = now, updatedAt = now)
        val other = StoreProfile(name = "Outra", sortOrder = 1, createdAt = now, updatedAt = now)
        val product = Product(storeID = shop.id, name = "Caneca", costPrice = BigDecimal("10.20"),
            salePrice = BigDecimal("39.90"), createdAt = now, updatedAt = now)
        val variant = ProductVariant(storeID = shop.id, productID = product.id, name = "Azul", quantity = 5,
            imageFileName = sourceImages.write(byteArrayOf(0x0A)), createdAt = now, updatedAt = now)
        val otherProduct = Product(storeID = other.id, name = "Bolsa", createdAt = now, updatedAt = now)
        val movement = StockMovement(storeID = shop.id, productID = product.id, variantID = variant.id,
            kindRawValue = "entry", quantityDelta = 5, balanceAfter = 5, createdAt = now)
        val order = SalesOrder(storeID = shop.id, orderedAt = now, createdAt = now, updatedAt = now,
            channelFeeAmount = BigDecimal("7.98"),
            items = listOf(
                SalesOrderItem.snapshot(product, variant, quantity = 2, position = 1),
                SalesOrderItem.snapshot(product, variant, quantity = 1, position = 0),
            ))
        for (s in listOf(shop, other)) from.saveStore(s)
        for (p in listOf(product, otherProduct)) from.saveProduct(p)
        from.saveVariant(variant)
        from.saveMovement(movement)
        from.saveOrder(order)

        // O destino ja tem dados, que precisam sumir.
        val stale = StoreProfile(name = "Velha", imageFileName = targetImages.write(byteArrayOf(0x09)))
        to.saveStore(stale)
        to.saveProduct(Product(storeID = stale.id, name = "Velho"))
        to.saveOrder(SalesOrder(storeID = stale.id, items = listOf(SalesOrderItem(storeID = stale.id, quantity = 1))))

        val data = BackupManager.export(shop.id, sourceImages, from, exportedAt = now)
        val result = BackupManager.apply(BackupManager.decode(data), targetImages, to)

        assertEquals(shop.id, result.selectedStoreID)
        assertEquals(setOf(shop.id, other.id), to.stores().map { it.id }.toSet())
        assertNull(to.store(stale.id))
        assertEquals(setOf(product.id, otherProduct.id), to.allProducts().map { it.id }.toSet())
        assertEquals(listOf(variant.copy(imageFileName = to.variant(variant.id)?.imageFileName)), to.allVariants())
        assertEquals(listOf(movement), to.allMovements())
        val restoredOrder = to.allOrders().single()
        assertEquals(order.id, restoredOrder.id)
        // O arquivo grava dinheiro sem zero a direita, como o iOS: 39.90 volta como 39.9, mesmo valor.
        fun normalized(items: List<SalesOrderItem>) = items.map {
            it.copy(unitPrice = it.unitPrice.stripTrailingZeros(), unitCost = it.unitCost.stripTrailingZeros())
        }
        assertEquals(normalized(order.itemList), normalized(restoredOrder.itemList))
        assertEquals(0, BigDecimal("7.98").compareTo(restoredOrder.channelFeeAmount))

        // Fotos copiadas para a pasta do destino; a da loja velha foi apagada.
        assertArrayEquals(byteArrayOf(0x01, 0x02), targetImages.read(to.store(shop.id)!!.imageFileName!!))
        assertArrayEquals(byteArrayOf(0x0A), targetImages.read(to.variant(variant.id)!!.imageFileName!!))
        assertEquals(2, targetDirectory.list()?.size)
        assertTrue(to.allOrders().none { it.storeID == stale.id })
    }
}
