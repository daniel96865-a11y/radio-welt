package de.klartaste.engine

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

enum class LanguageMode {
    GERMAN,
    ENGLISH,
    BOTH,
}

/**
 * Offline spelling correction. Known words are kept. A typed prefix of a known
 * word is kept too, so half-finished words are not replaced. Real typos go
 * through a small edit-distance search biased toward neighboring keys.
 */
class CorrectionEngine(
    private val german: Lexicon,
    private val english: Lexicon,
    aliases: Map<String, String> = emptyMap(),
    userWords: Map<String, String> = emptyMap(),
) {
    var language: LanguageMode = LanguageMode.BOTH

    private val aliases: Map<String, String> = aliases.mapKeys { it.key.lowercase(Locale.GERMAN) }
    private val user: LinkedHashMap<String, String> = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 500
    }

    init {
        user.putAll(userWords.mapKeys { it.key.lowercase(Locale.GERMAN) })
    }

    fun correctWord(typed: String): String {
        if (shouldLeaveAlone(typed)) return typed
        val key = typed.lowercase(Locale.GERMAN)
        user[key]?.let { return applyCase(typed, it) }
        val known = exact(key)
        if (known != null) return applyCase(typed, known)
        aliases[key]?.let { return applyCase(typed, it) }
        val extension = soleExtension(typed, key)
        if (extension != null) return extension
        if (isKnownPrefix(key)) return typed
        if (isAllCaps(typed) && typed.length <= 4) return typed
        val best = bestEdit(key) ?: return typed
        if (!accept(key, best)) return typed
        return applyCase(typed, best.entry)
    }

    fun suggest(typed: String, limit: Int = 3): List<String> {
        if (typed.length < 2 || limit <= 0) return emptyList()
        val out = LinkedHashSet<String>()
        out.add(correctWord(typed))
        val key = typed.lowercase(Locale.GERMAN)
        for (entry in mergedCompletions(key, 8)) {
            out.add(applyCase(typed, entry))
        }
        val best = bestEdit(key)
        if (best != null && accept(key, best)) out.add(applyCase(typed, best.entry))
        return out.take(limit)
    }

    fun learn(form: String) {
        val clean = form.trim()
        if (clean.length < 2 || clean.any { it.isWhitespace() || it.isDigit() }) return
        user[clean.lowercase(Locale.GERMAN)] = clean
    }

    fun clearLearned() {
        user.clear()
    }

    fun learned(): Map<String, String> = LinkedHashMap(user)

    private fun shouldLeaveAlone(typed: String): Boolean {
        if (typed.length < 2) return true
        if (typed.any { it.isDigit() || it == '@' || it == '/' || it == '\\' || it == '_' }) return true
        if (typed.any { it.isLetter() && it.isUpperCase() } && typed.drop(1).any { it.isUpperCase() } && !isAllCaps(typed)) {
            return true
        }
        return false
    }

    private fun lexicons(): List<Lexicon> = when (language) {
        LanguageMode.GERMAN -> listOf(german)
        LanguageMode.ENGLISH -> listOf(english)
        LanguageMode.BOTH -> listOf(german, english)
    }

    private fun exact(key: String): Entry? {
        var best: Entry? = null
        for (lexicon in lexicons()) {
            val found = lexicon.exact(key) ?: continue
            val entry = Entry(found)
            val current = best
            if (current == null || entry.freq > current.freq) best = entry
        }
        return best
    }

    private fun isKnownPrefix(key: String): Boolean = lexicons().any { it.hasPrefix(key) }

    private fun soleExtension(typed: String, key: String): String? {
        val all = ArrayList<Entry>()
        for (lexicon in lexicons()) {
            for (found in lexicon.completions(key, Int.MAX_VALUE)) all.add(Entry(found))
        }
        if (all.isEmpty()) return null
        var shared = all[0].key
        for (entry in all) {
            val other = entry.key
            val max = minOf(shared.length, other.length)
            var i = 0
            while (i < max && shared[i] == other[i]) i++
            shared = shared.substring(0, i)
            if (shared.length <= key.length) return null
        }
        val chosen = exact(shared) ?: return null
        val extra = shared.length - key.length
        if (extra !in 1..3 || chosen.freq < 800) return null
        return applyCase(typed, chosen)
    }

    private fun mergedCompletions(prefix: String, limit: Int): List<Entry> {
        val merged = ArrayList<Entry>()
        for (lexicon in lexicons()) {
            for (found in lexicon.completions(prefix, limit)) merged.add(Entry(found))
        }
        user.entries
            .filter { it.key.startsWith(prefix) && it.key != prefix }
            .sortedBy { it.key }
            .take(limit)
            .forEach { merged.add(Entry(it.value, 100_000)) }
        merged.sortByDescending { it.freq }
        return if (merged.size <= limit) merged else merged.subList(0, limit)
    }

    private fun bestEdit(key: String): Scored? {
        var best: Scored? = null
        fun consider(candidate: String, dist: Int) {
            if (candidate == key || candidate.length < 2) return
            val entry = exact(candidate) ?: return
            if (looksLikeInflection(key, candidate)) return
            val scored = Scored(entry, dist, score(key, entry, dist))
            if (best == null || scored.score > best!!.score) best = scored
        }
        for (edited in edits1(key)) consider(edited, 1)
        if (best == null && key.length >= 5) {
            var checks = 0
            outer@ for (first in edits1(key)) {
                for (second in edits1(first)) {
                    consider(second, 2)
                    if (++checks > 2500) break@outer
                }
            }
        }
        return best
    }

    private fun accept(key: String, best: Scored): Boolean {
        if (best.dist <= 0) return true
        if (best.dist == 1) {
            if (key.length <= 2) return false
            if (key.length == 3 && !adjacentTypo(key, best.entry.key)) return best.entry.freq >= 80_000
            if (key.length == best.entry.key.length &&
                key.length >= 4 &&
                key.dropLast(1) == best.entry.key.dropLast(1) &&
                !neighbors(key.last()).contains(best.entry.key.last())
            ) {
                return false
            }
            return true
        }
        if (key.length < 5 || best.entry.freq < 5000) return false
        val firstOk = key.first() == best.entry.key.first() || neighbors(key.first()).contains(best.entry.key.first())
        return firstOk
    }

    private fun looksLikeInflection(typed: String, target: String): Boolean {
        if (!typed.startsWith(target) || typed.length - target.length !in 1..3) return false
        val suffix = typed.substring(target.length)
        return suffix in INFLECTION_SUFFIXES
    }

    private fun score(typedKey: String, entry: Entry, dist: Int): Double {
        var value = ln(entry.freq.coerceAtLeast(1).toDouble()) - dist * 3.4
        if (dist == 1 && adjacentTypo(typedKey, entry.key)) value += 1.5
        if (typedKey.firstOrNull() != null && typedKey.first() == entry.key.firstOrNull()) value += 0.35
        return value
    }

    private fun edits1(word: String): Set<String> {
        val out = HashSet<String>(word.length * 40)
        val chars = word.toCharArray()
        for (i in word.indices) {
            out.add(word.removeRange(i, i + 1))
            if (i + 1 < word.length && chars[i] != chars[i + 1]) {
                val swapped = chars.clone()
                val tmp = swapped[i]
                swapped[i] = swapped[i + 1]
                swapped[i + 1] = tmp
                out.add(String(swapped))
            }
            for (ch in ALPHABET) {
                if (ch == chars[i]) continue
                val replaced = chars.clone()
                replaced[i] = ch
                out.add(String(replaced))
            }
        }
        for (i in 0..word.length) {
            for (ch in ALPHABET) {
                out.add(word.substring(0, i) + ch + word.substring(i))
            }
        }
        return out
    }

    private fun adjacentTypo(typed: String, target: String): Boolean {
        if (typed.length != target.length) return false
        var index = -1
        for (i in typed.indices) {
            if (typed[i] != target[i]) {
                if (index != -1) return false
                index = i
            }
        }
        if (index == -1) return false
        return neighbors(typed[index]).contains(target[index])
    }

    private fun applyCase(typed: String, entry: Entry): String = applyCase(typed, entry.form)

    private fun applyCase(typed: String, form: String): String {
        if (isAllCaps(typed)) return form.uppercase(Locale.GERMAN)
        if (typed.first().isUpperCase() && form.first().isLowerCase()) {
            return form.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMAN) else it.toString() }
        }
        return form
    }

    private fun isAllCaps(typed: String): Boolean {
        val letters = typed.filter { it.isLetter() }
        return letters.length >= 2 && letters.all { it.isUpperCase() }
    }

    private data class Scored(val entry: Entry, val dist: Int, val score: Double)

    private data class Entry(val form: String, val key: String, val freq: Int) {
        constructor(source: Lexicon.Entry) : this(source.form, source.key, source.freq)
        constructor(form: String, freq: Int) : this(form, form.lowercase(Locale.GERMAN), freq)
    }

    companion object {
        private val ALPHABET = "abcdefghijklmnopqrstuvwxyzäöüß"
        private val INFLECTION_SUFFIXES = setOf(
            "e", "n", "s", "m", "r", "en", "er", "em", "es", "ne", "se", "te",
        )

        private val neighborMap: Map<Char, Set<Char>> = buildNeighbors()

        fun neighbors(ch: Char): Set<Char> = neighborMap[ch.lowercaseChar()] ?: emptySet()

        fun load(
            germanText: String,
            englishText: String,
            aliasText: String,
            userWords: Map<String, String> = emptyMap(),
        ): CorrectionEngine {
            return CorrectionEngine(
                german = Lexicon.parse(germanText),
                english = Lexicon.parse(englishText),
                aliases = parseAliases(aliasText),
                userWords = userWords,
            )
        }

        fun parseAliases(text: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for (line in text.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val tab = trimmed.indexOf('\t')
                if (tab <= 0) continue
                val from = trimmed.substring(0, tab).trim().lowercase(Locale.GERMAN)
                val to = trimmed.substring(tab + 1).trim()
                if (from.isNotEmpty() && to.isNotEmpty()) out[from] = to
            }
            return out
        }

        private fun buildNeighbors(): Map<Char, Set<Char>> {
            val layouts = listOf(
                listOf("1234567890", "qwertzuiopü", "asdfghjklöä", "yxcvbnmß"),
                listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm"),
            )
            val acc = HashMap<Char, HashSet<Char>>()
            for (rows in layouts) {
                val pos = HashMap<Char, Pair<Int, Int>>()
                rows.forEachIndexed { row, chars ->
                    chars.forEachIndexed { col, ch -> pos.putIfAbsent(ch, row to col) }
                }
                for ((ch, from) in pos) {
                    val set = acc.getOrPut(ch) { HashSet() }
                    for ((other, to) in pos) {
                        if (other == ch) continue
                        if (abs(from.first - to.first) <= 1 && abs(from.second - to.second) <= 1) set.add(other)
                    }
                }
            }
            return acc
        }
    }
}
