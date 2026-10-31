package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRulesTest {

    private val t1 = "2026-10-31T19:30:00.123456+00:00"
    private val t2 = "2026-10-31T19:35:00+00:00"

    @Test
    fun `nowy rekord na telefonie idzie do chmury`() {
        assertEquals(SyncAction.PUSH, SyncRules.decide("h1", null, null, null, 1, null))
    }

    @Test
    fun `rekord tylko w chmurze jest pobierany`() {
        assertEquals(SyncAction.PULL, SyncRules.decide(null, null, t1, null, null, 1))
    }

    @Test
    fun `zmiana tylko po jednej stronie`() {
        assertEquals(SyncAction.PUSH, SyncRules.decide("h2", "h1", t1, t1, 5, 1))
        assertEquals(SyncAction.PULL, SyncRules.decide("h1", "h1", t2, t1, 1, 5))
        assertEquals(SyncAction.NONE, SyncRules.decide("h1", "h1", t1, t1, 1, 1))
    }

    @Test
    fun `konflikt wygrywa nowsza zmiana a remis chmura`() {
        assertEquals(SyncAction.PUSH, SyncRules.decide("h2", "h1", t2, t1, 10, 5))
        assertEquals(SyncAction.PULL, SyncRules.decide("h2", "h1", t2, t1, 5, 10))
        assertEquals(SyncAction.PULL, SyncRules.decide("h2", "h1", t2, t1, 7, 7))
    }

    @Test
    fun `czasy serwera w roznych zapisach`() {
        assertTrue(SyncRules.isAfter("2026-10-31T19:30:00.5Z", "2026-10-31T19:30:00.123456+00:00"))
        assertTrue(SyncRules.isAfter("2026-10-31T21:30:01+02:00", "2026-10-31T19:30:00Z"))
        assertEquals(SyncRules.toMicros("2026-10-31T21:30:00+02:00"), SyncRules.toMicros("2026-10-31T19:30:00Z"))
        assertEquals(0L, SyncRules.toMicros("1970-01-01T00:00:00Z"))
    }

    @Test
    fun `skrot jest stabilny i odroznia null od pustego`() {
        assertEquals(SyncRules.hash(listOf("a", 1, null)), SyncRules.hash(listOf("a", 1, null)))
        assertTrue(SyncRules.hash(listOf("a", null)) != SyncRules.hash(listOf("a", "")))
    }
}
