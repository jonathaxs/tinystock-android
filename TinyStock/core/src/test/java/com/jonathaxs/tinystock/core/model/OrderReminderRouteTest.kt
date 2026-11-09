package com.jonathaxs.tinystock.core.model

import com.jonathaxs.tinystock.core.support.withStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Porte de `OrderReminderRouteTests.swift`. */
class OrderReminderRouteTest {

    private fun reminder(
        storeID: UUID = UUID.randomUUID(),
        orderID: UUID = UUID.randomUUID(),
    ): OrderReminder = OrderReminder(
        storeID = storeID,
        orderID = orderID,
        kind = OrderReminder.Kind.Production,
        timing = OrderReminder.Timing.DueDay,
        fireDate = Instant.now(),
        storeName = "Loja",
        productName = "Produto",
        soundEnabled = true,
    )

    @Test
    fun conteudoDoAdaptadorAbreIDsCertosEPermiteToquesRepetidos() {
        val reminder = reminder()
        val userInfo = reminder.userInfo()
        val first = OrderReminderRoute(reminder.id, userInfo)!!
        val second = OrderReminderRoute(reminder.id, userInfo)!!
        assertEquals(reminder.storeID, first.storeID)
        assertEquals(reminder.orderID, first.orderID)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun identificadoresIncompletosOuDeOutraFuncaoSaoRecusados() {
        val reminder = reminder()
        val userInfo = reminder.userInfo()
        for (identifier in listOf(
            "other.feature",
            OrderReminder.IDENTIFIER_PREFIX,
            "${reminder.id}.extra",
            reminder.id.replace(".production.", ".unknown."),
        )) {
            assertNull(OrderReminderRoute(identifier, userInfo))
        }
        assertNull(OrderReminderRoute(reminder.id, emptyMap()))
        for (key in listOf(
            OrderReminder.KEY_STORE_ID,
            OrderReminder.KEY_ORDER_ID,
            OrderReminder.KEY_REMINDER_KIND,
        )) {
            val payload = userInfo.toMutableMap()
            payload[key] = UUID.randomUUID().toString()
            assertNull(OrderReminderRoute(reminder.id, payload))
        }
    }

    @Test
    fun destinoExigePedidoNaLojaAtivaENaoRestauraArquivada() = withStore { store ->
        val storeProfile = StoreProfile(name = "Loja do pedido")
        val order = SalesOrder(storeID = storeProfile.id)
        store.saveStore(storeProfile)
        store.saveOrder(order)

        val item = reminder(storeID = storeProfile.id, orderID = order.id)
        val route = OrderReminderRoute(item.id, item.userInfo())!!
        val destination = route.resolve(store)!!
        assertEquals(storeProfile.id, destination.store.id)
        assertEquals(order.id, destination.order.id)

        store.saveStore(storeProfile.copy(isArchived = true))
        assertNull(route.resolve(store))
        assertTrue(store.store(storeProfile.id)!!.isArchived)

        store.saveStore(storeProfile.copy(isArchived = false))
        store.saveOrder(order.copy(storeID = UUID.randomUUID()))
        assertNull(route.resolve(store))

        store.saveOrder(order)
        store.deleteOrder(order.id)
        assertNull(route.resolve(store))

        store.deleteStore(storeProfile.id)
        assertNull(route.resolve(store))
    }
}
