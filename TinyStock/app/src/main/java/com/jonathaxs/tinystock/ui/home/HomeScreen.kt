package com.jonathaxs.tinystock.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.jonathaxs.tinystock.ui.util.FoldInfo
import com.jonathaxs.tinystock.ui.util.rememberFoldInfo
import com.jonathaxs.tinystock.ui.util.rememberIsTablet
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
    /** Editar a loja atual, pelo menu do nome da loja. */
    onEditStore: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    LaunchedEffect(storeID) { viewModel.setStore(storeID) }
    val data by viewModel.data.collectAsStateWithLifecycle()
    var search by rememberSaveable(storeID) { mutableStateOf("") }
    val mode by viewModel.catalogMode.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    val filtered = remember(data, search) { data.products.filter { it.matches(search, data.variants) } }

    // Partes da Inicio, as mesmas no retrato e no layout largo.
    val storeSwitcher = @Composable {
        StoreSwitcher(
            selected = selectedStore,
            stores = stores,
            onSelect = onSelectStore,
            onEditCurrent = { onEditStore() },
        )
    }
    val hero = @Composable { modifier: Modifier -> HomeHero(data.products, data.variants, data.orders, modifier) }
    val pageButtons = @Composable { HomePageButtons(viewMode, viewModel::toggleViewMode, onReports, onCalendar, onSettings) }
    val addButton = @Composable { HomeAddButton(hasProducts = data.products.isNotEmpty(), onPick = onAdd) }
    val catalog: LazyListScope.() -> Unit = {
        catalogItems(data, filtered, search, { search = it }, mode, viewMode, viewModel::setCatalogMode, onProductAction, onEditVariant)
    }

    // Dobra (Pixel Fold e afins) e onde a Inicio esta na janela, para levar a faixa para coordenadas locais.
    val foldInfo = rememberFoldInfo()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val isTablet = rememberIsTablet()

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        // Largo a partir de 700 dp, ou ja a partir de 560 dp em tablet ou dobravel aberto (mesma regra do iOS).
        val isWide = maxWidth >= 700.dp || (maxWidth >= 560.dp && isTablet)
        if (isWide) {
            HomeWide(
                width = maxWidth,
                height = maxHeight,
                fold = foldBand(foldInfo, origin, density),
                storeSwitcher = storeSwitcher,
                hero = hero,
                pageButtons = pageButtons,
                addButton = addButton,
                viewMode = viewMode,
                onToggleViewMode = viewModel::toggleViewMode,
                onReports = onReports,
                onCalendar = onCalendar,
                onSettings = onSettings,
                catalog = catalog,
            )
        } else {
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
                item("store") { storeSwitcher() }
                item("hero") { hero(Modifier) }
                item("pages") { pageButtons() }
                item("add") { addButton() }
                catalog()
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

/** Catalogo: busca, produtos ou variacoes, em lista ou grade. */
private fun LazyListScope.catalogItems(
    data: HomeData,
    filtered: List<Product>,
    search: String,
    onSearch: (String) -> Unit,
    mode: CatalogMode,
    viewMode: CatalogViewMode,
    onModeChange: (CatalogMode) -> Unit,
    onProductAction: (ProductAction, Product) -> Unit,
    onEditVariant: (ProductVariant) -> Unit,
) {
    if (!data.loaded) return
    if (data.products.isEmpty()) {
        item("empty") { EmptyCatalog() }
        return
    }
    item("search") { SearchField(search, onChange = onSearch) }
    item("mode") { CatalogHeader(mode, onModeChange = onModeChange) }
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

// region Layout largo

/** Faixa da dobra em dp, nas coordenadas da Inicio (null sem dobra). */
private data class FoldBand(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp, val halfOpened: Boolean, val vertical: Boolean)

private fun foldBand(info: FoldInfo?, origin: Offset, density: Density): FoldBand? = info?.let {
    with(density) {
        FoldBand(
            left = (it.bounds.left - origin.x).toDp(),
            top = (it.bounds.top - origin.y).toDp(),
            right = (it.bounds.right - origin.x).toDp(),
            bottom = (it.bounds.bottom - origin.y).toDp(),
            halfOpened = it.halfOpened,
            vertical = it.vertical,
        )
    }
}

/** Como o topo da Inicio e o catalogo se dividem no layout largo. */
private sealed interface WideArrangement {
    /** Lado a lado: topo com largura fixa a esquerda, catalogo no resto. */
    data class Side(val heroWidth: Dp, val gap: Dp) : WideArrangement

    /** Empilhado: topo em cima com altura fixa, catalogo embaixo. */
    data class Stacked(val heroHeight: Dp, val gap: Dp) : WideArrangement
}

/** Margem de cada lado da dobra: o vinco nao e lugar para botao. */
private val FoldMargin = 24.dp

/**
 * Layout largo (tablet, dobravel aberto), porte do `wideLayout` da Inicio do iOS: topo da Inicio (botoes, loja,
 * caixa e Adicionar) numa coluna e o catalogo no resto. Dobra meio aberta: livro = topo na metade esquerda e
 * catalogo na direita; mesa = topo na metade de cima e catalogo embaixo. Aberto reto em pe = mesmo empilhado.
 * As views sao as mesmas em todos os casos (`animateBounds`), entao dobrar e desdobrar anima as colunas.
 * Tablet deitado sem dobra: os botoes vao para uma coluna de vidro a direita.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeWide(
    width: Dp,
    height: Dp,
    fold: FoldBand?,
    storeSwitcher: @Composable () -> Unit,
    hero: @Composable (Modifier) -> Unit,
    pageButtons: @Composable () -> Unit,
    addButton: @Composable () -> Unit,
    viewMode: CatalogViewMode,
    onToggleViewMode: () -> Unit,
    onReports: () -> Unit,
    onCalendar: () -> Unit,
    onSettings: () -> Unit,
    catalog: LazyListScope.() -> Unit,
) {
    val compact = width < 744.dp
    val landscape = width > height
    val book = fold != null && fold.halfOpened && fold.vertical
    val tabletop = fold != null && fold.halfOpened && !fold.vertical
    val flatPortrait = fold != null && !fold.halfOpened && !landscape
    val stacked = tabletop || flatPortrait
    val sideRail = landscape && !book && !stacked
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val top = insets.calculateTopPadding()
    val bottom = insets.calculateBottomPadding()
    val start = insets.calculateStartPadding(layoutDirection)
    val end = insets.calculateEndPadding(layoutDirection)

    val arrangement: WideArrangement = when {
        book -> WideArrangement.Side(
            heroWidth = (fold.left - FoldMargin - start).coerceAtLeast(0.dp),
            gap = (fold.right - fold.left) + FoldMargin * 2,
        )
        tabletop -> WideArrangement.Stacked(
            heroHeight = (fold.top - FoldMargin - top).coerceAtLeast(0.dp),
            gap = (fold.bottom - fold.top) + FoldMargin * 2,
        )
        flatPortrait -> WideArrangement.Stacked(heroHeight = height / 2 - top, gap = 0.dp)
        else -> WideArrangement.Side(heroWidth = if (compact) 320.dp else 360.dp, gap = 0.dp)
    }
    val bounds = BoundsTransform { _, _ -> spring(dampingRatio = 0.85f, stiffness = 130f) }

    Row(
        Modifier
            .fillMaxSize()
            .padding(start = start, end = end, top = top),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LookaheadScope {
            WideArrangedLayout(arrangement, Modifier.weight(1f).fillMaxHeight()) {
                // Topo da Inicio: centralizado na altura; na mesa, colado no topo (longe da dobra).
                Column(
                    modifier = Modifier
                        .animateBounds(this@LookaheadScope, boundsTransform = bounds)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (tabletop) Arrangement.spacedBy(14.dp, Alignment.Top)
                    else Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                ) {
                    if (stacked) {
                        // Meia tela de altura: a caixa ao lado do resto.
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        ) {
                            hero(Modifier.widthIn(max = 260.dp).weight(1f, fill = false))
                            Column(
                                Modifier.widthIn(max = 340.dp).weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                if (!sideRail) pageButtons()
                                storeSwitcher()
                                addButton()
                            }
                        }
                    } else {
                        if (!sideRail) {
                            Box(Modifier.padding(bottom = 6.dp)) { pageButtons() }
                        }
                        storeSwitcher()
                        hero(Modifier)
                        addButton()
                    }
                }
                // Catalogo
                LazyColumn(
                    modifier = Modifier.animateBounds(this@LookaheadScope, boundsTransform = bounds),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { catalog() }
            }
        }
        // Coluna de vidro a direita, centralizada na altura (iPad deitado no iOS).
        AnimatedVisibility(
            visible = sideRail,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
        ) {
            HomeSideRail(
                viewMode = viewMode,
                onToggleViewMode = onToggleViewMode,
                onReports = onReports,
                onCalendar = onCalendar,
                onSettings = onSettings,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
    }
}

/** Mede e posiciona as duas colunas conforme o arranjo, mantendo as mesmas views. */
@Composable
private fun WideArrangedLayout(
    arrangement: WideArrangement,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        when (arrangement) {
            is WideArrangement.Side -> {
                val heroW = arrangement.heroWidth.roundToPx().coerceIn(0, w)
                val gap = arrangement.gap.roundToPx()
                val catalogW = (w - heroW - gap).coerceAtLeast(0)
                val heroPlaceable = measurables[0].measure(Constraints.fixed(heroW, h))
                val catalogPlaceable = measurables[1].measure(Constraints.fixed(catalogW, h))
                layout(w, h) {
                    heroPlaceable.place(0, 0)
                    catalogPlaceable.place(heroW + gap, 0)
                }
            }
            is WideArrangement.Stacked -> {
                val heroH = arrangement.heroHeight.roundToPx().coerceIn(0, h)
                val gap = arrangement.gap.roundToPx()
                val catalogH = (h - heroH - gap).coerceAtLeast(0)
                val heroPlaceable = measurables[0].measure(Constraints.fixed(w, heroH))
                val catalogPlaceable = measurables[1].measure(Constraints.fixed(w, catalogH))
                layout(w, h) {
                    heroPlaceable.place(0, 0)
                    catalogPlaceable.place(0, heroH + gap)
                }
            }
        }
    }
}

// endregion

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
                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.common_clear))
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
