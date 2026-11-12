package com.jonathaxs.tinystock.ui.main

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.container
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.service.StoreProfileService
import com.jonathaxs.tinystock.core.service.StoreSession
import com.jonathaxs.tinystock.ui.calendar.CalendarOrderFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** O que a raiz do app mostra. */
enum class LaunchState { Loading, Failed, Welcome, Main }

/** Telas que abrem em sheet por cima da Inicio (equivale ao `AppSheet` do iOS). */
enum class AppSheet { Reports, Calendar }

/**
 * Estado da raiz do app: abre o banco, escolhe a loja, decide entre boas-vindas e Inicio e guarda a sheet
 * aberta. Faz o papel do `TinyStockApp` + `AppRouter` do iOS.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.container
    private val store = container.store
    private val preferences = container.preferences

    private var session: StoreSession? = null
    private val selectedID = MutableStateFlow<UUID?>(null)
    private val bootstrapped = MutableStateFlow<Boolean?>(null)

    val selectedStoreID: StateFlow<UUID?> = selectedID.asStateFlow()

    val accentColor: StateFlow<AppAccentColor> = preferences.accentColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppAccentColor.Default)

    val launchState: StateFlow<LaunchState> = combine(bootstrapped, preferences.didCompleteWelcome) { ok, done ->
        when (ok) {
            null -> LaunchState.Loading
            false -> LaunchState.Failed
            true -> if (done) LaunchState.Main else LaunchState.Welcome
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LaunchState.Loading)

    /** Lojas ativas na ordem escolhida pelo usuario. */
    val activeStores: StateFlow<List<StoreProfile>> = store.observeStores()
        .map { stores -> StoreProfileService.orderedForDisplay(stores.filter { it.isActive }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedStore: StateFlow<StoreProfile?> = combine(activeStores, selectedID) { stores, id ->
        stores.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Sheet aberta por cima da Inicio (null = nenhuma). */
    var sheet by mutableStateOf<AppSheet?>(null)
        private set

    init {
        viewModelScope.launch {
            try {
                val created = StoreSession.bootstrap(
                    defaultStoreName = application.getString(R.string.store_default_name),
                    storage = preferences,
                    store = store,
                )
                session = created
                selectedID.value = created.selectedStoreID
                markWelcomeCompletedIfHasData()
                bootstrapped.value = true
            } catch (error: Exception) {
                bootstrapped.value = false
            }
        }
    }

    /** Quem ja tem dados (atualizacao do app ou banco restaurado) nao ve a apresentacao. */
    private suspend fun markWelcomeCompletedIfHasData() {
        if (preferences.didCompleteWelcome.first()) return
        val hasData = store.allProducts().isNotEmpty() || store.allOrders().isNotEmpty() || store.stores().size > 1
        if (hasData) preferences.setDidCompleteWelcome(true)
    }

    fun select(profile: StoreProfile) {
        val session = session ?: return
        viewModelScope.launch {
            // Loja arquivada em outro lugar nesse meio tempo: a troca e ignorada, como o `try?` do iOS.
            runCatching { session.select(profile) }.onSuccess { selectedID.value = session.selectedStoreID }
        }
    }

    /** Filtro pedido pelos Relatorios ao abrir o Calendario; o Calendario consome e limpa. */
    var calendarFilterRequest by mutableStateOf<CalendarOrderFilter?>(null)
        private set

    fun present(newSheet: AppSheet) {
        sheet = newSheet
    }

    fun openCalendar(filter: CalendarOrderFilter) {
        calendarFilterRequest = filter
        sheet = AppSheet.Calendar
    }

    fun consumeCalendarFilterRequest() {
        calendarFilterRequest = null
    }

    fun dismissSheet() {
        sheet = null
        editingOrderID = null
    }

    /** Pedido em edicao, aberto pelo lapis do detalhe; a tela cobre o Calendario dentro da sheet. */
    var editingOrderID by mutableStateOf<UUID?>(null)
        private set

    fun editOrder(id: UUID?) {
        editingOrderID = id
    }

    fun setAccentColor(color: AppAccentColor) {
        viewModelScope.launch { preferences.setAccentColor(color) }
    }

    fun completeWelcome() {
        viewModelScope.launch { preferences.setDidCompleteWelcome(true) }
    }

    /** Backup restaurado nas boas-vindas: abre a loja indicada pelo arquivo e entra no app. */
    fun onBackupRestored(storeID: UUID) {
        val session = session ?: return
        viewModelScope.launch {
            store.store(storeID)?.let { restored -> runCatching { session.select(restored) } }
            selectedID.value = session.selectedStoreID
            preferences.setDidCompleteWelcome(true)
        }
    }
}
