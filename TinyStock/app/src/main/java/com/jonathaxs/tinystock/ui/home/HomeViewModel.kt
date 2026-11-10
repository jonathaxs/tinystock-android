package com.jonathaxs.tinystock.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.jonathaxs.tinystock.container
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
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
        viewModelScope.launch { preferences.setCatalogMode(mode.rawValue) }
    }

    fun toggleViewMode() {
        viewModelScope.launch { preferences.setCatalogViewMode(viewMode.value.next.rawValue) }
    }

    fun setStore(id: UUID) {
        storeID.value = id
    }
}
