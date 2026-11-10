// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import java.text.Normalizer

/**
 * Telex for Vietnamese: the keys typed for one word -> the word.
 *
 * A pure function of the raw keys ([convert]), so that a keyboard can keep the raw keys of the word and convert them again after
 * every key: Backspace removes the last key, and typing a modifier twice undoes it.
 *
 *     aa aw ee oo ow uw dd    â ă ê ô ơ ư đ     (w alone: ư; uow: ươ; w may come after the final consonant: duongw)
 *     s f r x j z             sắc huyền hỏi ngã nặng, z removes the tone (anywhere in the syllable after the vowels)
 *
 * Typing the same modifier a third time undoes it (aaa -> aa, ass -> as), and that key is then literal in the rest of the word.
 * The tone mark goes where Vietnamese puts it: on the vowel with a mark (ê, ơ; the second of ươ), else on the first vowel of a
 * pair (hóa, mía, thủy) or on the second one if a consonant follows (toán), on the middle vowel of three (khoái).
 * With [modernStyle] the pairs oa, oe, uy take it on the second vowel (hoá, khoẻ, thuỷ).
 * A tone key is literal when the syllable cannot take it: after no vowel, or after c, ch, p, t (only sắc and nặng there): "text".
 */
object TelexEngine {
    private const val VOWELS = "aeiouy"
    private const val CIRC = 1; private const val BREVE = 2; private const val HORN = 3; private const val STROKE = 4

    private class Ch(var base: Char, var mod: Int, val upper: Boolean, var key: Char = ' ', val lone: Boolean = false)

    private class State(val modernStyle: Boolean) {
        val chars = ArrayList<Ch>()
        var tone = 0                        // 0 none, 1 sắc, 2 huyền, 3 hỏi, 4 ngã, 5 nặng
        var toneKey = ' '
        val literal = HashSet<Char>()       // keys that were undone: literal for the rest of the word

        fun isVowel(i: Int): Boolean {
            val c = chars[i]
            if (c.base !in VOWELS || c.mod == STROKE) return false
            // qu: the u is part of the consonant; gi: the i is, if a vowel follows
            if (c.base == 'u' && i > 0 && chars[i - 1].base == 'q' && chars[i - 1].mod == 0) return false
            if (c.base == 'i' && i > 0 && chars[i - 1].base == 'g' && chars[i - 1].mod == 0 && i + 1 < chars.size
                && chars[i + 1].base in VOWELS) return false
            return true
        }

        /** The last run of vowels as an index range, or null. */
        fun nucleus(): IntRange? {
            var end = chars.size - 1
            while (end >= 0 && !isVowel(end)) end--
            if (end < 0) return null
            var start = end
            while (start > 0 && isVowel(start - 1)) start--
            return start..end
        }

        fun coda(): String {
            val n = nucleus() ?: return ""
            return chars.subList(n.last + 1, chars.size).joinToString("") { it.base.toString() }
        }

        fun canTakeTone(t: Int): Boolean {
            if (nucleus() == null) return false
            val coda = coda()
            if (coda.isEmpty() || coda in setOf("m", "n", "ng", "nh")) return true
            if (coda in setOf("c", "ch", "p", "t")) return t == 1 || t == 5 || t == 0
            return false
        }

