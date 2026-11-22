package com.jonathaxs.tinystock.ui.welcome

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
import com.jonathaxs.tinystock.core.service.StoreProfileError
import com.jonathaxs.tinystock.core.service.StoreProfileService
import com.jonathaxs.tinystock.core.util.DomainException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Etapas das boas-vindas, na ordem. */
enum class WelcomeStep(val emoji: String) {
    Start("📦"),
    Store("🛍️"),
    Product("🏷️"),
    Variants("🎨"),
    Ready("🚀"),
    ;

    /** Dicas do primeiro produto: tem o Pular, que vai direto para o fim. */
    val isProductTour: Boolean get() = this == Product || this == Variants

    companion object {
        /** Etapas mostradas. A parte do produto fica de fora quando a loja ja tem produtos (reaberta pelo Sobre). */
        fun flow(includesProductTour: Boolean): List<WelcomeStep> =
            if (includesProductTour) entries.toList() else listOf(Start, Store, Ready)
    }
}

/**
 * Estado das boas-vindas: etapa atual, nome da loja e a restauracao de um backup em arquivo. Porte da
 * logica do `WelcomeView` do iOS; sem iCloud, o backup vem so do seletor de arquivos.
 */
class WelcomeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.container
    private val store = container.store
    private val defaultName = application.getString(R.string.store_default_name)

    var steps by mutableStateOf(WelcomeStep.flow(includesProductTour = true))
        private set
    var step by mutableStateOf(WelcomeStep.Start)
        private set
    var isGoingForward by mutableStateOf(true)
        private set
    var storeName by mutableStateOf("")
    @get:StringRes
    var storeError by mutableStateOf<Int?>(null)
        private set

    /** Backup lido e conferido, esperando a confirmacao. */
    var pendingBackup by mutableStateOf<BackupPayload?>(null)
        private set
    @get:StringRes
    var backupError by mutableStateOf<Int?>(null)
        private set
    var isRestoring by mutableStateOf(false)
        private set

    /** Loja atual, para o formulario real do primeiro produto. */
    var storeID by mutableStateOf<UUID?>(null)
        private set
    /** Formulario do primeiro produto aberto pela dica de variacoes; a contagem muda a cada abertura. */
    var productFormAttempt by mutableStateOf<Int?>(null)
        private set
    private var productCountBeforeForm = 0
    private var formAttempts = 0

    init {
        viewModelScope.launch {
            val id = container.preferences.load() ?: return@launch
            storeID = id
            // Reaberta pelo Sobre com produtos na loja: as dicas do primeiro produto ficam de fora.
            if (store.products(id).isNotEmpty()) steps = WelcomeStep.flow(includesProductTour = false)
            // Um nome ja trocado aparece no campo; o padrao fica so como dica.
            val name = store.store(id)?.name ?: return@launch
            if (name != defaultName && storeName.isEmpty()) storeName = name
        }
    }

    /**
     * Avanca uma etapa. Na ultima, chama [onFinish]. Na etapa da loja, grava o nome antes; na dica de
     * variacoes, abre o formulario do produto.
     */
    fun advance(onFinish: () -> Unit) {
        viewModelScope.launch {
            if (step == WelcomeStep.Store && !saveStoreName()) return@launch
            if (step == WelcomeStep.Variants) {
                openProductForm()
                return@launch
            }
            val next = steps.getOrNull(steps.indexOf(step) + 1)
            if (next == null) onFinish() else go(next, forward = true)
        }
    }

    fun goBack() {
        val previous = steps.getOrNull(steps.indexOf(step) - 1) ?: return
        go(previous, forward = false)
    }

    fun skipProductTour() {
        go(WelcomeStep.Ready, forward = true)
    }

    private fun go(next: WelcomeStep, forward: Boolean) {
        isGoingForward = forward
        step = next
    }

    private suspend fun openProductForm() {
        val id = storeID ?: return
        productCountBeforeForm = store.products(id).size
        formAttempts += 1
        productFormAttempt = formAttempts
    }

    /** Produto salvo segue para o fim; cancelado fica na dica para tentar de novo ou pular. */
    fun productFormClosed() {
        productFormAttempt = null
        val id = storeID ?: return
        viewModelScope.launch {
            if (store.products(id).size > productCountBeforeForm) go(WelcomeStep.Ready, forward = true)
        }
    }

    fun dismissStoreError() {
        storeError = null
    }

    /** Em branco mantem o nome atual. Devolve false quando o nome nao pode ser salvo. */
    private suspend fun saveStoreName(): Boolean {
        val name = storeName.trim()
        val id = container.preferences.load() ?: return true
        val current = store.store(id) ?: return true
        if (name.isEmpty() || name == current.name) return true
        return try {
            StoreProfileService.update(current, name = name, imageFileName = current.imageFileName, store = store)
            true
        } catch (error: DomainException) {
            storeError = when (error.error) {
                StoreProfileError.DuplicateName -> R.string.store_error_duplicateName
                StoreProfileError.EmptyName -> R.string.store_error_emptyName
                StoreProfileError.TrashedStore -> R.string.store_error_trashedStore
                else -> R.string.store_error_archivedStore
            }
            false
        }
    }

    // region Backup

    /** Le e confere o arquivo escolhido; se estiver certo, pede a confirmacao. */
    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw DomainException(BackupError.InvalidFile)
                pendingBackup = withContext(Dispatchers.Default) { BackupManager.decode(bytes) }
            } catch (error: Exception) {
                backupError = messageFor(error)
            }
        }
    }

    fun cancelBackup() {
        pendingBackup = null
    }

    fun dismissBackupError() {
        backupError = null
    }

    /** Troca os dados do app pelos do backup e avisa qual loja abrir. */
    fun restoreBackup(onRestored: (UUID) -> Unit) {
        val payload = pendingBackup ?: return
        pendingBackup = null
        isRestoring = true
        viewModelScope.launch {
            try {
                val result = BackupManager.apply(payload, container.images, store)
                container.preferences.save(result.selectedStoreID)
                onRestored(result.selectedStoreID)
            } catch (error: Exception) {
                backupError = messageFor(error)
            } finally {
                isRestoring = false
            }
        }
    }

    @StringRes
    private fun messageFor(error: Exception): Int = when (val domain = (error as? DomainException)?.error) {
        is BackupError.UnsupportedVersion ->
            if (domain.version == 1) R.string.backup_error_legacy else R.string.backup_error_unsupportedVersion
        else -> R.string.backup_error_invalidFile
    }

    // endregion
}
