package com.jonathaxs.tinystock.ui.form

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.service.ProductError
import com.jonathaxs.tinystock.core.service.ProductFormError
import com.jonathaxs.tinystock.core.service.ProductVariantError
import com.jonathaxs.tinystock.core.service.StockError
import com.jonathaxs.tinystock.core.util.DomainException
import com.jonathaxs.tinystock.ui.components.AppCircleButton
import com.jonathaxs.tinystock.ui.components.AppMenuItem
import com.jonathaxs.tinystock.ui.components.AppSectionLabel
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.components.RoundStepButton
import com.jonathaxs.tinystock.ui.components.RoundStepKind
import com.jonathaxs.tinystock.ui.components.appCard
import com.jonathaxs.tinystock.ui.icons.AppIcons

/**
 * Tela de formulario em tela cheia (a "sheet" do iOS): X a esquerda, titulo no meio e o ✓ a direita, que
 * so acende quando da para salvar. Porte do `Form` com `SheetCloseButton`/`SheetConfirmButton` (C8).
 */
@Composable
fun FormPage(
    title: String,
    onClose: () -> Unit,
    onConfirm: (() -> Unit)?,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    closeLabel: String = stringResource(R.string.common_cancel),
    confirmLabel: String = stringResource(R.string.common_save),
    content: LazyListScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AppCircleButton(
                icon = Icons.Filled.Close,
                contentDescription = closeLabel,
                onClick = onClose,
                color = MaterialTheme.colorScheme.onSurface,
                size = 44.dp,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp)
                    .semantics { heading() },
            )
            if (onConfirm != null) {
                FilledIconButton(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(44.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = confirmLabel)
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = AppStyle.HorizontalPadding,
                end = AppStyle.HorizontalPadding,
                top = 8.dp,
                bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

/** Secao: titulo na cor de destaque (como `Section(tinted:)`), linhas num card e um rodape opcional. */
@Composable
fun FormSection(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    footerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let { AppSectionLabel(it, color = MaterialTheme.colorScheme.primary) }
        Column(Modifier.fillMaxWidth().appCard(), content = content)
        footer?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = footerColor,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/** Divisor entre linhas de um [FormSection]. */
@Composable
fun FormDivider(inset: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = inset),
        thickness = Dp.Hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Campo de texto sem moldura dentro do card, com o texto de exemplo em cinza. */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /** Nome lido pelo TalkBack quando o titulo da secao nao basta. */
    label: String = placeholder,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    textAlign: TextAlign = TextAlign.Start,
) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 5,
            keyboardOptions = keyboardOptions,
            textStyle = textStyle.copy(color = color, textAlign = textAlign),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        )
    }
}

/** Opcao de um [FormMenuRow]. */
data class FormMenuOption<T>(val value: T, val title: String)

/** Valor escolhido na cor de destaque com as setas; o toque abre o menu (o `Picker` em menu do iOS). */
@Composable
fun <T> FormMenuRow(
    label: String,
    selected: T?,
    options: List<FormMenuOption<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val title = options.firstOrNull { it.value == selected }?.title.orEmpty()
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button) { expanded = true }
                .semantics(mergeDescendants = true) { contentDescription = "$label, $title" }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, color = MaterialTheme.colorScheme.primary)
            Icon(AppIcons.UpDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in options) {
                AppMenuItem(
                    text = option.title,
                    checked = option.value == selected,
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    },
                )
            }
        }
    }
}

/** Linha "rotulo ... valor". Com [onClick] o valor fica na cor de destaque com a seta, como no iOS. */
@Composable
fun FormValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    bold: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(clickable)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Text(label, fontWeight = if (bold) FontWeight.SemiBold else null, modifier = Modifier.weight(1f))
        Text(
            value,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else valueColor,
            fontWeight = if (bold) FontWeight.SemiBold else null,
            textAlign = TextAlign.End,
        )
        if (onClick != null) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Quantidade com - e + redondos e o numero no meio, que tambem aceita digitar. Porte do
 * `StockQuantityStepper` (C8, V13). O titulo nao aparece (ja esta na secao) mas e o nome no TalkBack.
 */
@Composable
fun StockQuantityStepper(
    title: String,
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..999_999,
) {
    // O texto vive aqui para o campo poder ficar vazio enquanto a pessoa digita.
    var text by remember { mutableStateOf(quantity.toString()) }
    // Valor trocado de fora (outra variacao escolhida, limite do estoque): o campo acompanha.
    LaunchedEffect(quantity) { if (text.toIntOrNull() != quantity) text = quantity.toString() }

    fun step(delta: Int) {
        val next = (quantity + delta).coerceIn(range)
        if (next != quantity) {
            text = next.toString()
            onQuantityChange(next)
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundStepButton(
            kind = RoundStepKind.Minus,
            onClick = { step(-1) },
            enabled = quantity > range.first,
        )
        BasicTextField(
            value = text,
            onValueChange = { typed ->
                val digits = typed.filter(Char::isDigit).take(7)
                val value = digits.toIntOrNull()
                if (value == null) {
                    text = digits
                    return@BasicTextField
                }
                val clamped = value.coerceIn(range)
                text = clamped.toString()
                if (clamped != quantity) onQuantityChange(clamped)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                // Campo apagado volta para o valor atual ao sair.
                .onFocusChanged { if (!it.isFocused && text.isEmpty()) text = quantity.toString() }
                .semantics { contentDescription = title },
        )
        RoundStepButton(
            kind = RoundStepKind.Plus,
            onClick = { step(1) },
            enabled = quantity < range.last,
        )
    }
}

/** Mensagem dos erros de produto, variacao e estoque. Null para erro inesperado. */
@StringRes
fun productErrorMessage(error: Throwable): Int? = when ((error as? DomainException)?.error) {
    ProductError.EmptyName -> R.string.product_error_emptyName
    ProductError.DuplicateName -> R.string.product_error_duplicateName
    ProductError.NegativeCostPrice -> R.string.product_error_negativeCostPrice
    ProductError.NegativeSalePrice -> R.string.product_error_negativeSalePrice
    ProductError.ActiveOrders -> R.string.product_error_activeOrders
    ProductFormError.MissingVariation -> R.string.product_form_error_missingVariation
    ProductFormError.CannotDisableMultipleVariations -> R.string.product_form_error_multipleVariations
    ProductVariantError.EmptyName -> R.string.variant_error_emptyName
    ProductVariantError.DuplicateName -> R.string.variant_error_duplicateName
    ProductVariantError.NegativeQuantity -> R.string.variant_error_negativeQuantity
    ProductVariantError.ProductMismatch -> R.string.variant_error_productMismatch
    StockError.InvalidQuantity -> R.string.stock_error_invalidQuantity
    StockError.NegativeBalance -> R.string.stock_error_negativeBalance
    StockError.NoChange -> R.string.stock_error_noChange
    StockError.QuantityOverflow -> R.string.stock_error_quantityOverflow
    StockError.ProductMismatch -> R.string.stock_error_productMismatch
    StockError.MovementMismatch -> R.string.stock_error_movementMismatch
    StockError.AlreadyReversed -> R.string.stock_error_alreadyReversed
    StockError.ReversalNotAllowed -> R.string.stock_error_reversalNotAllowed
    StockError.OrderManagedMovement -> R.string.stock_error_orderManaged
    else -> null
}
