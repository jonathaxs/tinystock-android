package com.jonathaxs.tinystock.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.service.ProductVariantService
import com.jonathaxs.tinystock.core.service.StockService
import com.jonathaxs.tinystock.ui.components.ProductImage
import com.jonathaxs.tinystock.ui.form.FormMenuOption
import com.jonathaxs.tinystock.ui.form.FormMenuRow
import com.jonathaxs.tinystock.ui.form.FormPage
import com.jonathaxs.tinystock.ui.form.FormSection
import com.jonathaxs.tinystock.ui.form.FormTextField
import com.jonathaxs.tinystock.ui.form.PhotoField
import com.jonathaxs.tinystock.ui.form.StockQuantityStepper
import java.text.NumberFormat
import java.time.Instant
import java.util.UUID

/** Foto e nome do produto no topo das telas de variacao e estoque, sem card. */
@Composable
internal fun ProductSheetHeader(product: Product) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProductImage(product.imageFileName, side = 48.dp)
        Text(product.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun variantOptions(variants: List<ProductVariant>): List<FormMenuOption<UUID>> = variants.map {
    FormMenuOption(it.id, stringResource(R.string.order_form_variant_option, it.name, NumberFormat.getIntegerInstance().format(it.quantity)))
}

private val capitalizeWords = KeyboardOptions(capitalization = KeyboardCapitalization.Words)

/** Nova variacao num produto existente, com foto e estoque inicial. Porte do `NewProductVariantView`. */
@Composable
fun NewVariantScreen(
    storeID: UUID,
    initialProductID: UUID?,
    onClose: () -> Unit,
    viewModel: ProductFormsViewModel = viewModel(),
) {
    LaunchedEffect(storeID) { viewModel.setStore(storeID) }
    val data by viewModel.data.collectAsStateWithLifecycle()
    var productID by rememberSaveable { mutableStateOf(initialProductID?.toString()) }
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableIntStateOf(1) }
    val photo = viewModel.photo("newVariant", null)

    // Aberta pelo Adicionar, comeca pelo primeiro produto do catalogo.
    LaunchedEffect(data.loaded) {
        if (productID == null) productID = data.products.firstOrNull()?.id?.toString()
    }
    val product = data.product(productID?.let(UUID::fromString))
    // Um produto sem variacoes precisa ativar a opcao na edicao antes de receber outra.
    val usesDefault = data.variants(product).any { it.isDefault }
    val canSave = product != null && !usesDefault && !photo.isProcessing && name.isNotBlank()
    val stockTitle = stringResource(R.string.product_form_variant_initialStock)

    FormPage(
        title = stringResource(R.string.stock_entry_newVariant),
        onClose = onClose,
        onConfirm = {
            val selected = product ?: return@FormPage
            viewModel.save(photo, onSuccess = onClose) { imageFileName ->
                val store = viewModel.store
                store.inTransaction {
                    val variant = ProductVariantService.create(selected, name, initialQuantity = quantity, store = store)
                    if (imageFileName != null) {
                        store.saveVariant(variant.copy(imageFileName = imageFileName, updatedAt = Instant.now()))
                    }
                }
            }
        },
        confirmEnabled = canSave && !viewModel.isSaving,
    ) {
        item("photo") { PhotoField(photo, onError = viewModel::showError) }
        item("product") {
            FormSection(
                stringResource(R.string.order_form_section_product),
                footer = if (usesDefault) stringResource(R.string.product_variant_new_defaultFooter) else null,
            ) {
                FormMenuRow(
                    label = stringResource(R.string.order_form_section_product),
                    selected = product?.id,
                    options = data.products.map { FormMenuOption(it.id, it.name) },
                    onSelect = { productID = it.toString() },
                )
            }
        }
        item("name") {
            FormSection(stringResource(R.string.product_variant_edit_name)) {
                FormTextField(name, { name = it }, stringResource(R.string.product_form_variant_name), keyboardOptions = capitalizeWords)
            }
        }
        item("stock") {
            FormSection(stockTitle) {
                StockQuantityStepper(stockTitle, quantity, onQuantityChange = { quantity = it })
            }
        }
    }
    FormErrorAlert(viewModel, R.string.product_form_error_title)
}

