package com.jonathaxs.tinystock.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.format.CurrencyFormatter
import com.jonathaxs.tinystock.core.model.AppAccentColor
import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.report.SalesOrderChannelSummary
import com.jonathaxs.tinystock.core.report.SalesOrderOperationalSummary
import com.jonathaxs.tinystock.core.report.SalesOrderProductRanking
import com.jonathaxs.tinystock.core.report.SalesOrderReportDay
import com.jonathaxs.tinystock.core.report.SalesOrderReportQueue
import com.jonathaxs.tinystock.core.report.SalesOrderReportSummary
import com.jonathaxs.tinystock.core.report.SalesOrderReportTotals
import com.jonathaxs.tinystock.ui.components.AppGroupedCard
import com.jonathaxs.tinystock.ui.components.AppSectionLabel
import com.jonathaxs.tinystock.ui.components.CircleIcon
import com.jonathaxs.tinystock.ui.components.appCard
import com.jonathaxs.tinystock.ui.icons.AppIcons
import com.jonathaxs.tinystock.ui.theme.color
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Recuo do divisor nas linhas com icone: margem + icone de 32 + espaco. */
private val IconRowInset = 60.dp

// region Cores e textos

/** Cores de sistema do iOS usadas nos relatorios. */
private object ReportColors {
    fun green(dark: Boolean) = AppAccentColor.Green.color(dark)
    fun red(dark: Boolean) = AppAccentColor.Red.color(dark)
    fun blue(dark: Boolean) = AppAccentColor.Blue.color(dark)
    fun orange(dark: Boolean) = AppAccentColor.Orange.color(dark)
    fun pink(dark: Boolean) = AppAccentColor.Pink.color(dark)
    fun teal(dark: Boolean) = Color(if (dark) 0xFF40C8E0 else 0xFF30B0C7)
    fun indigo(dark: Boolean) = Color(if (dark) 0xFF5E5CE6 else 0xFF5856D6)
}

private fun BigDecimal.currencyText() = CurrencyFormatter.string(this)

/** Quantidade sem casas decimais, no formato do aparelho. */
private fun BigDecimal.wholeText(): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(this)

private fun Int.countText(): String = NumberFormat.getIntegerInstance().format(this)

private fun BigDecimal.isOne() = compareTo(BigDecimal.ONE) == 0

@Composable
private fun ordersText(count: Int): String =
    stringResource(if (count == 1) R.string.reports_count_orders_one else R.string.reports_count_orders_other, count)

@Composable
private fun SalesOrderChannelSummary.displayName(): String = customDisplayName ?: stringResource(
    when (channel) {
        SalesChannel.Direct -> R.string.order_channel_direct
        SalesChannel.Shopee -> R.string.order_channel_shopee
        SalesChannel.MercadoLivre -> R.string.order_channel_mercadoLivre
        SalesChannel.Other -> R.string.order_channel_other
        null -> R.string.order_channel_unknown
    },
)

// endregion

// region Secoes

