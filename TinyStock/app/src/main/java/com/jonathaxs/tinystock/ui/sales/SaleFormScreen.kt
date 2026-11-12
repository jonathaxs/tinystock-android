package com.jonathaxs.tinystock.ui.sales

import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.format.CurrencyFormatter
import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.service.ChannelFeeCalculator
import com.jonathaxs.tinystock.core.service.SalesOrderError
import com.jonathaxs.tinystock.core.service.SalesOrderLine
import com.jonathaxs.tinystock.core.service.SalesOrderService
import com.jonathaxs.tinystock.core.util.DomainException
import com.jonathaxs.tinystock.ui.calendar.OrderPresentation
import com.jonathaxs.tinystock.ui.form.FormDateRow
import com.jonathaxs.tinystock.ui.form.FormDivider
import com.jonathaxs.tinystock.ui.form.FormMenuOption
import com.jonathaxs.tinystock.ui.form.FormMenuRow
import com.jonathaxs.tinystock.ui.form.FormPage
import com.jonathaxs.tinystock.ui.form.FormPercentField
import com.jonathaxs.tinystock.ui.form.FormSection
import com.jonathaxs.tinystock.ui.form.FormSegmented
import com.jonathaxs.tinystock.ui.form.FormTextField
import com.jonathaxs.tinystock.ui.form.FormValueRow
import com.jonathaxs.tinystock.ui.form.StockQuantityStepper
import com.jonathaxs.tinystock.ui.products.FormError
import com.jonathaxs.tinystock.ui.products.FormErrorAlert
import com.jonathaxs.tinystock.ui.products.ProductFormsViewModel
import com.jonathaxs.tinystock.ui.products.ProductSheetHeader
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

internal val SalesChannel.nameRes: Int
    get() = when (this) {
        SalesChannel.Direct -> R.string.order_channel_direct
        SalesChannel.Shopee -> R.string.order_channel_shopee
        SalesChannel.MercadoLivre -> R.string.order_channel_mercadoLivre
        SalesChannel.Other -> R.string.order_channel_other
    }

internal val OrderFulfillment.titleRes: Int
    get() = when (this) {
        OrderFulfillment.ReadyStock -> R.string.order_fulfillment_readyStock
        OrderFulfillment.Production -> R.string.order_fulfillment_production
    }

@Composable
internal fun channelOptions(): List<FormMenuOption<SalesChannel>> =
    SalesChannel.entries.map { FormMenuOption(it, stringResource(it.nameRes)) }

/** Percentual digitado; vazio vale zero. Null quando o texto nao e numero. */
internal fun percentage(text: String): BigDecimal? =
    if (text.isBlank()) BigDecimal.ZERO else CurrencyFormatter.decimal(text)

/** Taxa do canal para mostrar no resumo; percentual invalido conta como zero, como o `try?` do iOS. */
internal fun previewFee(revenue: BigDecimal, percentage: BigDecimal?): BigDecimal =
    percentage?.let { runCatching { ChannelFeeCalculator.fee(revenue, it) }.getOrNull() } ?: BigDecimal.ZERO

/** Dia escolhido no calendario com a hora de [time] (o `DatePicker(.date)` do iOS mantem a hora). */
internal fun LocalDate.at(time: LocalTime, zone: ZoneId): Instant = atTime(time).atZone(zone).toInstant()

