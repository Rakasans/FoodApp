package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTextParserTest {

    @Test
    fun `tekst z notatnika bez naglowkow`() {
        val r = RecipeTextParser.parse(
            """
200ml oleju
20g gochugaru
15g chili
Sezam - 10g
5g soli
3 Ząbki czosnku
Mała dymka
Cynamon 1 pałka
Kardamon 6 ziarenek
Liść laurowy 3
Anyż gwiazdki 2 sztuki

W misce aluminiowej albo garnku mieszasz chili z solą i czosnkiem i zostawiasz.

Olej podgrzewasz z dymką i przyprawami, aż dymka się zarumieni, wyławiasz przyprawy i gorącym olejem zalewasz chili.
            """.trimIndent()
        )
        assertEquals(11, r.ingredients.size)
        assertEquals("Mała dymka", r.ingredients[6].raw)
        assertEquals(2, r.steps.size)
    }

    @Test
    fun `tekst z naglowkami`() {
        val r = RecipeTextParser.parse(
            """
            Racuchy z jabłkami
            Porcje: 4

            Składniki:
            - 500 ml kefiru
            - 2 jajka
            - cukier waniliowy

            Przygotowanie:
            1. Jajka roztrzep z kefirem.
            2. Dodaj mąkę i wymieszaj.
            """.trimIndent()
        )
        assertEquals("Racuchy z jabłkami", r.name)
        assertEquals(4, r.servings)
        assertEquals(listOf("500 ml kefiru", "2 jajka", "cukier waniliowy"), r.ingredients.map { it.raw })
        assertEquals(listOf("Jajka roztrzep z kefirem.", "Dodaj mąkę i wymieszaj."), r.steps)
    }

    @Test
    fun `grupy skladnikow`() {
        val r = RecipeTextParser.parse(
            """
            Pierniki
            Składniki
            Ciasto:
            500 g mąki
            200 g miodu
            Lukier:
            150 g cukru pudru
            Wykonanie
            Wszystko połącz.
            """.trimIndent()
        )
        assertEquals(listOf("Ciasto", "Ciasto", "Lukier"), r.ingredients.map { it.group })
        assertEquals(listOf("Wszystko połącz."), r.steps)
    }

    @Test
    fun `opis rolki z emoji i hashtagami bez naglowkow`() {
        val r = RecipeTextParser.parse(
            """
            🎃 Makaron z dynią 🎃
            • 250 g makaronu
            • 300 g dyni
            • parmezan
            1. Dynię podsmaż na maśle z czosnkiem do miękkości.
            2. Wymieszaj z makaronem i posyp parmezanem.
            #przepis #dynia #obiad
            """.trimIndent()
        )
        assertEquals("Makaron z dynią", r.name)
        assertEquals(listOf("250 g makaronu", "300 g dyni", "parmezan"), r.ingredients.map { it.raw })
        assertEquals(2, r.steps.size)
        assertEquals(null, r.servings)
    }

    @Test
    fun `brak przepisu daje puste pola`() {
        val r = RecipeTextParser.parse("   \n  \n")
        assertEquals("", r.name)
        assertTrue(r.ingredients.isEmpty() && r.steps.isEmpty())
    }

    @Test
    fun `porcje w formie dla osob`() {
        assertEquals(6, RecipeTextParser.parse("Gulasz\nDla 6 osób\nSkładniki\n1 kg wołowiny").servings)
    }

    @Test
    fun `prawdziwy opis rolki - wstep, Potrzebujemy, grupa Sos, kroki jako akapity`() {
        val r = RecipeTextParser.parse(KLUSKI)
        // wstęp to nie tytuł — nie zgadujemy nazwy
        assertEquals("", r.name)
        assertEquals(null, r.servings)
        assertEquals(18, r.ingredients.size)
        assertEquals("300-350 g chleba każdego", r.ingredients.first().raw)
        assertEquals("Sos", r.ingredients[8].group)
        assertEquals(4, r.steps.size)
        assertTrue(r.steps[0].startsWith("Nasze kluski chlebowe zrobimy"))
        // linie złamane w środku zdania są sklejone w jeden krok
        assertTrue(r.steps[1].contains("dodajemy odrobinę mleka. Wszystko zależy"))
        assertTrue(r.steps[1].endsWith("z kapką oliwy."))
        // „Smacznego” nie jest krokiem
        assertEquals("Karmelizowaną cebulę podsmażamy 2 minuty z kluskami chlebowymi.", r.steps[3])
    }

    @Test
    fun `kroki z kartki - lamanie linii i przenoszenie wyrazow z OCR`() {
        val r = RecipeTextParser.parse(
            """
            Chleb na zakwasie
            Składniki
            500 g mąki
            około 300 ml ciepłej wody
            Przygotowanie
            1. Mąkę wymieszaj z wodą i odstaw
            na 30 minut w ciepłe miej-
            sce.
            2. Dodaj zakwas.
            """.trimIndent()
        )
        assertEquals(listOf("Mąkę wymieszaj z wodą i odstaw na 30 minut w ciepłe miejsce.", "Dodaj zakwas."), r.steps)
        assertEquals("około 300 ml ciepłej wody", r.ingredients[1].raw)
    }
}

private val KLUSKI = """Te kluski chlebowe powinny mieć swoje święto narodowe. To idealny sposób na wykorzystanie czerstwego chleba. Z karmelizowaną cebulą w kremowym i aksamitnym sosie z mascarpone od Galbani. [reklama]
Tylko spójrzcie i koniecznie zróbcie to sobie sami. Przepis pochodzi z mojego programu telewizyjnego. Emisja co piątek i 20:05 w TV4. Kto będzie robił?????

Potrzebujemy:
300-350 g chleba każdego
200 ml mleka
1 większe jajko
2 garści startego Grana Padano Galbani
100 g mąki pszennej
duża garść ulubionych świeżych ziół, u mnie pietruszka
sól
świeżo mielony pieprz

Sos:
250 g mascarpone Galbani
2 łyżki masła z kapką oliwy z oliwek
3 klasyczne cebule
2 łyżki masła i kapka oliwy z oliwek
40-50 ml sosu jasnego sojowego
1-2 łyżki soku z cytryny
2 łyżki cukru
6 gałązek tymianku
1 łyżeczka gałki muszkatołowej
można serwować ze szczypiorkiem i Grana Padano

Nasze kluski chlebowe zrobimy, maczając drobno pokrojony chleb z lekko podgrzanym mlekiem przez 10 min. Miksujemy. Dodajemy mąkę, jajko, starty twardy ser Grana Padano, poszatkowane zioła, sól i pieprz do smaku.
Jeśli masa się nie formuje, dodamy odrobinę mąki albo bułki tartej. Jeśli jest za twarda, dodajemy odrobinę
mleka. Wszystko zależy od chleba, którego używamy. Kulki wielkości orzecha włoskiego formujemy MOKRYMI
dłońmi. Gotujemy w gorącej wodzie przez 5 minut, następnie smażymy na 2 łyżkach masła z kapką oliwy.
Wyjątkowo karmelizowaną cebulę zrobimy, podsmażając ją na maśle z kapką oliwy. Po 6 minutach dodajemy sos
sojowy, gałkę muszkatołową, obrane gałązki tymianku i cukier. Podsmażamy kolejne 5 minut i na ostatnie 5 minut
smażenia dodajemy mascarpone Galbani i sok z cytryny.
Karmelizowaną cebulę podsmażamy 2 minuty z kluskami chlebowymi. Smacznego."""
