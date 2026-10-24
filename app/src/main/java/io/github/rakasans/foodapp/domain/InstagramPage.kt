package io.github.rakasans.foodapp.domain

/** To, co da się odczytać z publicznej strony rolki/posta. Puste pole = nie znaleziono. */
data class InstagramPost(
    val caption: String?,
    val imageUrl: String?,
    val author: String?,
)

/**
 * Odczyt opisu, miniatury i autora z HTML publicznej strony Instagrama (strona „embed” albo zwykła).
 * Instagram nie publikuje stabilnego formatu, dlatego próbujemy kilku źródeł po kolei:
 * 1) JSON osadzony w stronie, 2) blok „Caption” strony embed, 3) metatagi og:*.
 * Format może się zmienić bez ostrzeżenia — wtedy import przechodzi na zrzut ekranu.
 */
object InstagramPage {

    private val SHORTCODE = Regex("""instagram\.com/(?:[A-Za-z0-9_.]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)

    fun shortcode(url: String): String? = SHORTCODE.find(url)?.groupValues?.get(1)

    /** Adres strony „embed” z opisem — zwykle dostępny bez logowania. */
    fun embedUrl(shortcode: String) = "https://www.instagram.com/p/$shortcode/embed/captioned/"

    fun pageUrl(shortcode: String) = "https://www.instagram.com/reel/$shortcode/"

    fun parse(html: String): InstagramPost {
        val caption = captionFromJson(html) ?: captionFromEmbed(html) ?: captionFromMeta(html)
        val image = imageFromMeta(html) ?: imageFromEmbed(html) ?: jsonString(html, "display_url") ?: jsonString(html, "thumbnail_src")
        val author = jsonString(html, "username") ?: authorFromEmbed(html) ?: authorFromMeta(html)
        return InstagramPost(
            caption = caption?.trim()?.takeIf { it.isNotEmpty() },
            imageUrl = image?.takeIf { it.startsWith("http") },
            author = author?.trim()?.removePrefix("@")?.takeIf { it.isNotEmpty() },
        )
    }

    // ---------- 1) JSON w stronie

    private fun captionFromJson(html: String): String? {
        Regex(""""edge_media_to_caption"\s*:\s*\{\s*"edges"\s*:\s*\[\s*\{\s*"node"\s*:\s*\{\s*"text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(html)?.let { return unescapeJson(it.groupValues[1]) }
        Regex(""""caption"\s*:\s*\{[^{}]*?"text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(html)?.let { return unescapeJson(it.groupValues[1]) }
        return null
    }

    private fun jsonString(html: String, key: String): String? =
        Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(html)?.groupValues?.get(1)?.let(::unescapeJson)

    // ---------- 2) strona embed

    private fun captionFromEmbed(html: String): String? {
        val start = Regex("""<div[^>]*class="[^"]*\bCaption\b[^"]*"[^>]*>""").find(html) ?: return null
        var block = html.substring(start.range.last + 1)
        block = block.substringBefore("<div class=\"CaptionComments\"").substringBefore("</div>")
        // nazwa autora na początku opisu to link — usuwamy
        block = block.replace(Regex("""<a[^>]*class="[^"]*CaptionUsername[^"]*"[^>]*>.*?</a>""", RegexOption.DOT_MATCHES_ALL), "")
        return htmlToText(block)
    }

    private fun authorFromEmbed(html: String): String? =
        Regex("""<a[^>]*class="[^"]*CaptionUsername[^"]*"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.let(::htmlToText)

    private fun imageFromEmbed(html: String): String? =
        Regex("""<img[^>]*class="[^"]*EmbeddedMediaImage[^"]*"[^>]*src="([^"]+)"""").find(html)?.groupValues?.get(1)?.let(::decodeEntities)

    // ---------- 3) metatagi

    private fun meta(html: String, property: String): String? {
        val tag = Regex("""<meta[^>]+(?:property|name)="$property"[^>]*>""", RegexOption.IGNORE_CASE).find(html)?.value ?: return null
        return Regex("""content="([^"]*)"""").find(tag)?.groupValues?.get(1)?.let(::decodeEntities)
    }

    /** og:description: „123 likes, 4 comments - autor on 1 May 2026: "opis"” — bierzemy tekst w cudzysłowie. */
    private fun captionFromMeta(html: String): String? {
        val d = meta(html, "og:description") ?: meta(html, "description") ?: return null
        val quoted = Regex(""":\s*"(.*)"\s*\.?\s*$""", RegexOption.DOT_MATCHES_ALL).find(d)?.groupValues?.get(1)
        return quoted ?: d
    }

    private fun imageFromMeta(html: String): String? = meta(html, "og:image")

    private fun authorFromMeta(html: String): String? =
        meta(html, "og:title")?.let { Regex("""^(.*?)\s+(?:on|na)\s+Instagram""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }

    // ---------- narzędzia

    fun htmlToText(fragment: String): String = decodeEntities(
        fragment
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), "")
    ).lines().joinToString("\n") { it.trim() }.trim()

    fun decodeEntities(s: String): String =
        s.replace(Regex("""&#x([0-9a-fA-F]+);""")) { m -> String(Character.toChars(m.groupValues[1].toInt(16))) }
            .replace(Regex("""&#(\d+);""")) { m -> String(Character.toChars(m.groupValues[1].toInt())) }
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
            .replace("&amp;", "&")

    fun unescapeJson(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'r' -> { i += 2 }
                    '/' , '"', '\\' -> { sb.append(n); i += 2 }
                    'u' -> if (i + 5 < s.length) {
                        sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6
                    } else { sb.append(c); i++ }
                    else -> { sb.append(n); i += 2 }
                }
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }
}
