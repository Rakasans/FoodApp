package io.github.rakasans.foodapp.domain

/**
 * Bardzo prosty, tolerancyjny parser HTML do drzewa elementów (czysty Kotlin, testowalny).
 * Wystarcza do odczytu mikrodanych (itemprop) i struktury przepisu; nie jest pełną implementacją HTML5.
 */
class HtmlNode(
    val tag: String,
    val attrs: Map<String, String> = emptyMap(),
    val parent: HtmlNode? = null,
) {
    val children = mutableListOf<Any>() // HtmlNode albo String (tekst)

    fun attr(name: String): String? = attrs[name]
    fun has(name: String): Boolean = attrs.containsKey(name)
    val classes: List<String> get() = attr("class")?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()

    val elements: List<HtmlNode> get() = children.filterIsInstance<HtmlNode>()

    fun descendants(): Sequence<HtmlNode> = sequence {
        for (c in elements) { yield(c); yieldAll(c.descendants()) }
    }

    fun ancestors(): Sequence<HtmlNode> = generateSequence(parent) { it.parent }

    /** Tekst elementu: bloki rozdzielone nową linią, białe znaki zwinięte. */
    fun text(skip: (HtmlNode) -> Boolean = { false }): String {
        val sb = StringBuilder()
        fun walk(n: HtmlNode) {
            if (skip(n)) return
            val block = n.tag in BLOCK
            if (block || n.tag == "br") sb.append('\n')
            for (c in n.children) when (c) {
                is String -> sb.append(c)
                is HtmlNode -> walk(c)
            }
            if (block) sb.append('\n')
        }
        walk(this)
        return sb.toString().replace(' ', ' ').lines()
            .map { it.replace(Regex("[ \t\r]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    companion object {
        val BLOCK = setOf("p", "div", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "section", "article", "tr", "table", "header", "footer", "dt", "dd", "blockquote", "figure")
    }
}

object MiniHtml {
    private val VOID = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr")
    private val RAW = setOf("script", "style", "textarea", "title")
    /** Elementy zamykane niejawnie, gdy otwiera się taki sam (np. kolejne <li>, <p>). */
    private val AUTO_CLOSE_SAME = setOf("li", "p", "option", "tr", "td", "th", "dt", "dd")

    fun parse(html: String): HtmlNode {
        val root = HtmlNode("#root")
        var current = root
        var i = 0
        val src = html
        val n = src.length
        while (i < n) {
            val lt = src.indexOf('<', i)
            if (lt < 0) { addText(current, src.substring(i)); break }
            if (lt > i) addText(current, src.substring(i, lt))
            // komentarz
            if (src.startsWith("<!--", lt)) {
                val end = src.indexOf("-->", lt + 4)
                i = if (end < 0) n else end + 3
                continue
            }
            // <!DOCTYPE>, <?xml?>
            if (lt + 1 < n && (src[lt + 1] == '!' || src[lt + 1] == '?')) {
                val end = src.indexOf('>', lt)
                i = if (end < 0) n else end + 1
                continue
            }
            var j = lt + 1
            val closing = j < n && src[j] == '/'
            if (closing) j++
            if (j >= n || !src[j].isLetter()) { addText(current, "<"); i = lt + 1; continue }
            val nameStart = j
            while (j < n && (src[j].isLetterOrDigit() || src[j] == '-' || src[j] == ':')) j++
            val name = src.substring(nameStart, j).lowercase()
            // atrybuty aż do '>' (z pominięciem cudzysłowów)
            val attrs = LinkedHashMap<String, String>()
            var selfClosing = false
            while (j < n && src[j] != '>') {
                val c = src[j]
                when {
                    c.isWhitespace() -> j++
                    c == '/' -> { selfClosing = true; j++ }
                    else -> {
                        val ks = j
                        while (j < n && !src[j].isWhitespace() && src[j] != '=' && src[j] != '>' && src[j] != '/') j++
                        val key = src.substring(ks, j).lowercase()
                        while (j < n && src[j].isWhitespace()) j++
                        var value = ""
                        if (j < n && src[j] == '=') {
                            j++
                            while (j < n && src[j].isWhitespace()) j++
                            if (j < n && (src[j] == '"' || src[j] == '\'')) {
                                val q = src[j]
                                val end = src.indexOf(q, j + 1).let { if (it < 0) n else it }
                                value = src.substring(j + 1, end)
                                j = minOf(n, end + 1)
                            } else {
                                val vs = j
                                while (j < n && !src[j].isWhitespace() && src[j] != '>') j++
                                value = src.substring(vs, j)
                            }
                        }
                        if (key.isNotEmpty()) attrs[key] = InstagramPage.decodeEntities(value)
                        selfClosing = false
                    }
                }
            }
            i = minOf(n, j + 1)
            if (closing) {
                var x: HtmlNode? = current
                while (x != null && x.tag != name) x = x.parent
                if (x?.parent != null) current = x.parent!!
                continue
            }
            if (name in AUTO_CLOSE_SAME) {
                val same = (sequenceOf(current) + current.ancestors())
                    .takeWhile { it.tag !in setOf("ul", "ol", "table", "div", "section", "article", "#root") }
                    .firstOrNull { it.tag == name }
                if (same?.parent != null) current = same.parent!!
            }
            val node = HtmlNode(name, attrs, current)
            current.children += node
            if (name in RAW) {
                val end = src.indexOf("</$name", i, ignoreCase = true).let { if (it < 0) n else it }
                node.children += src.substring(i, end)
                i = src.indexOf('>', end).let { if (it < 0) n else it + 1 }
            } else if (name !in VOID && !selfClosing) {
                current = node
            }
        }
        return root
    }

    private fun addText(node: HtmlNode, raw: String) {
        if (raw.isEmpty()) return
        node.children += InstagramPage.decodeEntities(raw)
    }
}
