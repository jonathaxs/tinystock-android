package com.jonathaxs.tinystock.core.data

import com.jonathaxs.tinystock.core.model.OrderReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderReminderSettingsCodingTest {

    @Test
    fun roundTripKeepsEveryField() {
        val settings = OrderReminderSettings(
            isEnabled = true, productionEnabled = false, shippingEnabled = true, dayBeforeEnabled = false,
            dueDayEnabled = true, overdueEnabled = false, soundEnabled = false, hour = 18, minute = 45,
        )
        assertEquals(settings, AppPreferences.decodeReminderSettings(AppPreferences.encodeReminderSettings(settings)))
    }

    @Test
    fun readsTheIosJsonAndFillsMissingFields() {
        val decoded = AppPreferences.decodeReminderSettings("""{"isEnabled":true,"hour":7,"minute":30}""")
        assertEquals(OrderReminderSettings(isEnabled = true, hour = 7, minute = 30), decoded)
    }

    @Test(expected = Exception::class)
    fun brokenJsonThrows() {
        AppPreferences.decodeReminderSettings("{not json")
    }
}
