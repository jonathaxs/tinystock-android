package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.OrderReminder
import com.jonathaxs.tinystock.core.model.OrderReminderError
import com.jonathaxs.tinystock.core.model.OrderReminderSettings
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderItem
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.support.expectError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Porte de `OrderReminderTests.swift`.
 * Os testes de `OrderReminderCoordinator`, `OrderReminderSettingsStore` e `SystemOrderReminderCenter`
 * ficam no :app (com DataStore e WorkManager).
 */
class OrderReminderTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")

    private fun date(day: Int, month: Int = 9, hour: Int = 8, minute: Int = 0): Instant =
        ZonedDateTime.of(2028, month, day, hour, minute, 0, 0, zone).toInstant()

    private val enabled = OrderReminderSettings(isEnabled = true)

    private fun order(
        store: StoreProfile,
        status: SalesOrderStatus = SalesOrderStatus.AwaitingProduction,
    ): SalesOrder = SalesOrder(
        storeID = store.id,
        fulfillmentRawValue = OrderFulfillment.Production.rawValue,
        statusRawValue = status.rawValue,
        buyerName = "Comprador privado",
        orderedAt = date(1),
        productionDueAt = date(10),
        shippingDueAt = date(12),
        items = listOf(SalesOrderItem(productName = "Produto", quantity = 2)),
    )

    private fun plan(
        orders: List<SalesOrder>,
        stores: List<StoreProfile>,
        settings: OrderReminderSettings = enabled,
        now: Instant = date(7),
    ): List<OrderReminder> = OrderReminderPlanner.reminders(
        orders = orders,
        stores = stores,
        settings = settings,
        now = now,
        zone = zone,
    )

    private suspend fun sync(
        service: OrderReminderService,
        orders: List<SalesOrder>,
        stores: List<StoreProfile>,
        settings: OrderReminderSettings = enabled,
    ): OrderReminderSyncResult = service.synchronize(
        orders = orders,
        stores = stores,
        settings = settings,
        now = date(7),
        zone = zone,
    )

    @Test
    fun producaoAgendaVesperaDiaEAtrasoParaAmbosOsPrazos() {
        val store = StoreProfile(name = "Loja")
        val reminders = plan(listOf(order(store)), stores = listOf(store))
        assertEquals(6, reminders.size)
        assertEquals(listOf(9, 10, 11, 11, 12, 13).map { date(it, hour = 9) }, reminders.map { it.fireDate })
        assertEquals(6, reminders.map { it.id }.toSet().size)
        assertEquals(
            listOf(
                OrderReminder.Timing.DayBefore,
                OrderReminder.Timing.DueDay,
                OrderReminder.Timing.Overdue,
            ),
            reminders.filter { it.kind == OrderReminder.Kind.Production }.map { it.timing },
        )
        assertTrue(reminders.all { it.storeName == "Loja" && it.productName == "Produto" })
    }

    @Test
    fun produzidoRetiraProducaoEDespachadoRetiraTudo() {
        val store = StoreProfile()
        val inProduction = order(store, status = SalesOrderStatus.InProduction)
        assertEquals(6, plan(listOf(inProduction), stores = listOf(store)).size)

        val ready = inProduction.copy(statusRawValue = SalesOrderStatus.ReadyToShip.rawValue)
        assertTrue(plan(listOf(ready), stores = listOf(store)).all { it.kind == OrderReminder.Kind.Shipping })

        for (state in listOf(
            SalesOrderStatus.New,
            SalesOrderStatus.Shipped,
            SalesOrderStatus.Completed,
            SalesOrderStatus.Cancelled,
        )) {
            val updated = ready.copy(statusRawValue = state.rawValue)
            assertTrue(plan(listOf(updated), stores = listOf(store)).isEmpty())
        }

        val unknown = ready.copy(statusRawValue = "future-state")
        assertTrue(plan(listOf(unknown), stores = listOf(store)).isEmpty())
    }

    @Test
    fun lojasAtivasParticipamSemMisturarIDsOuIncluirArquivadas() {
        val first = StoreProfile(name = "Primeira")
        val second = StoreProfile(name = "Segunda")
        val archived = StoreProfile(isArchived = true)
        val missing = StoreProfile()
        val firstOrder = order(first)
        val secondOrder = order(second).copy(id = firstOrder.id)
        val orders = listOf(firstOrder, secondOrder, order(archived), order(missing))

        val reminders = plan(orders, stores = listOf(first, second, archived))
        assertEquals(12, reminders.size)
        assertEquals(12, reminders.map { it.id }.toSet().size)
        assertEquals(setOf(first.id, second.id), reminders.map { it.storeID }.toSet())
        assertTrue(reminders.filter { it.storeID == second.id }.all { it.storeName == "Segunda" })
    }

    @Test
    fun desativadoEPrazosAusentesNaoGeramNotificacoes() {
        val store = StoreProfile()
        val order = order(store)
        assertTrue(plan(listOf(order), stores = listOf(store), settings = OrderReminderSettings()).isEmpty())

        val withoutDeadlines = order.copy(productionDueAt = null, shippingDueAt = null)
        assertTrue(plan(listOf(withoutDeadlines), stores = listOf(store)).isEmpty())
    }

    @Test
    fun preferenciasSelecionamEtapaMomentoESom() {
        val store = StoreProfile()
        val settings = enabled.copy(
            productionEnabled = false,
            dayBeforeEnabled = false,
            overdueEnabled = false,
            soundEnabled = false,
            hour = 14,
            minute = 35,
        )
        val reminders = plan(listOf(order(store)), stores = listOf(store), settings = settings)
        assertEquals(1, reminders.size)
        assertEquals(OrderReminder.Kind.Shipping, reminders.first().kind)
        assertEquals(OrderReminder.Timing.DueDay, reminders.first().timing)
        assertEquals(date(12, hour = 14, minute = 35), reminders.first().fireDate)
        assertFalse(reminders.first().soundEnabled)

        val disabledShipping = settings.copy(shippingEnabled = false)
        assertTrue(plan(listOf(order(store)), stores = listOf(store), settings = disabledShipping).isEmpty())
    }

    @Test
    fun horariosInvalidosSaoRecusadosAntesDeAgendar() {
        for ((hour, minute) in listOf(-1 to 0, 24 to 0, 9 to -1, 9 to 60)) {
            val settings = enabled.copy(hour = hour, minute = minute)
            expectError(OrderReminderError.InvalidTime) {
                plan(emptyList(), stores = emptyList(), settings = settings)
            }
        }
    }

    @Test
    fun naoReenviaAvisosPassadosNemAtrasosAntigos() {
        val store = StoreProfile()
        val order = order(store, status = SalesOrderStatus.ReadyToShip)
        val reminders = plan(listOf(order), stores = listOf(store), now = date(12, hour = 9))
        assertEquals(1, reminders.size)
        assertEquals(OrderReminder.Timing.Overdue, reminders.first().timing)
        assertTrue(plan(listOf(order), stores = listOf(store), now = date(14)).isEmpty())
    }

    @Test
    fun horarioDeVeraoMantemDiaLocalEAvancaHoraInexistente() {
        val store = StoreProfile()
        val order = order(store, status = SalesOrderStatus.ReadyToShip).copy(
            shippingDueAt = date(12, month = 3),
        )
        val settings = enabled.copy(hour = 2, minute = 30)
        val reminders = plan(listOf(order), stores = listOf(store), settings = settings, now = date(10, month = 3))
        val due = reminders.first { it.timing == OrderReminder.Timing.DueDay }
        assertEquals(12, due.fireDate.atZone(zone).dayOfMonth)
        assertEquals(3, due.fireDate.atZone(zone).hour)
        assertEquals(11, reminders.first().fireDate.atZone(zone).dayOfMonth)
        assertEquals(2, reminders.first().fireDate.atZone(zone).hour)
    }

    @Test
    fun alteracaoDePrazoMantemIDMasAtualizaAssinatura() {
        val store = StoreProfile()
        val order = order(store)
        val before = plan(listOf(order), stores = listOf(store))
        val updated = order.copy(productionDueAt = date(15))
        val after = plan(listOf(updated), stores = listOf(store))
        assertEquals(before.map { it.id }.toSet(), after.map { it.id }.toSet())

        val old = before.first {
            it.kind == OrderReminder.Kind.Production && it.timing == OrderReminder.Timing.DueDay
        }
        val new = after.first { it.id == old.id }
        assertNotEquals(old.signature(), new.signature())
    }

    @Test
    fun sincronizacaoRepetidaNaoDuplicaEAtualizaSomTextoEPrazo() = runBlocking {
        val store = StoreProfile(name = "Loja")
        val order = order(store)
        val center = TestReminderCenter()
        val service = OrderReminderService(center)

        sync(service, listOf(order), listOf(store))
        assertEquals(6, center.added.size)
        sync(service, listOf(order), listOf(store))
        assertEquals(6, center.added.size)

        val settings = enabled.copy(soundEnabled = false)
        val renamedStore = store.copy(name = "Novo nome")
        val movedOrder = order.copy(productionDueAt = date(15))
        sync(service, listOf(movedOrder), listOf(renamedStore), settings = settings)
        assertEquals(12, center.added.size)
        assertEquals(6, center.pending.size)
        assertTrue(center.added.takeLast(6).all { !it.soundEnabled && it.storeName == "Novo nome" })
        assertEquals(0, center.permissionRequests)
    }

    @Test
    fun transicoesCancelamentoEExclusaoRemovemAlertasObsoletos() = runBlocking {
        val store = StoreProfile()
        val order = order(store)
        val center = TestReminderCenter()
        val service = OrderReminderService(center)

        sync(service, listOf(order), listOf(store))
        val ready = order.copy(statusRawValue = SalesOrderStatus.ReadyToShip.rawValue)
        sync(service, listOf(ready), listOf(store))
        assertEquals(3, center.pending.size)
        assertTrue(center.pending.all { it.id.contains(".shipping.") })

        for (status in listOf(
            SalesOrderStatus.Cancelled,
            SalesOrderStatus.Shipped,
            SalesOrderStatus.Completed,
        )) {
            sync(service, listOf(ready), listOf(store))
            sync(service, listOf(ready.copy(statusRawValue = status.rawValue)), listOf(store))
            assertTrue(center.pending.isEmpty())
        }

        sync(service, listOf(ready), listOf(store))
        sync(service, emptyList(), listOf(store))
        assertTrue(center.pending.isEmpty())
    }

    @Test
    fun arquivarLojaOuDesativarRemoveSomenteLembretesDePedidos() = runBlocking {
        val store = StoreProfile()
        val order = order(store)
        val center = TestReminderCenter()
        center.pending.add(PendingOrderReminder(id = "another.feature", signature = null))
        val service = OrderReminderService(center)

        sync(service, listOf(order), listOf(store))
        sync(service, listOf(order), listOf(store.copy(isArchived = true)))
        assertEquals(listOf("another.feature"), center.pending.map { it.id })

        sync(service, listOf(order), listOf(store))
        sync(service, listOf(order), listOf(store), settings = OrderReminderSettings())
        assertEquals(listOf("another.feature"), center.pending.map { it.id })
    }

    @Test
    fun permissaoNegadaOuDesconhecidaNaoAgendaNemSolicitaDialogo() = runBlocking {
        val store = StoreProfile()
        for (authorization in listOf(
            OrderReminderAuthorization.Denied,
            OrderReminderAuthorization.NotDetermined,
            OrderReminderAuthorization.Unknown,
        )) {
            val center = TestReminderCenter()
            val service = OrderReminderService(center)
            val order = order(store)
            sync(service, listOf(order), listOf(store))
            center.status = authorization
            val result = sync(service, listOf(order), listOf(store))
            assertTrue(result.scheduledIDs.isEmpty())
            assertEquals(6, result.deferredIDs.size)
            assertTrue(center.pending.isEmpty())
            assertEquals(0, center.permissionRequests)
        }
    }

    @Test
    fun autorizacaoExplicitaEProvisoriaSaoSuportadas() = runBlocking {
        val store = StoreProfile()
        val center = TestReminderCenter()
        val service = OrderReminderService(center)
        assertTrue(service.requestAuthorization())
        assertEquals(1, center.permissionRequests)
        center.status = OrderReminderAuthorization.Provisional
        val result = sync(service, listOf(order(store)), listOf(store))
        assertEquals(6, result.scheduledIDs.size)
    }

    @Test
    fun limitePriorizaProximosEDescontaNotificacoesDeOutrasFuncoes() = runBlocking {
        val store = StoreProfile()
        val orders = (0 until 15).map { order(store) }
        val center = TestReminderCenter()
        center.pending.addAll((0 until 5).map { PendingOrderReminder(id = "foreign.$it", signature = null) })
        val result = sync(OrderReminderService(center), orders, listOf(store))
        val expected = plan(orders, stores = listOf(store))
        assertEquals(expected.take(55).map { it.id }, result.scheduledIDs)
        assertEquals(expected.drop(55).map { it.id }, result.deferredIDs)
        assertEquals(60, center.pending.size)
    }

    @Test
    fun falhaAoSubstituirRemovePrazoAntigoEPermiteNovaTentativa() = runBlocking {
        val store = StoreProfile()
        val order = order(store)
        val center = TestReminderCenter()
        val service = OrderReminderService(center)

        sync(service, listOf(order), listOf(store))
        val failedID = center.added.first().id
        center.failedID = failedID
        val updated = order.copy(productionDueAt = date(16))
        val result = sync(service, listOf(updated), listOf(store))
        assertEquals(listOf(failedID), result.failedIDs)
        assertFalse(center.pending.any { it.id == failedID })

        center.failedID = null
        val retry = sync(service, listOf(updated), listOf(store))
        assertTrue(retry.failedIDs.isEmpty())
        assertEquals(6, center.pending.size)
    }

    // O `requisicaoDoSistemaPreservaDataConteudoSomERota` do iOS testa `SystemOrderReminderCenter`
    // (UNNotificationRequest), que no Android vai para o :app. Aqui validamos o payload puro de `userInfo()`.
    @Test
    fun metadadosDoLembretePreservamRotaEAssinatura() {
        val store = StoreProfile(name = "Loja")
        val reminder = plan(listOf(order(store)), stores = listOf(store)).first()
        val userInfo = reminder.userInfo()
        assertEquals(store.id.toString(), userInfo[OrderReminder.KEY_STORE_ID])
        assertEquals(reminder.orderID.toString(), userInfo[OrderReminder.KEY_ORDER_ID])
        assertEquals(reminder.kind.rawValue, userInfo[OrderReminder.KEY_REMINDER_KIND])
        assertEquals(reminder.signature(), userInfo[OrderReminder.KEY_REMINDER_SIGNATURE])
    }

    @Test
    fun cancelamentoDuranteAgendamentoPrevaleceSobrePlanoAnterior() = runBlocking {
        val store = StoreProfile()
        val order = order(store)
        val center = TestReminderCenter()
        val service = OrderReminderService(center)

        val entered = CompletableDeferred<Unit>()
        val addGate = CompletableDeferred<Unit>()
        center.onPausedAdd = entered
        center.addGate = addGate
        center.pauseNextAdd = true

        val firstDeferred = async { sync(service, listOf(order), listOf(store)) }
        entered.await()

        val cancelled = order.copy(statusRawValue = SalesOrderStatus.Cancelled.rawValue)
        val secondDeferred = async { sync(service, listOf(cancelled), listOf(store)) }
        addGate.complete(Unit)

        val first = firstDeferred.await()
        val second = secondDeferred.await()
        assertEquals(6, first.scheduledIDs.size)
        assertTrue(second.scheduledIDs.isEmpty())
        assertTrue(center.pending.isEmpty())
    }

    @Test
    fun filaSemVagasPreservaOutrasFuncoesERefazPlanoQuandoHaEspaco() = runBlocking {
        val store = StoreProfile()
        val order = order(store)
        val center = TestReminderCenter()
        center.pending.addAll((0 until 61).map { PendingOrderReminder(id = "foreign.$it", signature = null) })
        val service = OrderReminderService(center)

        val full = sync(service, listOf(order), listOf(store))
        assertTrue(full.scheduledIDs.isEmpty())
        assertEquals(6, full.deferredIDs.size)
        assertEquals(61, center.pending.size)

        center.pending.clear()
        val retry = sync(service, listOf(order, order), listOf(store))
        assertEquals(6, retry.scheduledIDs.size)
        assertTrue(retry.deferredIDs.isEmpty())
        assertEquals(6, center.pending.size)
    }

    @Test
    fun horaRepetidaETrocaDeFusoMantemUmaUnicaOcorrenciaPorAviso() {
        val store = StoreProfile()
        val order = order(store, status = SalesOrderStatus.ReadyToShip).copy(
            shippingDueAt = date(5, month = 11),
        )
        val settings = enabled.copy(hour = 1, minute = 30)
        val reminders = plan(listOf(order), stores = listOf(store), settings = settings, now = date(3, month = 11))
        val due = reminders.first { it.timing == OrderReminder.Timing.DueDay }
        assertEquals(3, reminders.size)
        assertEquals(-4 * 3600, due.fireDate.atZone(zone).offset.totalSeconds)

        val otherZone = ZoneId.of("America/Sao_Paulo")
        val changed = OrderReminderPlanner.reminders(
            orders = listOf(order),
            stores = listOf(store),
            settings = settings,
            now = date(3, month = 11),
            zone = otherZone,
        )
        val changedDue = changed.first { it.id == due.id }
        assertNotEquals(due.fireDate, changedDue.fireDate)
        assertNotEquals(due.signature(), changedDue.signature())
    }
}

