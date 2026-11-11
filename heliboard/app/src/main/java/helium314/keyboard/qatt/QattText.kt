// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt

/**
 * Port of `QattText` of js/qatt-text.js (qtng/qatt): QATT code -> text for the QATT fonts (code points from U+F0000).
 * Indices are UTF-16 indices, as in JavaScript.
 */
object QattText {
    private const val BASE = 0xF0000
    private const val JOINER = BASE + 154
    private const val COMBINER = BASE + 155
    /** shape i: U+F0000 + 7 * i, with marking stroke n (1..6): U+F0000 + 7 * i + n */
    private val SHAPES = "_ ng h g c l tr d n t th nh ch dz x kh s r m b v ph".split(" ")
    /** tone 1..8 -> offset of the tone mark */
    private val TONES = listOf(null, 160, 157, 158, 159, 161, 162, 163, 156)
    private fun chr(o: Int) = String(Character.toChars(BASE + o))

    private fun letter(shape: String, mark: Int): String? {
        val i = SHAPES.indexOf(shape)
        return if (i < 0) null else chr(7 * i + mark)
    }

    private val PART = Regex("^(_|[a-z]+?)([1-6]?)$")

    /** "onset,rhyme,,tone" part: a shape and a marking stroke (none: the plain shape). null if it is none. */
    private fun split(part: String): String? {
        val m = PART.find(part.ifEmpty { "_" }) ?: return null
        return letter(m.groupValues[1], m.groupValues[2].toIntOrNull() ?: 0)
    }

    /** The cell for a code of the parser ("t,c5,,5": onset, rhyme, -, tone); the onset may carry a marking stroke too (Hợp Thư: "v2,v,,0"). */
    fun syllable(code: String): String? {
        val parts = code.split(",")
        val onset = parts.getOrElse(0) { "" }
        val rhyme = parts.getOrNull(1)
        val on = split(onset) ?: return null
        val rh: String = if (rhyme == "") "" else (split(rhyme ?: "") ?: return null)   // no rhyme: the shape alone (the bare consonant, or with stroke 1 ông)
        val t = TONES.getOrNull(parts.getOrNull(3)?.toIntOrNull() ?: 0)
        // onset and rhyme of the same shape (mô = m + m2): the marked shape alone is the whole syllable
        val sameShape = !rhyme.isNullOrEmpty() && Regex("^\\D+[2-6]$").matches(rhyme) && !onset.any { it.isDigit() } && rhyme.replace(Regex("\\d$"), "") == onset
        if (sameShape) return rh + (t?.let { chr(it) } ?: "")
        return on + (if (rh.isNotEmpty()) chr(JOINER - BASE) + rh else "") + (t?.let { chr(it) } ?: "")
    }

    /** A Quốc Ngữ word as a cell, or null if there is no glyph for it. */
    fun word(w: String, parser: VietnameseParser): String? {
        val parsed = parser.parse(w) ?: return null
        val code = parsed.qatt ?: parsed.hop
        return code?.let { syllable(it) }
    }

    private const val L = "[\\x{F0000}-\\x{F0099}]"
    /** the cells of QATT text: a letter of the block, joined or stacked letters, the tone marks after them (what the font draws as one square) */
    val CELL = Regex("$L(?:[\\x{F009A}\\x{F009B}]$L)*[\\x{F009C}-\\x{F00A3}]*")
    /** every character is part of some cell: the cells, a lone joiner, combiner or tone mark, any other character on its own */
    val CELL_ANY = Regex(CELL.pattern + "|[\\x{F009A}-\\x{F00A3}]+|(?s:.)")

    data class Cell(val cell: String, val index: Int)

    /** the cells of QATT text in the order they occur */
    fun cells(text: String): List<Cell> = CELL.findAll(text).map { Cell(it.value, it.range.first) }.toList()

    /**
     * A cell as the parts of the syllable (the reverse of [syllable]) or null (a cell that is no syllable, e.g. a stack with the combiner).
     * hop: "ông" (the form with stroke 1 alone), "ong" "ung" "uông" (Hợp Thư pairs).
     */
    fun decode(cell: String): CellParts? {
        val cps = cell.codePointList().map { it - BASE }
        fun glyph(o: Int?): Pair<String, Int>? = if (o != null && o in 0 until 154) SHAPES[o / 7] to o % 7 else null
        val first = glyph(cps.getOrNull(0)) ?: return null
        var i = 1
        var second: Pair<String, Int>? = null
        var joined = false
        if (cps.getOrNull(1) == JOINER - BASE) { second = glyph(cps.getOrNull(2)) ?: return null; joined = true; i = 3 }
        else if (cps.getOrNull(1) == COMBINER - BASE) return null
        var tone = 0
        while (i < cps.size) { val t = TONES.indexOf(cps[i]); if (t < 0) return null; tone = t; i++ }
        fun code(g: Pair<String, Int>) = g.first + (if (g.second != 0) g.second.toString() else "")
        if (!joined) {   // the form with stroke 1 alone is ông, m2 alone is m + m2, a plain shape alone is the bare consonant
            if (first.second == 1) return CellParts(first.first, "", tone, "ông")
            return if (first.second != 0) CellParts(first.first, code(first), tone) else CellParts(first.first, "", tone)
        }
        val sec = second!!
        if (first.second != 0 && sec.second == 0 && first.first == sec.first)
            return CellParts(first.first, "", tone, mapOf(2 to "ong", 3 to "ung", 4 to "uông")[first.second])
        if (first.second != 0) return null
        return CellParts(first.first, code(sec), tone)
    }

    /** The Quốc Ngữ reading of a cell with the tables of the parser's decoding, "?" if there is none. */
    fun reading(cell: String, parser: VietnameseParser): String {
        val parts = decode(cell) ?: return "?"
        return parser.toQuocNgu(parts)?.ifEmpty { null } ?: "?"
    }

    /** the readings of all cells of a text: cell -> reading (every cell once) */
    fun readings(text: String, parser: VietnameseParser): Map<String, String> = cells(text).associate { it.cell to reading(it.cell, parser) }

    val WORD = Regex("[\\p{L}\\p{M}]+")
    /** every Quốc Ngữ word in a text as a cell, the rest stays as it is */
    fun convert(text: String, parser: VietnameseParser): String = WORD.replace(text) { word(it.value, parser) ?: it.value }

    /** every cell is read after one decoding and written again after another one; a cell without a reading or a glyph in `to` stays */
    fun recode(text: String, from: VietnameseParser, to: VietnameseParser): String = CELL.replace(text) { m ->
        val read = reading(m.value, from)
        (if (read != "?") word(read, to) else null) ?: m.value
    }

    data class Repaired(val value: String, val caret: Int)

    /**
     * The text before and after an edit that only deleted characters, the caret after it (where the deleted part was): when the deletion cut into a cell,
     * the whole cell goes. null if nothing has to be repaired or the edit is no pure deletion.
     */
    fun repairDeletion(before: String, after: String, caret: Int): Repaired? {
        val length = before.length - after.length
        if (length <= 0 || caret < 0 || caret + length > before.length || before.substring(0, caret) + before.substring(caret + length) != after) return null
        var start = caret
        var end = caret + length
        for (m in CELL_ANY.findAll(before)) {
            val a = m.range.first
            val z = a + m.value.length
            if (a < caret && caret < z) start = a
            if (a < caret + length && caret + length < z) end = z
        }
        return if (start == caret && end == caret + length) null else Repaired(before.substring(0, start) + before.substring(end), start)
    }
}
