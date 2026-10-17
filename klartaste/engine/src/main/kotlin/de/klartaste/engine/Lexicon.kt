package de.klartaste.engine

import java.util.Locale

class Lexicon(entries: List<Entry>) {
    class Entry(val form: String, val freq: Int) {
        val key: String = form.lowercase(Locale.GERMAN)
    }

    private val byKey: Map<String, Entry>
    private val sorted: List<Entry>

    val size: Int

    init {
        val merged = LinkedHashMap<String, Entry>()
        for (entry in entries) {
            val prev = merged[entry.key]
            if (prev == null || entry.freq > prev.freq) merged[entry.key] = entry
        }
        byKey = merged
        sorted = merged.values.sortedBy { it.key }
        size = sorted.size
    }

    fun exact(key: String): Entry? = byKey[key]

    fun hasPrefix(prefix: String): Boolean {
        val index = lowerBound(prefix)
        return index < sorted.size && sorted[index].key.startsWith(prefix)
    }

    fun completions(prefix: String, limit: Int): List<Entry> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        val index = lowerBound(prefix)
        val found = ArrayList<Entry>()
        var i = index
        while (i < sorted.size && sorted[i].key.startsWith(prefix)) {
            found.add(sorted[i])
            i++
        }
        found.sortByDescending { it.freq }
        return if (found.size <= limit) found else found.subList(0, limit)
    }

    private fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].key < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        fun parse(text: String): Lexicon {
            val entries = ArrayList<Entry>()
            for (line in text.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val tab = trimmed.indexOf('\t')
                if (tab <= 0) continue
                val form = trimmed.substring(0, tab).trim()
                val freq = trimmed.substring(tab + 1).trim().toIntOrNull() ?: continue
                if (form.isNotEmpty()) entries.add(Entry(form, freq))
            }
            return Lexicon(entries)
        }
    }
}
