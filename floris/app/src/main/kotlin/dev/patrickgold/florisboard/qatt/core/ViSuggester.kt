// SPDX-License-Identifier: Apache-2.0

package dev.patrickgold.florisboard.qatt.core

import java.io.Reader

/**
 * Completions for Vietnamese without composing text: the (at most [MAX_SYLLABLE]) letters before the cursor are the prefix, the words
 * that begin with it come from a list with frequencies (assets/vi/words.txt: the Vietnamese word list of the HeliBoard dictionary
 * repository, CC BY 4.0, plus the syllables of nom.txt with frequency 1). The longest Vietnamese syllable has seven letters.
 */
class ViSuggester {
    private var words = arrayOf<String>()          // lower case, sorted
    private var freq = IntArray(0)

    fun load(reader: Reader): ViSuggester {
        val list = ArrayList<Pair<String, Int>>()
        reader.forEachLine { line ->
            val tab = line.indexOf('\t')
            if (tab > 0) list.add(line.substring(0, tab) to (line.substring(tab + 1).toIntOrNull() ?: 0))
        }
        list.sortBy { it.first }
        words = Array(list.size) { list[it].first }
        freq = IntArray(list.size) { list[it].second }
        return this
    }

    /** The most frequent words that begin with [prefix] (the case of the prefix is kept: Việt, VIỆT), at most [limit]; the prefix itself is not among them. */
    fun complete(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty() || prefix.length > MAX_SYLLABLE) return emptyList()
        val p = prefix.lowercase()
        var lo = 0
        var hi = words.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (words[mid] < p) lo = mid + 1 else hi = mid }
        val found = ArrayList<Int>()
        var i = lo
        while (i < words.size && words[i].startsWith(p)) { if (words[i] != p) found.add(i); i++ }
        found.sortWith(compareByDescending<Int> { freq[it] }.thenBy { words[it].length })
        return found.take(limit).map { match(prefix, words[it]) }
    }

    /** whether [word] is in the list (e.g. to decide that the typed text needs no correction) */
    fun contains(word: String): Boolean = words.binarySearch(word.lowercase()) >= 0

    private fun match(prefix: String, word: String): String = when {
        prefix.length > 1 && prefix.all { !it.isLowerCase() } -> word.uppercase()
        prefix[0].isUpperCase() -> word.replaceFirstChar { it.uppercase() }
        else -> word
    }

    companion object {
        const val MAX_SYLLABLE = 7
    }
}
