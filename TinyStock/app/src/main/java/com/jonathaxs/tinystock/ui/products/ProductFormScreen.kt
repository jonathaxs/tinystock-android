package com.jonathaxs.tinystock.ui.products

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.format.CurrencyFormatter
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.service.ProductFormService
import com.jonathaxs.tinystock.core.service.ProductVariantInput
import com.jonathaxs.tinystock.core.service.ProductVariantService
import com.jonathaxs.tinystock.core.service.StockService
import com.jonathaxs.tinystock.ui.form.FormDivider
import com.jonathaxs.tinystock.ui.form.FormPage
import com.jonathaxs.tinystock.ui.form.FormSection
import com.jonathaxs.tinystock.ui.form.FormTextField
import com.jonathaxs.tinystock.ui.form.FormValueRow
import com.jonathaxs.tinystock.ui.form.PhotoField
import com.jonathaxs.tinystock.ui.form.StockQuantityStepper
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.UUID

/** Preco que o detalhe do produto pede para focar ao abrir a edicao. */
enum class PriceField { Sale, Cost }

/** Campos do formulario de produto, em memoria ate salvar. */
private class ProductFormState(product: Product?) {
    var name by mutableStateOf(product?.name.orEmpty())
    var costText by mutableStateOf(CurrencyFormatter.editableText(product?.costPrice ?: BigDecimal.ZERO))
    var saleText by mutableStateOf(CurrencyFormatter.editableText(product?.salePrice ?: BigDecimal.ZERO))

    // Produto novo sempre nasce com uma variacao nomeada, o que libera o estoque inicial.
    var hasVariations by mutableStateOf(product == null)
    var variationName by mutableStateOf("")
    var quantity by mutableIntStateOf(if (product == null) 1 else 0)
    var usesLegacyDefault by mutableStateOf(false)
    var variants by mutableStateOf<List<ProductVariantInput>>(emptyList())
    var loaded by mutableStateOf(product == null)
}

private fun price(text: String): BigDecimal? =
    if (text.isBlank()) BigDecimal.ZERO else CurrencyFormatter.decimal(text)

/**
 * Cadastro e edicao de produto com foto e variacoes, sem gravar nada antes do ✓. Porte do
 * `ProductFormView`. Na edicao, o saldo escolhido vira ajuste registrado no historico.
 */
