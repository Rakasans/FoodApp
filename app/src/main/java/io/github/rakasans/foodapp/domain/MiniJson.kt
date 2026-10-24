package io.github.rakasans.foodapp.domain

/**
 * Minimalny parser JSON (czysty Kotlin, bez Androida — testowalny jednostkowo).
 * Wynik: Map<String, Any?>, List<Any?>, String, Double, Boolean albo null.
 * Na błędnym JSON-ie rzuca IllegalArgumentException.
 */
object MiniJson {

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        require(p.done()) { "Nadmiarowe znaki w JSON" }
        return v
    }

    fun parseOrNull(text: String): Any? = try { parse(text) } catch (e: IllegalArgumentException) { null }
        catch (e: IndexOutOfBoundsException) { null }

    private class Parser(val s: String) {
        var i = 0
        fun done() = i >= s.length
        fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun value(): Any? {
            skipWs()
            require(i < s.length) { "Koniec danych" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("Nieoczekiwany znak '$c'")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "Oczekiwano $word" }
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++ // {
            skipWs()
            if (s[i] == '}') { i++; return m }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                require(s[i] == ':') { "Oczekiwano ':'" }
                i++
                m[k] = value()
                skipWs()
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw IllegalArgumentException("Oczekiwano ',' lub '}'")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++ // [
            skipWs()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value()
                skipWs()
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> throw IllegalArgumentException("Oczekiwano ',' lub ']'")
                }
            }
        }

        private fun str(): String {
            require(s[i] == '"') { "Oczekiwano '\"'" }
            i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i]
                when {
                    c == '"' -> { i++; return sb.toString() }
                    c == '\\' -> {
                        val n = s[i + 1]
                        when (n) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> { sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                            else -> sb.append(n)
                        }
                        i += 2
                    }
                    else -> { sb.append(c); i++ }
                }
            }
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: throw IllegalArgumentException("Zła liczba")
        }
    }
}
