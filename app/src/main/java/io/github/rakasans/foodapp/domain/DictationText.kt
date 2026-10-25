package io.github.rakasans.foodapp.domain

/**
 * Porządkuje fragment podyktowanego tekstu, zanim trafi do parsera przepisu:
 * - „nowa linia” / „następna linia” / „enter” dzieli tekst na linie (parser pracuje na liniach),
 * - liczebnik słowny na początku linii zamieniamy na cyfrę („dwie łyżki oliwy” → „2 łyżki oliwy”),
 *   bo tak rozpoznawanie mowy często zapisuje małe liczby,
 * - pierwsza litera linii wielka.
 * Nie zmieniamy treści poza tym — transkrypcję można poprawić przed utworzeniem szkicu.
 */
object DictationText {

    private val NEW_LINE = Regex("""[,.]?\s*\b(nowa linia|następna linia|nowa linijka|enter)\b[,.]?\s*""", RegexOption.IGNORE_CASE)

    private val NUMBER_WORDS = mapOf(
        "jeden" to "1", "jedna" to "1", "jedno" to "1", "jedną" to "1",
        "dwa" to "2", "dwie" to "2", "trzy" to "3", "cztery" to "4", "pięć" to "5",
        "sześć" to "6", "siedem" to "7", "osiem" to "8", "dziewięć" to "9", "dziesięć" to "10",
    )

    fun normalize(chunk: String): List<String> =
        chunk.replace(NEW_LINE, "\n").lines().map { it.trim() }.filter { it.isNotEmpty() }.map(::line)

    private fun line(raw: String): String {
        val first = raw.substringBefore(' ')
        val digits = NUMBER_WORDS[first.lowercase()]
        val text = if (digits != null && raw.contains(' ')) digits + " " + raw.substringAfter(' ') else raw
        return text.replaceFirstChar { it.uppercase() }
    }

    /** Dokleja podyktowany fragment do dotychczasowego tekstu — każdy fragment od nowej linii. */
    fun append(current: String, chunk: String): String {
        val lines = normalize(chunk)
        if (lines.isEmpty()) return current
        val base = current.trimEnd()
        return (if (base.isEmpty()) "" else base + "\n") + lines.joinToString("\n")
    }
}
