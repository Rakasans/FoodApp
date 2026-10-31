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

/** Drobne ustawienia aplikacji (nie dane przepisów — te są w Room). */
class AppPrefs(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)

    /** Ekran powitalny pokazany przy pierwszym uruchomieniu. */
    var welcomeSeen: Boolean
        get() = prefs.getBoolean("welcome_seen", false)
        set(value) { prefs.edit().putBoolean("welcome_seen", value).apply() }
}
