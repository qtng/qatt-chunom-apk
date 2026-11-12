// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import helium314.keyboard.event.TelexKey
import helium314.keyboard.qatt.NomDictionary
import helium314.keyboard.qatt.PaText
import helium314.keyboard.qatt.QattText
import helium314.keyboard.qatt.VietnameseParser

/** What Space does in the composition field (a setting): a real space, or the word before the cursor becomes PA, QATT or the first Chữ Nôm candidate. */
enum class SpaceMode { NONE, PA, QATT, NOM }

enum class Kind { PA, QATT, NOM }

/** A candidate of the bar: [text] replaces the word being typed; [reading] is the reading of a Chữ Nôm entry. */
data class Candidate(val kind: Kind, val text: String, val reading: String? = null)

data class ComposeSettings(
    val pa: Boolean = true,                   // PA among the candidates
    val qatt: Boolean = true,                 // QATT among the candidates
    val nom: Boolean = true,                  // Chữ Nôm among the candidates
    val space: SpaceMode = SpaceMode.NONE,
    val slots: Int = 5,                       // places of the candidate bar
    val usage: Map<String, Int> = emptyMap(), // how often a Chữ Nôm glyph was chosen (the order of the candidates)
)

/**
 * The state of the composition field: the text and the cursor in it (the text is edited at the cursor, as in any text field). Pure logic, no Android.
 * Telex works on the text itself ([TelexKey]: the key is applied to the word before the cursor), so there is no state besides text and cursor.
 *
 * The word being typed is the run of Latin letters before the cursor ([word]); if a letter follows the cursor (the cursor is inside a word) there are no candidates.
 * Candidates for the word, in this order: PA, QATT (each if switched on and the word has a glyph), then Chữ Nôm, [ComposeSettings.slots] in all.
 * A candidate is taken by [pick] only, except that Space can take one (see [SpaceMode]). QATT and PA cells and surrogate pairs are never split by the cursor.
 */
class ComposeField(private val parser: VietnameseParser, private val nom: NomDictionary, var settings: ComposeSettings = ComposeSettings()) {
    private val sb = StringBuilder()

    /** The cursor: an index into [text], always between two cells. */
    var cursor = 0
        private set

    /** The text of the field. */
    val text: String get() = sb.toString()

    val isEmpty: Boolean get() = sb.isEmpty()

    /** The word being typed (Quốc Ngữ): the Latin letters before the cursor ("" if none). */
    val word: String
        get() {
            var i = cursor
            while (i > 0) {
                val cp = Character.codePointBefore(sb, i)
                if (!Character.isLetter(cp) || Cells.isGlyph(cp)) break
                i -= Character.charCount(cp)
            }
            return sb.substring(i, cursor)
        }

    /** Where the word being typed starts in [text] (to underline it); equal to [cursor] if there is none. */
    val composingStart: Int get() = cursor - word.length

    /** Is the cursor inside a word (a letter follows it)? Then there are no candidates. */
    private val insideWord: Boolean
        get() = cursor < sb.length && Character.isLetter(Character.codePointAt(sb, cursor)) && !Cells.isGlyph(Character.codePointAt(sb, cursor))

    private fun insert(s: String) { sb.insert(cursor, s); cursor += s.length }

    private fun replaceWord(with: String) {
        val start = composingStart
        // a PA syllable right behind a CJK character is its ruby annotation (written small over it by the font); Backspace behind it dissolves it
        val lead = if (isPaSyllable(with) && start > 0 && isRubyBase(sb.codePointBefore(start))) String(Character.toChars(PaText.RUBY_OVER)) else ""
        sb.replace(start, cursor, lead + with)
        cursor = start + lead.length + with.length
    }

    private fun isPaSyllable(s: String) = PaText.SYL.matchEntire(s) != null
    private fun isRubyBase(cp: Int) = cp in 0x2E80..0xD7FF || cp in 0xF900..0xFAFF || cp in 0x20000..0x3FFFF

    /** A key of the keyboard: a letter goes into the word (Telex), anything else is written as it is. */
    /** Telex is on while the keyboard is Vietnamese; in another language the letters go in as they are and Space is a plain space (the candidates stay). */
    var telex = true

