package com.jonathaxs.tinystock.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.service.StoreDeletionService
import com.jonathaxs.tinystock.ui.components.AppPage
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.icons.AppIcons
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

/**
 * Pagina de lojas arquivadas: restaurar ou mover para a lixeira. Porte do `ArchivedStoresView` do iOS.
 */
@Composable
fun ArchivedStoresScreen(
    onBack: () -> Unit,
    viewModel: StoresViewModel = viewModel(),
) {
    val archivedStores by viewModel.archivedStores.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary

    AppPage(stringResource(R.string.stores_archived_title), onBack) {
        if (archivedStores.isEmpty()) {
            StoreEmptyState(
                icon = AppIcons.Box,
                title = stringResource(R.string.stores_archived_empty_title),
                message = stringResource(R.string.stores_archived_empty_message),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = AppStyle.HorizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item("archived") {
                    StoreSection(
                        title = null,
                        accent = accent,
                        rows = archivedStores.map { store ->
                            @Composable {
                                ArchivedStoreRow(
                                    store = store,
                                    onRestore = { viewModel.restoreArchived(store) },
                                    onMoveToTrash = { viewModel.requestTrash(store) },
                                )
                            }
                        },
                    )
                }
                item("bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
            }
        }
    }

    viewModel.trashRequest?.let { store ->
        TrashStoreDialog(
            store = store,
            onDismiss = viewModel::cancelTrash,
            onConfirm = { viewModel.moveToTrash(store) },
        )
    }

    StoreErrorAlert(error = viewModel.error, onDismiss = viewModel::clearError)
}

/**
 * Pagina da lixeira de lojas: restaurar ou apagar definitivamente. Porte do `TrashedStoresView` do iOS.
 */
@Composable
fun TrashedStoresScreen(
    selectedStoreID: UUID?,
    onSelectStore: (StoreProfile) -> Unit,
    onBack: () -> Unit,
    viewModel: StoresViewModel = viewModel(),
) {
    val trashedStores by viewModel.trashedStores.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    val zone = ZoneId.systemDefault()

    AppPage(stringResource(R.string.stores_trash_title), onBack) {
        if (trashedStores.isEmpty()) {
            StoreEmptyState(
                icon = Icons.Filled.Delete,
                title = stringResource(R.string.stores_trash_empty_title),
                message = stringResource(R.string.stores_trash_empty_message),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = AppStyle.HorizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item("trashed") {
                    StoreSection(
                        title = null,
                        accent = accent,
                        footer = stringResource(R.string.stores_trash_footer),
                        rows = trashedStores.map { store ->
                            @Composable {
                                TrashedStoreRow(
                                    store = store,
                                    zone = zone,
                                    onRestore = { viewModel.restoreFromTrash(store) },
                                    onDeletePermanently = { viewModel.requestPermanentDeletion(store) },
                                )
                            }
                        },
                    )
                }
                item("bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
            }
        }
    }

    viewModel.deletionRequest?.let { request ->
        DeleteStoreDialog(
            request = request,
            onDismiss = viewModel::cancelDeletion,
            onConfirm = { viewModel.deletePermanently(request.store, selectedStoreID, onSelectStore) },
        )
    }

    StoreErrorAlert(error = viewModel.error, onDismiss = viewModel::clearError)
}

@Composable
private fun ArchivedStoreRow(
    store: StoreProfile,
    onRestore: () -> Unit,
    onMoveToTrash: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val actionsLabel = stringResource(R.string.stores_actions_accessibility, store.name)
    val errorColor = MaterialTheme.colorScheme.error

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoreManagementRowContent(
            store = store,
            modifier = Modifier
                .weight(1f)
                .alpha(0.75f)
                .semantics(mergeDescendants = true) {},
        )
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = actionsLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stores_restore)) },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRestore()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stores_moveToTrash), color = errorColor) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = errorColor) },
                    onClick = {
                        menuExpanded = false
                        onMoveToTrash()
                    },
                )
            }
        }
    }
}

@Composable
private fun TrashedStoreRow(
    store: StoreProfile,
    zone: ZoneId,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val actionsLabel = stringResource(R.string.stores_actions_accessibility, store.name)
    val errorColor = MaterialTheme.colorScheme.error

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoreManagementRowContent(
            store = store,
            detail = expirationText(store, zone),
            modifier = Modifier
                .weight(1f)
                .alpha(0.75f)
                .semantics(mergeDescendants = true) {},
        )
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = actionsLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stores_restore)) },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRestore()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stores_deletePermanently), color = errorColor) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = errorColor) },
                    onClick = {
                        menuExpanded = false
                        onDeletePermanently()
                    },
                )
            }
        }
    }
}

@Composable
private fun expirationText(store: StoreProfile, zone: ZoneId): String? {
    val expiration = StoreDeletionService.trashExpirationDate(store, zone) ?: return null
    val formattedDate = expiration.atZone(zone).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    return stringResource(R.string.stores_trash_expires, formattedDate)
}

@Composable
private fun StoreEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp),
        )
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Confirmacao da exclusao definitiva com a contagem de registros da loja. */
@Composable
private fun DeleteStoreDialog(
    request: StoreDeletionRequest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val number = NumberFormat.getIntegerInstance()
    val message = stringResource(
        R.string.stores_delete_confirm_message,
        request.store.name,
        number.format(request.summary.productCount),
        number.format(request.summary.variantCount),
        number.format(request.summary.stockMovementCount),
        number.format(request.summary.orderCount),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stores_delete_confirm_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.common_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
