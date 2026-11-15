package com.jonathaxs.tinystock.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.backup.BackupManager
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.ui.components.AppPage
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.icons.AppIcons
import com.jonathaxs.tinystock.ui.theme.color
import java.util.UUID

/**
 * Backup em arquivo: exportar e importar pelo seletor do sistema. Porte do `BackupSettingsView` do iOS,
 * sem a secao do iCloud (no Android o arquivo pode ir para o Drive pelo proprio seletor).
 */
@Composable
fun BackupSettingsScreen(
    selectedStoreID: UUID?,
    onRestored: (UUID) -> Unit,
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(),
) {
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && selectedStoreID != null) viewModel.export(uri, selectedStoreID)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.read(uri)
    }
    val blue = AppAccentColor.Blue.color(isSystemInDarkTheme())

    AppPage(stringResource(R.string.settings_backup_title), onBack) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppStyle.HorizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SettingsCardSection(
                title = stringResource(R.string.settings_backup_file_section),
                footer = stringResource(R.string.settings_backup_section_footer),
                rows = listOf(
                    {
                        SettingsActionRow(
                            icon = AppIcons.ArrowUpTray,
                            iconColor = blue,
                            title = stringResource(R.string.settings_backup_export),
                            enabled = !viewModel.isWorking && selectedStoreID != null,
                            onClick = { exporter.launch(BackupManager.suggestedFilename()) },
                        )
                    },
                    {
                        SettingsActionRow(
                            icon = AppIcons.ArrowDownTray,
                            iconColor = blue,
                            title = stringResource(R.string.settings_backup_import),
                            enabled = !viewModel.isWorking,
                            onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        )
                    },
                ),
            )
        }
    }

    viewModel.pendingBackup?.let { payload ->
        val summary = payload.summary
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.settings_backup_import_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_backup_import_confirm_message_v2,
                        summary.storeCount, summary.productCount, summary.variantCount, summary.orderCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.restore(onRestored) }) {
                    Text(stringResource(R.string.settings_backup_import_confirm_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    viewModel.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text(stringResource(message.title)) },
            text = { Text(stringResource(message.message)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessage) { Text(stringResource(R.string.common_ok)) }
            },
        )
    }
}
