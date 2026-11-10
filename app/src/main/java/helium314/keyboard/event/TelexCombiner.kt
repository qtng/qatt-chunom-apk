// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import java.util.ArrayList

/**
 * Vietnamese Telex. Keeps the keys typed for the word and shows [TelexEngine.convert] of them as the composing text; the
 * logic is in the engine. Backspace removes the last key.
 */
class TelexCombiner : Combiner {
    private val keys = StringBuilder()

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        if (event.keyCode == KeyCode.SHIFT) return event

        if (event.keyCode == KeyCode.DELETE) {
            if (keys.isEmpty()) return event      // nothing composed: delete in the editor
            val cp = keys.codePointBefore(keys.length)
            keys.delete(keys.length - Character.charCount(cp), keys.length)
            if (keys.isEmpty()) {
                // as in the other combiners: leave composing cleanly, the delete must not reach the committed text
                reset()
                return Event.createHardwareKeypressEvent(0x20, Constants.CODE_SPACE, 0, event, event.isKeyRepeat)
            }
            return Event.createConsumedEvent(event)
        }

        val codePoint = event.codePoint
        val valid = codePoint != Integer.MAX_VALUE && Character.isValidCodePoint(codePoint)
        if (event.isFunctionalKeyEvent || (valid && Character.isWhitespace(codePoint))) return commitAndReset(event)
        if (!valid) return Event.createConsumedEvent(event)

        keys.appendCodePoint(codePoint)
        return Event.createConsumedEvent(event)
    }

    override val combiningStateFeedback: CharSequence
        get() = TelexEngine.convert(keys.toString())

    override fun reset() {
        keys.setLength(0)
    }

    private fun commitAndReset(event: Event): Event {
        val converted = combiningStateFeedback
        reset()
        return Event.createSoftwareTextEvent(converted, KeyCode.MULTIPLE_CODE_POINTS, event)
    }
}