@Composable
private fun ReportSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSectionLabel(title, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun <T> GroupedRows(items: List<T>, dividerInset: Dp = 16.dp, row: @Composable (Int, T) -> Unit) {
    AppGroupedCard(
        rows = items.mapIndexed { index, item -> @Composable { row(index, item) } },
        dividerInset = dividerInset,
    )
}

@Composable
internal fun OperationSection(operations: SalesOrderOperationalSummary, onOpenCalendar: () -> Unit) {
    val dark = isSystemInDarkTheme()
    ReportSection(stringResource(R.string.reports_operation_title)) {
        AppGroupedCard(
            rows = listOf(
                { OperationRow(stringResource(R.string.reports_operation_toProduce), operations.toProduce, AppIcons.Hammer, ReportColors.orange(dark), onOpenCalendar) },
                { OperationRow(stringResource(R.string.reports_operation_readyToShip), operations.readyToShip, AppIcons.Box, ReportColors.blue(dark), onOpenCalendar) },
                { OperationRow(stringResource(R.string.reports_operation_overdue), operations.overdue, Icons.Filled.Warning, ReportColors.red(dark), onOpenCalendar) },
                { OperationRow(stringResource(R.string.reports_operation_shipped), operations.shipped, Icons.AutoMirrored.Filled.Send, ReportColors.green(dark), onOpenCalendar) },
            ),
            dividerInset = IconRowInset,
        )
    }
}

@Composable
internal fun ReceivedSection(totals: SalesOrderReportTotals) {
    val dark = isSystemInDarkTheme()
    val metrics = listOf(
        Metric(stringResource(R.string.reports_metric_revenue), totals.revenue.currencyText(), AppIcons.Money, ReportColors.green(dark)),
        Metric(
            stringResource(R.string.reports_metric_netProfit),
            totals.netProfit.currencyText(),
            AppIcons.TrendUp,
            if (totals.netProfit.signum() < 0) ReportColors.red(dark) else ReportColors.blue(dark),
        ),
        Metric(stringResource(R.string.reports_metric_orders), totals.orderCount.countText(), Icons.Filled.ShoppingCart, ReportColors.orange(dark)),
        Metric(stringResource(R.string.reports_metric_units), totals.unitCount.wholeText(), AppIcons.Box, ReportColors.teal(dark)),
        Metric(stringResource(R.string.reports_metric_cost), totals.cost.currencyText(), Icons.Filled.Build, ReportColors.indigo(dark)),
        Metric(stringResource(R.string.reports_metric_fees), totals.channelFees.currencyText(), AppIcons.Percent, ReportColors.pink(dark)),
    )
    val columns = if (isLargeFont()) 1 else 2
    ReportSection(stringResource(R.string.reports_received_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in metrics.chunked(columns)) {
                // Cards da mesma linha com a mesma altura.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (metric in row) MetricCard(metric, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
internal fun BestSellersSection(rankings: List<SalesOrderProductRanking>) {
    ReportSection(stringResource(R.string.reports_bestSellers_title)) {
        GroupedRows(rankings, dividerInset = 56.dp) { index, ranking -> BestSellingRow(index + 1, ranking) }
    }
}

@Composable
internal fun ChannelsSection(channels: List<SalesOrderChannelSummary>) {
    ReportSection(stringResource(R.string.reports_channels_title)) {
        GroupedRows(channels) { _, channel ->
            FinancialRow(
                title = channel.displayName(),
                subtitle = ordersText(channel.totals.orderCount),
                revenue = channel.totals.revenue,
                profitText = channel.totals.netProfit.currencyText(),
                profit = channel.totals.netProfit,
            )
        }
    }
}

@Composable
internal fun DailySection(groups: List<SalesOrderReportDay>, zone: ZoneId) {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    ReportSection(stringResource(R.string.reports_daily_title)) {
        GroupedRows(groups) { _, group ->
            FinancialRow(
                title = group.day.atZone(zone).toLocalDate().format(formatter),
                subtitle = ordersText(group.totals.orderCount),
                revenue = group.totals.revenue,
                profitText = stringResource(R.string.reports_daily_profit, group.totals.netProfit.currencyText()),
                profit = group.totals.netProfit,
            )
        }
    }
}

@Composable
internal fun ActivitySection(summary: SalesOrderReportSummary) {
    val dark = isSystemInDarkTheme()
    ReportSection(stringResource(R.string.reports_activity_title)) {
        AppGroupedCard(
            rows = listOf(
                { CountRow(stringResource(R.string.reports_activity_dispatched), summary.dispatched.orderCount, Icons.AutoMirrored.Filled.Send, ReportColors.blue(dark)) },
                { CountRow(stringResource(R.string.reports_activity_completed), summary.completed.orderCount, Icons.Filled.CheckCircle, ReportColors.green(dark)) },
                { CountRow(stringResource(R.string.reports_activity_cancelled), summary.cancelled.orderCount, AppIcons.CancelCircle, ReportColors.red(dark)) },
            ),
            dividerInset = IconRowInset,
        )
    }
}

// endregion

// region Linhas

private val headline @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
private val subheadline @Composable get() = MaterialTheme.typography.bodyMedium

/** Fila atual da loja; o toque abre o Calendario. Porte do `ReportOperationRowView`. */
@Composable
private fun OperationRow(title: String, queue: SalesOrderReportQueue, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val units = stringResource(
        if (queue.unitCount.isOne()) R.string.reports_count_units_one else R.string.reports_count_units,
        queue.unitCount.wholeText(),
    )
    val detail = "${ordersText(queue.orderCount)} · $units"
    val chevron = @Composable {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
    val modifier = Modifier
        .fillMaxWidth()
        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.reports_operation_openCalendar), onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp)
    if (isLargeFont()) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircleIcon(icon, tint)
                Text(title, style = headline, modifier = Modifier.weight(1f))
                chevron()
            }
            Text(detail, style = subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleIcon(icon, tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = headline)
                Text(detail, style = subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                queue.orderCount.countText(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clearAndSetSemantics { },
            )
            chevron()
        }
    }
}

/** Quantidade de eventos no periodo. Porte do `ReportCountRowView`. */
@Composable
private fun CountRow(title: String, count: Int, icon: ImageVector, tint: Color) {
    val modifier = Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) { }
        .padding(horizontal = 16.dp, vertical = 12.dp)
    val titleRow = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleIcon(icon, tint)
            Text(title, style = headline)
        }
    }
    if (isLargeFont()) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            titleRow()
            Text(count.countText(), style = headline)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { titleRow() }
            Text(count.countText(), style = headline)
        }
    }
}

