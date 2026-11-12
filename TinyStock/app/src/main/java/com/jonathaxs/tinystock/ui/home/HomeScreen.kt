package com.jonathaxs.tinystock.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.format.CurrencyFormatter
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.service.ProductVariantService
import com.jonathaxs.tinystock.ui.components.AppGroupedCard
import com.jonathaxs.tinystock.ui.components.AppPopover
import com.jonathaxs.tinystock.ui.components.AppSectionLabel
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.components.ProductImage
import com.jonathaxs.tinystock.ui.components.appCard
import com.jonathaxs.tinystock.ui.icons.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.UUID

/**
 * Tela Inicio: loja atual, caixa com o resumo, botoes redondos, Adicionar e o catalogo. Porte do topo do
 * `ProductsView` do iOS (C3, C3.2 e C4). O catalogo pode ser lista ou grade.
 */
@Composable
fun HomeScreen(
    storeID: UUID,
    selectedStore: StoreProfile?,
    stores: List<StoreProfile>,
    onSelectStore: (StoreProfile) -> Unit,
    onReports: () -> Unit,
    onCalendar: () -> Unit,
    onSettings: () -> Unit,
    /** Botao Adicionar: nova venda, novo produto ou nova variacao. */
    onAdd: (HomeAddAction) -> Unit,
    /** Acao escolhida no balao de um produto (lista ou grade). */
    onProductAction: (ProductAction, Product) -> Unit,
    onEditVariant: (ProductVariant) -> Unit,
    /** Telas ainda nao portadas (edicao da loja): abre o aviso "em construcao". */
    onComingSoon: (title: String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    LaunchedEffect(storeID) { viewModel.setStore(storeID) }
    val data by viewModel.data.collectAsStateWithLifecycle()
    var search by rememberSaveable(storeID) { mutableStateOf("") }
    val mode by viewModel.catalogMode.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    val filtered = remember(data, search) { data.products.filter { it.matches(search, data.variants) } }
    val editStoreTitle = stringResource(R.string.store_form_title_edit)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppStyle.HorizontalPadding,
                end = AppStyle.HorizontalPadding,
                top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 8.dp,
                bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item("store") {
                StoreSwitcher(
                    selected = selectedStore,
                    stores = stores,
                    onSelect = onSelectStore,
                    onEditCurrent = { onComingSoon(editStoreTitle) },
                )
            }
            item("hero") { HomeHero(data.products, data.variants, data.orders) }
            item("pages") { HomePageButtons(viewMode, viewModel::toggleViewMode, onReports, onCalendar, onSettings) }
            item("add") {
                HomeAddButton(hasProducts = data.products.isNotEmpty(), onPick = onAdd)
            }

            if (!data.loaded) return@LazyColumn
            if (data.products.isEmpty()) {
                item("empty") { EmptyCatalog() }
                return@LazyColumn
            }
            item("search") { SearchField(search, onChange = { search = it }) }
            item("mode") { CatalogHeader(mode, onModeChange = viewModel::setCatalogMode) }
            if (filtered.isEmpty()) {
                item("noResults") { NoResults(search) }
            } else if (viewMode == CatalogViewMode.Grid && mode == CatalogMode.Products) {
                item("productsGrid") {
                    ProductCatalogGrid(filtered, data.variants, onProductAction)
                }
            } else if (viewMode == CatalogViewMode.Grid) {
                items(filtered, key = { it.id }) { product ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppSectionLabel(product.name)
                        VariantCatalogGrid(data.variants.filter { it.belongsTo(product) }.sortedBy { it.name }, onEditVariant)
                        Spacer(Modifier.size(4.dp))
                    }
                }
            } else if (mode == CatalogMode.Products) {
                item("products") {
                    AppGroupedCard(
                        rows = filtered.map { product -> { ProductRow(product, data.variants, onProductAction) } },
                        dividerInset = 80.dp,
                    )
                }
            } else {
                items(filtered, key = { it.id }) { product ->
                    VariantSection(product, data.variants.filter { it.belongsTo(product) }.sortedBy { it.name }, onEditVariant)
                }
            }
        }
        // Faixa atras da barra de status, para a lista nao passar por baixo dos icones do sistema.
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
        )
    }
}

// region Catalogo

@Composable
private fun EmptyCatalog() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AppIcons.Box, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
        Text(
            stringResource(R.string.products_empty_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.products_empty_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

@Composable
private fun SearchField(text: String, onChange: (String) -> Unit) {
    val placeholder = stringResource(R.string.products_catalog_search)
    Row(
        Modifier
            .fillMaxWidth()
            .appCard()
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 14.dp),
        ) {
            if (text.isEmpty()) {
                Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = text,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = placeholder },
            )
        }
        if (text.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) {
                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.common_cancel))
            }
        }
    }
}

/** Acima do catalogo: troca entre produtos e variacoes. */
@Composable
private fun CatalogHeader(mode: CatalogMode, onModeChange: (CatalogMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.products_mode)) { expanded = true }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(mode.titleRes), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in CatalogMode.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(option.titleRes)) },
                    leadingIcon = {
                        Icon(
                            if (option == CatalogMode.Products) AppIcons.Box else AppIcons.Layers,
                            contentDescription = null,
                            tint = if (option == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        expanded = false
                        onModeChange(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun NoResults(search: String) {
    Text(
        text = "“$search”",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    )
}

@Composable
private fun ProductRow(product: Product, variants: List<ProductVariant>, onAction: (ProductAction, Product) -> Unit) {
    val quantity = NumberFormat.getNumberInstance().format(ProductVariantService.displayedQuantity(product, variants))
    var open by rememberSaveable(product.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.products_grid_hint)) { open = true }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProductImage(product.imageFileName)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(
                stringResource(R.string.products_catalog_quantity, quantity),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                CurrencyFormatter.string(product.salePrice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // Mesmo balao de acoes da grade (no iOS a lista abre a folha de acoes).
    AppPopover(expanded = open, onDismiss = { open = false }) {
        ProductActionsPopover(product, stringResource(R.string.products_catalog_quantity, quantity)) { action ->
            open = false
            scope.launch {
                delay(200)
                onAction(action, product)
            }
        }
    }
    }
}

@Composable
private fun VariantSection(product: Product, variants: List<ProductVariant>, onSelect: (ProductVariant) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppSectionLabel(product.name)
        AppGroupedCard(
            rows = variants.map { variant -> { VariantRow(variant, onSelect) } },
            dividerInset = 70.dp,
        )
        Spacer(Modifier.size(4.dp))
    }
}

@Composable
private fun VariantRow(variant: ProductVariant, onSelect: (ProductVariant) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onSelect(variant) }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProductImage(variant.imageFileName, side = 44.dp)
        Text(
            if (variant.isDefault) stringResource(R.string.products_variants_noVariation) else variant.name,
            modifier = Modifier.weight(1f),
        )
        Text(
            NumberFormat.getIntegerInstance().format(variant.quantity),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// endregion
