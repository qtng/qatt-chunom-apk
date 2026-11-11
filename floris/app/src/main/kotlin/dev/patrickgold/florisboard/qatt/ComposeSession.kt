// SPDX-License-Identifier: Apache-2.0

package dev.patrickgold.florisboard.qatt

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.qatt.core.PaText
import dev.patrickgold.florisboard.qatt.core.QattText
import dev.patrickgold.florisboard.qatt.core.Script
import dev.patrickgold.florisboard.qatt.core.ScriptRuns

enum class Kind { PA, QATT, NOM, QN }

/** A candidate for the word being typed: [text] replaces it. */
data class Candidate(val kind: Kind, val text: String)

/** The three places of the candidate bar (left PA, middle Chữ Nôm, right QATT, filled as described in [ComposeSession.slots]) and the candidates of the long press. */
data class Slots(val left: Candidate?, val middle: Candidate?, val right: Candidate?, val more: List<Candidate>)

/**
 * The composition field. Telex (Vietnamese only) is applied to the text before the end (a window of 30 characters): no composing region, no state besides the
 * text itself. The word being typed is the run of Latin letters at the end of the text.
 */
class ComposeSession {
    var text by mutableStateOf("")
        private set
    var open by mutableStateOf(false)
        private set
    var moreOpen by mutableStateOf(false)

    private var languageBefore: QattLanguage? = null

    /** Opens the field and switches the keyboard to Vietnamese (Telex); [close] puts the language back. */
    fun openField() {
        if (open) return
        languageBefore = QattLanguages.current
        open = true
        QattLanguages.use(QattLanguages.vietnamese)
    }

    fun close() {
        if (!open) return
        open = false
        moreOpen = false
        languageBefore?.let { QattLanguages.use(it) }
        languageBefore = null
    }

    fun toggleField() { if (open) close() else openField() }

    private fun prefs() = FlorisApplication.getHack().getSharedPreferences("qatt", Context.MODE_PRIVATE)
    private fun flag(key: String) = runCatching { prefs().getBoolean(key, true) }.getOrDefault(true)

    var pa by mutableStateOf(flag("pa"))
        private set
    var nom by mutableStateOf(flag("nom"))
        private set
    var qatt by mutableStateOf(flag("qatt"))
        private set

    fun togglePa() { pa = !pa; save("pa", pa) }
    fun toggleNom() { nom = !nom; save("nom", nom) }
    fun toggleQatt() { qatt = !qatt; save("qatt", qatt) }
    private fun save(key: String, v: Boolean) { runCatching { prefs().edit().putBoolean(key, v).apply() } }

    /** A key of the keyboard (the text it types). A letter goes through Telex (Vietnamese), anything else is appended. */
    fun type(s: String) {
        if (s.isEmpty()) return
        moreOpen = false
        if (QattLanguages.current.telex && s.length == 1 && s[0].isLetter()) {
            val window = text.takeLast(WINDOW)
            val result = TelexKey.key(window, s)
            if (result != null) {
                text = text.dropLast(window.length) + result
                return
            }
        }
        text += s
    }

    /** Backspace: one character, or a whole QATT or PA cell (with its joiners and tone marks). */
    fun backspace() {
        moreOpen = false
        if (text.isEmpty()) return
        val q = QattText.CELL_ANY.findAll(text).lastOrNull()?.range?.first ?: (text.length - 1)
        val p = PaText.CELL_ANY.findAll(text).lastOrNull()?.range?.first ?: (text.length - 1)
        var cut = minOf(q, p)
        // the last cell regex may match a single plain character; make sure a surrogate pair goes whole
        if (cut > 0 && Character.isLowSurrogate(text[cut]) && Character.isHighSurrogate(text[cut - 1])) cut--
        text = text.substring(0, cut)
    }

    fun enter() { moreOpen = false; text += "\n" }

    fun clear() { moreOpen = false; text = "" }

    /** The word being typed: the Latin letters at the end of the text. */
    val word: String
        get() {
            var i = text.length
            while (i > 0) {
                val cp = Character.codePointBefore(text, i)
                if (!Character.isLetter(cp) || ScriptRuns.scriptOf(cp) != Script.OTHER) break
                i -= Character.charCount(cp)
            }
            return text.substring(i)
        }

    /**
     * The places of the candidate bar: left PA, middle Chữ Nôm, right QATT. A place that is not used (PA or QATT switched off, or the word has no glyph) is filled with the
     * next candidates of the middle kind: Chữ Nôm if it is switched on, else Quốc Ngữ (the word as typed, then completions from the word list). The long press of the
     * middle place shows all of them.
     */
    fun slots(): Slots {
        val w = word
        if (w.isEmpty()) return Slots(null, null, null, emptyList())
        val paC = if (pa) PaText.word(w, QattData.parser)?.let { Candidate(Kind.PA, it) } else null
        val qattC = if (qatt) QattText.word(w, QattData.parser)?.let { Candidate(Kind.QATT, it) } else null
        val main: List<Candidate> = if (nom) {
            QattData.nom.lookup(w, emptyMap(), MAX_MORE).map { Candidate(Kind.NOM, it.glyph) }
        } else {
            (listOf(w) + QattData.vi.complete(w, MAX_MORE)).distinct().map { Candidate(Kind.QN, it) }
        }
        val fillers = main.drop(1).iterator()
        val middle = main.firstOrNull()
        val left = paC ?: if (fillers.hasNext()) fillers.next() else null
        val right = qattC ?: if (fillers.hasNext()) fillers.next() else null
        return Slots(left, middle, right, main)
    }

    /** A tap on a candidate: it replaces the word being typed. */
    fun pick(c: Candidate) {
        text = text.dropLast(word.length) + c.text
        moreOpen = false
    }

    companion object {
        const val WINDOW = 30
        const val MAX_MORE = 60
    }
}
