package io.github.rakasans.foodapp.domain

/** Co zrobić z jednym rekordem przy synchronizacji. */
enum class SyncAction { NONE, PUSH, PULL }

/**
 * Reguły synchronizacji telefon ↔ chmura (porównanie trójstronne).
 *
 * Dla każdego rekordu pamiętamy „bazę” — skrót treści i czas serwera z ostatniej udanej synchronizacji.
 * - zmienił się tylko telefon → wysyłamy,
 * - zmieniła się tylko chmura → pobieramy,
 * - zmieniły się obie strony → wygrywa nowsza zmiana (updatedAt z urządzenia); przy remisie chmura.
 * Usunięcie to zwykła zmiana (deletedAt) — nie znika bez śladu i synchronizuje się tak samo.
 */
object SyncRules {

    /**
     * @param localHash skrót treści na telefonie; null = rekordu nie ma na telefonie
     * @param baseHash skrót z ostatniej synchronizacji; null = nigdy nie synchronizowany
     * @param remoteServerAt czas zapisu w chmurze (ISO); null = rekordu nie ma w chmurze
     * @param baseServerAt czas zapisu w chmurze z ostatniej synchronizacji
     */
    fun decide(
        localHash: String?,
        baseHash: String?,
        remoteServerAt: String?,
        baseServerAt: String?,
        localUpdatedAt: Long?,
        remoteUpdatedAt: Long?,
    ): SyncAction {
        if (localHash == null && remoteServerAt == null) return SyncAction.NONE
        if (localHash == null) return SyncAction.PULL
        if (remoteServerAt == null) return SyncAction.PUSH
        val localChanged = localHash != baseHash
        val remoteChanged = baseServerAt == null || isAfter(remoteServerAt, baseServerAt)
        return when {
            !localChanged && !remoteChanged -> SyncAction.NONE
            localChanged && !remoteChanged -> SyncAction.PUSH
            !localChanged && remoteChanged -> SyncAction.PULL
            // konflikt: obie strony zmienione — nowsza zmiana wygrywa, remis → chmura
            (localUpdatedAt ?: 0) > (remoteUpdatedAt ?: 0) -> SyncAction.PUSH
            else -> SyncAction.PULL
        }
    }

    /** Porównanie czasów serwera w formacie ISO 8601 (różne strefy i liczby cyfr ułamka). */
    fun isAfter(a: String, b: String): Boolean = toMicros(a) > toMicros(b)

    fun toMicros(iso: String): Long {
        val m = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}(?::?\d{2})?)?$""").matchEntire(iso.trim())
            ?: throw IllegalArgumentException("Nieznany format czasu: $iso")
        val (y, mo, d, h, mi, s) = m.destructured
        val frac = m.groupValues[7].padEnd(6, '0').take(6).toLong()
        val zone = m.groupValues[8]
        val offsetMin = when {
            zone.isEmpty() || zone == "Z" -> 0
            else -> {
                val sign = if (zone[0] == '-') -1 else 1
                val digits = zone.drop(1).replace(":", "")
                sign * (digits.take(2).toInt() * 60 + digits.drop(2).ifEmpty { "0" }.toInt())
            }
        }
        val days = daysFromCivil(y.toInt(), mo.toInt(), d.toInt())
        val seconds = days * 86400L + h.toInt() * 3600L + mi.toInt() * 60L + s.toInt() - offsetMin * 60L
        return seconds * 1_000_000L + frac
    }

    /** Dni od 1970-01-01 (algorytm H. Hinnanta) — bez zależności od java.time w testach. */
    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    /** Skrót treści rekordu (stabilny: te same pola w tej samej kolejności → ten sam skrót). */
    fun hash(parts: List<Any?>): String {
        val text = parts.joinToString("\u001F") { it?.toString() ?: "\u0000" }
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
