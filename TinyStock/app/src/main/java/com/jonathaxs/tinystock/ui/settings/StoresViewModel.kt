package com.jonathaxs.tinystock.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jonathaxs.tinystock.container
import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.service.StoreDeletionService
import com.jonathaxs.tinystock.core.service.StoreDeletionSummary
import com.jonathaxs.tinystock.core.service.StoreProfileService
import com.jonathaxs.tinystock.ui.products.FormError
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Dados do pedido de exclusao definitiva na lixeira. */
data class StoreDeletionRequest(
    val store: StoreProfile,
    val summary: StoreDeletionSummary,
)

/**
 * Estado e operacoes de gerenciamento das lojas (ativas, arquivadas e lixeira).
 */
class StoresViewModel(application: Application) : AndroidViewModel(application) {

    private val store: TinyStockStore = application.container.store
    private val allStores = store.observeStores()

    val activeStores: StateFlow<List<StoreProfile>> = allStores
        .map { stores -> StoreProfileService.orderedForDisplay(stores.filter { it.isActive }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val archivedStores: StateFlow<List<StoreProfile>> = allStores
        .map { stores -> StoreProfileService.orderedArchivedForDisplay(stores) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trashedStores: StateFlow<List<StoreProfile>> = allStores
        .map { stores -> StoreProfileService.orderedTrashedForDisplay(stores) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Loja aguardando confirmacao para ir para a lixeira. */
    var trashRequest by mutableStateOf<StoreProfile?>(null)
        private set

    /** Loja na lixeira aguardando confirmacao para exclusao definitiva. */
    var deletionRequest by mutableStateOf<StoreDeletionRequest?>(null)
        private set

    /** Erro da ultima operacao, exibido num alerta. */
    var error by mutableStateOf<FormError?>(null)
        private set

    fun requestTrash(profile: StoreProfile) {
        trashRequest = profile
    }

    fun cancelTrash() {
        trashRequest = null
    }

    fun cancelDeletion() {
        deletionRequest = null
    }

    fun clearError() {
        error = null
    }

    /** Move uma loja ativa uma posicao para cima ou para baixo e salva a nova ordem. */
    fun moveActiveStore(profile: StoreProfile, up: Boolean) {
        val current = activeStores.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index == -1) return
        val target = if (up) index - 1 else index + 1
        if (target !in current.indices) return
        val item = current.removeAt(index)
        current.add(target, item)
        viewModelScope.launch {
            runCatching { StoreProfileService.setDisplayOrder(current, store = store) }
                .onFailure { error = storeErrorMessage(it) }
        }
    }

    /** Arquiva uma loja ativa e troca a selecao se ela era a loja atual. */
    fun archive(
        profile: StoreProfile,
        selectedStoreID: UUID?,
        onSelectStore: (StoreProfile) -> Unit,
    ) {
        deactivate(profile, selectedStoreID, onSelectStore) {
            StoreProfileService.archive(profile, store = store)
        }
    }

    /** Move uma loja para a lixeira e troca a selecao se ela era a loja atual. */
    fun moveToTrash(
        profile: StoreProfile,
        selectedStoreID: UUID? = null,
        onSelectStore: (StoreProfile) -> Unit = {},
    ) {
        trashRequest = null
        deactivate(profile, selectedStoreID, onSelectStore) {
            StoreDeletionService.moveToTrash(profile, store = store)
        }
    }

    private fun deactivate(
        profile: StoreProfile,
        selectedStoreID: UUID?,
        onSelectStore: (StoreProfile) -> Unit,
        operation: suspend () -> Unit,
    ) {
        val wasSelected = profile.id == selectedStoreID
        val replacement = activeStores.value.firstOrNull { it.id != profile.id }
        viewModelScope.launch {
            runCatching { operation() }
                .onSuccess {
                    if (wasSelected && replacement != null) {
                        onSelectStore(replacement)
                    }
                }
                .onFailure { error = storeErrorMessage(it) }
        }
    }

    /** Restaura uma loja arquivada para a lista de ativas. */
    fun restoreArchived(profile: StoreProfile) {
        viewModelScope.launch {
            runCatching { StoreProfileService.restore(profile, store = store) }
                .onFailure { error = storeErrorMessage(it) }
        }
    }

    /** Restaura uma loja da lixeira para o estado anterior (ativa ou arquivada). */
    fun restoreFromTrash(profile: StoreProfile) {
        viewModelScope.launch {
            runCatching { StoreDeletionService.restoreFromTrash(profile, store = store) }
                .onFailure { error = storeErrorMessage(it) }
        }
    }

    /** Calcula o resumo de itens vinculados antes de pedir a confirmacao de exclusao definitiva. */
    fun requestPermanentDeletion(profile: StoreProfile) {
        viewModelScope.launch {
            runCatching { StoreDeletionService.deletionSummary(profile, store = store) }
                .onSuccess { summary -> deletionRequest = StoreDeletionRequest(profile, summary) }
                .onFailure { error = storeErrorMessage(it) }
        }
    }

    /** Apaga definitivamente uma loja da lixeira e garante uma loja ativa selecionada. */
    fun deletePermanently(
        profile: StoreProfile,
        selectedStoreID: UUID?,
        onSelectStore: (StoreProfile) -> Unit,
    ) {
        deletionRequest = null
        viewModelScope.launch {
            runCatching {
                val replacement = StoreDeletionService.deletePermanently(profile, store = store)
                val selectedStore = store.stores().firstOrNull { it.id == selectedStoreID && it.isActive }
                selectedStore ?: replacement
            }.onSuccess { target ->
                if (target != null) {
                    onSelectStore(target)
                }
            }.onFailure { error = storeErrorMessage(it) }
        }
    }
}
