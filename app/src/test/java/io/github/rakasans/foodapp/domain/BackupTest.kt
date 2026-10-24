package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val recipe = BackupRecipe(
        id = "r1",
        fields = mapOf("name" to "Zupa \"krem\"\nz dyni", "servings" to 4.0, "status" to "APPROVED", "notes" to null),
        ingredients = listOf(
            BackupIngredient("300-350 g ziemniaków", "ziemniaków", 300.0, 350.0, "g", false, false, null),
            BackupIngredient("sól", "sól", null, null, null, true, false, "Sos"),
        ),
        steps = listOf("Gotuj 20 minut."),
        nutrition = listOf("Energia" to "210 kcal"),
        categories = listOf("Zupy"), tags = listOf("wege"), collections = listOf("Święta"),
        photoFile = "abc.jpg",
    )

    @Test
    fun `kopia odtwarza sie bez strat`() {
        val back = Backup.fromJson(Backup.toJson(BackupFile(Backup.VERSION, 1_761_300_000_000L, listOf(recipe))))
        val r = back.recipes.single()
        assertEquals(1_761_300_000_000L, back.exportedAt)
        assertEquals("Zupa \"krem\"\nz dyni", r.fields["name"])
        assertEquals(4.0, r.fields["servings"])
        assertEquals(null, r.fields["notes"])
        assertEquals(recipe.ingredients, r.ingredients)
        assertEquals(recipe.steps, r.steps)
        assertEquals(recipe.nutrition, r.nutrition)
        assertEquals(listOf("Święta"), r.collections)
        assertEquals("abc.jpg", r.photoFile)
    }

    @Test
    fun `nieznana ilosc zostaje null a nie zero`() {
        val json = Backup.toJson(BackupFile(1, 0, listOf(recipe)))
        assertTrue(json.contains("\"quantity\": null"))
        assertEquals(null, Backup.fromJson(json).recipes.single().ingredients[1].quantity)
    }

    @Test
    fun `obcy plik jest odrzucany`() {
        val e = runCatching { Backup.fromJson("{\"app\":\"Inna\"}") }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }
}
