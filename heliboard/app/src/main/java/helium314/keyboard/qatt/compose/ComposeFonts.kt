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
 * [FontStore]) has QATT, PA (from build 12 of the fonts), Chữ Nôm and the Latin of Quốc Ngữ. A character that the font does not have
 * is drawn with the fonts of the system; a font that cannot be loaded is skipped.
 */
class ComposeFonts(private val context: Context) {
    private class Loaded(val main: Typeface?, val stamp: Long)

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
        return Loaded(main, stamp).also { loaded[font] = it }
    }

    /** The font in use if it is installed, else QATT Pen. */
    private val current: Loaded get() = load(if (FontStore.isInstalled(context, family)) family else ComposeFont.PEN)

    private class FontSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) { tp.typeface = typeface }
        override fun updateMeasureState(tp: TextPaint) { tp.typeface = typeface }
    }

    /** [text] all in the big font, in one piece: QATT, PA, Chữ Nôm and the Latin are in it, and the ruby annotation (a character, U+F00B0 and a syllable) works inside the font. */
    fun styled(text: CharSequence): SpannableStringBuilder {
        val out = SpannableStringBuilder(text)
        val tf = current.main
        if (tf != null && text.isNotEmpty()) out.setSpan(FontSpan(tf), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return out
    }
}
