package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlRecipeTextTest {

    /** Układ podobny do AniaGotuje: nagłówek „Składniki”, ilość na końcu w osobnym elemencie. */
    private val withHeader = """
<html><head><meta property="og:title" content="Gulasz wołowy z dynią | AniaGotuje.pl">
<meta property="og:image" content="https://example.pl/gulasz.jpg"><script>var x = "1 kg";</script></head>
<body><nav><a>Przepisy</a><a>Składniki sezonowe</a></nav>
<h1>Gulasz wołowy z dynią</h1><p>Rozgrzewający gulasz na jesień.</p>
<h2>Składniki</h2>
<ul>
<li><span>karczek wołowy</span> <span>1 kg</span></li>
<li><span>dynia np. piżmowa</span> <span>650 g</span> - miąższ pokrojony w kostkę</li>
<li><span>cebule</span> <span>300 g</span> - 2 sztuki</li>
<li><span>czosnek</span> <span>4 ząbki</span></li>
<li><span>delikatny bulion wołowy</span> <span>500 ml</span></li>
</ul><button>Kopiuj</button>
<h2>Przygotowanie</h2>
<ol><li><p>Mięso pokroić w kostkę o boku około 3 cm, osuszyć i oprószyć solą oraz świeżo mielonym pieprzem.</p></li>
<li><p>Na dużej patelni rozgrzać masło klarowane i obsmażyć mięso partiami ze wszystkich stron.</p></li></ol>
<h3>Komentarze</h3><p>Pyszne! 5 gwiazdek</p>
<footer>© AniaGotuje</footer></body></html>
""".trimIndent()

    /** Układ podobny do bloga WordPress bez nagłówka „Składniki”: lista z ilościami w akapicie z <br>. */
    private val withoutHeader = """
<html><body><header><h1>Rozkoszny</h1></header>
<article><h1 class="entry-title">Sernik baskijski (ale jeszcze lepszy)</h1>
<p>Długi wstęp o tym, jak powstał ten sernik i dlaczego jest lepszy od poprzedniego, z mnóstwem szczegółów.</p>
<p><strong>Sernik baskijski</strong></p>
<p><em>tortownica 23 cm</em><br>180 g ulubionych ciasteczek, najlepiej digestives<br>3 łyżki masła<br>
750 g serka śmietankowego typu Philadelphia<br>1 ¼ szklanki (250 g) cukru<br>6 dużych jajek „zerówek”<br>
1 płaska łyżeczka drobnej soli morskiej</p>
<p>Piekarnik nagrzać do 220 stopni. Ciasteczka zmiksować na drobny piasek, wymieszać z roztopionym masłem.</p>
<p>Serek zmiksować z cukrem i solą, dodawać po jednym jajku, na końcu wlać śmietankę i wymieszać.</p>
<h3>Rady/porady</h3><p>Serek musi mieć temperaturę pokojową.</p>
</article></body></html>
""".trimIndent()

    @Test
    fun `strona z naglowkiem skladniki`() {
        val r = WebRecipePage.parse(withHeader)!!
        assertEquals("Gulasz wołowy z dynią", r.name)
        assertEquals(5, r.ingredients.size)
        assertEquals("karczek wołowy 1 kg", r.ingredients[0])
        assertEquals(2, r.steps.size)
        assertTrue(r.steps.none { it.contains("Pyszne") })
        assertEquals("https://example.pl/gulasz.jpg", r.imageUrl)
        assertTrue(r.originalText("https://x").startsWith("Tekst ze strony"))
        val first = IngredientParser.parse(r.ingredients[0])
        assertEquals(1.0, first.quantity!!, 1e-9); assertEquals("kg", first.unit)
    }

    @Test
    fun `strona bez naglowka z lista ilosci`() {
        val r = WebRecipePage.parse(withoutHeader)!!
        assertEquals("Sernik baskijski (ale jeszcze lepszy)", r.name)
        assertEquals(6, r.ingredients.size)
        assertEquals("180 g ulubionych ciasteczek, najlepiej digestives", r.ingredients[0])
        assertEquals(2, r.steps.size)
        assertTrue(r.steps.none { it.contains("temperaturę pokojową") })
    }

    @Test
    fun `strona bez przepisu`() {
        assertEquals(null, WebRecipePage.parse("<html><body><h1>O nas</h1><p>Jesteśmy blogiem od 2010 roku.</p></body></html>"))
    }
}