        fun type(k: Char) {
            val lower = k.lowercaseChar()
            val upper = k.isUpperCase()
            if (lower in literal) return add(k)
            when (lower) {
                's', 'f', 'r', 'x', 'j', 'z' -> {
                    val t = "sfrxj".indexOf(lower) + 1       // z -> 0
                    if (lower == 'z') {
                        if (tone != 0 && canTakeTone(0)) { tone = 0; toneKey = ' ' } else add(k)
                    } else if (tone == t && toneKey == lower) {   // the same key again: undo
                        tone = 0; toneKey = ' '; literal.add(lower); add(k)
                    } else if (canTakeTone(t)) { tone = t; toneKey = lower }
                    else add(k)
                }
                'a', 'e', 'o' -> {
                    val last = chars.lastOrNull()
                    if (last != null && last.base == lower && last.key != ' ' && last.mod == CIRC && last.key == lower) {
                        last.mod = 0; last.key = ' '; literal.add(lower); add(k)            // undo: aaa -> aa
                    } else if (last != null && last.base == lower && last.mod == 0 && isVowel(chars.size - 1)) {
                        last.mod = CIRC; last.key = lower
                    } else add(k)
                }
                'd' -> {
                    val first = chars.firstOrNull()
                    if (first != null && first.base == 'd' && first.mod == STROKE && first.key == 'd' && chars.size == 1) {
                        first.mod = 0; first.key = ' '; literal.add('d'); add(k)        // ddd -> dd
                    } else if (first != null && first.base == 'd' && first.mod == 0
                        && (1 until chars.size).all { isVowel(it) }) {                    // dd, did -> đi
                        first.mod = STROKE; first.key = 'd'
                    } else add(k)
                }
                'w' -> {
                    val n = nucleus()
                    var done = false
                    if (n != null) {
                        val plain = { i: Int -> chars[i].mod == 0 }
                        val u = n.firstOrNull { chars[it].base == 'u' && plain(it) }
                        val o = n.firstOrNull { chars[it].base == 'o' && plain(it) }
                        val horned = { i: Int -> chars[i].base == 'u' && chars[i].mod == HORN }
                        if (o != null && o > n.first && horned(o - 1)) {                         // ưo -> ươ
                            chars[o].mod = HORN; chars[o].key = 'w'; done = true
                        } else if (u != null && o != null && o == u + 1) {                       // uo -> ươ
                            chars[u].mod = HORN; chars[u].key = 'w'; chars[o].mod = HORN; chars[o].key = 'w'; done = true
                        } else {
                            val t = n.lastOrNull { chars[it].base in "aou" && plain(it) }
                            if (t != null) {
                                chars[t].mod = if (chars[t].base == 'a') BREVE else HORN; chars[t].key = 'w'; done = true
                            } else {
                                val marked = n.filter { chars[it].key == 'w' }                     // all done already: undo
                                if (marked.isNotEmpty()) {
                                    // ww: the ư made by a lone w becomes the w again and the second w is not added (Unikey: ww -> w), else the marks are taken off
                                    val lone = marked.firstOrNull { chars[it].lone }
                                    if (lone != null) { val up = chars[lone].upper; chars.removeAt(lone); add(if (up) 'W' else 'w') }
                                    else { for (i in marked) { chars[i].mod = 0; chars[i].key = ' ' }; add(k) }
                                    literal.add('w'); done = true
                                }
                            }
                        }
                    } else if (chars.none { it.base in VOWELS }) {
                        chars.add(Ch('u', HORN, upper, 'w', lone = true)); done = true                  // w alone: ư
                    }
                    if (!done) add(k)
                }
                else -> add(k)
            }
        }

        fun add(k: Char) { chars.add(Ch(k.lowercaseChar(), 0, k.isUpperCase())) }

        fun result(): String {
            val toneAt = toneIndex()
            val sb = StringBuilder()
            for ((i, c) in chars.withIndex()) {
                var s = when {
                    c.mod == STROKE -> "đ"
                    c.mod == CIRC -> compose(c.base, '̂')
                    c.mod == BREVE -> compose(c.base, '̆')
                    c.mod == HORN -> compose(c.base, '̛')
                    else -> c.base.toString()
                }
                if (i == toneAt && tone != 0) s = compose(s, "?̣́̀̉̃"[tone])
                sb.append(if (c.upper) s.uppercase() else s)
            }
            return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC)
        }

        private fun compose(base: Char, mark: Char) = Normalizer.normalize("$base$mark", Normalizer.Form.NFC)
        private fun compose(base: String, mark: Char) = Normalizer.normalize("$base$mark", Normalizer.Form.NFC)

        private fun toneIndex(): Int {
            val n = nucleus() ?: return -1
            val marked = n.filter { chars[it].mod != 0 }
            if (marked.isNotEmpty()) return marked.last()
            return when (n.count()) {
                1 -> n.first
                2 -> {
                    val pair = "" + chars[n.first].base + chars[n.last].base
                    val second = coda().isNotEmpty() || (modernStyle && pair in setOf("oa", "oe", "uy"))
                    if (second) n.last else n.first
                }
                else -> n.first + 1
            }
        }
    }

    /** The Vietnamese word for the Telex keys [raw]. */
    fun convert(raw: String, modernStyle: Boolean = false): String {
        val s = State(modernStyle)
        for (k in raw) {
            if (k.isLetter()) s.type(k) else s.add(k)
        }
        // a tone key typed before the final consonant that cannot take it (text: x, then t): not Vietnamese, the keys as typed
        if (s.tone !in intArrayOf(0, 1, 5) && s.coda() in setOf("c", "ch", "p", "t")) return raw
        return s.result()
    }
}
