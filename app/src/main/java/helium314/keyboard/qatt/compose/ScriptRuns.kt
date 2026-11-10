// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

enum class Script { QATT, PA, CJK, OTHER }

/** Splits a text into runs of one script, to draw each with its font: QATT (U+F0000..U+F00A3), PA (U+F00B0..U+F00EE), CJK (Chữ Nôm, Han, ideographic punctuation) and the rest. */
object ScriptRuns {
    data class Run(val start: Int, val end: Int, val script: Script)

    fun scriptOf(cp: Int): Script = when (cp) {
        in 0xF0000..0xF00A3 -> Script.QATT
        in 0xF00B0..0xF00EE -> Script.PA
        in 0x2E80..0x9FFF, in 0xF900..0xFAFF, in 0xFE30..0xFE4F, in 0xFF00..0xFFEF, in 0x20000..0x3FFFF -> Script.CJK
        else -> Script.OTHER
    }

    /** The runs of [text] (UTF-16 indices), neighbours of the same script are one run. */
    fun split(text: CharSequence): List<Run> {
        val runs = ArrayList<Run>()
        var i = 0
        var start = 0
        var current: Script? = null
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            val s = scriptOf(cp)
            if (s != current) {
                if (current != null) runs.add(Run(start, i, current))
                start = i
                current = s
            }
            i += Character.charCount(cp)
        }
        if (current != null) runs.add(Run(start, text.length, current))
        return runs
    }
}