private data class Metric(val title: String, val value: String, val icon: ImageVector, val tint: Color)

/** Card de metrica. Porte do `ReportMetricView`. */
@Composable
private fun MetricCard(metric: Metric, modifier: Modifier = Modifier) {
    Column(
        modifier
            .heightIn(min = 104.dp)
            .appCard()
            .semantics(mergeDescendants = true) { }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val title = @Composable {
            Text(metric.title, style = subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isLargeFont()) {
            CircleIcon(metric.icon, metric.tint)
            title()
        } else {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f).padding(end = 8.dp)) { title() }
                CircleIcon(metric.icon, metric.tint)
            }
        }
        Text(metric.value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** Produto no ranking, com a posicao num circulo na cor de destaque. Porte do `BestSellingRowView`. */
@Composable
private fun BestSellingRow(position: Int, ranking: SalesOrderProductRanking) {
    val accent = MaterialTheme.colorScheme.primary
    val units = stringResource(
        if (ranking.quantity.isOne()) R.string.reports_bestSellers_units_one else R.string.reports_bestSellers_units_other,
        ranking.quantity.wholeText(),
    )
    val info = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ranking.productName, style = headline)
            Text(units, style = subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val revenue = @Composable { RevenueText(ranking.revenue) }
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .widthIn(min = 28.dp)
                .heightIn(min = 28.dp)
                .background(accent.copy(alpha = 0.14f), CircleShape)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(position.countText(), style = subheadline, fontWeight = FontWeight.Bold, color = accent)
        }
        if (isLargeFont()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                info()
                revenue()
            }
        } else {
            Box(Modifier.weight(1f)) { info() }
            revenue()
        }
    }
}

@Composable
private fun RevenueText(value: BigDecimal, textAlign: TextAlign? = null) {
    Text(value.currencyText(), style = headline, textAlign = textAlign)
}

/** Linha com titulo, pedidos, total e lucro. Usada por canal e por dia. */
@Composable
private fun FinancialRow(title: String, subtitle: String, revenue: BigDecimal, profitText: String, profit: BigDecimal) {
    val profitColor = if (profit.signum() < 0) ReportColors.red(isSystemInDarkTheme()) else MaterialTheme.colorScheme.onSurfaceVariant
    val large = isLargeFont()
    val info = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = headline)
            Text(subtitle, style = subheadline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val financial = @Composable {
        Column(
            horizontalAlignment = if (large) Alignment.Start else Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RevenueText(revenue, textAlign = if (large) null else TextAlign.End)
            Text(profitText, style = subheadline, color = profitColor, textAlign = if (large) null else TextAlign.End)
        }
    }
    val modifier = Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) { }
        .padding(16.dp)
    if (large) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            info()
            financial()
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { info() }
            financial()
        }
    }
}

// endregion
