// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import java.util.ArrayList

/**
 * Vietnamese Telex, as in the website (js/ime.js): a key is applied to the text of the word that is there ([TelexKey.key]), the
 * combiner keeps no list of raw keys. So it cannot get out of step with the editor: a word that is resumed after a cursor move or
 * an edit (events marked [Event.isAlreadyTyped]) is taken as it is, and the next key works on what is shown.
 * Backspace removes the last character of the word. A space, a digit, a punctuation mark or a functional key ends the word.
 */
class TelexCombiner : Combiner {
    private val text = StringBuilder()

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        if (event.keyCode == KeyCode.SHIFT) return event

        if (event.keyCode == KeyCode.DELETE) {
            if (text.isEmpty()) return event      // nothing composed: delete in the editor
            text.delete(text.length - Character.charCount(text.codePointBefore(text.length)), text.length)
            if (text.isEmpty()) {
                // as in the other combiners: leave composing cleanly, the delete must not reach the committed text
                reset()
                return Event.createHardwareKeypressEvent(0x20, Constants.CODE_SPACE, 0, event, event.isKeyRepeat)
            }
            return Event.createConsumedEvent(event)
        }

        val codePoint = event.codePoint
        val valid = codePoint != Integer.MAX_VALUE && Character.isValidCodePoint(codePoint)
        // a space, a digit or a punctuation mark ends the word (it must not become part of the composing text and its suggestions)
        if (event.isFunctionalKeyEvent || (valid && !Character.isLetter(codePoint))) return commitAndReset(event)
        if (!valid) return Event.createConsumedEvent(event)

        val key = String(Character.toChars(codePoint))
        if (event.isAlreadyTyped) {
            text.append(key)                                                // text that was already there: as it is
        } else {
            val before = text.toString()
            text.setLength(0)
            text.append(TelexKey.key(before, key) ?: (before + key))
        }
        return Event.createConsumedEvent(event)
    }

    override val combiningStateFeedback: CharSequence
        get() = text.toString()

    override fun reset() {
        text.setLength(0)
    }

    private fun commitAndReset(event: Event): Event {
        val converted = combiningStateFeedback
        reset()
        return Event.createSoftwareTextEvent(converted, KeyCode.MULTIPLE_CODE_POINTS, event)
    }
}
