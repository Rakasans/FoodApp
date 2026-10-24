package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRecipePageTest {

    private val wordpress = """
<html><head>
<meta property="og:image" content="https://example.pl/og.jpg">
<script type="application/ld+json">{"@context":"https://schema.org","@graph":[
 {"@type":"WebPage","name":"Blog"},
 {"@type":"Recipe","name":"Zupa krem z dyni &amp; imbirem",
  "author":{"@type":"Person","name":"Ania"},
  "image":["https://example.pl/zupa-1200.jpg","https://example.pl/zupa-600.jpg"],
  "recipeYield":["4","4 porcje"],
  "prepTime":"PT15M","cookTime":"PT1H5M",
  "recipeIngredient":["1 kg dyni","300-350 g ziemniaków","sól do smaku"],
  "recipeInstructions":[
    {"@type":"HowToSection","name":"Zupa","itemListElement":[
      {"@type":"HowToStep","text":"Dynię obierz i pokrój."},
      {"@type":"HowToStep","text":"Gotuj 20 minut."}]},
    {"@type":"HowToStep","text":"Zmiksuj na gładko."}],
  "nutrition":{"@type":"NutritionInformation","calories":"210 kcal","proteinContent":"4 g"}}
]}</script></head><body></body></html>
""".trimIndent()

    @Test
    fun `odczytuje przepis z grafu json-ld`() {
        val r = WebRecipePage.parse(wordpress)!!
        assertEquals("Zupa krem z dyni & imbirem", r.name)
        assertEquals(4, r.servings)
        assertNull(r.yieldText)
        assertEquals(listOf("1 kg dyni", "300-350 g ziemniaków", "sól do smaku"), r.ingredients)
        assertEquals(listOf("Dynię obierz i pokrój.", "Gotuj 20 minut.", "Zmiksuj na gładko."), r.steps)
        assertEquals("https://example.pl/zupa-1200.jpg", r.imageUrl)
        assertEquals("Ania", r.author)
        assertEquals("15 min", r.prepTime)
        assertEquals("1 godz. 5 min", r.cookTime)
        assertEquals(listOf("Energia" to "210 kcal", "Białko" to "4 g"), r.nutrition)
        assertEquals("1 porcja", r.nutritionBasis)
    }

    @Test
    fun `instrukcje jako jeden blok html i wydajnosc niebedaca porcjami`() {
        val html = """<script type="application/ld+json">
            {"@type":["Recipe"],"name":"Brownie","recipeYield":"1 blacha 20x30",
             "recipeIngredient":["200 g czekolady"],
             "recipeInstructions":"<p>Roztop czekoladę.</p><p>Piecz 25&nbsp;minut.</p>"}
        </script>"""
        val r = WebRecipePage.parse(html)!!
        assertNull(r.servings)
        assertEquals("1 blacha 20x30", r.yieldText)
        assertEquals(listOf("Roztop czekoladę.", "Piecz 25 minut."), r.steps)
        assertTrue(r.nutrition.isEmpty())
        assertNull(r.nutritionBasis)
    }

    @Test
    fun `brak danych strukturalnych to brak przepisu`() {
        assertNull(WebRecipePage.parse("<html><script type=\"application/ld+json\">{\"@type\":\"Article\"}</script></html>"))
        assertNull(WebRecipePage.parse("<html><body>Przepis na pierogi</body></html>"))
    }

    @Test
    fun `zepsuty json w jednym bloku nie blokuje drugiego`() {
        val html = """<script type="application/ld+json">{"@type": "Organization",</script>
            <script type="application/ld+json">[{"@type":"Recipe","name":"Placki","recipeIngredient":["2 jajka"]}]</script>"""
        assertEquals("Placki", WebRecipePage.parse(html)!!.name)
    }

    @Test
    fun `czas iso`() {
        assertEquals("45 min", WebRecipePage.duration("PT45M"))
        assertEquals("1 godz. 30 min", WebRecipePage.duration("PT90M"))
        assertEquals("2 godz.", WebRecipePage.duration("PT2H"))
        assertNull(WebRecipePage.duration("PT0M"))
    }
}
