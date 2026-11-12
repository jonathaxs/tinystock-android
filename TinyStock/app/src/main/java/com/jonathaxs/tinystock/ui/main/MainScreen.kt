package com.jonathaxs.tinystock.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.ui.components.AppCircleButton
import com.jonathaxs.tinystock.ui.components.ComingSoonPage
import com.jonathaxs.tinystock.ui.calendar.CalendarScreen
import com.jonathaxs.tinystock.ui.home.HomeAddAction
import com.jonathaxs.tinystock.ui.home.HomeScreen
import com.jonathaxs.tinystock.ui.home.ProductAction
import com.jonathaxs.tinystock.ui.products.EditVariantScreen
import com.jonathaxs.tinystock.ui.products.NewVariantScreen
import com.jonathaxs.tinystock.ui.products.PriceField
import com.jonathaxs.tinystock.ui.products.ProductDetailScreen
import com.jonathaxs.tinystock.ui.products.ProductFormScreen
import com.jonathaxs.tinystock.ui.products.StockEntryScreen
import com.jonathaxs.tinystock.ui.sales.OrderEditScreen
import com.jonathaxs.tinystock.ui.sales.SaleFormScreen
import com.jonathaxs.tinystock.ui.settings.StoreFormScreen
import com.jonathaxs.tinystock.ui.reports.ReportsScreen
import com.jonathaxs.tinystock.ui.settings.ColorSettingsScreen
import com.jonathaxs.tinystock.ui.settings.SettingsPage
import com.jonathaxs.tinystock.ui.settings.SettingsScreen
import com.jonathaxs.tinystock.ui.theme.TinyStockTheme
import com.jonathaxs.tinystock.ui.util.ApplyOrientationLock
import com.jonathaxs.tinystock.ui.welcome.WelcomeScreen
import java.util.UUID

private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PRODUCT_FORM = "product/form?id={id}&focus={focus}"
private const val ROUTE_PRODUCT_DETAIL = "product/detail/{id}"
private const val ROUTE_VARIANT_NEW = "variant/new?product={product}"
private const val ROUTE_VARIANT_EDIT = "variant/edit/{product}?variant={variant}"
private const val ROUTE_STOCK_ENTRY = "stock/{product}?variant={variant}"

private fun productFormRoute(id: UUID? = null, focus: PriceField? = null) =
    "product/form?id=${id ?: ""}&focus=${focus?.name ?: ""}"

private fun variantEditRoute(productID: UUID, variantID: UUID? = null) =
    "variant/edit/$productID?variant=${variantID ?: ""}"

private fun stockEntryRoute(productID: UUID) = "stock/$productID?variant="

private const val ROUTE_SALE = "sale/new?product={product}"
private const val ROUTE_STORE_EDIT = "store/edit"

private fun saleRoute(productID: UUID? = null) = "sale/new?product=${productID ?: ""}"

private fun NavBackStackEntry.uuid(name: String): UUID? =
    arguments?.getString(name)?.takeIf { it.isNotEmpty() }?.let(UUID::fromString)

private fun optionalArgument(name: String) = navArgument(name) {
    type = NavType.StringType
    defaultValue = ""
}

private fun settingsRoute(page: SettingsPage) = "$ROUTE_SETTINGS/${page.name}"

/** Raiz do app: tema com a cor de destaque, boas-vindas na primeira abertura e a Inicio depois. */
@Composable
fun TinyStockApp(viewModel: MainViewModel = viewModel()) {
    val accent by viewModel.accentColor.collectAsStateWithLifecycle()
    val launch by viewModel.launchState.collectAsStateWithLifecycle()
    TinyStockTheme(accent = accent) {
        ApplyOrientationLock()
        Crossfade(targetState = launch, animationSpec = tween(500), label = "launch") { state ->
            when (state) {
                LaunchState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                LaunchState.Failed -> DataStoreError()
                LaunchState.Welcome -> WelcomeScreen(
                    onComplete = viewModel::completeWelcome,
                    onBackupRestored = viewModel::onBackupRestored,
                )
                LaunchState.Main -> MainScreen(viewModel)
            }
        }
    }
}

