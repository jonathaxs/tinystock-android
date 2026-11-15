package com.jonathaxs.tinystock.ui.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.service.StoreProfileError
import com.jonathaxs.tinystock.core.service.StoreProfileService
import com.jonathaxs.tinystock.core.util.DomainException
import com.jonathaxs.tinystock.ui.form.FormPage
import com.jonathaxs.tinystock.ui.form.FormSection
import com.jonathaxs.tinystock.ui.form.FormTextField
import com.jonathaxs.tinystock.ui.form.PhotoField
import com.jonathaxs.tinystock.ui.icons.AppIcons
import com.jonathaxs.tinystock.ui.products.FormError
import com.jonathaxs.tinystock.ui.products.FormErrorAlert
import com.jonathaxs.tinystock.ui.products.ProductFormsViewModel
import java.util.UUID

internal fun storeErrorMessage(error: Throwable): FormError {
    val res = when ((error as? DomainException)?.error) {
        StoreProfileError.EmptyName -> R.string.store_error_emptyName
        StoreProfileError.DuplicateName -> R.string.store_error_duplicateName
        StoreProfileError.LastActiveStore -> R.string.store_error_lastActiveStore
        StoreProfileError.ArchivedStore -> R.string.store_error_archivedStore
        StoreProfileError.TrashedStore -> R.string.store_error_trashedStore
        StoreProfileError.StoreNotInTrash -> R.string.store_error_storeNotInTrash
        else -> null
    }
    return FormError(res, error.localizedMessage)
}

/**
 * Cadastro e edicao de loja: foto e nome. Porte do `StoreFormView` do iOS.
 */
@Composable
fun StoreFormScreen(
    storeID: UUID?,
    onClose: () -> Unit,
    onCreated: (StoreProfile) -> Unit = {},
    viewModel: ProductFormsViewModel = viewModel(),
) {
    var loaded by remember { mutableStateOf<StoreProfile?>(null) }
    LaunchedEffect(storeID) {
        if (storeID != null) loaded = viewModel.store.store(storeID)
    }
    val editing = if (storeID != null) loaded ?: return else null
    var name by rememberSaveable(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    val photo = viewModel.photo(editing?.id?.toString() ?: "new", editing?.imageFileName)

    FormPage(
        title = stringResource(if (editing == null) R.string.store_form_title_new else R.string.store_form_title_edit),
        onClose = onClose,
        onConfirm = {
            var created: StoreProfile? = null
            viewModel.save(
                photo,
                onSuccess = {
                    created?.let(onCreated)
                    onClose()
                },
                describe = ::storeErrorMessage,
            ) { imageFileName ->
                if (editing != null) {
                    StoreProfileService.update(editing, name, imageFileName, store = viewModel.store)
                } else {
                    created = StoreProfileService.create(name, imageFileName, store = viewModel.store)
                }
            }
        },
        confirmEnabled = name.isNotBlank() && !photo.isProcessing && !viewModel.isSaving,
    ) {
        item("photo") {
            PhotoField(
                photo,
                onError = viewModel::showError,
                placeholder = AppIcons.Storefront,
                chooseLabel = stringResource(R.string.store_form_photo_choose),
                removeLabel = stringResource(R.string.store_form_photo_remove),
            )
        }
        item("name") {
            FormSection(stringResource(R.string.store_form_section_identification)) {
                FormTextField(
                    name,
                    { name = it },
                    stringResource(R.string.store_form_name),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
        }
    }
    FormErrorAlert(viewModel, R.string.store_error_title)
}
