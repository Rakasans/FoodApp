package io.github.rakasans.foodapp.core

import java.util.UUID

/** Identyfikatory generowane na urządzeniu — bez kolizji przy przyszłej synchronizacji. */
object Ids {
    fun new(): String = UUID.randomUUID().toString()
}

interface Clock {
    fun now(): Long
}

object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
