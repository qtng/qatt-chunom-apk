// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

/**
 * Telex as `VietIme.telex` of the website (js/ime.js of qtng/chunom, itself a port of VietUni): one key is applied to the TEXT
 * that is there (the word before the cursor), not to a list of raw keys. So there is no state that can get out of step with the
 * editor: whatever the text is (typed now, typed an hour ago, written by another keyboard), the next key works on it.
 *
 * [key] returns the new text, or null if the key is to be typed as it is. Golden tests compare it with ime.js (see docs/port.md).
 * Only the full Telex mode (s f j r x z a e o w d) is ported, not the "compound" mode.
 */
object TelexKey {
    // 12 vowels (a â ă e ê i o ô ơ u ư y), lower and upper case, in 6 tone sets (none, sắc, huyền, nặng, hỏi, ngã), then d đ D Đ. Index 0 is unused.
    private val VIET = intArrayOf(0,
        97, 226, 259, 101, 234, 105, 111, 244, 417, 117, 432, 121, 65, 194, 258, 69, 202, 73, 79, 212, 416, 85, 431, 89,
        225, 7845, 7855, 233, 7871, 237, 243, 7889, 7899, 250, 7913, 253, 193, 7844, 7854, 201, 7870, 205, 211, 7888, 7898, 218, 7912, 221,
        224, 7847, 7857, 232, 7873, 236, 242, 7891, 7901, 249, 7915, 7923, 192, 7846, 7856, 200, 7872, 204, 210, 7890, 7900, 217, 7914, 7922,
        7841, 7853, 7863, 7865, 7879, 7883, 7885, 7897, 7907, 7909, 7921, 7925, 7840, 7852, 7862, 7864, 7878, 7882, 7884, 7896, 7906, 7908, 7920, 7924,
        7843, 7849, 7859, 7867, 7875, 7881, 7887, 7893, 7903, 7911, 7917, 7927, 7842, 7848, 7858, 7866, 7874, 7880, 7886, 7892, 7902, 7910, 7916, 7926,
        227, 7851, 7861, 7869, 7877, 297, 245, 7895, 7905, 361, 7919, 7929, 195, 7850, 7860, 7868, 7876, 296, 213, 7894, 7904, 360, 7918, 7928,
        100, 273, 68, 272)
    private const val LAST_VOWEL = 144
    private const val LENGTH = 149
    private const val KEYS = "sfjrxzaeowd"
    private val ACTIONS = intArrayOf(1, 2, 3, 4, 5, 6, 9, 10, 11, 8, 15)
    // [typ, from, to] vowel changes: â ă ê ô ơ ư đ
    private val VMAP = arrayOf(
        intArrayOf(7, 7, 7, 8, 8, 8, 9, 10, 11, 15),
        intArrayOf(0, 3, 6, 0, 6, 9, 0, 3, 6, 0),
        intArrayOf(1, 4, 7, 2, 8, 10, 1, 4, 7, 1))

    private fun vietChar(i: Int): String? = if (i in 1 until LENGTH) VIET[i].toChar().toString() else null

    private fun isVowel(chr: String): Int {
        var i = LAST_VOWEL
        while (chr != vietChar(i) && i != 0) i--
        return i
    }

    private fun isVD(chr: String): Int {
        var i = LAST_VOWEL
        while (chr != vietChar(i) && i < LENGTH) i++
        return if (i < LENGTH) i else 0
    }

    private fun base(i: Int) = (i - 1) % 12

    /** the indexes of the two vowels if they are one of [pairs] (base vowels), else null */
    private fun vowelPair(c1: String, c2: String, pairs: Array<IntArray>): IntArray? {
        if (c1.isEmpty() || c2.isEmpty()) return null
        val i1 = isVowel(c1)
        val i2 = isVowel(c2)
        return if (pairs.any { base(i1) == it[0] && base(i2) == it[1] }) intArrayOf(i1, i2) else null
    }

    private val PAIRS_AYH = arrayOf(intArrayOf(0, 9), intArrayOf(0, 11), intArrayOf(9, 9), intArrayOf(9, 0), intArrayOf(9, 5), intArrayOf(6, 5))
    private val PAIRS_AE = arrayOf(intArrayOf(0, 3), intArrayOf(3, 5), intArrayOf(6, 9))
    private val PAIRS_UO = arrayOf(intArrayOf(9, 6), intArrayOf(9, 7), intArrayOf(9, 8), intArrayOf(10, 6), intArrayOf(10, 7), intArrayOf(10, 8))
    private fun isAyhAuhUuhUahUihOih(c1: String, c2: String) = vowelPair(c1, c2, PAIRS_AYH)
    private fun isAeEiOu(c1: String, c2: String) = vowelPair(c1, c2, PAIRS_AE)
    private fun isUO(c1: String, c2: String) = vowelPair(c1, c2, PAIRS_UO)

    private class Change(val changed: Boolean, val text: String, val keepKey: Boolean)

    // tone: changed, new char, whether the key is used up
    private fun getDau(ind: Int, typ: Int): Change {
        val accented = ind >= 25
        val indI = (ind - 1) % 24 + 1
        if (typ == 6 && !accented) return Change(false, "", false)
        var newInd = (if (typ == 6) 0 else typ) * 24 + indI
        if (newInd == ind) newInd = indI
        return Change(true, vietChar(newInd) ?: "", newInd > 24 || typ == 6)
    }

