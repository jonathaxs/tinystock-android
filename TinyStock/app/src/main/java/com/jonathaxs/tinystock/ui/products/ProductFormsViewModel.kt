package com.jonathaxs.tinystock.ui.products

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jonathaxs.tinystock.container
import com.jonathaxs.tinystock.core.data.ImageFileStore
import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.ui.form.PhotoDraft
import com.jonathaxs.tinystock.ui.form.productErrorMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Produtos e variacoes da loja aberta, vistos pelos formularios. */
data class CatalogData(
    val products: List<Product> = emptyList(),
    val variants: List<ProductVariant> = emptyList(),
    val orders: List<SalesOrder> = emptyList(),
    val loaded: Boolean = false,
) {
    fun product(id: UUID?): Product? = products.firstOrNull { it.id == id }

    /** Variacoes do produto em ordem de nome, como o `@Query(sort: \.name)` do iOS. */
    fun variants(product: Product?): List<ProductVariant> =
        if (product == null) emptyList() else variants.filter { it.belongsTo(product) }.sortedBy { it.name.lowercase() }
}

/** Erro mostrado no alerta: texto da tela ou, para erro inesperado, a mensagem do sistema. */
data class FormError(val messageRes: Int?, val text: String?)

/**
 * Estado compartilhado pelos formularios de produto, variacao e estoque: dados da loja, fotos em edicao
 * (sobrevivem a rotacao) e a gravacao com tratamento de erro. Um por tela.
 */
class ProductFormsViewModel(application: Application) : AndroidViewModel(application) {

    val store: TinyStockStore = application.container.store
    val images: ImageFileStore = application.container.images
    private val storeID = MutableStateFlow<UUID?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val data: StateFlow<CatalogData> = storeID.filterNotNull().flatMapLatest { id ->
        combine(store.observeProducts(id), store.observeVariants(id), store.observeOrders(id)) { products, variants, orders ->
            CatalogData(products.sortedWith(compareBy<Product> { it.sortOrder }.thenBy { it.name }), variants, orders, loaded = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogData())

    var isSaving by mutableStateOf(false)
        private set
    var error by mutableStateOf<FormError?>(null)
        private set

    /** Estado do formulario guardado aqui para nao se perder ao girar a tela. Cada tela usa o seu tipo. */
    var formState: Any? = null

    private val photos = HashMap<String, PhotoDraft>()

    fun setStore(id: UUID) {
        storeID.value = id
    }

    /** Foto em edicao de um registro (chave: id do produto ou da variacao). */
    fun photo(key: String, originalFileName: String?): PhotoDraft =
        photos.getOrPut(key) { PhotoDraft(originalFileName) }

    fun showError(messageRes: Int) {
        error = FormError(messageRes, null)
    }

    fun clearError() {
        error = null
    }

    /**
     * Grava a foto (se houver), roda [block] com o nome dela e fecha a tela no sucesso. Em falha desfaz a
     * foto nova e mostra o erro; o banco ja volta atras pela transacao do servico.
     */
    fun save(
        photo: PhotoDraft?,
        onSuccess: () -> Unit,
        /** Texto do erro; o padrao cobre produto, variacao e estoque. */
        describe: (Throwable) -> FormError = { FormError(productErrorMessage(it), it.localizedMessage) },
        block: suspend (imageFileName: String?) -> Unit,
    ) {
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            var written: String? = null
            try {
                written = photo?.write(images)
                block(written)
                photo?.finish(images, written, succeeded = true)
                onSuccess()
            } catch (failure: Exception) {
                photo?.finish(images, written, succeeded = false)
                error = describe(failure)
            } finally {
                isSaving = false
            }
        }
    }
}
