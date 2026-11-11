package com.jonathaxs.tinystock.ui.calendar

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jonathaxs.tinystock.container
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import com.jonathaxs.tinystock.core.service.SalesOrderManagementService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Calendario em dia ou lista. Os valores crus sao os do iOS (`orders.displayMode`). */
enum class OrderDisplayMode(val rawValue: String) {
    Day("day"),
    List("list");

    companion object {
        fun fromRawValue(raw: String?): OrderDisplayMode = entries.firstOrNull { it.rawValue == raw } ?: Day
    }
}

/** Pedidos da loja aberta e as operacoes da fila (avancar e cancelar). */
class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val store = application.container.store
    private val preferences = application.container.preferences
    private val storeID = MutableStateFlow<UUID?>(null)

    /** Null enquanto o banco ainda nao respondeu. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val orders: StateFlow<List<SalesOrder>?> = storeID.filterNotNull()
        .flatMapLatest { id -> store.observeOrders(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val displayMode: StateFlow<OrderDisplayMode> = preferences.ordersDisplayMode
        .map { OrderDisplayMode.fromRawValue(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, OrderDisplayMode.Day)

    /** Erro da ultima operacao, mostrado num alerta. */
    @get:StringRes
    var errorRes by mutableStateOf<Int?>(null)
        private set

    fun setStore(id: UUID) {
        storeID.value = id
    }

    fun setDisplayMode(mode: OrderDisplayMode) {
        viewModelScope.launch { preferences.setOrdersDisplayMode(mode.rawValue) }
    }

    fun transition(order: SalesOrder, status: SalesOrderStatus) {
        viewModelScope.launch {
            runCatching { SalesOrderManagementService.transition(order.id, status, store = store) }
                .onFailure { errorRes = OrderPresentation.errorMessage(it) }
        }
    }

    fun cancel(order: SalesOrder, reason: String) {
        viewModelScope.launch {
            runCatching { SalesOrderManagementService.cancel(order.id, reason, store = store) }
                .onFailure { errorRes = OrderPresentation.errorMessage(it) }
        }
    }

    fun clearError() {
        errorRes = null
    }
}
