package com.jonathaxs.tinystock.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.format.CurrencyFormatter
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.service.ProductVariantService
import com.jonathaxs.tinystock.ui.components.AppPopover
import com.jonathaxs.tinystock.ui.components.PopoverActionRow
import com.jonathaxs.tinystock.ui.components.PopoverTitle
import com.jonathaxs.tinystock.ui.components.ProductImage
import com.jonathaxs.tinystock.ui.components.appCard
import com.jonathaxs.tinystock.ui.icons.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.UUID

/** Produtos ou variacoes no catalogo. Os valores crus sao os do iOS (`products.catalogMode`). */
enum class CatalogMode(val rawValue: String, val titleRes: Int) {
    Products("products", R.string.products_mode_products),
    Variants("variants", R.string.products_mode_variants);

    companion object {
        fun fromRawValue(raw: String?): CatalogMode = entries.firstOrNull { it.rawValue == raw } ?: Products
    }
}

/** Catalogo em lista ou grade. Os valores crus sao os do iOS (`products.catalogViewMode`). */
enum class CatalogViewMode(val rawValue: String) {
    List("list"),
    Grid("grid");

    val next: CatalogViewMode get() = if (this == List) Grid else List

    companion object {
        fun fromRawValue(raw: String?): CatalogViewMode = entries.firstOrNull { it.rawValue == raw } ?: List
    }
}

/** Acoes do balao de um produto na grade, as mesmas da folha de acoes do iOS. */
enum class ProductAction(val titleRes: Int) {
    Sale(R.string.sale_new_title),
    StockEntry(R.string.stock_entry_new),
    NewVariant(R.string.stock_entry_newVariant),
    EditVariant(R.string.product_variant_edit_title),
    Edit(R.string.product_form_title_edit),
    Details(R.string.products_details),
}

private val GridMinTile = 100.dp
private val GridColumnSpacing = 12.dp
private val GridRowSpacing = 16.dp

/** Quantas colunas cabem com no minimo 100 dp cada (como o `GridItem(.adaptive(minimum: 100))`). */
private fun columnsFor(width: Dp): Int =
    ((width + GridColumnSpacing) / (GridMinTile + GridColumnSpacing)).toInt().coerceAtLeast(1)

/**
 * Grade generica: mede a largura disponivel e arruma os itens em linhas. Fica dentro de um item do
 * `LazyColumn` (o catalogo e curto, nao precisa de grade preguicosa).
 */
@Composable
private fun <T> CatalogGrid(items: List<T>, key: (T) -> Any, tile: @Composable (T) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val columns = columnsFor(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(GridRowSpacing)) {
            for (row in items.chunked(columns)) {
                Row(horizontalArrangement = Arrangement.spacedBy(GridColumnSpacing)) {
                    for (item in row) {
                        androidx.compose.runtime.key(key(item)) {
                            Box(Modifier.weight(1f)) { tile(item) }
                        }
                    }
                    // Completa a ultima linha para os cards nao esticarem.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// region Produtos

/** Grade de produtos: tocar abre um balao com as acoes do produto. */
@Composable
fun ProductCatalogGrid(
    products: List<Product>,
    variants: List<ProductVariant>,
    onAction: (ProductAction, Product) -> Unit,
) {
    var openProductID by rememberSaveable { mutableStateOf<UUID?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val hint = stringResource(R.string.products_grid_hint)

    CatalogGrid(products, key = { it.id }) { product ->
        val quantity = quantityText(ProductVariantService.displayedQuantity(product, variants))
        Box {
            CatalogGridTile(
                imageFileName = product.imageFileName,
                title = product.name,
                detail = quantity,
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = hint) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        openProductID = product.id
                    }
                    // TalkBack: nome, depois o estoque como valor e "toque para ver as acoes".
                    .semantics {
                        contentDescription = product.name
                        stateDescription = quantity
                    },
            )
            AppPopover(expanded = openProductID == product.id, onDismiss = { openProductID = null }) {
                ProductActionsPopover(product, quantity) { action ->
                    openProductID = null
                    // Espera o balao fechar antes de abrir a tela escolhida.
                    scope.launch {
                        delay(200)
                        onAction(action, product)
                    }
                }
            }
        }
    }
}

/** Balao com o nome, "estoque · preco" e as 6 acoes, no estilo do Adicionar. */
@Composable
internal fun ProductActionsPopover(product: Product, quantity: String, onPick: (ProductAction) -> Unit) {
    Column(
        Modifier
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        PopoverTitle(product.name)
        Text(
            text = quantity + " · " + CurrencyFormatter.string(product.salePrice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp).appCard()) {
            val rows = listOf(
                ProductAction.Sale to Icons.Filled.ShoppingCart,
                ProductAction.StockEntry to AppIcons.Box,
                ProductAction.NewVariant to AppIcons.Layers,
                ProductAction.EditVariant to Icons.Filled.Create,
                ProductAction.Edit to Icons.Filled.Edit,
                ProductAction.Details to Icons.Filled.Info,
            )
            rows.forEachIndexed { index, (action, icon) ->
                if (index > 0) {
                    HorizontalDivider(Modifier.padding(start = 50.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                PopoverActionRow(icon, stringResource(action.titleRes), { onPick(action) })
            }
        }
    }
}

// endregion

// region Variacoes

/** Grade das variacoes de um produto: tocar abre a edicao da variacao. */
@Composable
fun VariantCatalogGrid(variants: List<ProductVariant>, onSelect: (ProductVariant) -> Unit) {
    val noVariation = stringResource(R.string.products_variants_noVariation)
    CatalogGrid(variants, key = { it.id }) { variant ->
        val title = if (variant.isDefault) noVariation else variant.name
        val quantity = quantityText(variant.quantity)
        CatalogGridTile(
            imageFileName = variant.imageFileName,
            title = title,
            detail = quantity,
            modifier = Modifier
                .clickable(role = Role.Button) { onSelect(variant) }
                .semantics {
                    contentDescription = title
                    stateDescription = quantity
                },
        )
    }
}

// endregion

// region Pecas

@Composable
private fun quantityText(quantity: Number): String =
    stringResource(R.string.products_catalog_quantity, NumberFormat.getNumberInstance().format(quantity))

/** Foto quadrada, nome (ate 2 linhas) e estoque embaixo. */
@Composable
private fun CatalogGridTile(imageFileName: String?, title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            ProductImage(imageFileName, side = maxWidth)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

// endregion
