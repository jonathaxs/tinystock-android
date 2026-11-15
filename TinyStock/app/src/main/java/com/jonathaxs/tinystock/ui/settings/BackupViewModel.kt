package com.jonathaxs.tinystock.ui.settings

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.container
import com.jonathaxs.tinystock.core.backup.BackupError
import com.jonathaxs.tinystock.core.backup.BackupManager
import com.jonathaxs.tinystock.core.backup.BackupPayload
import com.jonathaxs.tinystock.core.util.DomainException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Aviso de fim de operacao: titulo e mensagem. */
data class BackupMessage(@StringRes val title: Int, @StringRes val message: Int)

/**
 * Exportar e importar o backup em arquivo. Porte do `DataBackupCoordinator` do iOS, sem o iCloud: o
 * arquivo vai e vem pelo seletor do sistema.
 */
class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.container
    private val store = container.store

    /** Backup lido e conferido, esperando a confirmacao. */
    var pendingBackup by mutableStateOf<BackupPayload?>(null)
        private set
    var message by mutableStateOf<BackupMessage?>(null)
        private set
    var isWorking by mutableStateOf(false)
        private set

    /** Grava o banco inteiro no arquivo escolhido. */
    fun export(uri: Uri, selectedStoreID: UUID) {
        isWorking = true
        viewModelScope.launch {
            message = try {
                val bytes = BackupManager.export(selectedStoreID, container.images, store)
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                } ?: throw IllegalStateException()
                BackupMessage(R.string.settings_backup_success_title, R.string.settings_backup_export_success)
            } catch (_: Exception) {
                BackupMessage(R.string.settings_backup_error_title, R.string.settings_backup_error_export)
            } finally {
                isWorking = false
            }
        }
    }

    /** Le e confere o arquivo escolhido; se estiver certo, pede a confirmacao. */
    fun read(uri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw DomainException(BackupError.InvalidFile)
                pendingBackup = withContext(Dispatchers.Default) { BackupManager.decode(bytes) }
            } catch (error: Exception) {
                message = BackupMessage(R.string.settings_backup_error_title, errorMessage(error))
            }
        }
    }

    fun cancelRestore() {
        pendingBackup = null
    }

    fun dismissMessage() {
        message = null
    }

    /** Troca os dados do app pelos do backup e avisa qual loja abrir. */
    fun restore(onRestored: (UUID) -> Unit) {
        val payload = pendingBackup ?: return
        pendingBackup = null
        isWorking = true
        viewModelScope.launch {
            message = try {
                val result = BackupManager.apply(payload, container.images, store)
                container.preferences.save(result.selectedStoreID)
                onRestored(result.selectedStoreID)
                BackupMessage(R.string.settings_backup_success_title, R.string.settings_backup_import_success)
            } catch (error: Exception) {
                BackupMessage(R.string.settings_backup_error_title, errorMessage(error))
            } finally {
                isWorking = false
            }
        }
    }

    @StringRes
    private fun errorMessage(error: Exception): Int = when (val domain = (error as? DomainException)?.error) {
        is BackupError.UnsupportedVersion ->
            if (domain.version == 1) R.string.backup_error_legacy else R.string.backup_error_unsupportedVersion
        else -> R.string.backup_error_invalidFile
    }
}
