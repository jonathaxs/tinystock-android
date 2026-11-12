package com.jonathaxs.tinystock.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.format.CurrencyFormatter
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.ui.components.ProductImage
import com.jonathaxs.tinystock.ui.form.FormDivider
import com.jonathaxs.tinystock.ui.form.FormPage
import com.jonathaxs.tinystock.ui.form.FormSection
import com.jonathaxs.tinystock.ui.form.FormValueRow
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.UUID

/** Foto, estoque por variacao, precos e potencial de venda. Porte do `ProductDetailView`. */
@Composable
fun ProductDetailScreen(
    storeID: UUID,
    productID: UUID,
    onClose: () -> Unit,
    onEditProduct: (PriceField?) -> Unit,
    onEditVariant: (ProductVariant) -> Unit,
    viewModel: ProductFormsViewModel = viewModel(),
) {
    LaunchedEffect(storeID) { viewModel.setStore(storeID) }
    val data by viewModel.data.collectAsStateWithLifecycle()
    val product = data.product(productID) ?: return
    val variants = data.variants(product)
    val internal = variants.size == 1 && variants.first().isDefault
    val total = variants.sumOf { it.quantity.toLong() }
    val unitProfit = product.salePrice - product.costPrice
    val negative = unitProfit < BigDecimal.ZERO
    val integer = NumberFormat.getIntegerInstance()

    FormPage(
        title = stringResource(R.string.product_details_title),
        onClose = onClose,
        onConfirm = null,
        closeLabel = stringResource(R.string.common_close),
    ) {
        // Foto e nome lado a lado; a linha inteira abre a edicao, onde fica o seletor de foto.
        item("header") {
            FormSection(title = null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onEditProduct(null) }
                        .semantics(mergeDescendants = true) { }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProductImage(product.imageFileName, side = 80.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(product.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.product_form_title_edit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
        item("stock") {
            FormSection(stringResource(R.string.product_details_stock)) {
                // Valores na cor de destaque e seta indicam linhas que abrem uma acao.
                if (!internal) {
                    variants.forEach { variant ->
                        FormValueRow(
                            label = variant.name,
                            value = integer.format(variant.quantity),
                            leading = { ProductImage(variant.imageFileName, side = 40.dp) },
                            onClick = { onEditVariant(variant) },
                        )
                        FormDivider(inset = 68.dp)
                    }
                }
                FormValueRow(
                    stringResource(R.string.product_details_totalStock),
                    integer.format(total),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    bold = true,
                )
            }
        }
        item("prices") {
            FormSection(stringResource(R.string.product_form_section_prices)) {
                FormValueRow(
                    stringResource(R.string.product_form_salePrice),
                    CurrencyFormatter.string(product.salePrice),
                    bold = true,
                    onClick = { onEditProduct(PriceField.Sale) },
                )
                FormDivider()
                FormValueRow(
                    stringResource(R.string.product_form_costPrice),
                    CurrencyFormatter.string(product.costPrice),
                    bold = true,
                    onClick = { onEditProduct(PriceField.Cost) },
                )
                FormDivider()
                FormValueRow(
                    stringResource(R.string.product_form_unitProfit),
                    CurrencyFormatter.string(unitProfit),
                    valueColor = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("potential") {
            FormSection(
                stringResource(R.string.product_details_potential),
                footer = stringResource(R.string.product_details_potentialFooter),
            ) {
                FormValueRow(
                    stringResource(R.string.product_details_potentialRevenue),
                    CurrencyFormatter.string(product.salePrice * BigDecimal.valueOf(total)),
                )
                FormDivider()
                FormValueRow(
                    stringResource(R.string.product_details_potentialProfit),
                    CurrencyFormatter.string(unitProfit * BigDecimal.valueOf(total)),
                    valueColor = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    bold = true,
                )
            }
        }
    }
}
