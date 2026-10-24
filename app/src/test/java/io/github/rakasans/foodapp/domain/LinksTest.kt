package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test
    fun `wyciaga link z tekstu udostepniania`() {
        assertEquals(
            "https://www.instagram.com/reel/ABC123/?igsh=xyz",
            extractUrl("Zobacz tę rolkę https://www.instagram.com/reel/ABC123/?igsh=xyz"),
        )
        assertEquals("https://example.com/przepis", extractUrl("(https://example.com/przepis)."))
        assertEquals(null, extractUrl("bez linku"))
    }

    @Test
    fun `rozpoznaje instagram`() {
        assertTrue(isInstagramUrl("https://www.instagram.com/reel/ABC/"))
        assertTrue(isInstagramUrl("https://instagram.com/p/XYZ"))
        assertFalse(isInstagramUrl("https://example.com/instagram.com/"))
    }
}
