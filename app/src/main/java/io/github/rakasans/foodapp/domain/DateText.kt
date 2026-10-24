package io.github.rakasans.foodapp.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("d.MM.yyyy")

/** „dziś, 19:15” / „wczoraj, 08:02” / „12.10.2026” — do kart w bibliotece. */
fun formatUpdated(epochMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return when (at.toLocalDate()) {
        today -> "dziś, ${at.format(TIME)}"
        today.minusDays(1) -> "wczoraj, ${at.format(TIME)}"
        else -> at.format(DATE)
    }
}
