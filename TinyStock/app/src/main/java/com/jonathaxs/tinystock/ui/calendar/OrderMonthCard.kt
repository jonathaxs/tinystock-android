package com.jonathaxs.tinystock.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonathaxs.tinystock.R
import com.jonathaxs.tinystock.core.service.SalesOrderSchedule
import com.jonathaxs.tinystock.ui.components.AppCircleButton
import com.jonathaxs.tinystock.ui.components.AppSecondaryButton
import com.jonathaxs.tinystock.ui.components.appCard
import com.jonathaxs.tinystock.ui.components.isLargeFont
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** Data por extenso, como o `weekday(.wide).day().month(.wide).year()` do iOS. */
internal fun LocalDate.fullText(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

/** Mes do dia escolhido com a contagem de pedidos de cada dia. Porte do `SalesOrderCalendarView`. */
@Composable
internal fun OrderMonthCard(
    selectedDate: LocalDate,
    countsByDay: Map<Instant, Int>,
    zone: ZoneId,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Locale observavel: a grade se refaz se o idioma do aparelho mudar.
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val days = SalesOrderSchedule.monthDays(selectedDate.atStartOfDay(zone).toInstant(), zone, firstDay)
        .map { it.atZone(zone).toLocalDate() }
    val today = LocalDate.now(zone)
    val accent = MaterialTheme.colorScheme.primary

    Box(modifier.fillMaxWidth().appCard().padding(12.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MonthButton(-1, selectedDate, onSelect)
                Text(
                    selectedDate.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                        .replaceFirstChar { it.titlecase(locale) },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                MonthButton(1, selectedDate, onSelect)
            }

            // Limita so o texto da grade para manter as sete colunas legiveis em fonte grande.
            val density = LocalDensity.current
            val large = isLargeFont()
            CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, 1.35f))) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                        for (offset in 0L until 7L) {
                            Text(
                                firstDay.plus(offset).getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).heightIn(min = 24.dp),
                            )
                        }
                    }
                    for (week in days.chunked(7)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (day in week) {
                                DayCell(
                                    day = day,
                                    selected = day == selectedDate,
                                    isToday = day == today,
                                    inMonth = day.month == selectedDate.month,
                                    count = countsByDay[day.atStartOfDay(zone).toInstant()] ?: 0,
                                    accent = accent,
                                    height = if (large) 68.dp else 52.dp,
                                    onClick = { onSelect(day) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            AppSecondaryButton(onClick = { onSelect(LocalDate.now(zone)) }, large = false, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.order_calendar_today), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MonthButton(offset: Long, selectedDate: LocalDate, onSelect: (LocalDate) -> Unit) {
    AppCircleButton(
        icon = if (offset < 0) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = stringResource(if (offset < 0) R.string.order_calendar_previousMonth else R.string.order_calendar_nextMonth),
        // Mover o mes tambem seleciona o primeiro dia, para nao listar pedidos de outro mes.
        onClick = { onSelect(selectedDate.withDayOfMonth(1).plusMonths(offset)) },
        size = 44.dp,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    )
}

@Composable
private fun DayCell(
    day: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    inMonth: Boolean,
    count: Int,
    accent: Color,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val textColor = when {
        selected -> onAccent
        inMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val todayText = stringResource(R.string.order_calendar_today)
    val label = if (isToday) "${day.fullText()}, $todayText" else day.fullText()
    val countText = NumberFormat.getIntegerInstance().format(count)
    val value = stringResource(R.string.order_calendar_dayCount, countText)
    Column(
        modifier
            .height(height)
            .clip(shape)
            .background(if (selected) accent else Color.Transparent)
            .then(if (isToday && !selected) Modifier.border(1.5.dp, accent, shape) else Modifier)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = value
                this.selected = selected
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(
            day.dayOfMonth.toString(),
            fontSize = 16.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
        )
        Text(
            when {
                count == 0 -> " "
                count > 99 -> "99+"
                else -> countText
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) onAccent else accent,
            maxLines = 1,
        )
    }
}