/**
 * Inicio de raiz com Ajustes como pagina; Relatorios e Calendario em sheet por cima, com o nome da loja
 * como subtitulo. Porte do `MainView` + `AppRouter` do iOS (C2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val storeID by viewModel.selectedStoreID.collectAsStateWithLifecycle()
    val selectedStore by viewModel.selectedStore.collectAsStateWithLifecycle()
    val stores by viewModel.activeStores.collectAsStateWithLifecycle()
    val accent by viewModel.accentColor.collectAsStateWithLifecycle()
    val storeName = selectedStore?.name ?: stringResource(R.string.store_default_name)

    NavHost(navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            val id = storeID ?: return@composable
            HomeScreen(
                storeID = id,
                selectedStore = selectedStore,
                stores = stores,
                onSelectStore = viewModel::select,
                onReports = { viewModel.present(AppSheet.Reports) },
                onCalendar = { viewModel.present(AppSheet.Calendar) },
                onSettings = {
                    // Ajustes sempre como pagina: fecha a sheet antes.
                    viewModel.dismissSheet()
                    navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                },
                onAdd = { action ->
                    when (action) {
                        HomeAddAction.Sale -> navController.navigate(saleRoute())
                        HomeAddAction.Product -> navController.navigate(productFormRoute())
                        HomeAddAction.Variant -> navController.navigate("variant/new?product=")
                    }
                },
                onProductAction = { action, product ->
                    when (action) {
                        ProductAction.Sale -> navController.navigate(saleRoute(product.id))
                        ProductAction.StockEntry -> navController.navigate(stockEntryRoute(product.id))
                        ProductAction.NewVariant -> navController.navigate("variant/new?product=${product.id}")
                        ProductAction.EditVariant -> navController.navigate(variantEditRoute(product.id))
                        ProductAction.Edit -> navController.navigate(productFormRoute(product.id))
                        ProductAction.Details -> navController.navigate("product/detail/${product.id}")
                    }
                },
                onEditVariant = { variant -> navController.navigate(variantEditRoute(variant.productID, variant.id)) },
                onEditStore = { navController.navigate(ROUTE_STORE_EDIT) { launchSingleTop = true } },
            )
        }
        val close: () -> Unit = { navController.popBackStack() }
        composable(ROUTE_PRODUCT_FORM, arguments = listOf(optionalArgument("id"), optionalArgument("focus"))) { entry ->
            val id = storeID ?: return@composable
            ProductFormScreen(
                storeID = id,
                productID = entry.uuid("id"),
                initialFocus = entry.arguments?.getString("focus")?.let { name -> PriceField.entries.firstOrNull { it.name == name } },
                onClose = close,
            )
        }
        composable(ROUTE_SALE, arguments = listOf(optionalArgument("product"))) { entry ->
            val id = storeID ?: return@composable
            SaleFormScreen(storeID = id, fixedProductID = entry.uuid("product"), onClose = close)
        }
        composable(ROUTE_STORE_EDIT) {
            val id = storeID ?: return@composable
            StoreFormScreen(storeID = id, onClose = close)
        }
        composable(ROUTE_PRODUCT_DETAIL) { entry ->
            val id = storeID ?: return@composable
            val productID = entry.uuid("id") ?: return@composable
            ProductDetailScreen(
                storeID = id,
                productID = productID,
                onClose = close,
                onEditProduct = { focus -> navController.navigate(productFormRoute(productID, focus)) },
                onEditVariant = { variant -> navController.navigate(variantEditRoute(productID, variant.id)) },
            )
        }
        composable(ROUTE_VARIANT_NEW, arguments = listOf(optionalArgument("product"))) { entry ->
            val id = storeID ?: return@composable
            NewVariantScreen(storeID = id, initialProductID = entry.uuid("product"), onClose = close)
        }
        composable(ROUTE_VARIANT_EDIT, arguments = listOf(optionalArgument("variant"))) { entry ->
            val id = storeID ?: return@composable
            val productID = entry.uuid("product") ?: return@composable
            EditVariantScreen(storeID = id, productID = productID, initialVariantID = entry.uuid("variant"), onClose = close)
        }
        composable(ROUTE_STOCK_ENTRY, arguments = listOf(optionalArgument("variant"))) { entry ->
            val id = storeID ?: return@composable
            val productID = entry.uuid("product") ?: return@composable
            StockEntryScreen(storeID = id, productID = productID, initialVariantID = entry.uuid("variant"), onClose = close)
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                selectedStore = selectedStore,
                onOpen = { page -> navController.navigate(settingsRoute(page)) { launchSingleTop = true } },
                onBack = { navController.popBackStack() },
            )
        }
        for (page in SettingsPage.entries) {
            composable(settingsRoute(page)) {
                val back = { navController.popBackStack(); Unit }
                when (page) {
                    SettingsPage.Colors -> ColorSettingsScreen(accent, viewModel::setAccentColor, back)
                    // As outras paginas dos Ajustes chegam nas proximas etapas.
                    else -> ComingSoonPage(title = stringResource(page.titleRes), onBack = back)
                }
            }
        }
    }

    viewModel.sheet?.let { sheet ->
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = null,
        ) {
            val id = storeID
            Box {
                Column {
                    SheetHeader(
                        title = stringResource(if (sheet == AppSheet.Reports) R.string.tab_reports else R.string.tab_sales),
                        subtitle = storeName,
                        onClose = viewModel::dismissSheet,
                    )
                    when {
                        id == null -> Unit
                        // Toque numa fila: troca a sheet pela do Calendario ja filtrado, como no iOS.
                        sheet == AppSheet.Reports -> ReportsScreen(storeID = id, onOpenCalendar = viewModel::openCalendar)
                        else -> CalendarScreen(
                            storeID = id,
                            filterRequest = viewModel.calendarFilterRequest,
                            onFilterRequestHandled = viewModel::consumeCalendarFilterRequest,
                            storeName = storeName,
                            onEditOrder = { viewModel.editOrder(it.id) },
                        )
                    }
                }
                // Editar pedido cobre a sheet inteira (com o proprio X e o ✓) sem desmontar o Calendario,
                // que volta no mesmo pedido ao fechar.
                val editing = viewModel.editingOrderID
                if (editing != null && id != null) {
                    OrderEditScreen(storeID = id, orderID = editing, onClose = { viewModel.editOrder(null) })
                }
            }
        }
    }
}

/** Topo das sheets: X a esquerda, titulo e o nome da loja embaixo. */
@Composable
private fun ColumnScope.SheetHeader(title: String, subtitle: String, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AppCircleButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.common_close),
            onClick = onClose,
            color = MaterialTheme.colorScheme.onSurface,
            size = 44.dp,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Caso raro em que o banco nao abre (disco cheio, arquivo corrompido): mostra o erro em vez de fechar. */
@Composable
private fun DataStoreError() {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(56.dp))
        Text(
            stringResource(R.string.app_error_dataStore_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.app_error_dataStore_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
