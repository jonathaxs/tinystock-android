package com.jonathaxs.tinystock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.ui.icons.AppIcons

/**
 * Camada de estilo unica do app, porte do `AppStyle.swift`: medidas, cards solidos e componentes
 * reutilizaveis. O vidro do iOS vira superficie solida do Material, sem imitar o efeito.
 */
object AppStyle {
    /** Raio dos cards. */
    val CardRadius = 22.dp

    /** Espaco horizontal das telas. */
    val HorizontalPadding = 20.dp

    val CardShape = RoundedCornerShape(CardRadius)
}

// region Rotulo de secao

/** Titulo pequeno acima de um card agrupado. Nos Ajustes vem na cor de destaque. */
@Composable
fun AppSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .padding(horizontal = 6.dp)
            .semantics { heading() },
    )
}

// endregion

// region Card

/** Fundo de card solido. Selecionado ganha tom e borda da cor de destaque. */
@Composable
fun Modifier.appCard(
    isSelected: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
): Modifier {
    val tint by animateColorAsState(
        if (isSelected) accentColor.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(200),
        label = "cardTint",
    )
    val border by animateColorAsState(
        if (isSelected) accentColor else Color.Transparent,
        animationSpec = tween(200),
        label = "cardBorder",
    )
    return this
        .clip(AppStyle.CardShape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .background(tint)
        .border(2.dp, border, AppStyle.CardShape)
}

/** Card no estilo lista agrupada: junta as linhas num bloco so, com divisor entre elas. */
@Composable
fun AppGroupedCard(
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    /** Recuo do divisor, alinhado ao texto depois do icone. */
    dividerInset: Dp = 52.dp,
) {
    Column(modifier.fillMaxWidth().appCard()) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = dividerInset),
                    thickness = Dp.Hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            row()
        }
    }
}

// endregion

// region Menu

/** Fonte grande (equivale ao `isAccessibilitySize` do iOS): troca linhas lado a lado por pilhas. */
@Composable
fun isLargeFont(): Boolean = LocalDensity.current.fontScale >= 1.5f

/**
 * Linha arredondada que abre um menu: icone, rotulo, valor atual e as setas. Porte do `AppMenuRow` do
 * iOS (periodo dos Relatorios, filtro do Calendario). O conteudo recebe `close` para fechar o menu.
 */
@Composable
fun AppMenuRow(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    menuContent: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isLargeFont()) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value)
                }
            } else {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                Text(value, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 2.dp))
                Icon(AppIcons.UpDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = shape) {
                menuContent { expanded = false }
            }
        }
    }
}

/** Opcao de menu com a marca de selecionado a esquerda, como o `Picker` inline do iOS. */
@Composable
fun AppMenuItem(text: String, checked: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null) else Spacer(Modifier.width(24.dp))
        },
        trailingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) } },
        modifier = Modifier.semantics { selected = checked },
    )
}

// endregion

// region Botoes

/** Botao principal em pilula com a cor de destaque. `large` segue o controlSize(.large) do iOS. */
@Composable
fun AppProminentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    large: Boolean = true,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (large) 52.dp else 40.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = if (large) 24.dp else 16.dp),
        content = content,
    )
}

/** Botao secundario em pilula neutra com o texto na cor de destaque. */
@Composable
fun AppSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    large: Boolean = true,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (large) 52.dp else 40.dp),
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = color,
        ),
        border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = if (large) 24.dp else 16.dp),
        content = content,
    )
}

/** Botao redondo so de icone, neutro com o icone na cor de destaque (linha de Relatorios, Calendario...). */
@Composable
fun AppCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 52.dp,
    /** Dentro de um card, usar um tom acima do card para o circulo aparecer. */
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = color,
        ),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.46f))
    }
}

// endregion

// region Icones

/** Icone branco num quadrado colorido com degrade leve, como nos Ajustes do sistema. Decorativo. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.18f), color)))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

/** Icone na cor pedida num circulo com tom da mesma cor. Equivale ao `GlassIcon` do iOS. Decorativo. */
@Composable
fun CircleIcon(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.52f))
    }
}

// endregion

// region RoundStepButton

enum class RoundStepKind { Minus, Plus, Trash }

/** Botao redondo com -, + ou lixeira, no tamanho exato pedido. */
@Composable
fun RoundStepButton(
    kind: RoundStepKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val icon = when (kind) {
        RoundStepKind.Minus -> AppIcons.Minus
        RoundStepKind.Plus -> Icons.Filled.Add
        RoundStepKind.Trash -> Icons.Filled.Delete
    }
    val label = contentDescription ?: stringResource(
        when (kind) {
            RoundStepKind.Minus -> R.string.a11y_decrease
            RoundStepKind.Plus -> R.string.a11y_increase
            RoundStepKind.Trash -> R.string.a11y_delete
        },
    )
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = color,
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            disabledContentColor = color.copy(alpha = 0.35f),
        ),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(size * 0.5f))
    }
}

// endregion
