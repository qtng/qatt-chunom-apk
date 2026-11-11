// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import helium314.keyboard.qatt.PaText
import helium314.keyboard.qatt.QattText

/**
 * The cells of a text: a QATT or PA cell with its joiners and tone marks is one cell, any other character (a surrogate pair is one character) too.
 * The cursor of the composition field and the line breaks of the image never fall inside a cell.
 */
object Cells {
    /** Is the character a glyph of QATT, PA or Chữ Nôm / Han (not a Latin letter)? */
    fun isGlyph(codePoint: Int) = ScriptRuns.scriptOf(codePoint) != Script.OTHER

    /** The length (in UTF-16 units) of the cell that starts at [i]. */
    private fun cellLength(text: String, i: Int): Int {
        val q = QattText.CELL_ANY.find(text, i)?.takeIf { it.range.first == i }?.value?.length ?: 0
        val p = PaText.CELL_ANY.find(text, i)?.takeIf { it.range.first == i }?.value?.length ?: 0
        return maxOf(q, p, Character.charCount(text.codePointAt(i)))
    }

    /** The borders between the cells, including 0 and the length of the text. */
    fun borders(text: CharSequence): IntArray {
        val s = text.toString()
        val out = ArrayList<Int>()
        var i = 0
        out.add(0)
        while (i < s.length) { i += cellLength(s, i); out.add(i) }
        return out.toIntArray()
    }

    fun split(text: String): List<String> = borders(text).let { b -> (1 until b.size).map { text.substring(b[it - 1], b[it]) } }

    /** The start of the cell that ends at or before [index] (the border before it). */
    fun startBefore(text: CharSequence, index: Int): Int = borders(text).lastOrNull { it < index } ?: 0

    /** The end of the cell that starts at or after [index]. */
    fun endAfter(text: CharSequence, index: Int): Int = borders(text).firstOrNull { it > index } ?: text.length

    /** The border nearest to [offset]; between two equally near ones, the lower. */
    fun snap(text: CharSequence, offset: Int): Int = borders(text).minByOrNull { Math.abs(it - offset) } ?: 0
}
