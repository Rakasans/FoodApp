package io.github.rakasans.foodapp.domain

/** To, co da się odczytać z publicznej strony rolki/posta. Puste pole = nie znaleziono. */
data class InstagramPost(
    val caption: String?,
    val imageUrl: String?,
    val author: String?,
    /** Instagram uciął opis w danych strony (koniec w pół znaku) — reszta jest tylko w aplikacji Instagram. */
    val truncated: Boolean = false,
)

/**
 * Odczyt opisu, miniatury i autora z HTML publicznej strony Instagrama (strona „embed” albo zwykła).
 * Instagram nie publikuje stabilnego formatu, dlatego próbujemy kilku źródeł po kolei:
 * 1) metatagi og:* — o ile dotyczą właśnie tej rolki (strona bywa profilem z opisami wielu rolek),
 * 2) blok „Caption” strony embed, 3) JSON w stronie — tylko opis przypisany do kodu tej rolki.
 * Format może się zmienić bez ostrzeżenia — wtedy import przechodzi na zrzut ekranu.
 */
object InstagramPage {

    private val SHORTCODE = Regex("""instagram\.com/(?:[A-Za-z0-9_.]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)

    fun shortcode(url: String): String? = SHORTCODE.find(url)?.groupValues?.get(1)

    /** Adres strony „embed” z opisem — zwykle dostępny bez logowania. */
    fun embedUrl(shortcode: String) = "https://www.instagram.com/p/$shortcode/embed/captioned/"

    fun pageUrl(shortcode: String) = "https://www.instagram.com/reel/$shortcode/"

    fun postUrl(shortcode: String) = "https://www.instagram.com/p/$shortcode/"

    /** Kanoniczny adres rolki — bez parametrów śledzących (?igsh=…) i nazwy profilu. */
    fun canonicalUrl(url: String): String = shortcode(url)?.let { pageUrl(it) } ?: url.substringBefore('?')

    /**
     * @param shortcode kod rolki, o którą pytamy. Gdy podany, odrzucamy treści innych rolek z tej samej strony.
     */
    fun parse(html: String, shortcode: String? = null): InstagramPost {
        val metaMatches = shortcode == null || metaIsAbout(html, shortcode)
        val caption = (if (metaMatches) captionFromMeta(html) else null)
            ?: captionFromEmbed(html)
            ?: captionFromJson(html, shortcode)
        val ogImage = if (metaMatches) imageFromMeta(html) else null
        // og:image ma nałożony znaczek „play” (wersja dla podglądów linków); to samo zdjęcie bez znaczka
        // jest w danych strony jako display_uri z tą samą nazwą pliku
        val image = ogImage?.let { withoutOverlay(html, it) } ?: ogImage ?: imageFromEmbed(html)
            ?: (if (shortcode == null) jsonString(html, "display_url") ?: jsonString(html, "thumbnail_src") else null)
        val author = (if (metaMatches) authorFromMetaDescription(html) ?: authorFromMeta(html) else null)
            ?: authorFromEmbed(html)
            ?: (if (shortcode == null) jsonString(html, "username") else null)
        // Instagram ucina długie opisy w pół znaku (\uFFFD na końcu) — oznaczamy to zamiast udawać pełny tekst
        val cut = caption?.trim()?.endsWith('\uFFFD') == true
        val text = if (cut) caption!!.trim().trimEnd('\uFFFD').trimEnd() + "…" else caption
        return InstagramPost(
            truncated = cut,
            caption = text?.trim()?.takeIf { it.isNotEmpty() },
            imageUrl = image?.takeIf { it.startsWith("http") },
            author = author?.trim()?.removePrefix("@")?.takeIf { it.isNotEmpty() },
        )
    }

    // ---------- 1) JSON w stronie

    /** Czy metatagi strony (og:url, kanoniczny adres, al:android:url) dotyczą tej rolki. */
    private fun metaIsAbout(html: String, shortcode: String): Boolean {
        val urls = listOfNotNull(
            meta(html, "og:url"), meta(html, "al:android:url"),
            Regex("""<link[^>]+rel="canonical"[^>]*href="([^"]+)"""").find(html)?.groupValues?.get(1),
        )
        return urls.any { shortcode(it) == shortcode }
    }

    /**
     * Opis z JSON. Bez kodu rolki — pierwszy znaleziony (strony z jedną rolką).
     * Z kodem — tylko opis, po którym najbliższy `"code"` to kod tej rolki (na stronie profilu każda rolka
     * ma obiekt z `caption`, a po nim swój `code`).
     */
    private fun captionFromJson(html: String, shortcode: String? = null): String? {
        val captionRx = Regex(""""caption"\s*:\s*\{[^{}]*?"text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        if (shortcode != null) {
            val codeRx = Regex(""""code"\s*:\s*"([A-Za-z0-9_-]+)"""")
            captionRx.findAll(html).forEach { m ->
                val next = codeRx.find(html, m.range.last)
                if (next?.groupValues?.get(1) == shortcode) return unescapeJson(m.groupValues[1])
            }
            return null
        }
        Regex(""""edge_media_to_caption"\s*:\s*\{\s*"edges"\s*:\s*\[\s*\{\s*"node"\s*:\s*\{\s*"text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(html)?.let { return unescapeJson(it.groupValues[1]) }
        captionRx.find(html)?.let { return unescapeJson(it.groupValues[1]) }
        return null
    }

    /** Szuka w danych strony adresu tego samego pliku zdjęcia co og:image, ale bez nakładki (bez `cmp1` w `stp`). */
    private fun withoutOverlay(html: String, ogImage: String): String? {
        val file = Regex("""/([^/?]+\.(?:jpg|jpeg|webp|heic))""").find(ogImage)?.groupValues?.get(1) ?: return null
        return Regex(""""display_(?:uri|url)"\s*:\s*"((?:[^"\\]|\\.)*)"""").findAll(html)
            .map { unescapeJson(it.groupValues[1]) }
            .firstOrNull { it.contains("/$file") && !it.contains("cmp1") }
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

    /** og:description: „1,229 likes, 44 comments - gawe_l – October 24, 2026: …” → gawe_l */
    private fun authorFromMetaDescription(html: String): String? {
        val d = meta(html, "og:description") ?: return null
        return Regex("""comments?\s*-\s*([A-Za-z0-9._]+)\s+(?:–|-|on|w dniu)""").find(d)?.groupValues?.get(1)
    }

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
