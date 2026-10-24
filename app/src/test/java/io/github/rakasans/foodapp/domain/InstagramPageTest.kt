package io.github.rakasans.foodapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramPageTest {

    @Test
    fun `kod rolki z roznych adresow`() {
        assertEquals("DEMO123", InstagramPage.shortcode("https://www.instagram.com/reel/DEMO123/?igsh=abc"))
        assertEquals("XyZ_9-a", InstagramPage.shortcode("https://instagram.com/p/XyZ_9-a"))
        assertEquals("AbC", InstagramPage.shortcode("https://www.instagram.com/kuchnia_demo/reel/AbC/"))
        assertEquals(null, InstagramPage.shortcode("https://example.com/reel/abc"))
    }

    @Test
    fun `opis ze strony embed`() {
        val html = """
            <div class="Caption"><a class="CaptionUsername" href="/kuchnia_demo/">kuchnia_demo</a><br />Kluski chlebowe<br /><br />Potrzebujemy:<br />200 ml mleka<br />1 jajko &amp; sól<div class="CaptionComments">x</div></div>
            <img class="EmbeddedMediaImage" alt="" src="https://cdn.example/zdjecie.jpg?a=1&amp;b=2" />
        """.trimIndent()
        val post = InstagramPage.parse(html)
        assertEquals("Kluski chlebowe\n\nPotrzebujemy:\n200 ml mleka\n1 jajko & sól", post.caption)
        assertEquals("https://cdn.example/zdjecie.jpg?a=1&b=2", post.imageUrl)
        assertEquals("kuchnia_demo", post.author)
    }

    @Test
    fun `opis z JSON w stronie`() {
        val html = """<script>{"edge_media_to_caption":{"edges":[{"node":{"text":"Pierniki\n\nSkładniki:\n500 g mąki \"tortowej\""}}]},"display_url":"https:\/\/cdn.example\/p.jpg","username":"autor"}</script>"""
        val post = InstagramPage.parse(html)
        assertEquals("Pierniki\n\nSkładniki:\n500 g mąki \"tortowej\"", post.caption)
        assertEquals("https://cdn.example/p.jpg", post.imageUrl)
        assertEquals("autor", post.author)
    }

    @Test
    fun `opis z metatagow og`() {
        val html = """
            <meta property="og:title" content="Kuchnia Demo on Instagram: &quot;Zupa&quot;" />
            <meta property="og:description" content="120 likes, 4 comments - kuchnia_demo on October 1, 2026: &quot;Zupa krem&#10;1 kg dyni&quot;." />
            <meta property="og:image" content="https://cdn.example/og.jpg" />
        """.trimIndent()
        val post = InstagramPage.parse(html)
        assertEquals("Zupa krem\n1 kg dyni", post.caption)
        assertEquals("https://cdn.example/og.jpg", post.imageUrl)
        // nazwa konta z og:description ma pierwszeństwo przed nazwą wyświetlaną z og:title
        assertEquals("kuchnia_demo", post.author)
    }

    @Test
    fun `strona bez opisu`() {
        val post = InstagramPage.parse("<html><body>Zaloguj się</body></html>")
        assertEquals(null, post.caption)
        assertEquals(null, post.imageUrl)
    }

    @Test
    fun `strona profilu z wieloma rolkami - bierzemy opis tej rolki z og`() {
        // Tak wyglądała prawdziwa odpowiedź: JSON z opisami kilku rolek profilu (pierwsza to inna rolka),
        // a metatagi og:* dotyczą rolki, o którą pytamy.
        val html = """
            <link rel="canonical" href="https://www.instagram.com/reel/KLUSKI1/" />
            <meta property="og:url" content="https://www.instagram.com/autor_demo/reel/KLUSKI1/" />
            <meta property="og:description" content="1,229 likes, 44 comments - autor_demo – October 24, 2026: &quot;Kluski chlebowe&#10;Potrzebujemy:&#10;200 ml mleka&#10;&#10;#kluski&quot;. " />
            <meta property="og:image" content="https://cdn.example/kluski.jpg" />
            <script>{"caption":{"text":"O kurka! Inny przepis"},"code":"KURKI22"},{"caption":{"text":"Kluski z JSON"},"code":"KLUSKI1"}</script>
        """.trimIndent()
        val post = InstagramPage.parse(html, "KLUSKI1")
        assertEquals("Kluski chlebowe\nPotrzebujemy:\n200 ml mleka\n\n#kluski", post.caption)
        assertEquals("https://cdn.example/kluski.jpg", post.imageUrl)
        assertEquals("autor_demo", post.author)
    }

    @Test
    fun `bez metatagow - opis z JSON przypisany do kodu rolki`() {
        val html = """{"caption":{"text":"O kurka! Inny przepis"},"code":"KURKI22"},{"caption":{"text":"Kluski z JSON"},"code":"KLUSKI1"}"""
        assertEquals("Kluski z JSON", InstagramPage.parse(html, "KLUSKI1").caption)
        // obcej rolki nie bierzemy
        assertEquals(null, InstagramPage.parse(html, "INNA999").caption)
    }

    @Test
    fun `adres kanoniczny bez parametrow sledzacych`() {
        assertEquals(
            "https://www.instagram.com/reel/De3nC8wgGN0/",
            InstagramPage.canonicalUrl("https://www.instagram.com/reel/De3nC8wgGN0/?igsh=MWQ1ZGUxMzBkMA=="),
        )
    }

    @Test
    fun `miniatura bez znaczka play z danych strony`() {
        val html = """
            <meta property="og:url" content="https://www.instagram.com/reel/KLUSKI1/" />
            <meta property="og:description" content="1 likes, 0 comments - autor – October 24, 2026: &quot;Kluski&quot;." />
            <meta property="og:image" content="https://cdn.example/v/123_abc_n.jpg?stp=cmp1_dst-jpg_s640x640&amp;oh=x" />
            <script>{"display_uri":"https:\/\/cdn.example\/v\/999_inne_n.jpg?stp=dst-jpg"},{"display_uri":"https:\/\/cdn.example\/v\/123_abc_n.jpg?stp=dst-jpg_s640x640\u0026oh=y"}</script>
        """.trimIndent()
        assertEquals("https://cdn.example/v/123_abc_n.jpg?stp=dst-jpg_s640x640&oh=y", InstagramPage.parse(html, "KLUSKI1").imageUrl)
    }
}
