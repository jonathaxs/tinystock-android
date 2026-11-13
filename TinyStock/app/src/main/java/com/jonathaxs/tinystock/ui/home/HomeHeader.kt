package com.jonathaxs.tinystock.ui.home

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.report.HomeSummary
import com.jonathaxs.tinystock.ui.components.AppCircleButton
import com.jonathaxs.tinystock.ui.components.AppPopover
import com.jonathaxs.tinystock.ui.components.AppSecondaryButton
import com.jonathaxs.tinystock.ui.components.AppSectionLabel
import com.jonathaxs.tinystock.ui.components.PopoverActionRow
import com.jonathaxs.tinystock.ui.components.PopoverTitle
import com.jonathaxs.tinystock.ui.components.StoreImage
import com.jonathaxs.tinystock.ui.components.appCard
import com.jonathaxs.tinystock.ui.icons.AppIcons
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Instant

// region Titulo com a loja atual

/** Titulo da Inicio: foto e nome da loja atual, com a seta do menu de lojas. */
@Composable
fun StoreSwitcher(
    selected: StoreProfile?,
    stores: List<StoreProfile>,
    onSelect: (StoreProfile) -> Unit,
    onEditCurrent: (StoreProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val name = selected?.name ?: stringResource(R.string.store_default_name)
    val others = stores.filter { it.id != selected?.id }
    val switcherLabel = stringResource(R.string.store_switcher_accessibility)
    // O menu abre preso ao titulo, nao a largura toda.
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box {
            Row(
                modifier = Modifier
                    .clickable(onClickLabel = switcherLabel, role = Role.Button) { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    // TalkBack: "Escolher loja", com o nome da loja como valor (o texto de dentro nao repete).
                    .semantics {
                        contentDescription = switcherLabel
                        stateDescription = name
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StoreImage(selected?.imageFileName, side = 34.dp)
                Text(
                    text = name,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clearAndSetSemantics { },
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                // A loja atual fica no topo com o lapis; tocar nela abre a edicao da loja.
                if (selected != null) {
                    MenuSection(stringResource(R.string.store_switcher_current))
                    DropdownMenuItem(
                        text = { Text(selected.name) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onEditCurrent(selected)
                        },
                    )
                }
                if (others.isNotEmpty()) {
                    if (selected != null) HorizontalDivider()
                    MenuSection(stringResource(R.string.store_switcher_others))
                    for (store in others) {
                        DropdownMenuItem(
                            text = { Text(store.name) },
                            leadingIcon = { StoreImage(store.imageFileName, side = 26.dp) },
                            onClick = {
                                expanded = false
                                onSelect(store)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuSection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

// endregion

// region Caixa com o resumo

/** Resumo da loja: a caixa do icone e uma frase de status. Tocar na caixa explica os numeros. */
@Composable
fun HomeHero(
    products: List<Product>,
    variants: List<ProductVariant>,
    orders: List<SalesOrder>,
    modifier: Modifier = Modifier,
) {
    // Recalcula atrasos quando a tela cruza a meia-noite.
    val now by produceState(Instant.now()) {
        while (true) {
            delay(60_000)
            value = Instant.now()
        }
    }
    val summary = remember(products, variants, orders, now) { HomeSummary.from(products, variants, orders, now) }
    val darkTheme = isSystemInDarkTheme()
    val graphite = StockBoxColors.graphite(darkTheme)

    // As barras crescem ao abrir; com animacoes desligadas no sistema, ja aparecem prontas.
    val context = LocalContext.current
    val growth = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val animationsOff = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        if (animationsOff) {
            growth.snapTo(1f)
        } else {
            delay(150)
            growth.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow))
        }
    }

    var showsInfo by rememberSaveable { mutableStateOf(false) }
    val a11y = stringResource(
        R.string.home_box_a11y,
        summary.products, summary.units, summary.production, summary.readyToShip, summary.shipped,
    )
    val hint = stringResource(R.string.home_box_hint)

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            StockBox(
                leftValue = summary.products,
                leftLabel = stringResource(R.string.home_box_products),
                rightValue = summary.units,
                rightLabel = stringResource(R.string.home_box_units),
                bars = listOf(
                    StockBoxBar(summary.production, StockBoxColors.teal),
                    StockBoxBar(summary.readyToShip, graphite),
                    StockBoxBar(summary.shipped, StockBoxColors.deepTeal),
                ),
                growth = growth.value,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = hint,
                    ) { showsInfo = true }
                    .semantics { contentDescription = a11y },
            )
            AppPopover(expanded = showsInfo, onDismiss = { showsInfo = false }, width = 320.dp) {
                HomeBoxInfo(summary, graphite.right)
            }
        }
        Text(
            text = statusText(summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 20.dp, end = 20.dp),
        )
    }
}

/** Uma frase so, do que mais pede atencao para o que menos pede. */
@Composable
private fun statusText(summary: HomeSummary): String = when {
    summary.products == 0 -> stringResource(R.string.home_status_empty)
    summary.overdue > 0 -> stringResource(R.string.home_status_overdue, summary.overdue)
    summary.outOfStock > 0 -> stringResource(R.string.home_status_outOfStock, summary.outOfStock)
    summary.openOrders > 0 -> stringResource(R.string.home_status_open, summary.openOrders)
    else -> stringResource(R.string.home_status_allGood)
}

/**
 * Explica cada numero da caixa na mesma disposicao dela: os pedidos das barras em cima e o catalogo dos
 * lados embaixo (produtos a esquerda, estoque a direita).
 */
@Composable
private fun HomeBoxInfo(summary: HomeSummary, graphite: Color) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        PopoverTitle(stringResource(R.string.home_box_info_title))
        AppSectionLabel(stringResource(R.string.home_box_info_orders))
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(StockBoxColors.teal.right, stringResource(R.string.home_box_info_production), summary.production)
            InfoTile(graphite, stringResource(R.string.home_box_info_ready), summary.readyToShip)
            InfoTile(StockBoxColors.deepTeal.right, stringResource(R.string.home_box_info_shipped), summary.shipped)
        }
        Spacer(Modifier.size(12.dp))
        AppSectionLabel(stringResource(R.string.home_box_info_catalog))
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(StockBoxColors.leftLegend, stringResource(R.string.home_box_info_products), summary.products)
            InfoTile(StockBoxColors.rightLegend, stringResource(R.string.home_box_info_units), summary.units)
        }
    }
}

@Composable
private fun RowScope.InfoTile(color: Color, title: String, value: Int) {
    val number = NumberFormat.getIntegerInstance().format(value)
    Column(
        modifier = Modifier
            .weight(1f)
            .appCard()
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .clearAndSetSemantics { contentDescription = "$number, $title" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(22.dp)
                .size(width = 22.dp, height = 8.dp)
                .background(
                    Brush.verticalGradient(listOf(color, androidx.compose.ui.graphics.lerp(color, Color.Black, 0.15f))),
                    RoundedCornerShape(3.dp),
                ),
        )
        Text(number, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// endregion

// region Botoes

/** Linha de botoes redondos: lista ou grade, Relatorios, Calendario e Ajustes. */
@Composable
fun HomePageButtons(
    viewMode: CatalogViewMode,
    onToggleViewMode: () -> Unit,
    onReports: () -> Unit,
    onCalendar: () -> Unit,
    onSettings: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        // O icone mostra para onde vai: em lista, o de grade.
        AppCircleButton(
            icon = if (viewMode == CatalogViewMode.List) AppIcons.Grid else AppIcons.ListBullet,
            contentDescription = stringResource(
                if (viewMode == CatalogViewMode.List) R.string.products_viewMode_showGrid else R.string.products_viewMode_showList,
            ),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onToggleViewMode()
            },
        )
        AppCircleButton(AppIcons.BarChart, stringResource(R.string.tab_reports), onReports)
        AppCircleButton(Icons.Filled.DateRange, stringResource(R.string.tab_sales), onCalendar)
        AppCircleButton(Icons.Filled.Settings, stringResource(R.string.tab_settings), onSettings)
    }
}

/**
 * Coluna de vidro com os mesmos botoes, na vertical (tablet deitado), porte do `HomeSideRail` do iOS, no
 * estilo da barra vertical do iPhone Duo.
 */
@Composable
fun HomeSideRail(
    viewMode: CatalogViewMode,
    onToggleViewMode: () -> Unit,
    onReports: () -> Unit,
    onCalendar: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val tint = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onToggleViewMode()
            }) {
                Icon(
                    if (viewMode == CatalogViewMode.List) AppIcons.Grid else AppIcons.ListBullet,
                    contentDescription = stringResource(
                        if (viewMode == CatalogViewMode.List) R.string.products_viewMode_showGrid else R.string.products_viewMode_showList,
                    ),
                    tint = tint,
                )
            }
            IconButton(onClick = onReports) { Icon(AppIcons.BarChart, stringResource(R.string.tab_reports), tint = tint) }
            IconButton(onClick = onCalendar) { Icon(Icons.Filled.DateRange, stringResource(R.string.tab_sales), tint = tint) }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.tab_settings), tint = tint) }
        }
    }
}