    // â ă ê ô ơ ư đ, also uô / ươ
    private fun getAEOWD(ind: Int, ind2: Int, typ: Int, isuo: Boolean): Change {
        val i1 = ind
        val vc1 = if (typ == 15) (i1 - 1) % 2 else (i1 - 1) % 12
        if (isuo) {
            val b = ind2 - (ind2 - 1) % 12
            var c: String? = null
            if (typ == 7 || typ == 11) c = (vietChar(i1 - vc1 + 9) ?: "null") + (vietChar(b + 7) ?: "null")
            else if (typ == 8) c = (vietChar(i1 - vc1 + 10) ?: "null") + (vietChar(b + 8) ?: "null")
            return Change(c != null, c ?: "", true)
        }
        var shif = 0
        val del: Boolean
        var i = 0
        while (shif == 0 && i < VMAP[0].size) {
            if (VMAP[0][i] == typ) {
                if (VMAP[1][i] == vc1) shif = VMAP[2][i] - vc1
                else if (VMAP[2][i] == vc1) shif = VMAP[1][i] - vc1
            }
            i++
        }
        if (shif == 0) {
            if (typ == 7 && (vc1 == 2 || vc1 == 8)) shif = -1
            else if ((typ == 9 && vc1 == 2) || (typ == 11 && vc1 == 8)) shif = -1
            else if (typ == 8 && (vc1 == 1 || vc1 == 7)) shif = 1
            del = true
        } else del = shif > 0
        return Change(shif != 0, vietChar(i1 + shif) ?: "", del)
    }

    /** vowel index (and the second one of a uo pair), position, length, uo pair; [backslash]: the "\" before the cursor */
    private class Info(val chr: Int, val chr2: Int, val pos: Int, val len: Int, val isuo: Boolean, val backslash: Boolean = false)

    private fun findCharToChange(value: String, typ: Int): Info? {
        val last = Array(5) { k -> val ix = value.length - k - 1; if (ix >= 0 && ix < value.length) value[ix].toString() else "" }
        var i = 0
        var c = last[0]
        var chr = 0
        if (c == "\\") return Info(0, 0, value.length - 1, 1, false, true)

        if (typ == 15) {
            chr = isVD(c)
            if (chr == 0) return null
        } else {
            while ("cghmnptCGHMNPT".indexOf(c) >= 0) {
                if (c < "A" || i >= 3) return null
                c = last[++i]
                if (c.isEmpty()) return null
            }
        }
        c = last[0].lowercase()
        val pc = last[1].lowercase()
        val ppc = last[2].lowercase()
        val pppc = last[3].lowercase()

        if (i > 0 && typ > 8) return null
        if (i > 0) {
            if (c == "h" && isUO(pppc, ppc) != null) {
                i += 1
            } else if (c == "h" && isAyhAuhUuhUahUihOih(ppc, pc) != null) {
                i += 1
                chr = isVowel(ppc)
            } else if (isAeEiOu(ppc, pc) != null) {
                i += 1
                chr = isVowel(ppc)
            }
        } else if (typ != 15) {
            if (isAeEiOu(ppc, pc) != null) {
                i += 2
                chr = isVowel(ppc)
            } else if (isAeEiOu(pc, c) != null) {
                i++
            } else {
                chr = isVowel(last[1])
                if (chr != 0 && "uyoia".indexOf(c) >= 0 && isUO(pc, c) == null && !((pc == "o" && c == "a") || (pc == "u" && c == "y"))
                    && !((ppc == "q" && pc == "u") || (ppc == "g" && pc == "i"))) i++
            }
            if (c == "a" && (typ == 9 || typ == 7)) i = 0
        }
        c = last[i]
        if ((i == 0 || chr == 0) && typ != 15) chr = isVowel(c)
        if (chr == 0) return null

        var len = 1
        var chr2 = 0
        var isuo = false
        if (i > 0 && (typ == 7 || typ == 8 || typ == 11)) {
            if (i + 1 >= last.size) return null
            val uo = isUO(last[i + 1], c)
            if (uo != null) {
                chr = uo[0]; chr2 = uo[1]
                len++
                i++
                isuo = true
            }
        }
        return Info(chr, chr2, value.length - i - 1, len, isuo)
    }

    private val GN = Regex("[^\\u0001-\\u007f](gn)$", RegexOption.IGNORE_CASE)

    /** Applies the key [key] (one character) to the text before the cursor; returns the new text, or null if the key is typed as it is. */
    fun key(value0: String, key: String): String? {
        var value = value0
        if (key.length != 1 || key[0].code < 49 || value.isEmpty() || value.last().isWhitespace()) return null
        var changed = false
        val at = KEYS.indexOf(key.lowercase())
        val action = if (at >= 0) ACTIONS[at] else 0
        if (action != 0) {
            val info = findCharToChange(value, action)
            if (info != null && (info.backslash || info.chr != 0)) {
                val t = when {
                    info.backslash -> Change(true, key, true)
                    action > 6 -> getAEOWD(info.chr, info.chr2, action, info.isuo)
                    else -> getDau(info.chr, action)
                }
                changed = t.changed
                if (changed) value = value.substring(0, info.pos) + t.text + value.substring(minOf(info.pos + info.len, value.length)) + (if (t.keepKey) "" else key)
            }
        }
        // "gn" after a Vietnamese vowel becomes "ng"
        val v = if ("nNcC".indexOf(key) >= 0) value + key else value
        val er = GN.find(v)
        if (er != null) return v.substring(0, v.length - 2) + er.groupValues[1][1] + er.groupValues[1][0]
        return if (changed) value else null
    }

    /** A whole word typed in Telex, key by key: "nguwowif" -> "người". */
    fun word(raw: String): String {
        var out = ""
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            val ch = String(Character.toChars(cp))
            i += Character.charCount(cp)
            out = key(out, ch) ?: (out + ch)
        }
        return out
    }
}
