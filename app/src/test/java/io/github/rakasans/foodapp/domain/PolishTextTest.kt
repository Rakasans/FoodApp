package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PolishTextTest {
    @Test
    fun `odmiana przepisow`() {
        assertEquals("1 przepis", recipesCount(1))
        assertEquals("3 przepisy", recipesCount(3))
        assertEquals("5 przepisów", recipesCount(5))
        assertEquals("12 przepisów", recipesCount(12))
        assertEquals("22 przepisy", recipesCount(22))
    }
}
