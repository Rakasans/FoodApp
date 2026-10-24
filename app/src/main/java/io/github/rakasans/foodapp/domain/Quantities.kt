package io.github.rakasans.foodapp.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/** Formatowanie ilości po polsku: przecinek dziesiętny, maks. 2 miejsca, bez zbędnych zer. */
fun formatQuantity(value: Double): String {
    val rounded = (value * 100).roundToLong() / 100.0
    if (abs(rounded - rounded.roundToLong()) < 1e-9) return rounded.roundToLong().toString()
    return rounded.toString().trimEnd('0').trimEnd('.').replace('.', ',')
}

/** Normalizacja nazwy do łączenia pozycji: tylko identyczne nazwy (decyzja: „mleko” ≠ „mleko kokosowe”). */
fun normalizeName(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

/**
 * Współczynnik przeliczenia przepisu.
 * - porcje znane → target / servings,
 * - porcje nieznane → tylko mnożnik całego przepisu (bez pozornej precyzji).
 */
sealed interface Scaling {
    val factor: Double
    val label: String

    data class ByServings(val original: Int, val target: Int) : Scaling {
        init { require(original > 0 && target > 0) }
        override val factor: Double get() = target.toDouble() / original
        override val label: String get() = "$target porc. zamiast $original"
    }

    data class ByMultiplier(val multiplier: Double) : Scaling {
        init { require(multiplier > 0) }
        override val factor: Double get() = multiplier
        override val label: String get() = "×${formatQuantity(multiplier)} przepisu"
    }
}
