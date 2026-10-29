package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prawdziwa rolka (29.10.2026): kroki numerowane emoji-cyframi, części „Baza:” / „Tofu:”, opis ucięty przez Instagram. */
class InstagramRealTest {

    private fun page(name: String) =
        requireNotNull(javaClass.getResourceAsStream("/web/$name")).bufferedReader(Charsets.UTF_8).readText()

    @Test
    fun `rolka ramen - kroki nie trafiaja do skladnikow`() {
        val post = InstagramPage.parse(page("instagram_ramen_DdzH3rCIGf3.html"), "DdzH3rCIGf3")
        assertTrue(post.truncated)
        assertTrue(post.caption!!.endsWith("papryk…"))
        assertEquals("podwojny_apetyt", post.author)

        val r = RecipeTextParser.parse(post.caption!!)
        assertEquals("KREMOWY DYNIOWY SPICY RAMEN", r.name)
        assertEquals(8, r.servings)
        assertEquals(28, r.ingredients.size)
        assertTrue(r.ingredients.none { it.raw.contains("pokrój") || it.raw.contains("Dynię") })
        assertEquals("Baza", r.ingredients[0].group)
        assertEquals("Tofu", r.ingredients[15].group)
        assertEquals("Dodatki", r.ingredients.last().group)
        assertEquals(7, r.steps.size)
        assertTrue(r.steps[0].startsWith("Baza: Dynię pokrój"))
        assertTrue(r.steps[6].startsWith("Tofu: Tofu rozdrobnij"))
    }
}
