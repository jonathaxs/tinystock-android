package com.jonathaxs.tinystock.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jonathaxs.tinystock.TinyStockApplication
import com.jonathaxs.tinystock.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Le a foto gravada pelo app (fora da thread principal). Null enquanto carrega ou sem foto. */
@Composable
fun rememberStoredImage(fileName: String?): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, fileName) {
        value = null
        if (fileName == null) return@produceState
        val images = (context.applicationContext as TinyStockApplication).container.images
        value = withContext(Dispatchers.IO) {
            images.read(fileName)?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        }
    }
    return image
}

/** Foto quadrada com cantos arredondados, ou um icone cinza no lugar. Decorativa: o nome ao lado identifica. */
@Composable
fun StoredImage(fileName: String?, placeholder: ImageVector, side: Dp, modifier: Modifier = Modifier) {
    val image = rememberStoredImage(fileName)
    val shape = RoundedCornerShape(side / 4.5f)
    Box(
        modifier
            .size(side)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(side))
        } else {
            Icon(
                placeholder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(side * 0.45f),
            )
        }
    }
}

@Composable
fun StoreImage(fileName: String?, modifier: Modifier = Modifier, side: Dp = 44.dp) =
    StoredImage(fileName, AppIcons.Storefront, side, modifier)

@Composable
fun ProductImage(fileName: String?, modifier: Modifier = Modifier, side: Dp = 52.dp) =
    StoredImage(fileName, AppIcons.Box, side, modifier)