/** Mensagem dos erros de venda e de pedido, com os dados do estoque quando falta unidade. */
internal fun salesErrorText(context: Context, error: Throwable): FormError {
    val domain = (error as? DomainException)?.error
    val number = NumberFormat.getIntegerInstance()
    val text = when (domain) {
        SalesOrderError.EmptyOrder -> context.getString(R.string.order_error_empty)
        SalesOrderError.StoreUnavailable -> context.getString(R.string.order_error_store)
        SalesOrderError.ProductUnavailable -> context.getString(R.string.order_error_product)
        SalesOrderError.VariantMismatch -> context.getString(R.string.order_error_variant)
        SalesOrderError.InvalidQuantity -> context.getString(R.string.order_error_quantity)
        SalesOrderError.QuantityOverflow -> context.getString(R.string.order_error_quantityOverflow)
        is SalesOrderError.InsufficientStock -> context.getString(
            R.string.order_error_stock,
            number.format(domain.requested),
            domain.productName,
            domain.variantName,
            number.format(domain.available),
        )
        SalesOrderError.InvalidMoney -> context.getString(R.string.order_error_money)
        SalesOrderError.InvalidChannelFee -> context.getString(R.string.order_error_fee)
        SalesOrderError.InvalidDates -> context.getString(R.string.order_error_dates)
        SalesOrderError.FutureOrderDate -> context.getString(R.string.order_error_futureDate)
        SalesOrderError.ShippingBeforeOrder -> context.getString(R.string.order_error_shippingDate)
        SalesOrderError.ProductionDateRequired -> context.getString(R.string.order_error_productionRequired)
        SalesOrderError.InvalidProductionDate -> context.getString(R.string.order_error_productionDate)
        SalesOrderError.DuplicateOrder -> context.getString(R.string.order_error_duplicate)
        null -> error.localizedMessage
        else -> context.getString(OrderPresentation.errorMessage(error))
    }
    return FormError(null, text)
}

/**
 * Pedido de um produto e uma variacao, escolhidos no catalogo ou no proprio formulario. Porte do
 * `SalesOrderFormView`. Aberto pelo produto, nao oferece a troca de produto.
 */
