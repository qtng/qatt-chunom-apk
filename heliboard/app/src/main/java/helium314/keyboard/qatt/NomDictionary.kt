// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt

import java.io.Reader

/**
 * The Chữ Nôm dictionary: the candidates for a typed Quốc Ngữ word, in the order of `VietIme` of js/ime.js (qtng/chunom).
 * The data is assets/nom/nom.txt (tools/nom/build_nom.mjs): one line per entry, "reading TAB glyph glyph!…", a glyph with "!" has a definition.
 */
class NomDictionary {
    /** A candidate: the glyph(s), the reading of the dictionary entry ("chữ nôm") and whether the entry has a definition. */
    data class Candidate(val glyph: String, val reading: String, val defined: Boolean)

    private class Entry(val key: String, val glyph: String, val defined: Boolean)

    private val entries = HashMap<String, ArrayList<Entry>>()   // lower-case reading -> entries in the order of the data
    private var keys: List<String>? = null                      // sorted readings, built on demand
    private val pairs = HashSet<String>()

    /** Reads the data (see above); can be called more than once, entries already known are skipped. */
    fun load(reader: Reader): NomDictionary {
        reader.useLines { lines ->
            for (line in lines) {
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val key = line.substring(0, tab)
                val list = entries.getOrPut(key.lowercase()) { ArrayList() }
                for (g in line.substring(tab + 1).split(' ')) {
                    if (g.isEmpty()) continue
                    val defined = g.endsWith("!")
                    val glyph = if (defined) g.dropLast(1) else g
                    if (glyph.isEmpty() || !pairs.add("$key:$glyph")) continue
                    list.add(Entry(key, glyph, defined))
                }
            }
        }
        keys = null
        return this
    }

    val size get() = pairs.size

    private class Scored(val entry: Entry, val remainder: Int, val score: Int, val order: Int)

    /**
     * The entries whose reading starts with the (lower-case) prefix: short remainders first, then used and defined ones, then the order of the data.
     * [usage]: how often each glyph was chosen (the website counts this in the browser); a defined entry counts double.
     */
    fun candidates(prefix: String, usage: Map<String, Int> = emptyMap(), limit: Int = 100): List<Candidate> {
        val sorted = keys ?: entries.keys.sorted().also { keys = it }
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < prefix) lo = mid + 1 else hi = mid
        }
        val list = ArrayList<Scored>()
        val seen = HashSet<String>()
        var i = lo
        while (i < sorted.size && sorted[i].startsWith(prefix)) {
            for (e in entries.getValue(sorted[i])) {
                if (!seen.add(e.glyph)) continue
                val used = usage[e.glyph] ?: 0
                list.add(Scored(e, sorted[i].length - prefix.length, if (e.defined) (used + 1) * 2 else used, list.size))
            }
            i++
        }
        return list.sortedWith(compareBy<Scored> { it.remainder }.thenByDescending { it.score }.thenBy { it.order })
            .take(limit).map { Candidate(it.entry.glyph, readingOf(it.entry.key), it.entry.defined) }
    }

    /** The candidates for a word as typed (spaces, hyphens and apostrophes are ignored; "chữ nôm" finds the entry chữNôm). */
    fun lookup(word: String, usage: Map<String, Int> = emptyMap(), limit: Int = 100): List<Candidate> {
        val w = word.lowercase().replace(Regex("[\\s\\-']"), "")
        return if (w.isEmpty()) emptyList() else candidates(w, usage, limit)
    }

    companion object {
        private val UPPER = Regex("(\\p{Lu})")
        /** "chữNôm" -> "chữ nôm" */
        fun readingOf(key: String) = UPPER.replace(key, " $1").trim().lowercase()
    }
}
