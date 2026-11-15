package com.jonathaxs.tinystock.notifications

import com.jonathaxs.tinystock.core.data.AppPreferences
import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.OrderReminderIssue
import com.jonathaxs.tinystock.core.model.OrderReminderRoute
import com.jonathaxs.tinystock.core.model.OrderReminderSettings
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.service.OrderReminderAuthorization
import com.jonathaxs.tinystock.core.service.OrderReminderService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** O que a pagina de Notificacoes mostra. */
data class OrderReminderState(
    val settings: OrderReminderSettings = OrderReminderSettings(),
    val authorization: OrderReminderAuthorization = OrderReminderAuthorization.Unknown,
    val issue: OrderReminderIssue? = null,
    val scheduledCount: Int = 0,
    val deferredCount: Int = 0,
    val isLoaded: Boolean = false,
    val isSynchronizing: Boolean = false,
)

/**
 * Preferencias, permissao e fila dos lembretes do app inteiro. Porte do `OrderReminderCoordinator` +
 * `OrderReminderLifecycle` do iOS: renova a fila ao abrir o app, ao mudar lojas ou pedidos e a cada aviso.
 */
class OrderReminderCoordinator(
    private val store: TinyStockStore,
    private val preferences: AppPreferences,
    private val center: WorkManagerOrderReminderCenter,
    private val scope: CoroutineScope,
) {
    private val service = OrderReminderService(center)
    private val mutex = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val _state = MutableStateFlow(OrderReminderState())
    val state: StateFlow<OrderReminderState> = _state.asStateFlow()

    /** Toque numa notificacao esperando a Inicio abrir o pedido. */
    private val _route = MutableStateFlow<OrderReminderRoute?>(null)
    val route: StateFlow<OrderReminderRoute?> = _route.asStateFlow()

    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            loadSettings()
            // Pedidos seguidos (varias telas salvando) viram uma so renovacao.
            for (request in requests) refreshNow()
        }
        scope.launch {
            // O primeiro valor e o estado atual: a renovacao inicial ja vem do pedido abaixo.
            store.observeReminderChanges().drop(1).debounce(500).collect { requestRefresh() }
        }
        requestRefresh()
    }

    fun requestRefresh() {
        requests.trySend(Unit)
    }

    fun open(route: OrderReminderRoute) {
        _route.value = route
    }

    fun consumeRoute() {
        _route.value = null
    }

    /** Ligar ou desligar os lembretes. Ligar sem permissao deixa a tela pedir a do sistema. */
    suspend fun setEnabled(enabled: Boolean) {
        if (!persist(_state.value.settings.copy(isEnabled = enabled))) return
        refreshNow()
    }

    suspend fun update(transform: (OrderReminderSettings) -> OrderReminderSettings) {
        // Le a versao mais recente para nao sobrescrever outro controle alterado em seguida.
        if (!persist(transform(_state.value.settings))) return
        refreshNow()
    }

    /** Resposta do pedido de permissao do sistema (Android 13+). */
    suspend fun onPermissionResult() {
        preferences.setNotificationPermissionRequested()
        refreshNow()
    }

    /** Relogio ou fuso do aparelho mudou: refaz a fila inteira. */
    suspend fun rebuild() {
        mutex.withLock { runCatching { center.removeAll() } }
        refreshNow()
    }

    /** Reconcilia a fila com o plano atual. Chamadas sobrepostas esperam a anterior terminar. */
    suspend fun refreshNow() {
        if (!_state.value.isLoaded) loadSettings()
        mutex.withLock {
            _state.update { it.copy(isSynchronizing = true) }
            try {
                val settings = _state.value.settings
                val snapshot = try {
                    if (settings.isEnabled) store.stores() to store.allOrders() else emptyList<StoreProfile>() to emptyList<SalesOrder>()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    val authorization = runCatching { center.authorization() }.getOrDefault(OrderReminderAuthorization.Unknown)
                    _state.update { it.copy(issue = OrderReminderIssue.DataRead, authorization = authorization) }
                    return
                }
                try {
                    val result = service.synchronize(orders = snapshot.second, stores = snapshot.first, settings = settings)
                    _state.update { current ->
                        current.copy(
                            authorization = result.authorization,
                            scheduledCount = result.scheduledIDs.size,
                            deferredCount = result.deferredIDs.size,
                            issue = when {
                                result.failedIDs.isNotEmpty() -> OrderReminderIssue.Scheduling
                                current.issue == OrderReminderIssue.DataRead || current.issue == OrderReminderIssue.Scheduling -> null
                                else -> current.issue
                            },
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    _state.update { it.copy(issue = OrderReminderIssue.Scheduling) }
                }
            } finally {
                _state.update { it.copy(isSynchronizing = false) }
            }
        }
    }

    private suspend fun loadSettings() {
        mutex.withLock {
            if (_state.value.isLoaded) return
            val loaded = try {
                preferences.loadOrderReminderSettings() to null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                OrderReminderSettings() to OrderReminderIssue.Preferences
            }
            _state.update { it.copy(settings = loaded.first, issue = loaded.second, isLoaded = true) }
        }
    }

    private suspend fun persist(settings: OrderReminderSettings): Boolean = try {
        preferences.saveOrderReminderSettings(settings)
        _state.update { it.copy(settings = settings, issue = null) }
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        _state.update { it.copy(issue = OrderReminderIssue.Preferences) }
        false
    }
}
