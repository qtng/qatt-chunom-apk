// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

/**
 * Can the system show a text with its fonts? [hasGlyph] asks the system (`Paint.hasGlyph` on the default typeface, API 23; false below it).
 * Only characters of other scripts than Latin are asked (Chữ Nôm and other CJK, ideographic punctuation, the private use area of QATT and PA).
 * This tells what this device shows, not what the recipient's device shows.
 */
object GlyphSupport {
    /** One character each of QATT (U+F0007), PA (U+F00B2) and a rare Chữ Nôm character (𡦂, CJK Ext. B), for a field that is still empty. */
    fun probes(qatt: Boolean, pa: Boolean): List<String> =
        listOfNotNull(if (qatt) String(Character.toChars(0xF0007)) else null, if (pa) String(Character.toChars(0xF00B2)) else null, String(Character.toChars(0x219C2)))

    private fun needsCheck(cp: Int) = cp >= 0x2E80

    fun systemShows(text: String, hasGlyph: (String) -> Boolean): Boolean {
        val seen = HashSet<Int>()
        var i = 0
        while (i < text.length && seen.size < 200) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (needsCheck(cp) && seen.add(cp) && !hasGlyph(String(Character.toChars(cp)))) return false
        }
        return true
    }

    /** For an empty field: the probes. For a field with text: its characters. */
    fun systemShows(text: String, qatt: Boolean, pa: Boolean, hasGlyph: (String) -> Boolean): Boolean =
        if (text.isEmpty()) probes(qatt, pa).all(hasGlyph) else systemShows(text, hasGlyph)
}
