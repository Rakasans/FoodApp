package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DateTextTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    private val now = ms(2026, 10, 24, 19, 20)

    @Test
    fun `dzis wczoraj i data`() {
        assertEquals("dziś, 19:15", formatUpdated(ms(2026, 10, 24, 19, 15), now, zone))
        assertEquals("wczoraj, 08:02", formatUpdated(ms(2026, 10, 23, 8, 2), now, zone))
        assertEquals("12.10.2026", formatUpdated(ms(2026, 10, 12, 10, 0), now, zone))
    }
}