/** Renomeia a variacao e corrige o saldo com ajuste no historico. Porte do `EditProductVariantView`. */
@Composable
fun EditVariantScreen(
    storeID: UUID,
    productID: UUID,
    initialVariantID: UUID?,
    onClose: () -> Unit,
    viewModel: ProductFormsViewModel = viewModel(),
) {
    LaunchedEffect(storeID) { viewModel.setStore(storeID) }
    val data by viewModel.data.collectAsStateWithLifecycle()
    val product = data.product(productID)
    val variants = data.variants(product)
    var selectedID by rememberSaveable { mutableStateOf(initialVariantID?.toString()) }
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableIntStateOf(0) }
    var loadedID by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(variants.isNotEmpty()) {
        if (selectedID == null) selectedID = variants.firstOrNull()?.id?.toString()
    }
    val selected = variants.firstOrNull { it.id.toString() == selectedID }
    // Trocar de variacao recarrega os campos dela.
    LaunchedEffect(selected?.id) {
        val variant = selected ?: return@LaunchedEffect
        if (loadedID == variant.id.toString()) return@LaunchedEffect
        name = if (variant.isDefault) "" else variant.name
        quantity = variant.quantity
        loadedID = variant.id.toString()
    }
    if (product == null || selected == null) return

    // Produto antigo sem variacoes: a variacao interna nao tem nome nem foto, so o saldo e editavel.
    val internal = selected.isDefault
    val photo = viewModel.photo(selected.id.toString(), selected.imageFileName)
    val renamed = !internal && name.trim() != selected.name
    val hasChanges = renamed || quantity != selected.quantity || photo.changed
    val canSave = hasChanges && !photo.isProcessing && (internal || name.isNotBlank())
    val stockTitle = stringResource(R.string.product_variant_edit_stock)

    FormPage(
        title = stringResource(R.string.product_variant_edit_title),
        onClose = onClose,
        onConfirm = {
            viewModel.save(if (internal) null else photo, onSuccess = onClose) { imageFileName ->
                val store = viewModel.store
                store.inTransaction {
                    var current = selected
                    if (renamed) current = ProductVariantService.rename(current, name, product, store = store)
                    if (!internal && photo.changed) {
                        current = current.copy(imageFileName = imageFileName, updatedAt = Instant.now())
                        store.saveVariant(current)
                    }
                    if (quantity != current.quantity) {
                        StockService.registerAdjustment(quantity, current, product, store = store)
                    }
                }
            }
        },
        confirmEnabled = canSave && !viewModel.isSaving,
    ) {
        item("header") { ProductSheetHeader(product) }
        if (!internal) item("photo-${selected.id}") { PhotoField(photo, onError = viewModel::showError) }
        if (variants.size > 1) {
            item("variant") {
                FormSection(stringResource(R.string.product_form_variant_title)) {
                    FormMenuRow(
                        label = stringResource(R.string.product_form_variant_title),
                        selected = selected.id,
                        options = variantOptions(variants),
                        onSelect = { selectedID = it.toString() },
                    )
                }
            }
        }
        if (!internal) {
            item("name") {
                FormSection(stringResource(R.string.product_variant_edit_name)) {
                    FormTextField(name, { name = it }, stringResource(R.string.product_form_variant_name), keyboardOptions = capitalizeWords)
                }
            }
        }
        // Aceita zero para desfazer uma entrada feita por engano.
        item("stock") {
            FormSection(stockTitle) {
                StockQuantityStepper(stockTitle, quantity, onQuantityChange = { quantity = it })
            }
        }
    }
    FormErrorAlert(viewModel, R.string.product_form_error_title)
}

/** Recebimento de unidades numa variacao existente. Porte do `StockEntryView`. */
@Composable
fun StockEntryScreen(
    storeID: UUID,
    productID: UUID,
    initialVariantID: UUID?,
    onClose: () -> Unit,
    viewModel: ProductFormsViewModel = viewModel(),
) {
    LaunchedEffect(storeID) { viewModel.setStore(storeID) }
    val data by viewModel.data.collectAsStateWithLifecycle()
    val product = data.product(productID)
    val variants = data.variants(product)
    var selectedID by rememberSaveable { mutableStateOf(initialVariantID?.toString()) }
    var quantity by rememberSaveable { mutableIntStateOf(1) }
    var note by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(variants.isNotEmpty()) {
        if (selectedID == null) selectedID = variants.firstOrNull()?.id?.toString()
    }
    if (product == null) return
    val selected = variants.firstOrNull { it.id.toString() == selectedID }
    val internal = variants.size == 1 && variants.first().isDefault
    val current = selected?.quantity ?: 0
    val result = current.toLong() + quantity
    val resultValid = quantity > 0 && result <= Int.MAX_VALUE
    val format = NumberFormat.getIntegerInstance()
    val summary = buildString {
        append(stringResource(R.string.stock_entry_currentSummary, format.format(current)))
        if (resultValid) append("\n").append(stringResource(R.string.stock_entry_resultSummary, format.format(result)))
    }
    val quantityTitle = stringResource(R.string.stock_entry_quantity)

    FormPage(
        // O titulo completo nao cabe entre o X e o ✓; o cabecalho mostra o produto.
        title = stringResource(R.string.stock_entry_navigationTitle),
        onClose = onClose,
        onConfirm = {
            val variant = selected ?: return@FormPage
            viewModel.save(null, onSuccess = onClose) {
                StockService.registerEntry(quantity, product, variant.id, note = note, store = viewModel.store)
            }
        },
        confirmEnabled = resultValid && selected != null && !viewModel.isSaving,
    ) {
        item("header") { ProductSheetHeader(product) }
        if (!internal) {
            item("variant") {
                FormSection(stringResource(R.string.product_form_variant_title)) {
                    FormMenuRow(
                        label = stringResource(R.string.product_form_variant_title),
                        selected = selected?.id,
                        options = variantOptions(variants),
                        onSelect = { selectedID = it.toString() },
                    )
                }
            }
        }
        item("quantity") {
            FormSection(quantityTitle, footer = summary) {
                StockQuantityStepper(quantityTitle, quantity, onQuantityChange = { quantity = it }, range = 1..999_999)
            }
        }
        item("note") {
            FormSection(stringResource(R.string.stock_entry_note)) {
                FormTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = stringResource(R.string.stock_entry_notePlaceholder),
                    label = stringResource(R.string.stock_entry_note),
                    singleLine = false,
                )
            }
        }
    }
    FormErrorAlert(viewModel, R.string.stock_entry_error)
}