private class TestReminderCenter : OrderReminderCenter {
    var status: OrderReminderAuthorization = OrderReminderAuthorization.Authorized
    val pending = ArrayList<PendingOrderReminder>()
    val added = ArrayList<OrderReminder>()
    var failedID: String? = null
    var permissionRequests = 0
    var pauseNextAdd = false
    var onPausedAdd: CompletableDeferred<Unit>? = null
    var addGate: CompletableDeferred<Unit>? = null

    override suspend fun authorization(): OrderReminderAuthorization = status

    override suspend fun requestAuthorization(): Boolean {
        permissionRequests += 1
        return true
    }

    override suspend fun pendingRequests(): List<PendingOrderReminder> = pending.toList()

    override suspend fun add(reminder: OrderReminder) {
        if (pauseNextAdd) {
            pauseNextAdd = false
            onPausedAdd?.complete(Unit)
            addGate?.await()
        }
        if (reminder.id == failedID) {
            throw IllegalStateException("Falha simulada ao agendar")
        }
        pending.removeAll { it.id == reminder.id }
        pending.add(PendingOrderReminder(id = reminder.id, signature = reminder.signature()))
        added.add(reminder)
    }

    override suspend fun removePending(identifiers: List<String>) {
        val set = identifiers.toSet()
        pending.removeAll { it.id in set }
    }
}
