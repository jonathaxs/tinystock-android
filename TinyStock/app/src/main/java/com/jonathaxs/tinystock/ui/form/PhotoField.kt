package com.jonathaxs.tinystock.ui.form

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.data.ImageFileStore
import com.jonathaxs.tinystock.ui.components.StoredImage
import com.jonathaxs.tinystock.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Foto em edicao: a gravada ([originalFileName]) ate a pessoa escolher outra ([newBytes]) ou remover.
 * Nada vai para o disco antes de salvar o formulario.
 */
@Stable
class PhotoDraft(val originalFileName: String?) {
    var newBytes by mutableStateOf<ByteArray?>(null)
        private set
    var removed by mutableStateOf(false)
        private set
    var isProcessing by mutableStateOf(false)

    val hasPhoto: Boolean get() = newBytes != null || (!removed && originalFileName != null)
    val changed: Boolean get() = newBytes != null || (removed && originalFileName != null)

    fun replace(bytes: ByteArray) {
        newBytes = bytes
        removed = false
    }

    fun remove() {
        newBytes = null
        removed = true
    }

    /**
     * Grava a foto nova (se houver) e devolve o nome a guardar no registro. Chamar [finish] depois de
     * salvar, com o resultado, para apagar a foto que sobrou.
     */
    suspend fun write(images: ImageFileStore): String? = when {
        newBytes != null -> images.write(newBytes!!)
        removed -> null
        else -> originalFileName
    }

    /** Depois do salvamento: em sucesso apaga a foto antiga trocada; em falha apaga a nova gravada. */
    suspend fun finish(images: ImageFileStore, writtenName: String?, succeeded: Boolean) {
        if (succeeded) {
            if (changed && originalFileName != null && originalFileName != writtenName) {
                originalFileName?.let { runCatching { images.delete(it) } }
            }
        } else if (writtenName != null && writtenName != originalFileName) {
            runCatching { images.delete(writtenName) }
        }
    }
}

/** Reduz a foto escolhida (maior lado 1024 px) e recomprime em JPEG 80. Porte do `ProductImageProcessor`. */
object PhotoProcessor {
    private const val MAX_DIMENSION = 1024
    private const val QUALITY = 80

    suspend fun prepared(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            // O ImageDecoder ja aplica a rotacao do EXIF (foto deitada nao aparece torta).
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val largest = max(info.size.width, info.size.height)
                if (largest > MAX_DIMENSION) {
                    val scale = MAX_DIMENSION.toFloat() / largest
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)
                output.toByteArray()
            }
        }.getOrNull()
    }
}

/**
 * Foto no topo do formulario com o selo da camera; o toque abre Escolher foto, Tirar foto e Remover foto.
 * Porte do `ProductPhotoEditor`.
 */
@Composable
fun PhotoField(draft: PhotoDraft, onError: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val cameraUri = remember { cameraFileUri(context) }

    fun prepare(uri: Uri) {
        draft.isProcessing = true
        scope.launch {
            val bytes = PhotoProcessor.prepared(context, uri)
            draft.isProcessing = false
            if (bytes != null) draft.replace(bytes) else onError(R.string.product_form_photo_error)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::prepare)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) prepare(cameraUri)
    }

    val changeLabel = stringResource(R.string.product_form_photo_change)
    Box(modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable(enabled = !draft.isProcessing) { expanded = true }
                .clearAndSetSemantics {
                    contentDescription = changeLabel
                    role = Role.Button
                },
        ) {
            PhotoPreview(draft)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
                    .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Camera, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
            if (draft.isProcessing) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.product_form_photo_choose)) },
                    leadingIcon = { Icon(AppIcons.Photo, contentDescription = null) },
                    onClick = {
                        expanded = false
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                if (hasCamera) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.product_form_photo_camera)) },
                        leadingIcon = { Icon(AppIcons.Camera, contentDescription = null) },
                        onClick = {
                            expanded = false
                            camera.launch(cameraUri)
                        },
                    )
                }
                if (draft.hasPhoto) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.product_form_photo_remove), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            expanded = false
                            draft.remove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoPreview(draft: PhotoDraft) {
    val side = 104.dp
    val bytes = draft.newBytes
    if (bytes != null) {
        val image = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        if (image != null) {
            Image(
                image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(side).clip(RoundedCornerShape(side / 4.5f)),
            )
            return
        }
    }
    StoredImage(if (draft.removed) null else draft.originalFileName, AppIcons.Box, side)
}

/** Arquivo temporario onde a camera grava a foto, entregue por FileProvider. */
private fun cameraFileUri(context: Context): Uri {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(directory, "photo.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
