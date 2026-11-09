package com.jonathaxs.tinystock.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jonathaxs.tinystock.core.data.room.RoomTinyStockStore
import com.jonathaxs.tinystock.core.data.room.TinyStockDatabase
import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import com.jonathaxs.tinystock.core.model.StockMovementKind
import com.jonathaxs.tinystock.core.service.ProductService
import com.jonathaxs.tinystock.core.service.ProductVariantService
import com.jonathaxs.tinystock.core.service.SalesOrderError
import com.jonathaxs.tinystock.core.service.SalesOrderLine
import com.jonathaxs.tinystock.core.service.SalesOrderManagementService
import com.jonathaxs.tinystock.core.service.SalesOrderService
import com.jonathaxs.tinystock.core.service.StoreProfileService
import com.jonathaxs.tinystock.core.util.DomainException
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
import java.time.ZoneOffset
import java.util.UUID

/** Fluxo de ponta a ponta dos servicos de dominio sobre o [RoomTinyStockStore]. */
@RunWith(AndroidJUnit4::class)
class ServicesOnRoomTest {

    private val now: Instant = Instant.ofEpochMilli(1_800_000_000_000L)
    private val zone = ZoneOffset.UTC

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
    fun fluxoDeLojaProdutoVariacaoPedidoCancelamentoEFalhaDeEstoqueNoRoom() = runBlocking {
        val storeProfile = StoreProfileService.create(
            name = "Loja Principal",
            imageFileName = null,
            date = now,
            store = store,
        )
        val product = ProductService.create(
            storeID = storeProfile.id,
            name = "Caneca",
            costPrice = BigDecimal("10.50"),
            salePrice = BigDecimal("25.00"),
            imageFileName = null,
            date = now,
            store = store,
        )
        val variant = ProductVariantService.create(
            product = product,
            name = "Verde",
            initialQuantity = 5,
            date = now,
            store = store,
        )
        assertEquals(5, store.variant(variant.id)?.quantity)

        val order = SalesOrderService.register(
            storeID = storeProfile.id,
            lines = listOf(SalesOrderLine(productID = product.id, variantID = variant.id, quantity = 2)),
            fulfillment = OrderFulfillment.ReadyStock,
            channel = SalesChannel.Direct,
            orderedAt = now,
            shippingDueAt = now,
            now = now,
            zone = zone,
            store = store,
        )
        assertEquals(SalesOrderStatus.ReadyToShip, order.status)
        assertEquals(3, store.variant(variant.id)?.quantity)
        assertEquals(2, store.order(order.id)?.itemList?.single()?.quantity)

        val cancelDate = Instant.ofEpochMilli(1_800_003_600_000L)
        val cancelled = SalesOrderManagementService.cancel(
            id = order.id,
            reason = "Cliente desistiu",
            date = cancelDate,
            store = store,
        )
        assertEquals(SalesOrderStatus.Cancelled, cancelled.status)
        assertEquals(SalesOrderStatus.Cancelled, store.order(order.id)?.status)
        assertEquals(5, store.variant(variant.id)?.quantity)

        val movements = store.movements(storeProfile.id)
        assertEquals(3, movements.size)
        assertTrue(movements.any { it.kind == StockMovementKind.Reversal && it.quantityDelta == 2 })

        val failedOrderId = UUID.randomUUID()
        try {
            SalesOrderService.register(
                id = failedOrderId,
                storeID = storeProfile.id,
                lines = listOf(SalesOrderLine(productID = product.id, variantID = variant.id, quantity = 10)),
                fulfillment = OrderFulfillment.ReadyStock,
                orderedAt = now,
                shippingDueAt = now,
                now = now,
                zone = zone,
                store = store,
            )
            fail("Deveria falhar por estoque insuficiente")
        } catch (error: DomainException) {
            assertTrue(error.error is SalesOrderError.InsufficientStock)
        }

        assertNull(store.order(failedOrderId))
        assertEquals(1, store.orders(storeProfile.id).size)
        assertEquals(5, store.variant(variant.id)?.quantity)
    }
}
