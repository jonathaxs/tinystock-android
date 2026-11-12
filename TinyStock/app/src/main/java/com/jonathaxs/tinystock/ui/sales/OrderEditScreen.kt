package com.jonathaxs.tinystock.ui.sales

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.format.CurrencyFormatter
import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.service.SalesOrderDetails
import com.jonathaxs.tinystock.core.service.SalesOrderService
import com.jonathaxs.tinystock.core.service.update
import com.jonathaxs.tinystock.ui.calendar.OrderPresentation
import com.jonathaxs.tinystock.ui.form.FormDateRow
import com.jonathaxs.tinystock.ui.form.FormDivider
import com.jonathaxs.tinystock.ui.form.FormPage
import com.jonathaxs.tinystock.ui.form.FormPercentField
import com.jonathaxs.tinystock.ui.form.FormMenuRow
import com.jonathaxs.tinystock.ui.form.FormSection
import com.jonathaxs.tinystock.ui.form.FormTextField
import com.jonathaxs.tinystock.ui.form.FormValueRow
import com.jonathaxs.tinystock.ui.products.FormErrorAlert
import com.jonathaxs.tinystock.ui.products.ProductFormsViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Dados administrativos e prazos de um pedido em aberto. Produto, quantidade e atendimento ficam como na
 * venda. Porte do `SalesOrderEditView`.
 */
@Composable
fun OrderEditScreen(
    storeID: UUID,
    orderID: UUID,
    onClose: () -> Unit,
    viewModel: ProductFormsViewModel = viewModel(),
) {
    LaunchedEffect(storeID) { viewModel.setStore(storeID) }
    BackHandler(onBack = onClose)
    val data by viewModel.data.collectAsStateWithLifecycle()
    val order = data.orders.firstOrNull { it.id == orderID } ?: return
    OrderEditForm(order, viewModel, onClose)
}

