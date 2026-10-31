package io.github.rakasans.foodapp.domain

/** Składnik do wyszukiwania w treści kroków: [name] po rozpoznaniu, [raw] — zapis z przepisu. */
data class MentionIngredient(val id: String, val name: String, val raw: String)

/** Fragment kroku ([start], [end]) wskazujący składnik(i) z listy. */
data class Mention(val start: Int, val end: Int, val ingredientIds: List<String>)

/**
 * Znajduje w treści kroku nazwy składników z listy („Dodaj gochujang, miso, masło orzechowe…”),
 * żeby w trybie gotowania po kliknięciu pokazać ilość z listy składników.
 *
 * Polski odmienia nazwy (masła orzechowego → masło orzechowe), więc porównujemy początki słów. Gdy nie mamy pewności, nie zaznaczamy — brak podpowiedzi jest lepszy niż zła ilość.
 * Ilości nie dzielimy między kroki: pokazujemy zapis z listy składników (łącznie na przepis).
 */
object IngredientMentions {

    private val WORD = Regex("""\p{L}+""")

    /** Przymiotniki i słowa opisowe na początku nazwy — nie nadają się na słowo kluczowe. */
    private val MODIFIERS = setOf(
        "duży", "duża", "duże", "dużych", "dużej", "mały", "mała", "małe", "małych", "małej",
        "średni", "średnia", "średnie", "średnich", "średniej", "świeży", "świeża", "świeże", "świeżo", "świeżej", "świeżego",
        "drobny", "drobna", "drobne", "drobnej", "posiekany", "posiekana", "posiekane", "posiekanej", "posiekanego",
        "starty", "starta", "starte", "startego", "startej", "mielony", "mielona", "mielone", "mielonego", "mielonej",
        "suszony", "suszona", "suszone", "suszonego", "suszonej", "ciemny", "ciemnego", "ciemnej", "jasny", "jasnej", "jasnego",
        "delikatny", "delikatnego", "delikatnej", "ugotowany", "ugotowanego", "pokrojony", "pokrojonego", "dobrej", "dobry",
        "sok", "soku", "skórka", "skórki", "połowy", "połówki", "ćwiartki", "opakowanie", "opakowania", "puszka", "puszki", "kostka", "kostki", "plaster", "plastry",
    )

    /**
     * Czy słowo z kroku to (odmieniona) forma słowa z nazwy składnika: wspólny początek obejmuje
     * wszystko poza najwyżej dwiema ostatnimi literami krótszego słowa (sosu/sosem, czosnku/czosnek, masła/masło).
     */
    fun sameWord(token: String, word: String): Boolean {
        val a = token.lowercase()
        val b = word.lowercase()
        val common = a.zip(b).takeWhile { (x, y) -> x == y }.size
        return common >= maxOf(3, minOf(a.length, b.length) - 2)
    }

    /** Słowa kluczowe nazwy: do pierwszego dopisku (myślnik, przecinek, nawias), bez przymiotników na początku. */
    fun keywords(name: String): List<String> {
        val core = name.split(Regex("""\s[-–—]\s|[,(;]""")).first()
        val words = WORD.findAll(core).map { it.value.lowercase() }.filter { it.length >= 3 }.toList()
        val head = words.dropWhile { it in MODIFIERS }
        return (head.ifEmpty { words }).take(3)
    }

    fun find(step: String, ingredients: List<MentionIngredient>): List<Mention> {
        val tokens = WORD.findAll(step).toList()
        if (tokens.isEmpty()) return emptyList()
        val keys = ingredients.mapNotNull { ing -> keywords(ing.name.ifBlank { ing.raw }).takeIf { it.isNotEmpty() }?.let { ing to it } }

        val out = mutableListOf<Mention>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i].value.lowercase()
            // dla każdego składnika: ile kolejnych słów kroku pasuje do jego słów kluczowych
            var best = 0
            val ids = mutableListOf<String>()
            for ((ing, words) in keys) {
                if (token.length < 3 || !sameWord(token, words[0])) continue
                var len = 1
                while (len < words.size && i + len < tokens.size && sameWord(tokens[i + len].value, words[len])) len++
                when {
                    len > best -> { best = len; ids.clear(); ids += ing.id }
                    len == best -> ids += ing.id
                }
            }
            if (best > 0) {
                out += Mention(tokens[i].range.first, tokens[i + best - 1].range.last + 1, ids.distinct())
                i += best
            } else i++
        }
        return out
    }
}
