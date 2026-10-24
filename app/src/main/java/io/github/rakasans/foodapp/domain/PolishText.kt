package io.github.rakasans.foodapp.domain

/** Polska odmiana liczebników: 1 przepis, 2–4 przepisy, 5+ przepisów (z wyjątkiem 12–14). */
fun polishPlural(n: Int, one: String, few: String, many: String): String {
    if (n == 1) return one
    val last = n % 10
    val lastTwo = n % 100
    return if (last in 2..4 && lastTwo !in 12..14) few else many
}

fun recipesCount(n: Int): String = "$n ${polishPlural(n, "przepis", "przepisy", "przepisów")}"
