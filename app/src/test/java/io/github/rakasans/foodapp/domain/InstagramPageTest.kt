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
        assertEquals("Kuchnia Demo", post.author)
    }

    @Test
    fun `strona bez opisu`() {
        val post = InstagramPage.parse("<html><body>Zaloguj się</body></html>")
        assertEquals(null, post.caption)
        assertEquals(null, post.imageUrl)
    }
}