@Composable
fun ProductFormScreen(
    storeID: UUID,
    productID: UUID?,
    initialFocus: PriceField?,
    onClose: () -> Unit,
    viewModel: ProductFormsViewModel = viewModel(),
) {
    LaunchedEffect(storeID) { viewModel.setStore(storeID) }
    val data by viewModel.data.collectAsStateWithLifecycle()
    val editing = data.product(productID)
    if (productID != null && editing == null) return // carregando

    @Suppress("UNCHECKED_CAST")
    val state = (viewModel.formState as? ProductFormState) ?: ProductFormState(editing).also { viewModel.formState = it }
    val photo = viewModel.photo(productID?.toString() ?: "new", editing?.imageFileName)

    // Edicao: le as variacoes uma vez, como o `loadVariants` do iOS.
    LaunchedEffect(editing?.id, data.loaded) {
        if (editing == null || state.loaded || !data.loaded) return@LaunchedEffect
        val stored = data.variants(editing)
        state.variants = stored.map {
            ProductVariantInput(id = it.id, existingID = it.id, name = if (it.isDefault) "" else it.name, initialQuantity = it.quantity)
        }
        state.hasVariations = stored.isNotEmpty() && stored.none { it.isDefault }
        state.usesLegacyDefault = stored.any { it.isDefault }
        state.variants.singleOrNull()?.let {
            state.variationName = it.name
            state.quantity = it.initialQuantity
        }
        state.loaded = true
    }

    var editingDraftID by remember { mutableStateOf<UUID?>(null) }
    val saleFocus = remember { FocusRequester() }
    val costFocus = remember { FocusRequester() }
    LaunchedEffect(initialFocus, state.loaded) {
        if (initialFocus == null || !state.loaded) return@LaunchedEffect
        delay(400)
        runCatching { (if (initialFocus == PriceField.Sale) saleFocus else costFocus).requestFocus() }
    }

    val directFields = editing == null || state.variants.size <= 1
    val formVariants: List<ProductVariantInput> = when {
        !state.hasVariations -> emptyList()
        editing != null && state.variants.size > 1 -> state.variants
        else -> listOf(
            ProductVariantInput(
                existingID = if (editing == null) null else state.variants.firstOrNull()?.existingID,
                name = state.variationName,
                initialQuantity = state.quantity,
            ),
        )
    }
    val variationValid = when {
        !state.hasVariations -> true
        !directFields -> state.variants.isNotEmpty()
        else -> state.variationName.isNotBlank() && state.quantity >= 0
    }
    val cost = price(state.costText)
    val sale = price(state.saleText)
    val canSave = cost != null && sale != null && state.name.isNotBlank() && cost >= BigDecimal.ZERO &&
        sale >= BigDecimal.ZERO && variationValid && state.loaded && !photo.isProcessing
    val stockTitle = stringResource(
        if (editing == null) R.string.product_form_variant_initialStock else R.string.product_form_variant_available,
    )

    fun save() {
        if (!canSave || cost == null || sale == null) return
        viewModel.save(photo, onSuccess = onClose) { imageFileName ->
            val store = viewModel.store
            store.inTransaction {
                val saved = ProductFormService.apply(
                    product = editing,
                    storeID = storeID,
                    name = state.name,
                    costPrice = cost,
                    salePrice = sale,
                    imageFileName = imageFileName,
                    variants = formVariants,
                    usesVariants = state.hasVariations,
                    store = store,
                )
                // Na edicao, a diferenca de saldo entra como ajuste auditavel.
                if (editing != null) {
                    val stored = ProductVariantService.variants(saved, store)
                    for (input in formVariants) {
                        val variant = stored.firstOrNull { it.id == input.existingID } ?: continue
                        if (variant.quantity != input.initialQuantity) {
                            StockService.registerAdjustment(input.initialQuantity, variant, saved, store = store)
                        }
                    }
                }
            }
        }
    }

    val draft = state.variants.firstOrNull { it.id == editingDraftID }
    if (draft != null) {
        VariantDraftPage(draft, onClose = { editingDraftID = null }) { updated ->
            state.variants = state.variants.map { if (it.id == updated.id) updated else it }
            editingDraftID = null
        }
        return
    }

    FormPage(
        title = stringResource(if (editing == null) R.string.product_form_title_new else R.string.product_form_title_edit),
        onClose = onClose,
        onConfirm = ::save,
        confirmEnabled = canSave && !viewModel.isSaving,
    ) {
        item("photo") { PhotoField(photo, onError = viewModel::showError) }
        item("name") {
            FormSection(stringResource(R.string.product_variant_edit_name)) {
                FormTextField(
                    value = state.name,
                    onValueChange = { state.name = it },
                    placeholder = stringResource(R.string.product_form_name),
                    keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
                )
            }
        }
        // So produtos antigos, criados sem variacao, ainda precisam ativar a opcao.
        if (state.usesLegacyDefault) {
            item("legacy") {
                FormSection(
                    title = null,
                    footer = if (!state.hasVariations) stringResource(R.string.product_form_variant_disabled_footer) else null,
                ) {
                    SwitchRow(
                        label = stringResource(R.string.product_form_variant_enabled),
                        checked = state.hasVariations,
                        enabled = directFields,
                        onCheckedChange = { state.hasVariations = it },
                    )
                }
            }
        }
        if (directFields) {
            item("variation") {
                FormSection(stringResource(R.string.product_form_variant_title)) {
                    FormTextField(
                        value = state.variationName,
                        onValueChange = { state.variationName = it },
                        placeholder = stringResource(R.string.product_form_variant_name),
                        enabled = state.hasVariations,
                        keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
                    )
                }
            }
            item("stock") {
                FormSection(stockTitle) {
                    if (state.hasVariations) {
                        StockQuantityStepper(stockTitle, state.quantity, onQuantityChange = { state.quantity = it })
                    } else {
                        FormValueRow(stockTitle, NumberFormat.getIntegerInstance().format(state.quantity))
                    }
                }
            }
        } else {
            item("variants") {
                FormSection(
                    stringResource(R.string.product_form_variants),
                    footer = stringResource(R.string.product_form_variant_multiple_footer),
                ) {
                    state.variants.forEachIndexed { index, input ->
                        if (index > 0) FormDivider()
                        FormValueRow(
                            label = input.name,
                            value = NumberFormat.getIntegerInstance().format(input.initialQuantity),
                            onClick = { editingDraftID = input.id },
                        )
                    }
                }
            }
        }
        item("sale") {
            FormSection(stringResource(R.string.product_form_salePrice)) {
                PriceTextField(state.saleText, { state.saleText = it }, stringResource(R.string.product_form_salePrice), saleFocus)
            }
        }
        item("cost") {
            val profit = if (sale != null && cost != null) sale - cost else null
            FormSection(
                stringResource(R.string.product_form_costPrice),
                footer = profit?.let { stringResource(R.string.product_form_unitProfitSummary, CurrencyFormatter.string(it)) },
                footerColor = if (profit != null && profit < BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                PriceTextField(state.costText, { state.costText = it }, stringResource(R.string.product_form_costPrice), costFocus)
            }
        }
    }

    FormErrorAlert(viewModel, R.string.product_form_error_title)
}

@Composable
private fun PriceTextField(value: String, onChange: (String) -> Unit, label: String, focus: FocusRequester) {
    FormTextField(
        value = value,
        onValueChange = onChange,
        placeholder = CurrencyFormatter.string(BigDecimal.ZERO),
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.focusRequester(focus),
    )
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Rascunho de uma variacao de produto com varias variacoes. Porte do `ProductVariantFormView`. */
@Composable
private fun VariantDraftPage(
    input: ProductVariantInput,
    onClose: () -> Unit,
    onSave: (ProductVariantInput) -> Unit,
) {
    var name by remember(input.id) { mutableStateOf(input.name) }
    var quantity by remember(input.id) { mutableIntStateOf(input.initialQuantity) }
    val stockTitle = stringResource(
        if (input.existingID == null) R.string.product_form_variant_initialStock else R.string.product_form_variant_available,
    )
    BackHandler(onBack = onClose)
    FormPage(
        title = stringResource(R.string.product_form_variant_title),
        onClose = onClose,
        onConfirm = { onSave(input.copy(name = name.trim(), initialQuantity = quantity)) },
        confirmEnabled = name.isNotBlank() && quantity >= 0,
    ) {
        item("name") {
            FormSection(stringResource(R.string.product_variant_edit_name)) {
                FormTextField(name, { name = it }, stringResource(R.string.product_form_variant_name))
            }
        }
        // Numa variacao existente, o formulario do produto grava a diferenca como ajuste.
        item("stock") {
            FormSection(stockTitle) {
                StockQuantityStepper(stockTitle, quantity, onQuantityChange = { quantity = it })
            }
        }
    }
}

/** Alerta de erro dos formularios, com o titulo da tela. */
@Composable
internal fun FormErrorAlert(viewModel: ProductFormsViewModel, titleRes: Int) {
    val error = viewModel.error ?: return
    AlertDialog(
        onDismissRequest = viewModel::clearError,
        title = { Text(stringResource(titleRes)) },
        text = { Text(error.messageRes?.let { stringResource(it) } ?: error.text.orEmpty()) },
        confirmButton = { TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.common_ok)) } },
        modifier = Modifier.semantics { },
    )
}
