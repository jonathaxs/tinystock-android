package com.jonathaxs.tinystock.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jonathaxs.tinystock.core.data.room.RoomTinyStockStore
import com.jonathaxs.tinystock.core.data.room.TinyStockDatabase
import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderItem
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.model.StockMovementKind
import com.jonathaxs.tinystock.core.model.StoreProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Testes instrumentados de [RoomTinyStockStore] sobre um banco Room em memoria. */
@RunWith(AndroidJUnit4::class)
class RoomTinyStockStoreTest {

    private lateinit var db: TinyStockDatabase
    private lateinit var store: RoomTinyStockStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TinyStockDatabase::class.java).build()
        store = RoomTinyStockStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun idaEVoltaDeCadaTipoPreservaTodosOsCamposEscalaEMilissegundos() = runBlocking {
        val t1 = Instant.ofEpochMilli(1_800_000_123_456L)
        val t2 = Instant.ofEpochMilli(1_800_000_654_321L)

        val activeStore = StoreProfile(
            id = UUID.randomUUID(),
            name = "Loja A",
            imageFileName = null,
            isArchived = false,
            archivedAt = null,
            trashedAt = null,
            wasArchivedBeforeTrash = false,
            sortOrder = 1,
            createdAt = t1,
            updatedAt = t2,
        )
        val trashedStore = StoreProfile(
            id = UUID.randomUUID(),
            name = "Loja B",
            imageFileName = "loja_b.jpg",
            isArchived = true,
            archivedAt = t1,
            trashedAt = t2,
            wasArchivedBeforeTrash = true,
            sortOrder = 2,
            createdAt = t1,
            updatedAt = t2,
        )
        store.saveStore(activeStore)
        store.saveStore(trashedStore)
        assertEquals(activeStore, store.store(activeStore.id))
        assertEquals(trashedStore, store.store(trashedStore.id))

        val productWithNullImage = Product(
            id = UUID.randomUUID(),
            storeID = activeStore.id,
            name = "Caderno",
            category = "Papelaria",
            quantity = 4,
            minimumStock = 2,
            costPrice = BigDecimal("10.50"),
            salePrice = BigDecimal("25.00"),
            imageFileName = null,
            createdAt = t1,
            updatedAt = t2,
            sortOrder = 3,
        )
        val productWithImage = productWithNullImage.copy(
            id = UUID.randomUUID(),
            name = "Caneta",
            imageFileName = "caneta.jpg",
        )
        store.saveProduct(productWithNullImage)
        store.saveProduct(productWithImage)
        val loadedProduct = store.product(productWithNullImage.id)!!
        assertEquals(productWithNullImage, loadedProduct)
        assertEquals("10.50", loadedProduct.costPrice.toPlainString())
        assertEquals("25.00", loadedProduct.salePrice.toPlainString())
        assertEquals(productWithImage, store.product(productWithImage.id))

        val variant = ProductVariant(
            id = UUID.randomUUID(),
            storeID = activeStore.id,
            productID = productWithNullImage.id,
            name = "Azul",
            isDefault = false,
            quantity = 7,
            imageFileName = "azul.jpg",
            createdAt = t1,
            updatedAt = t2,
        )
        val variantNullImage = variant.copy(
            id = UUID.randomUUID(),
            name = "Verde",
            isDefault = true,
            imageFileName = null,
        )
        store.saveVariant(variant)
        store.saveVariant(variantNullImage)
        assertEquals(variant, store.variant(variant.id))
        assertEquals(variantNullImage, store.variant(variantNullImage.id))

        val movementNulls = StockMovement(
            id = UUID.randomUUID(),
            storeID = activeStore.id,
            productID = productWithNullImage.id,
            variantID = variant.id,
            kindRawValue = StockMovementKind.InitialStock.rawValue,
            quantityDelta = 7,
            balanceAfter = 7,
            note = "Saldo inicial",
            referenceID = null,
            reversedMovementID = null,
            reversedAt = null,
            createdAt = t1,
        )
        val movementFull = StockMovement(
            id = UUID.randomUUID(),
            storeID = activeStore.id,
            productID = productWithNullImage.id,
            variantID = variant.id,
            kindRawValue = StockMovementKind.Reversal.rawValue,
            quantityDelta = 2,
            balanceAfter = 9,
            note = "Estorno",
            referenceID = UUID.randomUUID(),
            reversedMovementID = movementNulls.id,
            reversedAt = t2,
            createdAt = t2,
        )
        store.saveMovement(movementNulls)
        store.saveMovement(movementFull)
        assertEquals(movementNulls, store.movement(movementNulls.id))
        assertEquals(movementFull, store.movement(movementFull.id))

        val item = SalesOrderItem(
            id = UUID.randomUUID(),
            storeID = activeStore.id,
            productID = productWithNullImage.id,
            variantID = variant.id,
            productName = "Caderno",
            variantName = "Azul",
            unitPrice = BigDecimal("25.00"),
            unitCost = BigDecimal("10.50"),
            quantity = 2,
            position = 0,
        )
        val orderFull = SalesOrder(
            id = UUID.randomUUID(),
            storeID = activeStore.id,
            channelRawValue = SalesChannel.Other.rawValue,
            customChannelName = "Feira",
            fulfillmentRawValue = OrderFulfillment.Production.rawValue,
            statusRawValue = SalesOrderStatus.Cancelled.rawValue,
            buyerName = "Cliente",
            externalReference = "PED-01",
            orderedAt = t1,
            productionDueAt = t1,
            shippingDueAt = t2,
            productionStartedAt = t1,
            producedAt = t2,
            shippedAt = t2,
            completedAt = t2,
            cancelledAt = t2,
            cancellationReason = "Teste",
            trackingCode = "BR123",
            note = "Nota",
            channelFeePercentage = BigDecimal("12.50"),
            channelFeeAmount = BigDecimal("6.25"),
            createdAt = t1,
            updatedAt = t2,
            items = listOf(item),
        )
        val orderNulls = SalesOrder(
            id = UUID.randomUUID(),
            storeID = activeStore.id,
            channelRawValue = SalesChannel.Direct.rawValue,
            customChannelName = "",
            fulfillmentRawValue = OrderFulfillment.ReadyStock.rawValue,
            statusRawValue = SalesOrderStatus.ReadyToShip.rawValue,
            buyerName = "",
            externalReference = "",
            orderedAt = t1,
            productionDueAt = null,
            shippingDueAt = null,
            productionStartedAt = null,
            producedAt = null,
            shippedAt = null,
            completedAt = null,
            cancelledAt = null,
            cancellationReason = "",
            trackingCode = "",
            note = "",
            channelFeePercentage = BigDecimal("0.00"),
            channelFeeAmount = BigDecimal("0.00"),
            createdAt = t1,
            updatedAt = t1,
            items = emptyList(),
        )
        store.saveOrder(orderFull)
        store.saveOrder(orderNulls)

        val loadedOrderFull = store.order(orderFull.id)!!
        assertEquals(orderFull, loadedOrderFull)
        assertEquals("12.50", loadedOrderFull.channelFeePercentage.toPlainString())
        assertEquals("10.50", loadedOrderFull.itemList.single().unitCost.toPlainString())
        assertEquals(orderNulls, store.order(orderNulls.id))
    }

    @Test
    fun filtraPorLojaSubstituiPeloIdEApaga() = runBlocking {
        val t = Instant.ofEpochMilli(1_800_000_000_000L)
        val storeA = UUID.randomUUID()
        val storeB = UUID.randomUUID()

        val productA = Product(
            id = UUID.randomUUID(),
            storeID = storeA,
            name = "Produto A",
            costPrice = BigDecimal("5.00"),
            salePrice = BigDecimal("10.00"),
            createdAt = t,
            updatedAt = t,
        )
        val productB = productA.copy(
            id = UUID.randomUUID(),
            storeID = storeB,
            name = "Produto B",
        )
        store.saveProduct(productA)
        store.saveProduct(productB)

        assertEquals(listOf(productA), store.products(storeA))
        assertEquals(listOf(productB), store.products(storeB))

        val updatedA = productA.copy(name = "Produto A Editado", salePrice = BigDecimal("15.50"))
        store.saveProduct(updatedA)
        assertEquals(updatedA, store.product(productA.id))
        assertEquals(1, store.products(storeA).size)

        store.deleteProduct(productA.id)
        assertNull(store.product(productA.id))
        assertTrue(store.products(storeA).isEmpty())
        assertEquals(listOf(productB), store.products(storeB))
    }

    @Test
    fun pedidoComLinhasMantemOrdemDePositionRemoveLinhaNoSaveEApagaLinhasNoDelete() = runBlocking {
        val t = Instant.ofEpochMilli(1_800_000_000_000L)
        val storeID = UUID.randomUUID()
        val itemPos1 = SalesOrderItem(
            id = UUID.randomUUID(),
            storeID = storeID,
            productID = UUID.randomUUID(),
            variantID = UUID.randomUUID(),
            productName = "Item 2",
            variantName = "M",
            unitPrice = BigDecimal("20.00"),
            unitCost = BigDecimal("10.00"),
            quantity = 1,
            position = 1,
        )
        val itemPos0 = SalesOrderItem(
            id = UUID.randomUUID(),
            storeID = storeID,
            productID = UUID.randomUUID(),
            variantID = UUID.randomUUID(),
            productName = "Item 1",
            variantName = "P",
            unitPrice = BigDecimal("15.00"),
            unitCost = BigDecimal("7.50"),
            quantity = 3,
            position = 0,
        )
        val order = SalesOrder(
            id = UUID.randomUUID(),
            storeID = storeID,
            orderedAt = t,
            shippingDueAt = t,
            createdAt = t,
            updatedAt = t,
            items = listOf(itemPos1, itemPos0),
        )
        store.saveOrder(order)

        val loaded = store.order(order.id)!!
        assertEquals(listOf(itemPos0, itemPos1), loaded.itemList)
        assertEquals(listOf(itemPos0, itemPos1), store.orders(storeID).single().itemList)

        val orderWithOneItem = order.copy(items = listOf(itemPos0))
        store.saveOrder(orderWithOneItem)
        assertEquals(listOf(itemPos0), store.order(order.id)!!.itemList)
        assertEquals(1, db.salesOrderDao().itemsByOrder(order.id).size)

        store.deleteOrder(order.id)
        assertNull(store.order(order.id))
        assertTrue(store.orders(storeID).isEmpty())
        assertTrue(db.salesOrderDao().itemsByOrder(order.id).isEmpty())
        assertTrue(db.salesOrderDao().itemsByStore(storeID).isEmpty())
    }

    @Test
    fun transacaoReverteAoLancarExcecaoESuportaAninhamento() = runBlocking {
        val t = Instant.ofEpochMilli(1_800_000_000_000L)
        val storeID = UUID.randomUUID()

        try {
            store.inTransaction {
                store.saveStore(StoreProfile(id = storeID, name = "Loja", createdAt = t, updatedAt = t))
                store.inTransaction {
                    store.saveProduct(
                        Product(
                            id = UUID.randomUUID(),
                            storeID = storeID,
                            name = "Produto",
                            createdAt = t,
                            updatedAt = t,
                        ),
                    )
                }
                throw IllegalStateException("falha simulada")
            }
            fail("Deveria propagar a excecao")
        } catch (_: IllegalStateException) {
            // Esperado.
        }

        assertTrue(store.stores().isEmpty())
        assertTrue(store.products(storeID).isEmpty())

        val productID = UUID.randomUUID()
        store.inTransaction {
            store.saveStore(StoreProfile(id = storeID, name = "Loja Confirmada", createdAt = t, updatedAt = t))
            store.inTransaction {
                store.saveProduct(
                    Product(
                        id = productID,
                        storeID = storeID,
                        name = "Produto Confirmado",
                        createdAt = t,
                        updatedAt = t,
                    ),
                )
            }
        }

        assertEquals(1, store.stores().size)
        assertEquals(productID, store.products(storeID).single().id)
    }

    @Test
    fun observeProductsEObserveOrdersEmitemEstadoNovoDepoisDeSave() = runBlocking {
        val t = Instant.ofEpochMilli(1_800_000_000_000L)
        val storeID = UUID.randomUUID()

        assertTrue(store.observeProducts(storeID).first().isEmpty())
        assertTrue(store.observeOrders(storeID).first().isEmpty())

        val product = Product(
            id = UUID.randomUUID(),
            storeID = storeID,
            name = "Camiseta",
            costPrice = BigDecimal("12.00"),
            salePrice = BigDecimal("30.00"),
            createdAt = t,
            updatedAt = t,
        )
        store.saveProduct(product)
        assertEquals(listOf(product), store.observeProducts(storeID).first())

        val item = SalesOrderItem(
            id = UUID.randomUUID(),
            storeID = storeID,
            productID = product.id,
            variantID = UUID.randomUUID(),
            productName = "Camiseta",
            variantName = "G",
            unitPrice = BigDecimal("30.00"),
            unitCost = BigDecimal("12.00"),
            quantity = 2,
            position = 0,
        )
        val order = SalesOrder(
            id = UUID.randomUUID(),
            storeID = storeID,
            orderedAt = t,
            shippingDueAt = t,
            createdAt = t,
            updatedAt = t,
            items = listOf(item),
        )
        store.saveOrder(order)
        assertEquals(listOf(order), store.observeOrders(storeID).first())
    }
}
