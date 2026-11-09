package com.jonathaxs.tinystock.core.service

import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Porte de `SalesOrderScheduleTests.swift`. */
class SalesOrderScheduleTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")

    private fun date(month: Int, day: Int, hour: Int = 12, year: Int = 2026): Instant =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant()

    @Test
    fun producaoApareceNosDoisPrazosSemDuplicarNoMesmoDia() {
        val order = SalesOrder(
            fulfillmentRawValue = OrderFulfillment.Production.rawValue,
            productionDueAt = date(9, 5),
            shippingDueAt = date(9, 7),
        )
        assertEquals(1, SalesOrderSchedule.orders(date(9, 5), listOf(order), zone).size)
        assertEquals(1, SalesOrderSchedule.orders(date(9, 7), listOf(order), zone).size)
        assertTrue(SalesOrderSchedule.orders(date(9, 6), listOf(order), zone).isEmpty())

        val sameDay = order.copy(shippingDueAt = date(9, 5, hour = 23))
        val counts = SalesOrderSchedule.countsByDay(listOf(sameDay), zone)
        assertEquals(mapOf(SalesOrderSchedule.startOfDay(date(9, 5), zone) to 1), counts)
    }

    @Test
    fun produzidoRemoveCompromissoDeProducaoEDespachadoUsaDataEfetiva() {
        val ready = SalesOrder(
            fulfillmentRawValue = OrderFulfillment.Production.rawValue,
            statusRawValue = SalesOrderStatus.ReadyToShip.rawValue,
            productionDueAt = date(9, 5),
            shippingDueAt = date(9, 7),
        )
        assertTrue(SalesOrderSchedule.orders(date(9, 5), listOf(ready), zone).isEmpty())
        assertEquals(
            listOf(SalesOrderScheduleEntry.Kind.Shipping),
            SalesOrderSchedule.entries(ready).map { it.kind },
        )

        val shipped = ready.copy(
            statusRawValue = SalesOrderStatus.Shipped.rawValue,
            shippedAt = date(9, 6),
        )
        assertEquals(date(9, 6), SalesOrderSchedule.entries(shipped).first().date)
        assertTrue(SalesOrderSchedule.orders(date(9, 7), listOf(shipped), zone).isEmpty())
    }

    @Test
    fun concluidosCanceladosEDesconhecidosContinuamConsultaveis() {
        val completed = SalesOrder(
            statusRawValue = SalesOrderStatus.Completed.rawValue,
            orderedAt = date(9, 1),
            completedAt = date(9, 7),
            updatedAt = date(9, 8),
        )
        assertEquals(date(9, 7), SalesOrderSchedule.entries(completed).first().date)

        val cancelled = completed.copy(
            statusRawValue = SalesOrderStatus.Cancelled.rawValue,
            cancelledAt = date(9, 4),
        )
        assertEquals(date(9, 4), SalesOrderSchedule.entries(cancelled).first().date)

        val cancelledWithoutDate = cancelled.copy(cancelledAt = null)
        assertEquals(date(9, 8), SalesOrderSchedule.entries(cancelledWithoutDate).first().date)

        val unknown = cancelledWithoutDate.copy(statusRawValue = "future-status")
        assertEquals(date(9, 1), SalesOrderSchedule.entries(unknown).first().date)
    }

    @Test
    fun atrasoComecaNoDiaSeguinteENaoContinuaAposDespacho() {
        val order = SalesOrder(shippingDueAt = date(9, 5, hour = 0))
        assertFalse(SalesOrderSchedule.isOverdue(order, now = date(9, 5, hour = 23), zone = zone))
        assertTrue(SalesOrderSchedule.isOverdue(order, now = date(9, 6, hour = 0), zone = zone))

        val shipped = order.copy(statusRawValue = SalesOrderStatus.Shipped.rawValue)
        assertFalse(SalesOrderSchedule.isOverdue(shipped, now = date(9, 6), zone = zone))

        val cancelled = order.copy(statusRawValue = SalesOrderStatus.Cancelled.rawValue)
        assertFalse(SalesOrderSchedule.isOverdue(cancelled, now = date(9, 6), zone = zone))
    }

    @Test
    fun atrasoDeProducaoPermaneceMesmoComDespachoFuturo() {
        val order = SalesOrder(
            fulfillmentRawValue = OrderFulfillment.Production.rawValue,
            productionDueAt = date(9, 4),
            shippingDueAt = date(9, 7),
        )
        assertTrue(SalesOrderSchedule.isOverdue(order, now = date(9, 5), zone = zone))

        val ready = order.copy(statusRawValue = SalesOrderStatus.ReadyToShip.rawValue)
        assertFalse(SalesOrderSchedule.isOverdue(ready, now = date(9, 5), zone = zone))
    }

    @Test
    fun contagemUsaDiaLocalENaoDiaUTC() {
        val order = SalesOrder(shippingDueAt = date(9, 5, hour = 23))
        val counts = SalesOrderSchedule.countsByDay(listOf(order), zone)
        assertEquals(1, counts[SalesOrderSchedule.startOfDay(date(9, 5), zone)])
        assertNull(counts[SalesOrderSchedule.startOfDay(date(9, 6), zone)])
    }

    @Test
    fun gradeIncluiBissextoESemanasCompletas() {
        val days = SalesOrderSchedule.monthDays(
            containing = date(2, 15, year = 2028),
            zone = zone,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        assertEquals(0, days.size % 7)
        assertEquals(DayOfWeek.MONDAY, days.first().atZone(zone).dayOfWeek)
        assertEquals(29, days.count { it.atZone(zone).monthValue == 2 })
        assertEquals(days.size, days.toSet().size)

        val sundayDays = SalesOrderSchedule.monthDays(
            containing = date(9, 5),
            zone = zone,
            firstDayOfWeek = DayOfWeek.SUNDAY,
        )
        assertEquals(DayOfWeek.SUNDAY, sundayDays.first().atZone(zone).dayOfWeek)
    }

    @Test
    fun gradeAtravessaHorarioDeVeraoSemPularDia() {
        val days = SalesOrderSchedule.monthDays(
            containing = date(3, 15),
            zone = zone,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        val march = days.filter { it.atZone(zone).monthValue == 3 }
        assertEquals((1..31).toList(), march.map { it.atZone(zone).dayOfMonth })
        assertTrue(march.all { it.atZone(zone).hour == 0 })
        assertTrue(
            march.any { day ->
                val next = day.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
                Duration.between(day, next).seconds == 23L * 60 * 60
            },
        )
    }
}
