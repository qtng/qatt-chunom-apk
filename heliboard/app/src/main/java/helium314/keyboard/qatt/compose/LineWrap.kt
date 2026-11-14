// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

/**
 * The line breaks of the image (and of the field, which shows the same lines). A line holds at most a number of cells (a QATT or PA cell, a Chữ Nôm character,
 * a letter: one cell each). Lines are broken between words; Chữ Nôm and QATT are written without spaces, so a line may also be broken between two of their glyphs,
 * but never inside a run of Latin letters (a Quốc Ngữ word such as "khó"), and punctuation stays with the word before it (an opening bracket with the word after it).
 * Only a unit that is longer than a whole line is cut into pieces.
 */
object LineWrap {
    private const val OPENING = "([{“‘«‹「『（【"

    private fun isLetterCell(cell: String): Boolean {
        val cp = cell.codePointAt(0)
        if (Cells.isGlyph(cp)) return false
        return Character.isLetterOrDigit(cp) || Character.getType(cp) == Character.NON_SPACING_MARK.toInt()
    }

    /** The units of a word (a text without spaces) that a line may not be broken inside, each as its cells. */
    fun units(word: String): List<List<String>> {
        val units = ArrayList<ArrayList<String>>()
        var pendingOpening = ArrayList<String>()                 // opening brackets wait for the unit after them
        var lastWasLetter = false
        for (cell in Cells.split(word)) {
            val letter = isLetterCell(cell)
            when {
                letter && lastWasLetter && pendingOpening.isEmpty() -> units.last().add(cell)
                letter || Cells.isGlyph(cell.codePointAt(0)) -> { units.add(ArrayList(pendingOpening).apply { add(cell) }); pendingOpening = ArrayList() }
                cell in OPENING -> pendingOpening.add(cell)
                pendingOpening.isNotEmpty() -> pendingOpening.add(cell)
                units.isNotEmpty() -> units.last().add(cell)       // punctuation goes with what is before it
                else -> units.add(arrayListOf(cell))
            }
            lastWasLetter = letter
        }
        if (pendingOpening.isNotEmpty()) units.add(pendingOpening)
        return units
    }

    /** The lines: at most [perLine] cells each. */
    fun wrap(text: String, perLine: Int): List<String> {
        val lines = ArrayList<String>()
        for (paragraph in text.trimEnd().split('\n')) {
            var line = ArrayList<String>()
            fun flush() { lines.add(line.joinToString("")); line = ArrayList() }
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                units(word).forEachIndexed { i, unit ->
                    val space = i == 0 && line.isNotEmpty()           // between two words a space, between the units of one word none
                    if (line.isNotEmpty() && line.size + (if (space) 1 else 0) + unit.size > perLine) flush()
                    if (unit.size > perLine) {                       // longer than a line: cut into pieces
                        unit.chunked(perLine).forEachIndexed { k, chunk -> if (k > 0) flush(); line.addAll(chunk) }
                    } else {
                        if (i == 0 && line.isNotEmpty()) line.add(" ")
                        line.addAll(unit)
                    }
                }
            }
            flush()
        }
        return lines
    }
}
