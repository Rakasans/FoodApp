package io.github.rakasans.foodapp.domain

import java.text.Normalizer

/**
 * Poprawki typowych błędów OCR w polskich przepisach (decyzja z 24.10, wariant A):
 * rozpoznawanie na telefonie często gubi ogonki („maka” zamiast „mąka”) albo myli „ł” z „l”/„t”.
 *
 * Zasada: poprawiamy tylko słowa ze słownika kulinarnego, i tylko gdy forma bez ogonków
 * nie jest sama poprawnym polskim słowem (np. „cebule” zostaje — to liczba mnoga).
 * Użytkownik i tak widzi rozpoznany tekst i może go poprawić przed utworzeniem szkicu.
 */
object OcrCorrector {

    /** Słowa z polskimi znakami, które często pojawiają się w przepisach. */
    private val DICTIONARY = """
        mąka mąki mąkę masło masła masłem łyżka łyżki łyżek łyżkę łyżkach łyżeczka łyżeczki łyżeczek łyżeczkę
        sól soli śmietana śmietany śmietanę śmietanka śmietanki śmietankę żółtko żółtka żółtek
        ząbek ząbki ząbków gałązka gałązki gałązek gałka gałki mięso mięsa mięsem wołowina wołowiny wołowinę
        pierś piersi drożdże drożdży szklanka szklanki szklankę szklanek szczyptę garść garści trochę około
        ryż ryżu twaróg jabłko jabłka jabłek bułka bułki bułkę bułek żelatyna żelatyny żelatynę łosoś łososia
        sałata sałaty liść liście liści ziół zioła ziemniaków ciepłej ciepłą ciepłe ciepła gorącej gorącą gorące
        wodę zimnej godzinę godziny piekarnik smażyć smażymy smażenia smażymy podsmaż podsmażamy
        posól posolić wymieszaj wymieszać dodaj gotuj gotować włóż włożyć wlej wlać ubij ubić utrzyj pokrój pokroić
        posiekaj posiekać zagotuj zagotować odstaw ostudź ostudzić przełóż przełożyć połącz połączyć dopraw doprawić
        składniki przygotowanie sposób wykonanie porcje porcji minut minuty średnio średniej dużą duża dużej
        cukru cukier ćwiartka ćwierć jaśmin kasztany śliwki śliwek orzechów migdałów pieczarki pieczarek
        świeży świeże świeżo świeżej zielona ząbków ostrożnie równo kawałek kawałki kawałków plasterki plasterków
        rozgrzanym rozgrzanej rozgrzej oliwę łagodnie przyprawę przyprawy
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }

    /** Formy bez ogonków, które same są poprawnymi słowami — nie zamieniamy ich. */
    private val VALID_PLAIN = setOf(
        "cebule", "kasze", "cukru", "cukier", "duza", "porcje", "minut", "minuty",
        "woda", "oliwa", "cebula", "sola", "zimna", "mala", "cieplej", "przyprawa",
    )

    /** Typowe pomyłki „ł” → „l”/„t” w słowach jednostek. */
    private val EXPLICIT = mapOf(
        "tyzki" to "łyżki", "tyżki" to "łyżki", "lyżki" to "łyżki", "łyzki" to "łyżki",
        "tyzka" to "łyżka", "tyżka" to "łyżka", "lyżka" to "łyżka", "łyzka" to "łyżka",
        "tyzeczki" to "łyżeczki", "lyżeczki" to "łyżeczki", "łyzeczki" to "łyżeczki",
        "tyzeczka" to "łyżeczka", "lyżeczka" to "łyżeczka", "łyzeczka" to "łyżeczka",
    )

    private val CORRECTIONS: Map<String, String> = buildMap {
        // Forma bez ogonków pasująca do kilku słów (np. „ciepła” / „ciepłą”) jest niejednoznaczna — pomijamy.
        DICTIONARY.groupBy { stripDiacritics(it) }
            .filter { (plain, words) -> words.distinct().size == 1 && plain != words.first() && plain !in VALID_PLAIN }
            .forEach { (plain, words) -> put(plain, words.first()) }
        putAll(EXPLICIT)
    }

    private val WORD = Regex("""\p{L}+""")

    fun correct(text: String): String = WORD.replace(text) { m ->
        val token = m.value
        val fixed = CORRECTIONS[token.lowercase()] ?: return@replace token
        when {
            token.all { it.isUpperCase() } && token.length > 1 -> fixed.uppercase()
            token.first().isUpperCase() -> fixed.replaceFirstChar { it.uppercase() }
            else -> fixed
        }
    }

    fun stripDiacritics(s: String): String =
        Normalizer.normalize(s.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