enum class HomeAddAction { Sale, Product, Variant }

/** Capsula Adicionar, com o balao de opcoes. Sem produtos, so da para cadastrar um produto. */
@Composable
fun HomeAddButton(hasProducts: Boolean, onPick: (HomeAddAction) -> Unit) {
    var showsOptions by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box {
            AppSecondaryButton(onClick = { showsOptions = true }, large = false) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_add), fontWeight = FontWeight.SemiBold)
            }
            AppPopover(expanded = showsOptions, onDismiss = { showsOptions = false }) {
                PopoverTitle(stringResource(R.string.home_add))
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp).appCard()) {
                    val pick = { action: HomeAddAction ->
                        showsOptions = false
                        onPick(action)
                    }
                    PopoverActionRow(Icons.Filled.ShoppingCart, stringResource(R.string.sale_new_title), { pick(HomeAddAction.Sale) }, hasProducts)
                    HorizontalDivider(Modifier.padding(start = 50.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    PopoverActionRow(AppIcons.Box, stringResource(R.string.product_form_title_new), { pick(HomeAddAction.Product) })
                    HorizontalDivider(Modifier.padding(start = 50.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    PopoverActionRow(AppIcons.Layers, stringResource(R.string.stock_entry_newVariant), { pick(HomeAddAction.Variant) }, hasProducts)
                }
            }
        }
    }
}

// endregion
