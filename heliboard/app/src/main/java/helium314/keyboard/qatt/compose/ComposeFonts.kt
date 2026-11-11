// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/**
 * The fonts the composition field and the image are drawn with. The big font of the chosen [ComposeFont] (QATT Pen is in the app, the others are imported, see
 * [FontStore]) has QATT, Chữ Nôm and the Latin of Quốc Ngữ; PA is not in it and comes from its own small file (assets/fonts). A character that the fonts do not have
 * is drawn with the fonts of the system; a font that cannot be loaded is skipped.
 */
class ComposeFonts(private val context: Context) {
    private class Loaded(val main: Typeface?, val pa: Typeface?, val stamp: Long)

    private val loaded = HashMap<ComposeFont, Loaded>()

    /** The font in use; changing it changes the next drawing. */
    var family: ComposeFont = ComposeFont.PEN

    private fun load(font: ComposeFont): Loaded {
        val file = FontStore.imported(context, font)
        val stamp = file?.lastModified() ?: 0L
        loaded[font]?.let { if (it.stamp == stamp) return it }
        val main = try {
            if (file != null) Typeface.createFromFile(file) else if (font.bundled) Typeface.createFromAsset(context.assets, "fonts/${font.file}") else null
        } catch (e: Exception) { null }
        val pa = try { Typeface.createFromAsset(context.assets, "fonts/${font.pa}") } catch (e: Exception) { null }
        return Loaded(main, pa, stamp).also { loaded[font] = it }
    }

    /** The font in use if it is installed, else QATT Pen. */
    private val current: Loaded get() = load(if (FontStore.isInstalled(context, family)) family else ComposeFont.PEN)

    private class FontSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) { tp.typeface = typeface }
        override fun updateMeasureState(tp: TextPaint) { tp.typeface = typeface }
    }

    /** [text] in the big font, its PA characters in the PA font. */
    fun styled(text: CharSequence): SpannableStringBuilder {
        val out = SpannableStringBuilder(text)
        val fonts = current
        for (run in ScriptRuns.split(text)) {
            val tf = (if (run.script == Script.PA) fonts.pa else fonts.main) ?: continue
            out.setSpan(FontSpan(tf), run.start, run.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }
}
