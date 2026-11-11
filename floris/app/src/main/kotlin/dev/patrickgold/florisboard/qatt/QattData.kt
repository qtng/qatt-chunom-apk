// SPDX-License-Identifier: Apache-2.0

package dev.patrickgold.florisboard.qatt

import android.graphics.Typeface
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.qatt.core.NomDictionary
import dev.patrickgold.florisboard.qatt.core.ScriptRuns
import dev.patrickgold.florisboard.qatt.core.Script
import dev.patrickgold.florisboard.qatt.core.ViSuggester
import dev.patrickgold.florisboard.qatt.core.VietnameseParser

/** The data of the composition field (loaded when first needed): the Chữ Nôm dictionary, the Vietnamese word list, the parser for PA and QATT, the fonts. */
object QattData {
    private val assets get() = FlorisApplication.getHack().assets

    val parser by lazy { VietnameseParser("qatt") }
    val nom by lazy { assets.open("qatt/nom.txt").bufferedReader().use { NomDictionary().load(it) } }
    val vi by lazy { assets.open("qatt/words.txt").bufferedReader().use { ViSuggester().load(it) } }

    private val mainFamily by lazy { runCatching { FontFamily(Typeface.createFromAsset(assets, "qatt/qatt-pen.ttf")) }.getOrNull() }
    private val paFamily by lazy { runCatching { FontFamily(Typeface.createFromAsset(assets, "qatt/pa-pen.ttf")) }.getOrNull() }

    /** [text] with its PA characters in the PA font and everything else in QATT Pen (QATT, Chữ Nôm and the Latin of Quốc Ngữ). */
    fun styled(text: String): AnnotatedString = buildAnnotatedString {
        append(text)
        for (run in ScriptRuns.split(text)) {
            val f = (if (run.script == Script.PA) paFamily else mainFamily) ?: continue
            addStyle(SpanStyle(fontFamily = f), run.start, run.end)
        }
    }
}