    fun type(codePoint: Int) {
        if (telex && Character.isLetter(codePoint) && !Cells.isGlyph(codePoint)) {
            val key = String(Character.toChars(codePoint))
            val start = maxOf(0, cursor - WINDOW)
            val window = sb.substring(start, cursor)
            val result = TelexKey.key(window, key)
            if (result != null) {
                sb.replace(start, cursor, result)
                cursor = start + result.length
                return
            }
        }
        insert(String(Character.toChars(codePoint)))
    }

    fun type(s: String) {
        var i = 0
        while (i < s.length) { val cp = s.codePointAt(i); type(cp); i += Character.charCount(cp) }
    }

    /** Space: see [SpaceMode]. */
    fun space() {
        val w = word
        if (telex && w.isNotEmpty() && !insideWord) {
            val converted = when (settings.space) {
                SpaceMode.NONE -> null
                SpaceMode.PA -> PaText.word(w, parser)
                SpaceMode.QATT -> QattText.word(w, parser)
                SpaceMode.NOM -> nom.lookup(w, settings.usage, 1).firstOrNull()?.glyph
            }
            if (converted != null) { replaceWord(converted); return }       // the space is swallowed: cells stand side by side
        }
        insert(" ")
    }

    fun enter() = insert("\n")

    /** Backspace: a whole cell (QATT and PA cells with their joiners and tone marks) or character before the cursor. */
    fun backspace() {
        if (cursor == 0) return
        val start = Cells.startBefore(sb, cursor)
        // behind an annotated character only the ruby control goes first: the syllable stays as normal text
        val unit = Cells.RUBY_UNIT.find(sb, start)?.takeIf { it.range.first == start && start + it.value.length == cursor }
        if (unit != null) {
            val control = start + Character.charCount(sb.codePointAt(start))
            sb.delete(control, control + 2)
            cursor -= 2
            return
        }
        sb.delete(start, cursor)
        cursor = start
    }

    fun moveLeft() { if (cursor > 0) cursor = Cells.startBefore(sb, cursor) }
    fun moveRight() { if (cursor < sb.length) cursor = Cells.endAfter(sb, cursor) }
    fun moveToStart() { cursor = 0 }
    fun moveToEnd() { cursor = sb.length }

    /** Puts the cursor at [offset] (a tap), moved to the nearest border between two cells. */
    fun setCursor(offset: Int) { cursor = Cells.snap(sb, offset.coerceIn(0, sb.length)) }

    /** All candidates for the word being typed: PA, QATT (each if switched on and the word has a glyph), then Chữ Nôm (at most [MAX_NOM]). */
    fun allCandidates(): List<Candidate> {
        val w = word
        if (w.isEmpty() || insideWord) return emptyList()
        val out = LinkedHashMap<String, Candidate>()
        fun add(c: Candidate) { if (c.text.isNotEmpty() && c.text !in out) out[c.text] = c }
        if (settings.pa) PaText.word(w, parser)?.let { add(Candidate(Kind.PA, it)) }
        if (settings.qatt) QattText.word(w, parser)?.let { add(Candidate(Kind.QATT, it)) }
        if (settings.nom) for (c in nom.lookup(w, settings.usage, MAX_NOM)) add(Candidate(Kind.NOM, c.glyph, c.reading))
        return out.values.toList()
    }

    /** The candidates of the bar: the first [ComposeSettings.slots]; the others are shown when the user opens the list ([allCandidates]). */
    fun candidates(): List<Candidate> = allCandidates().take(settings.slots)

    /** Are there more candidates than the bar has places for? */
    fun hasMoreCandidates(): Boolean = allCandidates().size > settings.slots

    /** A tap on candidate [index] of [allCandidates] (the bar shows the first ones): it replaces the word being typed. Returns it (null if there is none at that place). */
    fun pick(index: Int): Candidate? {
        val c = allCandidates().getOrNull(index) ?: return null
        replaceWord(c.text)
        return c
    }

    companion object {
        /** At most this many Chữ Nôm candidates. */
        const val MAX_NOM = 60
        /** Telex looks at this many characters before the cursor (a word is at most seven letters). */
        const val WINDOW = 30
    }

    /** Removes everything (the arrow to the left, or after sending). */
    fun clear() {
        sb.setLength(0)
        cursor = 0
    }
}
