package com.jonathaxs.tinystock.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppAccentColorTest {

    @Test
    fun rawValuesMatchIos() {
        assertEquals(
            listOf("cyan", "green", "blue", "purple", "red", "orange", "yellow", "pink"),
            AppAccentColor.entries.map { it.rawValue },
        )
    }

    @Test
    fun defaultIsCyan() {
        assertEquals(AppAccentColor.Cyan, AppAccentColor.Default)
        assertEquals("app.accentColor", AppAccentColor.STORAGE_KEY)
    }

    @Test
    fun unknownRawValueFallsBackToDefault() {
        assertEquals(AppAccentColor.Pink, AppAccentColor.fromRawValue("pink"))
        assertEquals(AppAccentColor.Default, AppAccentColor.fromRawValue("magenta"))
        assertEquals(AppAccentColor.Default, AppAccentColor.fromRawValue(null))
    }
}
