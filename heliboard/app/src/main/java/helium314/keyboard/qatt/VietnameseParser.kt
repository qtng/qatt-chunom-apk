// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt

import java.text.Normalizer

/** The result of [VietnameseParser.parse]; the fields are those of the JavaScript object. */
data class Parsed(
    val code: String,         // the older code of the phoneme glyphs
    val qatt: String?,        // the code of the decoding, e.g. tự -> "t,c5,,5" (null if the rhyme is not in the table)
    val hop: String?,         // the Hợp Thư code of ong, ung, uông, ông
    val onset: String,
    val glide: String,
    val vowel: String,        // NFC, with the tone mark
    val coda: String,
    val tone: String,         // ngang sac huyen hoi nga nang
)

/** A QATT cell as the parts of a syllable (see [QattText.decode]). */
data class CellParts(val onset: String, val rhyme: String, val tone: Int, val hop: String? = null)

private fun nfd(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

/** Port of `VietnameseParser` of js/parser.js (qtng/qatt): same steps, same results (tests: golden files made from the JavaScript). */
class VietnameseParser(table: List<List<String>>, val decoding: String = "") {
    /** [decoding]: the name of a table of [DECODINGS] (qatt, qoex, qoex2, omniglot). */
    constructor(decoding: String = "qatt") : this(DECODINGS[decoding] ?: throw IllegalArgumentException("Unknown decoding: $decoding"), decoding)

    private val onsets = ONSETS.map { nfd(it).lowercase() }
    private val codas = CODAS.map { nfd(it).lowercase() }
    private val vowels = VALID_VOWELS.map { nfd(it).lowercase() }
    private val rhymes = HashMap<String, String>()   // rhyme -> code of its glyph, e.g. "ư" -> "c5"
    private val glyphs = HashMap<String, String>()   // glyph code -> rhyme (the reverse; also the glyphs whose rhyme another glyph has first)

    init {
        table.forEachIndexed { i, row ->
            val shape = SHAPES[i].let { if (it == "z") "_" else it }
            row.forEachIndexed { j, rhyme ->
                if (rhyme.isEmpty()) return@forEachIndexed
                val code = shape + if (j < 5) (j + 2).toString() else ""
                if (rhyme !in rhymes) rhymes[rhyme] = code
                glyphs[code] = rhyme
            }
        }
    }

    // ---------- the reverse: QATT glyph -> Quốc Ngữ ----------

    /**
     * The spelling of a syllable (what [QattText.decode] makes of a cell of QATT text): onset: shape of the onset ("_" none), rhyme: code of the rhyme glyph
     * ("" none: a bare consonant, or with [hop] the Hợp Thư writing), tone 0 to 8 as in the qatt code. null if there is no reading.
     */
    fun toQuocNgu(parts: CellParts) = toQuocNgu(parts.onset, parts.rhyme, parts.tone, parts.hop)

    fun toQuocNgu(onset: String, rhyme: String, tone: Int = 0, hop: String? = null): String? {
        if (hop.isNullOrEmpty() && rhyme == "") {   // a bare consonant: the plain shape alone
            val on = BARE[onset] ?: onset
            return on.ifEmpty { null }
        }
        var rh = hop?.ifEmpty { null } ?: glyphs[rhyme] ?: return null
        rh = OPEN[rh] ?: rh                      // the table writes the open rhymes of the diphthongs with their closed form
        if (rh == "ơnh") rh = "anh"              // anh / oanh stand for ơnh / uơnh where the table lacks them
        if (rh == "uơnh") rh = "oanh"
        var t: String? = listOf(null, "sac", "huyen", "hoi", "nga", "nang").getOrNull(tone)
        if (tone == 6 || tone == 7) {            // entering tones: the nasal of the twin becomes the stop
            val m = Regex("(ng|nh|n|m)$").find(rh) ?: return null
            rh = rh.dropLast(m.groupValues[1].length) + STOP.getValue(m.groupValues[1])
            t = if (tone == 6) "sac" else "nang"
        }
        var on = BACK[onset] ?: onset
        if (onset == "c") {                      // c, k or q
            if (Regex("^(uâ|uê|uy|uơ|oa|oă|oe)").containsMatchIn(rh)) {
                on = "qu"; rh = rh.drop(1)
                if (rh.startsWith("o")) rh = "u" + rh.drop(1)
                if (rh == "âc") rh = "ôc"        // quốc is written like quấc
            } else if (Regex("^(e|ê|i|y|iê|ia)").containsMatchIn(rh)) on = "k"
        } else if (onset == "g" && Regex("^(e|ê|i)").containsMatchIn(rh)) on = "gh"
        else if (onset == "ng" && Regex("^(e|ê|i)").containsMatchIn(rh)) on = "ngh"
        return on + tonePlaced(rh, t)
    }

    /** The tone mark on the right vowel of a rhyme (the vowel with a hat or horn, else the middle one, with a coda the second, else the first: hòa, thúy). */
    internal fun tonePlaced(rhyme: String, tone: String?): String {
        if (tone == null) return rhyme
        val chars = nfc(rhyme).codePointList().map { String(Character.toChars(it)) }.toMutableList()
        val vowels = chars.indices.filter { VOWEL_CHARS.contains(chars[it].lowercase()) }
        val modified = vowels.filter { MODIFIED_CHARS.contains(chars[it].lowercase()) }
        val at = when {
            modified.isNotEmpty() -> modified.last()
            vowels.size == 1 -> vowels[0]
            vowels.size == 3 -> vowels[1]
            vowels.size == 2 -> if (vowels[1] < chars.size - 1) vowels[1] else vowels[0]   // a coda follows: the second vowel (toán), else the first
            else -> return rhyme
        }
        chars[at] = nfc(nfd(chars[at]) + MARK.getValue(tone))
        return chars.joinToString("")
    }

    // ---------- Quốc Ngữ -> parts ----------

    /** Parses a syllable into its parts, null if it is none. */
    fun parse(word: String): Parsed? {
        if (Regex("[fjwz]", RegexOption.IGNORE_CASE).containsMatchIn(word.drop(1)) && !word.lowercase().startsWith("gi")) return null   // f, j, w, z are onsets only

        var normalized = nfd(word).lowercase()
        var tone = "ngang"
        var toneChar = ""

        // 1. isolate the tone
        for (ch in normalized) {
            val name = DIACRITIC[ch]
            if (name != null) {
                tone = name
                toneChar = ch.toString()
                normalized = normalized.replaceFirst(ch.toString(), "")
                break
            }
        }

        var onset = ""; var glide = ""; var vowel = ""; var coda = ""
        var temp = normalized

        // 2. onset
        for (o in onsets) {
            if (temp.startsWith(o)) { onset = o; temp = temp.substring(o.length); break }
        }
        // an onset alone is a bare consonant: the plain shape without a rhyme (not gi: that is the syllable gi)
        if (onset.isNotEmpty() && onset != "gi" && temp.isEmpty() && tone == "ngang") {
            val shape = QATT_ONSET_MAP[onset] ?: onset
            return Parsed("$shape,,,0", "$shape,,,0", null, onset, "", "", "", tone)
        }
        // what is written after the onset, as in the spelling (the rhyme looked up in the decoding)
        var spelling = nfc(temp)
        if (onset == "qu") spelling = if (spelling == "a") "oa" else "u" + (if (spelling == "ôc") "âc" else spelling)   // quốc is treated like quấc; qua is oa
        if (onset == "gi" && spelling == "") spelling = "i"

        if (onset == "qu") { onset = "q"; glide = "u" }

        // 3. coda
        for (c in codas) {
            if (temp.endsWith(c) && temp.length > c.length) {
                val remainder = temp.substring(0, temp.length - c.length)
                if (remainder.isNotEmpty()) { coda = c; temp = remainder; break }
            }
        }

        // 4. glide (u/o) vs vowel
        if (temp.length >= 2 && temp !in vowels && (temp.startsWith("u") || temp.startsWith("o"))) {
            val first = temp.substring(0, 1)
            val rest = temp.substring(1)
            if (rest in vowels) { glide = first; vowel = rest; temp = "" }
        } else if (temp.isEmpty() && onset == "gi") {
            vowel = "i"
        }

        // 5. the rest is the vowel
        if (temp != "" && temp in vowels) { vowel = temp; temp = "" }

        // a semi-vowel ending (i/y/o/u at the end)
        if (coda.isEmpty() && vowel.length > 1) {
            val last = vowel.last()
            if (last in "iyou") {
                coda = last.toString()
                vowel = vowel.dropLast(1)
                if ((last == 'y' || last == 'u') && vowel == "a") vowel = "ă"
            }
        }

        if (temp != "" || vowel == "") return null

        return finalize(onset, glide, vowel, coda, tone, toneChar, spelling)
    }

    private fun finalize(onset: String, glide: String, vowel: String, coda: String, tone: String, toneChar: String, spelling: String): Parsed {
        val vowelNfc = nfc(vowel)
        // the vowel with the tone mark (toNFC of the JavaScript)
        var t = vowelNfc
        var p = t.length - 2
        if (t.take(2) in setOf("iê", "uô", "ươ", "yê")) p = 2
        if (p < 0) p = 0
        t = t.substring(0, minOf(p + 1, t.length)) + toneChar + t.substring(minOf(p + 1, t.length))
        val vowelOut = nfc(t)

        val code = listOf(
            QATT_ONSET_MAP[onset] ?: onset.ifEmpty { "_" },
            (if (glide.isNotEmpty()) "w" else "") + (QATT_VOWEL_MAP[vowelNfc] ?: vowel),
            if (coda == "o") "u" else if (coda == "y") "i" else coda,
            (TONE_INT[tone] ?: 0).toString(),
        ).joinToString(",").replaceFirst("c,wo,", "c,waw2,")

        return Parsed(code, qattCode(onset, coda, tone, spelling), hopCode(onset, coda, tone, spelling), onset, glide, vowelOut, coda, tone)
    }

    /** Code for qatt.js after the decoding: "onset,shape+marking,,tone", e.g. tự -> "t,c5,,5". null if the rhyme is not in the table. */
    private fun qattCode(onset: String, coda: String, tone: String, spelling: String): String? {
        val (rhyme, toneInt) = rhymeAndTone(coda, tone, spelling)
        val glyph = lookup(rhyme, onset) ?: return null
        val on = QATT_ONSET_MAP[onset] ?: onset.ifEmpty { "_" }
        return listOf(on, glyph, "", toneInt.toString()).joinToString(",")
    }

    /** The glyph of a rhyme: the table first, then the substitutes (after qu the glide u is written o; y is i; anh / oanh; the open ia, ua, ưa are iê, uô, ươ). */
    private fun lookup(rhyme: String, onset: String): String? {
        val candidates = arrayListOf(rhyme)
        if (onset == "q" && rhyme.startsWith("u")) candidates.add("o" + rhyme.drop(1))
        for (r in candidates.toList()) if (r.startsWith("y")) candidates.add("i" + r.drop(1))
        for (r in candidates.toList()) RHYME_SUBSTITUTES[r]?.let { candidates.add(it) }
        for (r in candidates.toList()) {
            val m = OPEN_RE.find(r) ?: continue
            candidates.add(m.groupValues[1] + OPEN_DIPHTHONG.getValue(m.groupValues[2]))
        }
        for (r in candidates) rhymes[r]?.let { return it }
        return null
    }

    private fun rhymeAndTone(coda: String, tone: String, spelling: String): Pair<String, Int> {
        var rhyme = spelling
        var t = TONE_INT[tone] ?: 0
        val twin = STOP_TWIN[coda]
        if (twin != null) {
            rhyme = rhyme.substring(0, maxOf(0, rhyme.length - coda.length)) + twin
            if (tone == "sac") t = 6 else if (tone == "nang") t = 7
        }
        return rhyme to t
    }

    /** Hợp Thư code for a rhyme that the table lacks (ong, ung, uông, ông), else null: "shape+mark,shape,,tone". */
    private fun hopCode(onset: String, coda: String, tone: String, spelling: String): String? {
        val (rhyme, toneInt) = rhymeAndTone(coda, tone, spelling)
        val mark = HOP_THU[rhyme] ?: return null
        if (rhyme in rhymes) return null
        val shape = QATT_ONSET_MAP[onset] ?: onset.ifEmpty { "_" }
        return if (mark > 1) listOf(shape + mark, shape, "", toneInt).joinToString(",") else listOf(shape + mark, "", "", toneInt).joinToString(",")
    }

    companion object {
        val ONSETS = listOf("ngh", "ng", "nh", "gh", "ph", "th", "ch", "tr", "kh", "gi", "qu", "b", "c", "d", "đ", "g", "h", "k", "l", "m", "n", "p", "r", "s", "t", "v", "x", "f", "j", "w", "z")
        val CODAS = listOf("ng", "nh", "ch", "m", "n", "p", "t", "c")
        val VALID_VOWELS = listOf(
            "ai", "ao", "au", "ay", "ây", "âu", "eo", "êu", "ia", "iê", "iêu", "iu",
            "oi", "ôi", "ơi", "ua", "uô", "uôi", "ui", "ưa", "ưi", "ươ", "ươi", "ươu", "ưu",
            "ya", "yê", "yêu", "yu",
            "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y",
        )
        val QATT_ONSET_MAP = mapOf("p" to "b", "đ" to "d", "d" to "dz", "ngh" to "ng", "q" to "c", "k" to "c", "gh" to "g", "gi" to "dz", "f" to "ph", "j" to "tr", "w" to "v", "z" to "dz")
        /** The 22 base shapes in the order of the rhyme tables (z is the shape without strokes, written "_" in codes). */
        val SHAPES = "l z ng h g c tr d n t th nh ch dz x kh s r m b v ph".split(" ")
        val HOP_THU = mapOf("ong" to 2, "ung" to 3, "uông" to 4, "ông" to 1)
        val RHYME_SUBSTITUTES = mapOf("anh" to "ơnh", "oanh" to "uơnh")
        val OPEN_DIPHTHONG = mapOf("ia" to "iê", "ya" to "yê", "ua" to "uô", "ưa" to "ươ")
        val STOP_TWIN = mapOf("c" to "ng", "ch" to "nh", "t" to "n", "p" to "m")
        val QATT_VOWEL_MAP = mapOf(
            "a" to "a", "ă" to "aw", "â" to "aw2", "e" to "e2", "ê" to "e", "i" to "i",
            "ia" to "i2", "iê" to "i2", "o" to "o2", "ơ" to "a2", "ô" to "o", "u" to "u",
            "ư" to "y", "ua" to "u2", "uô" to "u2", "ưa" to "y2", "ươ" to "y2", "y" to "i",
            "ya" to "i2", "yê" to "i2",
        )
        val TONE_INT = mapOf("ngang" to 0, "sac" to 1, "huyen" to 2, "hoi" to 3, "nga" to 4, "nang" to 5)
        private val DIACRITIC = mapOf('̀' to "huyen", '́' to "sac", '̉' to "hoi", '̃' to "nga", '̣' to "nang")
        private val MARK = mapOf("sac" to "́", "huyen" to "̀", "hoi" to "̉", "nga" to "̃", "nang" to "̣")
        private val BARE = mapOf("dz" to "d", "d" to "đ", "z" to "", "_" to "")
        private val BACK = mapOf("_" to "", "dz" to "d", "d" to "đ", "b" to "b", "c" to "c", "g" to "g", "ng" to "ng")
        private val OPEN = mapOf("iê" to "ia", "uyê" to "uya", "uô" to "ua", "ươ" to "ưa")
        private val STOP = mapOf("ng" to "c", "nh" to "ch", "n" to "t", "m" to "p")
        private val OPEN_RE = Regex("^(.*?)(ia|ya|ua|ưa)$")
        private const val VOWEL_CHARS = "aăâeêioôơuưy"
        private const val MODIFIED_CHARS = "ăâêôơư"
    }
}
