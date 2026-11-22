package com.jonathaxs.tinystock.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Troca lista/grade dos produtos: foto com foto e nome com nome se transformam, como o
 * `matchedGeometryEffect` do iOS. Fora do catalogo da Inicio fica null e nada se transforma.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class CatalogMorphScopes(val shared: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

val LocalCatalogMorph = staticCompositionLocalOf<CatalogMorphScopes?> { null }

/** Liga a peca a equivalente do outro modo pela chave (null = sem transformacao). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.catalogMorph(key: String?): Modifier {
    val scopes = LocalCatalogMorph.current
    if (key == null || scopes == null) return this
    return with(scopes.shared) {
        this@catalogMorph.sharedElement(rememberSharedContentState(key), scopes.visibility)
    }
}
