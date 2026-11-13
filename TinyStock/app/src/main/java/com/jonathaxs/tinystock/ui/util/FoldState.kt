package com.jonathaxs.tinystock.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * Dobra do aparelho (Pixel Fold e afins), equivalente ao `reservedRegions(kind: .division)` do iOS 27.1.
 * [bounds] em px na janela; [halfOpened] = meio aberto (livro ou mesa); [vertical] = dobradica em pe (livro).
 */
data class FoldInfo(val bounds: Rect, val halfOpened: Boolean, val vertical: Boolean)

/** Le a dobra atual pelo Jetpack WindowManager; null em aparelho sem dobra (ou fora de uma Activity). */
@Composable
fun rememberFoldInfo(): FoldInfo? {
    val activity = LocalContext.current.findActivity() ?: return null
    var fold by remember { mutableStateOf<FoldInfo?>(null) }
    LaunchedEffect(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()?.let { feature ->
                FoldInfo(
                    bounds = Rect(
                        feature.bounds.left.toFloat(), feature.bounds.top.toFloat(),
                        feature.bounds.right.toFloat(), feature.bounds.bottom.toFloat(),
                    ),
                    halfOpened = feature.state == FoldingFeature.State.HALF_OPENED,
                    vertical = feature.orientation == FoldingFeature.Orientation.VERTICAL,
                )
            }
        }
    }
    return fold
}