@Composable
fun SaleFormScreen(
    storeID: UUID,
    fixedProductID: UUID?,
    onClose: () -> Unit,
    viewModel: ProductFormsViewModel = viewModel(),
) {
    LaunchedEffect(storeID) { viewModel.setStore(storeID) }
    val context = LocalContext.current
    val data by viewModel.data.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    // O mesmo id acompanha todas as tentativas deste rascunho e impede uma segunda baixa.
    val draftID by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var productID by rememberSaveable { mutableStateOf(fixedProductID?.toString()) }
    var variantID by rememberSaveable { mutableStateOf<String?>(null) }
    var fulfillment by rememberSaveable { mutableStateOf(OrderFulfillment.ReadyStock) }
    var quantity by rememberSaveable { mutableIntStateOf(1) }
    var channel by rememberSaveable { mutableStateOf(SalesChannel.Direct) }
    var customChannel by rememberSaveable { mutableStateOf("") }
    var buyer by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf("") }
    var orderedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var productionDay by rememberSaveable { mutableLongStateOf(today.plusDays(1).toEpochDay()) }
    var shippingDay by rememberSaveable { mutableLongStateOf(today.plusDays(2).toEpochDay()) }
    var feeText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    val product = data.product(productID?.let(UUID::fromString))
    val variants = data.variants(product)
    val hasStock = variants.any { it.quantity > 0 }
    val selected = variants.firstOrNull { it.id.toString() == variantID }
    val internal = variants.size == 1 && variants.first().isDefault
    val quantityLimit = if (fulfillment == OrderFulfillment.ReadyStock) maxOf(1, selected?.quantity ?: 0) else 9_999

    fun clampQuantity() {
        quantity = quantity.coerceIn(1, quantityLimit)
    }

    // Aberto pelo Adicionar, comeca pelo primeiro produto do catalogo.
    LaunchedEffect(data.loaded) {
        if (productID == null) productID = data.products.firstOrNull()?.id?.toString()
    }
    // Sem estoque em nenhuma variacao, a venda so pode ser atendida por producao.
    LaunchedEffect(product?.id, data.loaded) {
        if (product == null || !data.loaded) return@LaunchedEffect
        if (variants.none { it.id.toString() == variantID }) {
            variantID = (variants.firstOrNull { it.quantity > 0 } ?: variants.firstOrNull())?.id?.toString()
        }
        if (!hasStock) fulfillment = OrderFulfillment.Production
    }
    LaunchedEffect(quantityLimit) { clampQuantity() }

    // Pronta entrega precisa de estoque: troca para uma variacao com estoque ou passa para producao.
    fun selectFulfillment(option: OrderFulfillment) {
        fulfillment = option
        if (option == OrderFulfillment.ReadyStock && (selected?.quantity ?: 0) == 0) {
            val withStock = variants.firstOrNull { it.quantity > 0 }
            if (withStock != null) variantID = withStock.id.toString() else fulfillment = OrderFulfillment.Production
        }
    }

    // Uma variacao sem estoque so pode ser atendida por producao.
    fun selectVariant(id: UUID) {
        variantID = id.toString()
        if (fulfillment == OrderFulfillment.ReadyStock && (variants.firstOrNull { it.id == id }?.quantity ?: 0) == 0) {
            fulfillment = OrderFulfillment.Production
        }
    }

    val ordered = LocalDate.ofEpochDay(orderedDay)
    val shipping = LocalDate.ofEpochDay(shippingDay)
    val production = LocalDate.ofEpochDay(productionDay)
    val fee = percentage(feeText)
    val salePrice = product?.salePrice ?: BigDecimal.ZERO
    val costPrice = product?.costPrice ?: BigDecimal.ZERO
    val subtotal = salePrice * BigDecimal(quantity)
    val gross = (salePrice - costPrice) * BigDecimal(quantity)
    val channelFee = previewFee(subtotal, fee)
    val canSave = buyer.isNotBlank() && product != null && selected != null && quantity > 0 &&
        fee != null && fee >= BigDecimal.ZERO && fee <= BigDecimal(100) &&
        (fulfillment != OrderFulfillment.ReadyStock || selected.quantity >= quantity)
    val integer = NumberFormat.getIntegerInstance()

    FormPage(
        title = stringResource(R.string.order_form_title),
        onClose = onClose,
        confirmLabel = stringResource(R.string.order_form_save),
        onConfirm = {
            val chosenProduct = product ?: return@FormPage
            val variant = selected ?: return@FormPage
            val percentage = fee ?: return@FormPage
            val time = LocalTime.now(zone)
            viewModel.save(null, onSuccess = onClose, describe = { salesErrorText(context, it) }) {
                SalesOrderService.register(
                    id = UUID.fromString(draftID),
                    storeID = storeID,
                    lines = listOf(SalesOrderLine(chosenProduct.id, variant.id, quantity)),
                    fulfillment = fulfillment,
                    channel = channel,
                    customChannelName = customChannel,
                    buyerName = buyer,
                    externalReference = reference,
                    orderedAt = minOf(ordered.at(time, zone), Instant.now()),
                    productionDueAt = if (fulfillment == OrderFulfillment.Production) production.at(time, zone) else null,
                    shippingDueAt = shipping.at(time, zone),
                    note = note,
                    channelFeePercentage = percentage,
                    store = viewModel.store,
                )
            }
        },
        confirmEnabled = canSave && !viewModel.isSaving,
    ) {
        item("customer") {
            FormSection(stringResource(R.string.order_form_section_customer)) {
                FormTextField(
                    buyer,
                    { buyer = it },
                    stringResource(R.string.order_form_buyer),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
        }
        item("reference") {
            FormSection(stringResource(R.string.order_form_section_reference)) {
                FormTextField(
                    reference,
                    { reference = it },
                    stringResource(R.string.order_form_optional),
                    label = stringResource(R.string.order_form_section_reference),
                )
            }
        }
        if (fixedProductID != null) {
            product?.let { item("header") { ProductSheetHeader(it) } }
        } else {
            item("product") {
                FormSection(stringResource(R.string.order_form_section_product)) {
                    FormMenuRow(
                        label = stringResource(R.string.order_form_section_product),
                        selected = product?.id,
                        options = data.products.map { FormMenuOption(it.id, it.name) },
                        onSelect = {
                            productID = it.toString()
                            variantID = null
                        },
                    )
                }
            }
        }
        if (product != null && variants.isEmpty()) {
            item("noVariants") {
                FormSection(null) {
                    FormValueRow(stringResource(R.string.order_form_noVariants), "")
                }
            }
        } else if (product != null) {
            if (!internal) {
                item("variant") {
                    FormSection(stringResource(R.string.product_form_variant_title)) {
                        FormMenuRow(
                            label = stringResource(R.string.product_form_variant_title),
                            selected = selected?.id,
                            options = variants.map {
                                FormMenuOption(
                                    it.id,
                                    stringResource(R.string.order_form_variant_option, it.name, integer.format(it.quantity)),
                                )
                            },
                            onSelect = ::selectVariant,
                        )
                    }
                }
            }
            item("quantity") {
                val title = stringResource(R.string.order_form_quantity)
                FormSection(
                    title,
                    footer = selected?.let { stringResource(R.string.products_catalog_quantity, integer.format(it.quantity)) },
                ) {
                    StockQuantityStepper(title, quantity, { quantity = it }, range = 1..quantityLimit)
                }
            }
        }
        item("fulfillment") {
            FormSection(
                stringResource(R.string.order_form_fulfillment),
                footer = stringResource(
                    if (fulfillment == OrderFulfillment.ReadyStock) R.string.order_form_fulfillment_ready_footer
                    else R.string.order_form_fulfillment_production_footer,
                ),
            ) {
                FormSegmented(
                    options = OrderFulfillment.entries.map { FormMenuOption(it, stringResource(it.titleRes)) },
                    selected = fulfillment,
                    onSelect = ::selectFulfillment,
                )
            }
        }
        item("ordered") {
            val title = stringResource(R.string.order_form_orderedAtTitle)
            FormSection(title) {
                FormDateRow(null, title, ordered, {
                    orderedDay = it.toEpochDay()
                    // Prazos nunca antes da venda; producao ate o despacho.
                    if (shippingDay < orderedDay) shippingDay = orderedDay
                    productionDay = productionDay.coerceIn(orderedDay, shippingDay)
                }, max = today)
            }
        }
        if (fulfillment == OrderFulfillment.Production) {
            item("production") {
                val title = stringResource(R.string.order_form_productionDueAtTitle)
                FormSection(title) {
                    FormDateRow(null, title, production, { productionDay = it.toEpochDay() }, min = ordered, max = shipping)
                }
            }
        }
        item("shipping") {
            val title = stringResource(R.string.order_form_shippingDueAtTitle)
            FormSection(title) {
                FormDateRow(null, title, shipping, {
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
            }
        }
        item("fee") {
            FormSection(stringResource(R.string.order_form_channelFee)) {
                FormPercentField(feeText, { feeText = it }, stringResource(R.string.order_form_channelFee))
            }
        }
        item("summary") {
            FormSection(stringResource(R.string.order_form_section_summary)) {
                FormValueRow(stringResource(R.string.order_form_total), CurrencyFormatter.string(subtotal))
                FormDivider()
                FormValueRow(stringResource(R.string.order_form_fee), CurrencyFormatter.string(channelFee))
                FormDivider()
                FormValueRow(stringResource(R.string.order_form_netProfit), CurrencyFormatter.string(gross - channelFee), bold = true)
            }
        }
        item("notes") {
            FormSection(stringResource(R.string.order_form_section_notes)) {
                FormTextField(
                    note,
                    { note = it },
                    stringResource(R.string.order_form_optional),
                    label = stringResource(R.string.order_form_section_notes),
                    singleLine = false,
                )
            }
        }
    }
    FormErrorAlert(viewModel, R.string.order_form_error_title)
}
