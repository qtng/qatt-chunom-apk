// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import helium314.keyboard.event.TelexEngine
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
    val space: SpaceMode = SpaceMode.NONE,
    val slots: Int = 5,                       // places of the candidate bar
    val usage: Map<String, Int> = emptyMap(), // how often a Chữ Nôm glyph was chosen (the order of the candidates)
)

/**
 * The state of the composition field: the text so far, and the word being typed (the Telex keys, shown as Quốc Ngữ). Pure logic, no Android.
 * The cursor is always at the end (moving it is not done yet).
 *
 * Candidates for the word being typed, in this order: PA, QATT (each if switched on and the word has a glyph), then Chữ Nôm, [ComposeSettings.slots] in all.
 * A candidate is taken by [pick] only, except that Space can take one (see [SpaceMode]).
 */
class ComposeField(private val parser: VietnameseParser, private val nom: NomDictionary, var settings: ComposeSettings = ComposeSettings()) {
    private val committed = StringBuilder()
    private val keys = StringBuilder()

    /** The word being typed as Quốc Ngữ ("" if none). */
    val word: String get() = TelexEngine.convert(keys.toString())

    /** The text of the field: what is settled, then the word being typed. */
    val text: String get() = committed.toString() + word

    /** Where the word being typed starts in [text] (to underline it); equal to its length if there is none. */
    val composingStart: Int get() = committed.length

    val isEmpty: Boolean get() = committed.isEmpty() && keys.isEmpty()

    /** A key of the keyboard: a letter goes into the word (Telex), anything else ends the word and is written as it is. */
    fun type(codePoint: Int) {
        if (Character.isLetter(codePoint)) keys.appendCodePoint(codePoint)
        else { settle(); committed.appendCodePoint(codePoint) }
    }

    fun type(s: String) {
        var i = 0
        while (i < s.length) { val cp = s.codePointAt(i); type(cp); i += Character.charCount(cp) }
    }

    /** The word being typed becomes part of the text, as Quốc Ngữ. */
    private fun settle() {
        committed.append(word)
        keys.setLength(0)
    }

    /** Space: see [SpaceMode]. */
    fun space() {
        val w = word
        if (w.isNotEmpty()) {
            val converted = when (settings.space) {
                SpaceMode.NONE -> null
                SpaceMode.PA -> PaText.word(w, parser)
                SpaceMode.QATT -> QattText.word(w, parser)
                SpaceMode.NOM -> nom.lookup(w, settings.usage, 1).firstOrNull()?.glyph
            }
            if (converted != null) {            // the space is swallowed: cells stand side by side
                committed.append(converted)
                keys.setLength(0)
                return
            }
            settle()
        }
        committed.append(' ')
    }

    fun enter() {
        settle()
        committed.append('\n')
    }

    /** Backspace: a key of the word being typed, else a whole cell (QATT and PA cells with their joiners and tone marks) or character. */
    fun backspace() {
        if (keys.isNotEmpty()) {
            val cp = keys.codePointBefore(keys.length)
            keys.setLength(keys.length - Character.charCount(cp))
            return
        }
        if (committed.isEmpty()) return
        val s = committed.toString()
        val q = QattText.CELL_ANY.findAll(s).lastOrNull()?.range?.first ?: (s.length - 1)
        val p = PaText.CELL_ANY.findAll(s).lastOrNull()?.range?.first ?: (s.length - 1)
        committed.setLength(minOf(q, p))
    }

    /** All candidates for the word being typed: PA, QATT (each if switched on and the word has a glyph), then Chữ Nôm (at most [MAX_NOM]). */
    fun allCandidates(): List<Candidate> {
        val w = word
        if (w.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, Candidate>()
        fun add(c: Candidate) { if (c.text.isNotEmpty() && c.text !in out) out[c.text] = c }
        if (settings.pa) PaText.word(w, parser)?.let { add(Candidate(Kind.PA, it)) }
        if (settings.qatt) QattText.word(w, parser)?.let { add(Candidate(Kind.QATT, it)) }
        for (c in nom.lookup(w, settings.usage, MAX_NOM)) add(Candidate(Kind.NOM, c.glyph, c.reading))
        return out.values.toList()
    }

    /** The candidates of the bar: the first [ComposeSettings.slots]; the others are shown when the user opens the list ([allCandidates]). */
    fun candidates(): List<Candidate> = allCandidates().take(settings.slots)

    /** Are there more candidates than the bar has places for? */
    fun hasMoreCandidates(): Boolean = allCandidates().size > settings.slots

    /** A tap on candidate [index] of [allCandidates] (the bar shows the first ones): it replaces the word being typed. Returns it (null if there is none at that place). */
    fun pick(index: Int): Candidate? {
        val c = allCandidates().getOrNull(index) ?: return null
        committed.append(c.text)
        keys.setLength(0)
        return c
    }

    companion object {
        /** At most this many Chữ Nôm candidates. */
        const val MAX_NOM = 60
    }

    /** Removes everything (the arrow to the left, or after sending). */
    fun clear() {
        committed.setLength(0)
        keys.setLength(0)
    }
}
