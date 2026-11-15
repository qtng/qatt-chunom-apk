// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt

import java.io.Reader
import java.text.Normalizer

/**
 * The Chữ Nôm dictionary: the candidates for a typed Quốc Ngữ word, in the order of `VietIme` of js/ime.js (qtng/chunom).
 * The data is assets/nom/nom.txt (tools/nom/build_nom.mjs): one line per entry, "reading TAB glyph glyph!…", a glyph with "!" has a definition.
 */
class NomDictionary {
    /** A candidate: the glyph(s), the reading of the dictionary entry ("chữ nôm") and whether the entry has a definition. */
    data class Candidate(val glyph: String, val reading: String, val defined: Boolean)

    private class Entry(val key: String, val glyph: String, val defined: Boolean, val order: Int)

    /**
     * The search key of a reading, as SearchKey of js/ime.js: [fold] the letters without any mark ("đ" counts as "d"), [letters] each letter without its tone,
     * [syl] the syllable of each letter (a new one starts at a capital: "chữNôm"), [tones] the tone of each syllable (0 ngang, 1 sắc, 2 huyền, 3 hỏi, 4 ngã, 5 nặng),
     * [toneAt] the typed tones (letter, tone), [sort] the letters, then the marks in the order of the alphabet, then the tones.
     */
    private class Key(val fold: String, val letters: List<String>, val syl: IntArray, val tones: IntArray, val toneAt: List<Pair<Int, Int>>, val sort: String)

    private val byFold = HashMap<String, ArrayList<Entry>>()   // letters without marks -> entries in the order of the data
    private val info = HashMap<String, Key>()                   // reading -> its key
    private var keys: List<String>? = null                      // the sorted keys of byFold, built on demand
    private val pairs = HashSet<String>()

    /** Reads the data (see above); can be called more than once, entries already known are skipped. */
    fun load(reader: Reader): NomDictionary {
        reader.useLines { lines ->
            for (line in lines) {
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val key = Normalizer.normalize(line.substring(0, tab), Normalizer.Form.NFC)
                val k = info.getOrPut(key) { searchKey(key) }
                val list = byFold.getOrPut(k.fold) { ArrayList() }
                for (g in line.substring(tab + 1).split(' ')) {
                    if (g.isEmpty()) continue
                    val defined = g.endsWith("!")
                    val glyph = if (defined) g.dropLast(1) else g
                    if (glyph.isEmpty() || !pairs.add("$key:$glyph")) continue
                    list.add(Entry(key, glyph, defined, pairs.size))
                }
            }
        }
        keys = null
        return this
    }

    val size get() = pairs.size

    private class Scored(val entry: Entry, val exact: Boolean, val rest: Int, val score: Int, val sort: String)

    /**
     * The entries that match the typed prefix, as js/ime.js: found by the letters without marks ("nuoc" finds "nước", "d" finds "đ"); a typed mark must be on
     * the same letter, a typed tone in the same syllable (wherever it is written). First those written exactly as typed so far, then short remainders, then used
     * and defined ones, then the order of the readings (letters, marks, tones) and of the data; every glyph once.
     * [usage]: how often each glyph was chosen (the website counts this in the browser); a defined entry counts double.
     */
    fun candidates(prefix: String, usage: Map<String, Int> = emptyMap(), limit: Int = 100): List<Candidate> {
        val sorted = keys ?: byFold.keys.sorted().also { keys = it }
        val t = searchKey(prefix.lowercase())
        val n = t.letters.size
        if (n == 0) return emptyList()
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < t.fold) lo = mid + 1 else hi = mid
        }
        val list = ArrayList<Scored>()
        var i = lo
        while (i < sorted.size && sorted[i].startsWith(t.fold)) {
            for (e in byFold.getValue(sorted[i])) {
                val k = info.getValue(e.key)
                var ok = true
                var exact = true
                for (j in 0 until n) {
                    if (k.letters[j] != t.letters[j]) { exact = false; if (t.letters[j] != t.fold[j].toString()) { ok = false; break } }   // a typed mark must be there
                }
                if (!ok) continue
                val typedTone = HashMap<Int, Int>()
                for ((j, tone) in t.toneAt) typedTone[k.syl[j]] = tone
                if (typedTone.any { (s, tone) -> k.tones[s] != tone }) continue                                // a typed tone must be there
                if (exact) for (s in 0..k.syl[n - 1]) if ((typedTone[s] ?: 0) != k.tones[s]) { exact = false; break }
                val used = usage[e.glyph] ?: 0
                list.add(Scored(e, exact, k.letters.size - n, if (e.defined) (used + 1) * 2 else used, k.sort))
            }
            i++
        }
        val seen = HashSet<String>()
        return list.sortedWith(compareByDescending<Scored> { it.exact }.thenBy { it.rest }.thenByDescending { it.score }.thenBy { it.sort }.thenBy { it.entry.order })
            .filter { seen.add(it.entry.glyph) }
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

        private val TONE = mapOf('\u0301' to 1, '\u0300' to 2, '\u0309' to 3, '\u0303' to 4, '\u0323' to 5)
        private val VARIANT = mapOf("ă" to 1, "â" to 2, "đ" to 1, "ê" to 1, "ô" to 1, "ơ" to 2, "ư" to 1)

        private fun searchKey(text: String): Key {
            val fold = StringBuilder()
            val marks = StringBuilder()
            val letters = ArrayList<String>()
            val syl = ArrayList<Int>()
            val tones = arrayListOf(0)
            val toneAt = ArrayList<Pair<Int, Int>>()
            var s = 0
            var at = 0
            val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
            while (at < nfc.length) {
                val cp = nfc.codePointAt(at)
                val ch = String(Character.toChars(cp))
                at += Character.charCount(cp)
                if (letters.isNotEmpty() && Character.isUpperCase(cp)) { s++; tones.add(0) }
                val d = Normalizer.normalize(ch, Normalizer.Form.NFD)
                val baseLen = Character.charCount(d.codePointAt(0))
                val base = d.substring(0, baseLen)
                val mods = StringBuilder()
                var tone = 0
                for (c in d.substring(baseLen)) { val t = TONE[c]; if (t != null) tone = t else mods.append(c) }
                val letter = Normalizer.normalize(base + mods, Normalizer.Form.NFC).lowercase()
                if (tone != 0) { tones[s] = tone; toneAt.add(letters.size to tone) }
                fold.append(if (letter == "đ") "d" else base.lowercase())
                marks.append(VARIANT[letter] ?: 0)
                letters.add(letter)
                syl.add(s)
            }
            return Key(fold.toString(), letters, syl.toIntArray(), tones.toIntArray(), toneAt, "$fold\u0000$marks\u0000${tones.joinToString("")}")
        }
    }
}
