package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.model.OrderReminder
import com.jonathaxs.tinystock.core.model.OrderReminderSettings
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.StoreProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

enum class OrderReminderAuthorization {
    NotDetermined,
    Denied,
    Authorized,
    Provisional,
    Unknown;

    val allowsScheduling: Boolean
        get() = this == Authorized || this == Provisional
}

data class PendingOrderReminder(
    val id: String,
    val signature: String?,
)

/** Permite testar o agendamento sem solicitar permissao nem emitir alertas reais. */
interface OrderReminderCenter {
    suspend fun authorization(): OrderReminderAuthorization
    suspend fun requestAuthorization(): Boolean
    suspend fun pendingRequests(): List<PendingOrderReminder>
    suspend fun add(reminder: OrderReminder)
    suspend fun removePending(identifiers: List<String>)
}

data class OrderReminderSyncResult(
    val authorization: OrderReminderAuthorization,
    val scheduledIDs: List<String>,
    val deferredIDs: List<String>,
    val failedIDs: List<String>,
)

/**
 * Reconcilia o plano completo de lembretes com os agendamentos pendentes. Porte do `OrderReminderService`.
 */
class OrderReminderService(
    private val center: OrderReminderCenter,
) {
    private val mutex = Mutex()

    suspend fun authorization(): OrderReminderAuthorization = center.authorization()

    /** Solicitar permissao somente na acao explicita de ativar notificacoes. */
    suspend fun requestAuthorization(): Boolean = center.requestAuthorization()

    /**
     * Deve receber um retrato completo de todas as lojas, apos salvar os dados com sucesso.
     * O teto de 60 e uma politica conservadora do app; a fila prioriza os prazos proximos.
     */
    suspend fun synchronize(
        orders: List<SalesOrder>,
        stores: List<StoreProfile>,
        settings: OrderReminderSettings,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): OrderReminderSyncResult {
        // Os modelos sao lidos antes da espera para nao misturar estados de duas edicoes.
        val plan = OrderReminderPlanner.reminders(
            orders = orders,
            stores = stores,
            settings = settings,
            now = now,
            zone = zone,
        )
        // Serializa inclusive chamadas sobrepostas durante os awaits do centro.
        return mutex.withLock {
            reconcile(plan, center)
        }
    }

    companion object {
        private const val MAX_PENDING_NOTIFICATIONS = 60

        private suspend fun reconcile(
            plan: List<OrderReminder>,
            center: OrderReminderCenter,
        ): OrderReminderSyncResult {
            val authorization = center.authorization()
            val pending = center.pendingRequests()
            val owned = pending.filter { it.id.startsWith(OrderReminder.IDENTIFIER_PREFIX) }
            val foreignCount = pending.size - owned.size
            val capacity = maxOf(0, MAX_PENDING_NOTIFICATIONS - foreignCount)

            // IDs repetidos nao consomem duas vagas nem geram duas chamadas ao sistema.
            val seen = HashSet<String>()
            val unique = plan.filter { seen.add(it.id) }
            val desired = if (authorization.allowsScheduling) unique.take(capacity) else emptyList()
            val desiredIDs = desired.mapTo(HashSet()) { it.id }
            val obsolete = owned.filter { it.id !in desiredIDs }.map { it.id }
            if (obsolete.isNotEmpty()) {
                center.removePending(obsolete)
            }

            val scheduledIDs = ArrayList<String>()
            val failedIDs = ArrayList<String>()
            for (reminder in desired) {
                try {
                    val signature = reminder.signature()
                    if (owned.none { it.id == reminder.id && it.signature == signature }) {
                        center.add(reminder)
                    }
                    scheduledIDs.add(reminder.id)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // Se a substituicao falhar, um prazo antigo nao deve continuar avisando.
                    center.removePending(listOf(reminder.id))
                    failedIDs.add(reminder.id)
                }
            }

            val deferredIDs = if (authorization.allowsScheduling) {
                unique.drop(capacity).map { it.id }
            } else {
                unique.map { it.id }
            }
            return OrderReminderSyncResult(
                authorization = authorization,
                scheduledIDs = scheduledIDs,
                deferredIDs = deferredIDs,
                failedIDs = failedIDs,
            )
        }
    }
}
