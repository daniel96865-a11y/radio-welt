package de.klartaste.engine

object ChatJson {
    fun assistantContent(json: String): String? {
        val message = json.indexOf("\"message\"")
        val from = if (message >= 0) message else 0
        val key = "\"content\""
        var cursor = from
        while (cursor < json.length) {
            val at = json.indexOf(key, cursor)
            if (at < 0) return null
            var i = at + key.length
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != ':') {
                cursor = at + key.length
                continue
            }
            i++
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != '"') {
                cursor = at + key.length
                continue
            }
            return readString(json, i)
        }
        return null
    }

    fun clean(text: String): String {
        var value = text.trim().trim('"', '“', '”', '„', '\'')
        if (value.startsWith("```")) {
            value = value.removePrefix("```").removeSuffix("```").trim()
        }
        return value.trim()
    }

    private fun readString(json: String, openingQuote: Int): String {
        val out = StringBuilder()
        var i = openingQuote + 1
        while (i < json.length) {
            val ch = json[i]
            if (ch == '"') return out.toString()
            if (ch == '\\' && i + 1 < json.length) {
                when (val esc = json[i + 1]) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000c')
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    '/' -> out.append('/')
                    'u' -> {
                        if (i + 5 < json.length) {
                            val hex = json.substring(i + 2, i + 6)
                            out.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                            i += 6
                            continue
                        }
                    }
                    else -> out.append(esc)
                }
                i += 2
                continue
            }
            out.append(ch)
            i++
        }
        return out.toString()
    }
}
