// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt

import java.text.Normalizer

/** Port of `PaText` of js/pa-text.js (qtng/qatt): Quốc Ngữ <-> the PA script (code points from U+F00D0; U+F00B0 / U+F00B1 are the annotation controls, see [RUBY_OVER]). */
object PaText {
    const val J = 0xF00D0
    const val K = 0xF00D1
    private const val A0 = 0xF00D2
    private const val N0 = 0xF00E9
    private const val C0 = 0xF00FF
    private const val T0 = 0xF0109
    /** The ruby controls (shared by PA and QATT): a CJK character, the control, one syllable: the syllable is drawn small over (RUBY_OVER) or under (RUBY_UNDER) the character. */
    const val RUBY_OVER = 0xF00B0
    const val RUBY_UNDER = 0xF00B1
    private val ONSETS = "Z B C CH DD D PH G GI H KH L M N NG NH R S T TH TR V X".split(" ")
    private val NUCLEI = "A OW AW AA EE E I IEE OO O U UOO UW UWOW OA UOW OAW UAA UEE OE UY UYEE".split(" ")
    private val CODAS = "U I M N NG NH P T C CH".split(" ")
    private fun chr(c: Int) = String(Character.toChars(c))

    /** spelling of the onset (Quốc Ngữ -> PA name) */
    private val ONSET = mapOf("b" to "B", "c" to "C", "k" to "C", "q" to "C", "ch" to "CH", "d" to "D", "đ" to "DD", "g" to "G", "gh" to "G", "gi" to "GI", "h" to "H",
        "kh" to "KH", "l" to "L", "m" to "M", "n" to "N", "ng" to "NG", "ngh" to "NG", "nh" to "NH", "ph" to "PH", "r" to "R", "s" to "S", "t" to "T", "th" to "TH",
        "tr" to "TR", "v" to "V", "x" to "X", "f" to "PH", "j" to "TR", "w" to "V", "z" to "D", "" to "Z")
    /** the shape name that VietnameseParser.toQuocNgu takes */
    private val SHAPE = mapOf("Z" to "_", "B" to "b", "C" to "c", "CH" to "ch", "DD" to "d", "D" to "dz", "PH" to "ph", "G" to "g", "GI" to "gi", "H" to "h", "KH" to "kh",
        "L" to "l", "M" to "m", "N" to "n", "NG" to "ng", "NH" to "nh", "R" to "r", "S" to "s", "T" to "t", "TH" to "th", "TR" to "tr", "V" to "v", "X" to "x")
    /** nucleus spellings (the glide and the vowel without the tone mark) */
    private val NUC = mapOf("a" to "A", "ơ" to "OW", "ă" to "AW", "â" to "AA", "ê" to "EE", "e" to "E", "i" to "I", "y" to "I", "iê" to "IEE", "ia" to "IEE", "yê" to "IEE",
        "ya" to "IEE", "ô" to "OO", "o" to "O", "u" to "U", "uô" to "UOO", "ua" to "UOO", "ư" to "UW", "ươ" to "UWOW", "ưa" to "UWOW", "oa" to "OA", "uơ" to "UOW",
        "oă" to "OAW", "uâ" to "UAA", "uê" to "UEE", "oe" to "OE", "uy" to "UY", "uyê" to "UYEE", "uya" to "UYEE")
    private val QU = mapOf("ô" to "UAA", "a" to "OA", "ơ" to "UOW", "ă" to "OAW", "â" to "UAA", "e" to "OE", "ê" to "UEE", "i" to "UY", "y" to "UY", "yê" to "UYEE",
        "iê" to "UYEE", "ya" to "UYEE", "ia" to "UYEE")
    private val CODA = mapOf("m" to "M", "n" to "N", "ng" to "NG", "nh" to "NH", "p" to "P", "t" to "T", "c" to "C", "ch" to "CH", "i" to "I", "y" to "I", "o" to "U", "u" to "U")
    private val TONE = mapOf("sac" to 1, "huyen" to 2, "hoi" to 3, "nga" to 4, "nang" to 5)
    private val SPELL = mapOf("A" to "a", "OW" to "ơ", "AW" to "ă", "AA" to "â", "EE" to "ê", "E" to "e", "I" to "i", "IEE" to "iê", "OO" to "ô", "O" to "o", "U" to "u",
        "UOO" to "uô", "UW" to "ư", "UWOW" to "ươ", "OA" to "oa", "UOW" to "uơ", "OAW" to "oă", "UAA" to "uâ", "UEE" to "uê", "OE" to "oe", "UY" to "uy", "UYEE" to "uyê")
    private val CODA_SPELL = mapOf("M" to "m", "N" to "n", "NG" to "ng", "NH" to "nh", "P" to "p", "T" to "t", "C" to "c", "CH" to "ch")
    private val TONE_MARKS = Regex("[̣̀́̃̉]")

