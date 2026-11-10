package com.jonathaxs.tinystock.ui.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jonathaxs.tinystock.container
import com.jonathaxs.tinystock.core.model.SalesOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/** Pedidos da loja aberta para os Relatorios. Null enquanto o banco ainda nao respondeu. */
class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = application.container.store
    private val storeID = MutableStateFlow<UUID?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val orders: StateFlow<List<SalesOrder>?> = storeID.filterNotNull()
        .flatMapLatest { id -> store.observeOrders(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setStore(id: UUID) {
        storeID.value = id
    }
}
