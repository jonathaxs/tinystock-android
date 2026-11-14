package com.jonathaxs.tinystock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Balao preso ao elemento logo antes dele no layout (colocar dentro do mesmo `Box` do botao). Abre
 * para baixo e, quando falta espaco embaixo e sobra em cima, para cima. Porte do `adaptivePopover` do iOS.
 */
@Composable
fun AppPopover(
    expanded: Boolean,
    onDismiss: () -> Unit,
    width: Dp = 300.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return

    val density = LocalDensity.current
    // A janela do balao tem uma folga em volta para a sombra nao sair cortada; a posicao desconta a folga.
    val provider = remember(density) {
        BalloonPositionProvider(
            margin = with(density) { (12.dp - ShadowRoom).roundToPx() },
            gap = with(density) { (8.dp - ShadowRoom).roundToPx() },
        )
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        // Focavel: o voltar fecha o balao e o TalkBack entra nele.
        properties = PopupProperties(focusable = true),
    ) {
        val origin = TransformOrigin(0.5f, if (provider.opensUp) 1f else 0f)
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.85f, transformOrigin = origin),
            exit = fadeOut(tween(120)) + scaleOut(tween(150), targetScale = 0.9f, transformOrigin = origin),
        ) {
            // No escuro o balao sobe um tom (#1C1C1E) e os cards dentro dele outro (#2C2C2E), como no iOS.
            val base = MaterialTheme.colorScheme
            val dark = base.background.luminance() < 0.5f
            val elevated = if (dark) {
                base.copy(background = base.surfaceContainer, surfaceContainer = base.surfaceContainerHigh)
            } else {
                base
            }
            MaterialTheme(colorScheme = elevated) {
                Surface(
                    modifier = Modifier
                        .padding(ShadowRoom)
                        .width(width),
                    shape = RoundedCornerShape(26.dp),
                    color = elevated.background,
                    shadowElevation = 16.dp,
                ) {
                    Column(content = content)
                }
            }
        }
    }
}

private val ShadowRoom = 24.dp

private class BalloonPositionProvider(private val margin: Int, private val gap: Int) : PopupPositionProvider {
    var opensUp = false
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin.coerceAtLeast(0))
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(margin, maxX)
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val fitsBelow = below + popupContentSize.height <= windowSize.height - margin
        opensUp = !fitsBelow && above >= margin
        return IntOffset(x, if (opensUp) above else below)
    }
}

/** Titulo centralizado no topo de um balao. */
@Composable
fun PopoverTitle(text: String) {
    androidx.compose.material3.Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .wrapContentHeight()
            .padding(horizontal = 16.dp)
            .semantics { heading() },
    )
}

/** Linha de acao de um balao: icone na cor de destaque e texto. Desligada fica cinza. */
@Composable
fun PopoverActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        androidx.compose.material3.Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