@Composable
private fun OrderEditForm(order: SalesOrder, viewModel: ProductFormsViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    fun day(instant: Instant?) = (instant ?: order.orderedAt).atZone(zone).toLocalDate().toEpochDay()

    var channel by rememberSaveable { mutableStateOf(order.channel ?: SalesChannel.Direct) }
    var customChannel by rememberSaveable { mutableStateOf(order.customChannelName) }
    var buyer by rememberSaveable { mutableStateOf(order.buyerName) }
    var reference by rememberSaveable { mutableStateOf(order.externalReference) }
    var orderedDay by rememberSaveable { mutableLongStateOf(day(order.orderedAt)) }
    var productionDay by rememberSaveable { mutableLongStateOf(day(order.productionDueAt)) }
    var shippingDay by rememberSaveable { mutableLongStateOf(day(order.shippingDueAt)) }
    var tracking by rememberSaveable { mutableStateOf(order.trackingCode) }
    var note by rememberSaveable { mutableStateOf(order.note) }
    var feeText by rememberSaveable { mutableStateOf(CurrencyFormatter.editableText(order.channelFeePercentage)) }

    val fulfillment = order.fulfillment
    val fee = percentage(feeText)
    val preview = previewFee(order.total, fee)
    val canSave = buyer.isNotBlank() && fulfillment != null && order.status != null && fee != null &&
        fee >= BigDecimal.ZERO && fee <= BigDecimal(100) && OrderPresentation.canEdit(order)
    val ordered = LocalDate.ofEpochDay(orderedDay)
    val shipping = LocalDate.ofEpochDay(shippingDay)

    // Mantem a hora original de cada data: o calendario so troca o dia.
    fun keepingTime(original: Instant?, day: Long): Instant {
        val time = (original ?: order.orderedAt).atZone(zone).toLocalTime()
        return LocalDate.ofEpochDay(day).at(time, zone)
    }

    FormPage(
        title = stringResource(R.string.order_edit_title),
        onClose = onClose,
        onConfirm = {
            val percentage = fee ?: return@FormPage
            val details = SalesOrderDetails(
                channel = channel,
                customChannelName = customChannel,
                buyerName = buyer,
                externalReference = reference,
                orderedAt = minOf(keepingTime(order.orderedAt, orderedDay), Instant.now()),
                productionDueAt = if (fulfillment == OrderFulfillment.Production) keepingTime(order.productionDueAt, productionDay) else null,
                shippingDueAt = keepingTime(order.shippingDueAt, shippingDay),
                trackingCode = tracking,
                note = note,
                channelFeePercentage = percentage,
            )
            viewModel.save(null, onSuccess = onClose, describe = { salesErrorText(context, it) }) {
                SalesOrderService.update(order.id, details, store = viewModel.store)
            }
        },
        confirmEnabled = canSave && !viewModel.isSaving,
    ) {
        item("items") {
            FormSection(
                stringResource(R.string.order_detail_items),
                footer = stringResource(R.string.order_edit_items_footer),
            ) {
                order.itemList.forEachIndexed { index, item ->
                    if (index > 0) FormDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) { }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.productName)
                            if (item.variantName.isNotEmpty()) {
                                Text(item.variantName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(NumberFormat.getIntegerInstance().format(item.quantity), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item("customer") {
            FormSection(stringResource(R.string.order_form_section_customer)) {
                FormTextField(
                    buyer,
                    { buyer = it },
                    stringResource(R.string.order_form_buyer),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                FormDivider()
                FormTextField(reference, { reference = it }, stringResource(R.string.order_form_reference))
            }
        }
        item("dates") {
            FormSection(stringResource(R.string.order_form_section_dates)) {
                val orderedLabel = stringResource(R.string.order_form_orderedAt)
                FormDateRow(orderedLabel, orderedLabel, ordered, {
                    orderedDay = it.toEpochDay()
                    if (shippingDay < orderedDay) shippingDay = orderedDay
                    productionDay = productionDay.coerceIn(orderedDay, shippingDay)
                }, max = LocalDate.now(zone))
                if (fulfillment == OrderFulfillment.Production) {
                    FormDivider()
                    val productionLabel = stringResource(R.string.order_form_productionDueAt)
                    FormDateRow(
                        productionLabel,
                        productionLabel,
                        LocalDate.ofEpochDay(productionDay),
                        { productionDay = it.toEpochDay() },
                        min = ordered,
                        max = shipping,
                    )
                }
                FormDivider()
                val shippingLabel = stringResource(R.string.order_form_shippingDueAt)
                FormDateRow(shippingLabel, shippingLabel, shipping, {
                    shippingDay = it.toEpochDay()
                    productionDay = productionDay.coerceIn(orderedDay, shippingDay)
                }, min = ordered)
            }
        }
        item("channel") {
            FormSection(stringResource(R.string.order_form_section_channel)) {
                FormMenuRow(stringResource(R.string.order_form_channel), channel, channelOptions(), { channel = it })
                if (channel == SalesChannel.Other) {
                    FormDivider()
                    FormTextField(customChannel, { customChannel = it }, stringResource(R.string.order_form_customChannel))
                }
                FormDivider()
                FormPercentField(
                    feeText,
                    { feeText = it },
                    stringResource(R.string.order_form_channelFee),
                    label = stringResource(R.string.order_form_channelFee),
                )
            }
        }
        item("additional") {
            FormSection(stringResource(R.string.order_detail_additional)) {
                FormTextField(
                    tracking,
                    { tracking = it },
                    stringResource(R.string.order_edit_tracking),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                FormDivider()
                FormTextField(note, { note = it }, stringResource(R.string.order_form_note), singleLine = false)
            }
        }
        item("summary") {
            FormSection(stringResource(R.string.order_form_section_summary)) {
                FormValueRow(stringResource(R.string.order_form_total), CurrencyFormatter.string(order.total))
                FormDivider()
                FormValueRow(stringResource(R.string.order_form_fee), CurrencyFormatter.string(preview))
                FormDivider()
                FormValueRow(stringResource(R.string.order_form_netProfit), CurrencyFormatter.string(order.grossProfit - preview))
            }
        }
    }
    FormErrorAlert(viewModel, R.string.order_operation_error_title)
}
