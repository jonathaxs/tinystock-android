package com.jonathaxs.tinystock.ui.calendar

import androidx.compose.foundation.isSystemInDarkTheme
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.jonathaxs.tinystock.core.model.OrderCalendarEventKind
import com.jonathaxs.tinystock.core.service.OrderCalendarExportPlanner
import com.jonathaxs.tinystock.ui.components.CircleIcon
import com.jonathaxs.tinystock.ui.icons.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.ui.components.AppCircleButton
import com.jonathaxs.tinystock.ui.components.AppGroupedCard
import com.jonathaxs.tinystock.ui.components.AppSecondaryButton
import com.jonathaxs.tinystock.ui.components.AppSectionLabel
import com.jonathaxs.tinystock.ui.components.AppStyle
import com.jonathaxs.tinystock.ui.components.isLargeFont
import com.jonathaxs.tinystock.ui.theme.color
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId

/**
 * Detalhe do pedido com as operacoes validas. Porte do `SalesOrderDetailView`; editar o pedido e
 * exportar para a agenda chegam com os formularios (C8).
 */
@Composable
internal fun OrderDetail(
    order: SalesOrder,
    zone: ZoneId,
    storeName: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAdvance: (OrderQuickAction) -> Unit,
    onCancel: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val action = OrderPresentation.quickAction(order, dark)
    val context = LocalContext.current
    val drafts = OrderCalendarExportPlanner.drafts(order, storeName, zone)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.welcome_button_back),
                onClick = onBack,
                size = 44.dp,
            )
            Text(
                stringResource(R.string.order_detail_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // Despachado, concluido ou cancelado nao edita mais.
            if (OrderPresentation.canEdit(order)) {
                AppCircleButton(
                    icon = AppIcons.Pencil,
                    contentDescription = stringResource(R.string.common_edit),
                    onClick = onEdit,
                    size = 44.dp,
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = AppStyle.HorizontalPadding, end = AppStyle.HorizontalPadding, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item("status") {
                val unknown = stringResource(R.string.order_queue_unknown)
                val symbol = statusSymbol(order.status, dark)
                AppGroupedCard(
                    rows = listOf(
                        {
                            DetailRow(
                                stringResource(R.string.order_detail_status),
                                order.status?.let { stringResource(it.nameRes) } ?: unknown,
                                leading = { CircleIcon(symbol.icon, symbol.color) },
                                bold = true,
                            )
                        },
                        {
                            DetailRow(
                                stringResource(R.string.order_form_fulfillment),
                                order.fulfillment?.let { stringResource(it.nameRes) } ?: unknown,
                            )
                        },
                    ),
                    dividerInset = 16.dp,
                )
            }
            item("items") { ItemsCard(order) }
            item("customer") {
                DetailCard(
                    stringResource(R.string.order_form_section_customer),
                    buildList {
                        if (order.buyerName.isNotEmpty()) add(stringResource(R.string.order_detail_buyer) to order.buyerName)
                        if (order.externalReference.isNotEmpty()) add(stringResource(R.string.order_detail_reference) to order.externalReference)
                        add(stringResource(R.string.order_form_channel) to order.channelName())
                    },
                )
            }
            item("dates") {
                fun day(date: Instant) = OrderPresentation.shortDate(date, zone)
                fun time(date: Instant) = OrderPresentation.dateTime(date, zone)
                DetailCard(
                    stringResource(R.string.order_form_section_dates),
                    buildList {
                        add(stringResource(R.string.order_form_orderedAt) to day(order.orderedAt))
                        order.productionDueAt?.let { add(stringResource(R.string.order_form_productionDueAt) to day(it)) }
                        order.shippingDueAt?.let { add(stringResource(R.string.order_form_shippingDueAt) to day(it)) }
                        order.productionStartedAt?.let { add(stringResource(R.string.order_detail_productionStartedAt) to time(it)) }
                        order.producedAt?.let { add(stringResource(R.string.order_detail_producedAt) to time(it)) }
                        order.shippedAt?.let { add(stringResource(R.string.order_detail_shippedAt) to time(it)) }
                        order.completedAt?.let { add(stringResource(R.string.order_detail_completedAt) to time(it)) }
                        order.cancelledAt?.let { add(stringResource(R.string.order_detail_cancelledAt) to time(it)) }
                    },
                )
            }
            item("summary") {
                DetailCard(
                    stringResource(R.string.order_form_section_summary),
                    listOf(
                        stringResource(R.string.order_form_total) to order.total.currencyText(),
                        stringResource(R.string.order_form_fee) to order.channelFeeAmount.currencyText(),
                        stringResource(R.string.order_detail_grossProfit) to order.grossProfit.currencyText(),
                        stringResource(R.string.order_form_netProfit) to order.netProfit.currencyText(),
                    ),
                )
            }
            if (order.trackingCode.isNotEmpty() || order.note.isNotEmpty() || order.cancellationReason.isNotEmpty()) {
                item("additional") {
                    DetailCard(
                        stringResource(R.string.order_detail_additional),
                        buildList {
                            if (order.trackingCode.isNotEmpty()) add(stringResource(R.string.order_edit_tracking) to order.trackingCode)
                            if (order.note.isNotEmpty()) add(order.note to "")
                            if (order.cancellationReason.isNotEmpty()) {
                                add(stringResource(R.string.order_detail_cancellationReason) to order.cancellationReason)
                            }
                        },
                    )
                }
            }
            if (drafts.isNotEmpty()) {
                item("calendarExport") {
                    val hint = stringResource(R.string.calendar_export_open_hint)
                    val red = AppAccentColor.Red.color(dark)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSectionLabel(stringResource(R.string.calendar_export_section), color = MaterialTheme.colorScheme.primary)
                        AppGroupedCard(
                            rows = drafts.map { draft ->
                                {
                                    ActionRow(
                                        icon = if (draft.kind == OrderCalendarEventKind.Production) AppIcons.Hammer else AppIcons.Box,
                                        iconColor = red,
                                        title = stringResource(
                                            if (draft.kind == OrderCalendarEventKind.Production) R.string.calendar_export_production_action
                                            else R.string.calendar_export_shipping_action,
                                        ),
                                        textColor = MaterialTheme.colorScheme.primary,
                                        value = dayMonth(draft.startDate, zone),
                                        hint = hint,
                                        onClick = { CalendarExport.open(context, draft, zone) },
                                    )
                                }
                            },
                            dividerInset = 60.dp,
                        )
                    }
                }
            }
            if (action != null) {
                item("advance") {
                    AppGroupedCard(
                        rows = listOf({
                            ActionRow(action.icon, action.color, stringResource(action.titleRes), action.color, onClick = { onAdvance(action) })
                        }),
                    )
                }
            }
            if (OrderPresentation.canCancel(order)) {
                item("cancel") {
                    val red = AppAccentColor.Red.color(dark)
                    AppGroupedCard(
                        rows = listOf({
                            ActionRow(AppIcons.CancelCircle, red, stringResource(R.string.order_action_cancel), red, onClick = onCancel)
                        }),
                    )
                }
            }
            item("bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

private val OrderFulfillment.nameRes: Int
    get() = when (this) {
        OrderFulfillment.ReadyStock -> R.string.order_fulfillment_readyStock
        OrderFulfillment.Production -> R.string.order_fulfillment_production
    }

/** Card de linhas "rotulo ... valor". Valor vazio mostra so o texto (observacao). */
@Composable
private fun DetailCard(title: String?, rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let { AppSectionLabel(it, color = MaterialTheme.colorScheme.primary) }
        AppGroupedCard(
            rows = rows.map { (label, value) -> @Composable { DetailRow(label, value) } },
            dividerInset = 16.dp,
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    leading: (@Composable () -> Unit)? = null,
    bold: Boolean = false,
) {
    val modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }.padding(horizontal = 16.dp, vertical = 12.dp)
    val weight = if (bold) FontWeight.SemiBold else null
    when {
        value.isEmpty() -> Box(modifier) { Text(label) }
        isLargeFont() -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            leading?.invoke()
            Text(label)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = weight)
        }
        else -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(label)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

/** Linha de acao: icone no circulo e o texto na cor da acao, com um valor opcional a direita. */
@Composable
private fun ActionRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    textColor: Color,
    value: String? = null,
    hint: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = hint, onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircleIcon(icon, iconColor)
        Text(title, color = textColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        value?.let { Text(it, color = textColor.copy(alpha = 0.7f)) }
    }
}

@Composable
private fun ItemsCard(order: SalesOrder) {
    val quantityLabel = stringResource(R.string.order_form_quantity)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSectionLabel(stringResource(R.string.order_detail_items), color = MaterialTheme.colorScheme.primary)
        AppGroupedCard(
            rows = order.itemList.map { item ->
                @Composable {
                    val large = isLargeFont()
                    val modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }.padding(horizontal = 16.dp, vertical = 12.dp)
                    val identification = @Composable {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.productName, fontWeight = FontWeight.Medium)
                            if (item.variantName.isNotEmpty()) {
                                Text(item.variantName, style = subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    val values = @Composable {
                        Column(horizontalAlignment = if (large) Alignment.Start else Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.subtotal.currencyText())
                            Text(
                                "$quantityLabel: ${NumberFormat.getIntegerInstance().format(item.quantity)}",
                                style = caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (large) {
                        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) { identification(); values() }
                    } else {
                        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f)) { identification() }
                            values()
                        }
                    }
                }
            },
            dividerInset = 16.dp,
        )
    }
}

/** Dia e mes sem o ano ("21 de set."), como o `.day().month(.abbreviated)` do iOS. */
private fun dayMonth(date: java.time.Instant, zone: ZoneId): String {
    val locale = java.util.Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM")
    return date.atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
}
