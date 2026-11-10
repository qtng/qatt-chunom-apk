// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/**
 * The fonts the composition field and the image are drawn with (assets/fonts, see tools/fonts/fetch_fonts.py): QATT Pen letters, PA Pen and the most
 * frequent Chữ Nôm characters of QATT Pen. A character they do not have is drawn with the fonts of the system. A font that cannot be loaded is skipped.
 */
class ComposeFonts(context: Context) {
    private fun load(context: Context, name: String): Typeface? = try { Typeface.createFromAsset(context.assets, "fonts/$name") } catch (e: Exception) { null }

    val qatt: Typeface? = load(context, "qatt-pen-letters.ttf")
    val pa: Typeface? = load(context, "pa-pen.ttf")
    val nom: Typeface? = load(context, "qatt-pen-00.ttf")
    private val nomPaint = nom?.let { Paint().apply { typeface = it } }

    /** The font for a character of the script, null: the font of the system. */
    fun typefaceFor(script: Script, codePoint: Int): Typeface? = when (script) {
        Script.QATT -> qatt
        Script.PA -> pa
        Script.CJK -> if (Build.VERSION.SDK_INT >= 23 && nomPaint?.hasGlyph(String(Character.toChars(codePoint))) == true) nom else null
        Script.OTHER -> null
    }

    private class FontSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) { tp.typeface = typeface }
        override fun updateMeasureState(tp: TextPaint) { tp.typeface = typeface }
    }

    /** [text] with a font span on every part that has a font of its own. */
    fun styled(text: CharSequence): SpannableStringBuilder {
        val out = SpannableStringBuilder(text)
        for (run in ScriptRuns.split(text)) {
            var i = run.start
            var start = i
            var current: Typeface? = null
            while (i < run.end) {
                val cp = Character.codePointAt(text, i)
                val tf = typefaceFor(run.script, cp)
                if (tf !== current) {
                    if (current != null) out.setSpan(FontSpan(current), start, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    start = i
                    current = tf
                }
                i += Character.charCount(cp)
            }
            if (current != null) out.setSpan(FontSpan(current), start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }
}
