package com.jonathaxs.tinystock.ui.home

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.jonathaxs.tinystock.container
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.service.ProductService
import com.jonathaxs.tinystock.ui.form.productErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/** Dados da loja aberta que a Inicio mostra. `loaded` evita piscar o catalogo vazio na abertura. */
data class HomeData(
    val products: List<Product> = emptyList(),
    val variants: List<ProductVariant> = emptyList(),
    val orders: List<SalesOrder> = emptyList(),
    val loaded: Boolean = false,
)

/** Erro ao apagar ou reordenar: texto conhecido ([message]) ou o da excecao ([fallback]). */
data class CatalogError(@StringRes val message: Int?, val fallback: String?)

/** Le do banco os produtos, variacoes e pedidos da loja aberta, e acompanha cada mudanca. */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = application.container.store
    private val preferences = application.container.preferences
    private val storeID = MutableStateFlow<UUID?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val data: StateFlow<HomeData> = storeID.filterNotNull().flatMapLatest { id ->
        combine(store.observeProducts(id), store.observeVariants(id), store.observeOrders(id)) { products, variants, orders ->
            HomeData(
                // Ordem escolhida no catalogo; empate pelo nome, como o iOS.
                products = products.sortedWith(compareBy<Product> { it.sortOrder }.thenBy { it.name }),
                variants = variants,
                orders = orders,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeData())

    /** Produtos ou variacoes, lembrado entre aberturas como no iOS. */
    val catalogMode: StateFlow<CatalogMode> = preferences.catalogMode
        .map { CatalogMode.fromRawValue(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CatalogMode.Products)

    /** Lista ou grade, lembrado entre aberturas como no iOS. */
    val viewMode: StateFlow<CatalogViewMode> = preferences.catalogViewMode
        .map { CatalogViewMode.fromRawValue(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CatalogViewMode.List)

    fun setCatalogMode(mode: CatalogMode) {
        isEditing = false
        viewModelScope.launch { preferences.setCatalogMode(mode.rawValue) }
    }

    fun toggleViewMode() {
        // Editar (apagar e reordenar) so existe na lista.
        isEditing = false
        viewModelScope.launch { preferences.setCatalogViewMode(viewMode.value.next.rawValue) }
    }

    fun setStore(id: UUID) {
        if (storeID.value != id) {
            // Nao leva o modo de edicao nem a exclusao pendente de uma loja para outra.
            isEditing = false
            pendingDeletion = null
        }
        storeID.value = id
    }

    // region Editar do catalogo (porte do modo de edicao do `ProductsView` do iOS)

    var isEditing by mutableStateOf(false)
        private set

    /** Produto esperando a confirmacao para ser apagado. */
    var pendingDeletion by mutableStateOf<Product?>(null)
        private set

    var error by mutableStateOf<CatalogError?>(null)
        private set

    fun toggleEditing() {
        isEditing = !isEditing
    }

    /** Catalogo ficou vazio (ex.: apagou o ultimo produto): sai do modo de edicao. */
    fun stopEditing() {
        isEditing = false
    }

    fun requestDeletion(product: Product) {
        pendingDeletion = product
    }

    fun cancelDeletion() {
        pendingDeletion = null
    }

    fun dismissError() {
        error = null
    }

    fun confirmDeletion() {
        val product = pendingDeletion ?: return
        pendingDeletion = null
        viewModelScope.launch {
            try {
                ProductService.delete(product, store)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                error = CatalogError(productErrorMessage(failure), failure.localizedMessage)
            }
        }
    }

    /** Sobe ([offset] = -1) ou desce (+1) o produto e grava a ordem completa do catalogo. */
    fun move(product: Product, offset: Int) {
        val ordered = data.value.products.toMutableList()
        val from = ordered.indexOfFirst { it.id == product.id }
        val to = from + offset
        if (from < 0 || to !in ordered.indices) return
        ordered.add(to, ordered.removeAt(from))
        viewModelScope.launch {
            try {
                ProductService.setDisplayOrder(ordered, store)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                error = CatalogError(productErrorMessage(failure), failure.localizedMessage)
            }
        }
    }

    // endregion
}
