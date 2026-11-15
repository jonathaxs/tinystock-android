package com.jonathaxs.tinystock.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.ui.components.AppGroupedCard
import com.jonathaxs.tinystock.ui.components.AppPage
import com.jonathaxs.tinystock.ui.components.AppSectionLabel
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.components.CircleIcon
import com.jonathaxs.tinystock.ui.components.StoreImage
import com.jonathaxs.tinystock.ui.components.isLargeFont
import com.jonathaxs.tinystock.ui.icons.AppIcons
import com.jonathaxs.tinystock.ui.products.FormError
import com.jonathaxs.tinystock.ui.theme.color
import java.text.NumberFormat
import java.util.UUID

/** Recuo do divisor alinhado ao texto apos a foto da loja (14 + 40 + 12). */
internal val StoreRowDividerInset = 66.dp

/** Recuo do divisor nas linhas de navegacao com icone circular (14 + 32 + 14). */
private val OrganizationDividerInset = 60.dp

/**
 * Pagina Lojas dos Ajustes: selecao, ordenacao e gerenciamento das lojas ativas, mais os atalhos
 * para arquivadas e lixeira. Porte do `StoresView` do iOS.
 */
@Composable
fun StoresScreen(
    selectedStoreID: UUID?,
    onSelectStore: (StoreProfile) -> Unit,
    onAddStore: () -> Unit,
    onEditStore: (StoreProfile) -> Unit,
    onOpenArchived: () -> Unit,
    onOpenTrash: () -> Unit,
    onBack: () -> Unit,
    viewModel: StoresViewModel = viewModel(),
) {
    val activeStores by viewModel.activeStores.collectAsStateWithLifecycle()
    val archivedStores by viewModel.archivedStores.collectAsStateWithLifecycle()
    val trashedStores by viewModel.trashedStores.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    val dark = isSystemInDarkTheme()

    AppPage(stringResource(R.string.stores_title), onBack) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = AppStyle.HorizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("active") {
                val canDeactivate = activeStores.size > 1
                StoreSection(
                    title = stringResource(R.string.stores_section_active),
                    accent = accent,
                    footer = stringResource(R.string.stores_section_footer),
                    dividerInset = StoreRowDividerInset,
                    rows = activeStores.mapIndexed { index, store ->
                        @Composable {
                            ActiveStoreRow(
                                store = store,
                                isSelected = store.id == selectedStoreID,
                                canMoveUp = index > 0,
                                canMoveDown = index < activeStores.lastIndex,
                                canDeactivate = canDeactivate,
                                onSelect = { onSelectStore(store) },
                                onEdit = { onEditStore(store) },
                                onMoveUp = { viewModel.moveActiveStore(store, up = true) },
                                onMoveDown = { viewModel.moveActiveStore(store, up = false) },
                                onArchive = { viewModel.archive(store, selectedStoreID, onSelectStore) },
                                onMoveToTrash = { viewModel.requestTrash(store) },
                            )
                        }
                    } + listOf {
                        AddStoreRow(onClick = onAddStore)
                    },
                )
            }
            item("organization") {
                StoreSection(
                    title = stringResource(R.string.stores_section_organization),
                    accent = accent,
                    dividerInset = OrganizationDividerInset,
                    rows = listOf(
                        {
                            OrganizationRow(
                                icon = AppIcons.Box,
                                iconColor = AppAccentColor.Orange.color(dark),
                                title = stringResource(R.string.stores_archived_title),
                                count = archivedStores.size,
                                onClick = onOpenArchived,
                            )
                        },
                        {
                            OrganizationRow(
                                icon = Icons.Filled.Delete,
                                iconColor = AppAccentColor.Red.color(dark),
                                title = stringResource(R.string.stores_trash_title),
                                count = trashedStores.size,
                                onClick = onOpenTrash,
                            )
                        },
                    ),
                )
            }
            item("bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }

    viewModel.trashRequest?.let { store ->
        TrashStoreDialog(
            store = store,
            onDismiss = viewModel::cancelTrash,
            onConfirm = { viewModel.moveToTrash(store, selectedStoreID, onSelectStore) },
        )
    }

    StoreErrorAlert(error = viewModel.error, onDismiss = viewModel::clearError)
}

@Composable
private fun ActiveStoreRow(
    store: StoreProfile,
    isSelected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDeactivate: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onArchive: () -> Unit,
    onMoveToTrash: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val selectHint = stringResource(R.string.stores_select_hint)
    val actionsLabel = stringResource(R.string.stores_actions_accessibility, store.name)
    val destructiveColor = if (canDeactivate) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClickLabel = selectHint, onClick = onSelect)
                .semantics { selected = isSelected }
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StoreManagementRowContent(
                store = store,
                isSelected = isSelected,
                modifier = Modifier.weight(1f),
            )
        }

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
                    text = { Text(stringResource(R.string.common_edit)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stores_move_up)) },
                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                    enabled = canMoveUp,
                    onClick = {
                        menuExpanded = false
                        onMoveUp()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stores_move_down)) },
                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                    enabled = canMoveDown,
                    onClick = {
                        menuExpanded = false
                        onMoveDown()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stores_archive)) },
                    leadingIcon = { Icon(AppIcons.Box, contentDescription = null) },
                    enabled = canDeactivate,
                    onClick = {
                        menuExpanded = false
                        onArchive()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stores_moveToTrash), color = destructiveColor) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = destructiveColor) },
                    enabled = canDeactivate,
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
private fun AddStoreRow(onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircleIcon(Icons.Filled.Add, accent, size = 40.dp)
        Text(
            text = stringResource(R.string.stores_add),
            style = MaterialTheme.typography.bodyLarge,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OrganizationRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircleIcon(icon, iconColor)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = NumberFormat.getIntegerInstance().format(count),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Identificacao e estado da loja nas listas de gerenciamento. Porte do `StoreManagementRow` do iOS.
 */
@Composable
internal fun StoreManagementRowContent(
    store: StoreProfile,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    detail: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StoreImage(store.imageFileName, side = 40.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = store.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (isLargeFont()) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun StoreSection(
    title: String?,
    accent: Color,
    footer: String? = null,
    dividerInset: Dp = StoreRowDividerInset,
    rows: List<@Composable () -> Unit>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
        if (title != null) {
            AppSectionLabel(title, color = accent)
        }
        AppGroupedCard(rows, dividerInset = dividerInset)
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/** Confirmacao antes de mover uma loja ativa ou arquivada para a lixeira. */
@Composable
internal fun TrashStoreDialog(
    store: StoreProfile,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stores_trash_confirm_title)) },
        text = { Text(stringResource(R.string.stores_trash_confirm_message, store.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.stores_moveToTrash),
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

/** Alerta de erro nas operacoes de gerenciamento de lojas. Porte do `storeOperationErrorAlert` do iOS. */
@Composable
internal fun StoreErrorAlert(
    error: FormError?,
    onDismiss: () -> Unit,
) {
    val current = error ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stores_error_title)) },
        text = { Text(current.messageRes?.let { stringResource(it) } ?: current.text.orEmpty()) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
            }
        },
    )
}