    private fun noTone(s: String) = Normalizer.normalize(TONE_MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), ""), Normalizer.Form.NFC)

    /** [stacked]: the coda and tone stand over the stem (the combiner); [ngang]: the tone ngang is written (tone mark 6) */
    data class Options(val stacked: Boolean = true, val ngang: Boolean = false)

    private fun encode(onset: String, nucleus: String, coda: String?, toneIn: Int, stacked: Boolean, ngang: Boolean): String {
        val sb = StringBuilder()
        sb.append(chr(A0 + ONSETS.indexOf(onset))).append(chr(J)).append(chr(N0 + NUCLEI.indexOf(nucleus)))
        val tone = if (toneIn != 0) toneIn else if (ngang) 6 else 0
        if (stacked && (coda != null || tone != 0)) sb.append(chr(K))
        if (coda != null) sb.append(chr(C0 + CODAS.indexOf(coda)))
        if (tone != 0) sb.append(chr(T0 + tone - 1))
        return sb.toString()
    }

    /** A Quốc Ngữ word as a PA cell, or null if there is none. */
    fun word(w: String, parser: VietnameseParser, opt: Options = Options()): String? {
        val p = parser.parse(w) ?: return null
        val onset = ONSET[p.onset] ?: return null
        val vowel = noTone(p.vowel)
        val glide = p.glide
        val nucleus = (if (p.onset == "q" && glide == "u") QU[vowel] else NUC[glide + vowel]) ?: return null
        val coda = if (p.coda.isNotEmpty()) (CODA[p.coda] ?: return null) else null
        return encode(onset, nucleus, coda, TONE[p.tone] ?: 0, opt.stacked, opt.ngang)
    }

    private const val LETTER = "[\\x{F00D2}-\\x{F00E8}]"
    /** a syllable of PA text: the stem, then a coda (with or without the combiner), a tone alone, or the combiner and a tone alone */
    val SYL = Regex("($LETTER)\\x{F00D0}([\\x{F00E9}-\\x{F00FE}])(?:\\x{F00D1}?([\\x{F00FF}-\\x{F0108}])([\\x{F0109}-\\x{F010E}])?|\\x{F00D1}?([\\x{F0109}-\\x{F010E}]))?")

    private fun readMatch(m: MatchResult, parser: VietnameseParser): String? {
        val g = m.groups
        val on = ONSETS[g[1]!!.value.codePointAt(0) - A0]
        val nu = NUCLEI[g[2]!!.value.codePointAt(0) - N0]
        val co = g[3]?.let { CODAS[it.value.codePointAt(0) - C0] }
        val t = g[4]?.value ?: g[5]?.value
        val tone = if (t != null) t.codePointAt(0) - T0 + 1 else 0
        var rhyme = SPELL.getValue(nu)
        if (co == "U") rhyme = if (nu == "A" || nu == "E" || nu == "OA" || nu == "OE") rhyme + "o" else rhyme + "u"
        else if (co == "I") rhyme += if (nu == "AW" || nu == "AA") "y" else "i"
        else if (co != null) {
            if (nu == "AW" && (co == "NH" || co == "CH")) rhyme = "a"   // the short a before nh, ch is written a
            rhyme += CODA_SPELL.getValue(co)
        }
        if ((co == "U" || co == "I") && nu == "AW") rhyme = "a" + rhyme.substring(1)   // ay, au: the short a is written a
        if (on == "Z" && nu == "IEE") rhyme = "yê" + rhyme.substring(2)                  // yêu, yên: no onset
        var s = parser.toQuocNgu(SHAPE.getValue(on), "x", if (tone == 6) 0 else tone, rhyme)
        if (s != null && on == "GI") s = s.replace(Regex("^gi(?=[iíìỉĩị])"), "g")
        return s
    }

    /**
     * The reading of PA text. PA cells stand without spaces between them, so a reading gets a space where it touches another syllable or a letter.
     */
    fun read(text: String, parser: VietnameseParser): String {
        var last = -1
        return SYL.replace(text) { m ->
            val at = m.range.first
            val r = readMatch(m, parser) ?: m.value
            val end = at + m.value.length
            // text[at - 1] is one UTF-16 unit, as str[at - 1] in the JavaScript
            val out = (if (at == last || (at > 0 && Character.isLetter(text[at - 1]))) " " else "") + r +
                (if (end < text.length && Character.isLetter(text.codePointAt(end))) " " else "")
            last = end
            out
        }
    }

    private val BETWEEN = Regex("([\\x{F00D2}-\\x{F010E}]) (?=[\\x{F00D2}-\\x{F010E}])")

    private val LATIN_RUN = Regex("[\\p{script=Latin}\\p{M}]+")

    /** every Quốc Ngữ word (runs of Latin letters and marks) as a PA cell, the rest stays; no space between cells */
    fun convert(text: String, parser: VietnameseParser, opt: Options = Options()): String =
        BETWEEN.replace(LATIN_RUN.replace(text) { word(it.value, parser, opt) ?: it.value }, "$1")

    /** the text read and written again (e.g. to stack a PA text typed unstacked) */
    fun restack(text: String, parser: VietnameseParser, opt: Options = Options()) = convert(read(text, parser), parser, opt)

    private const val NUCLEUS = "[\\x{F00E9}-\\x{F00FE}]"
    private const val CODA_T = "[\\x{F00FF}-\\x{F0108}][\\x{F0109}-\\x{F010E}]?"
    private const val TONE_T = "[\\x{F0109}-\\x{F010E}]"
    /** the cells: a stem alone, with a tone mark, or stacked with a coda (and a tone); a nucleus, a coda with its tone, a tone: each alone */
    val CELL = Regex("$LETTER(?:\\x{F00D0}$NUCLEUS(?:\\x{F00D1}(?:$CODA_T|$TONE_T)|$TONE_T)?)?|$NUCLEUS|$CODA_T|$TONE_T")
    val CELL_ANY = Regex(CELL.pattern + "|[\\x{F00D0}\\x{F00D1}]|(?s:.)")
}
