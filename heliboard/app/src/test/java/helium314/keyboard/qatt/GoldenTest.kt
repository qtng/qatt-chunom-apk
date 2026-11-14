package helium314.keyboard.qatt

import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The Kotlin port against the JavaScript of qtng/qatt: the golden files in resources/golden are made by tools/golden/make_golden.mjs
 * (node tools/golden/make_golden.mjs) and must be reproduced exactly. "-" in a file is null, "." the empty string, characters are lists of hex code points.
 */
class GoldenTest {
    // in the order of VietnameseParser.DECODINGS of parser.js (the columns of the golden files); qatt is the qatt.org style guide, qode the decoding qatt.org
    private val names = listOf("qode", "qatt", "omniglot")
    private val parsers = names.map { VietnameseParser(it) }
    private val p = parsers[names.indexOf("qatt")]

    private fun lines(name: String): List<String> {
        val stream = javaClass.getResourceAsStream("/golden/$name") ?: fail("missing resource golden/$name (run tools/golden/make_golden.mjs)")
        return GZIPInputStream(stream).bufferedReader().readLines().filter { it.isNotEmpty() }
    }

    private fun s(v: String?) = when (v) { null -> "-"; "" -> "."; else -> v }
    private fun cps(v: String?) = if (v == null) "-" else v.codePoints().toArray().joinToString(" ") { it.toString(16).uppercase() }.ifEmpty { "." }
    private fun fromCps(v: String) = if (v == "-" || v == ".") "" else v.split(" ").joinToString("") { String(Character.toChars(it.toInt(16))) }

    /** compares everything and reports the number of differences with the first few */
    private fun check(what: String, rows: List<Pair<String, () -> List<Pair<String, String>>>>) {
        var bad = 0
        val shown = StringBuilder()
        for ((id, f) in rows) {
            val diffs = f().filter { it.first != it.second }
            if (diffs.isNotEmpty()) {
                bad++
                if (bad <= 8) shown.append("\n  $id: expected ${diffs.first().first} but was ${diffs.first().second}")
            }
        }
        assertTrue(bad == 0, "$what: $bad of ${rows.size} rows differ$shown")
    }

    private val pa0 = 10 + names.size   // the PA columns follow the readings of every decoding

    @Test fun words() {
        val rows = lines("words.tsv.gz").map { line ->
            val c = line.split("\t")
            c[0] to {
                val w = c[0]
                val r = p.parse(w)
                if (c[1] == "-") listOf(c[1] to (if (r == null) "-" else "parsed"))
                else if (r == null) listOf("parsed" to "-")
                else {
                    val qatt = QattText.word(w, p)
                    val pa = PaText.word(w, p)
                    listOf(
                        c[1] to s(r.code), c[2] to s(r.qatt), c[3] to s(r.hop), c[4] to s(r.onset), c[5] to s(r.glide), c[6] to s(r.vowel), c[7] to s(r.coda), c[8] to r.tone,
                        c[9] to cps(qatt),
                    ) + parsers.mapIndexed { i, q -> c[10 + i] to (if (qatt == null) "-" else s(QattText.reading(qatt, q))) } + listOf(
                        c[pa0] to cps(pa), c[pa0 + 1] to cps(PaText.word(w, p, PaText.Options(stacked = false))), c[pa0 + 2] to cps(PaText.word(w, p, PaText.Options(ngang = true))),
                        c[pa0 + 3] to s(PaText.read(pa ?: "", p)),
                    )
                }
            }
        }
        assertTrue(rows.size > 100000, "golden words: ${rows.size}")
        check("words", rows)
    }

    @Test fun cells() {
        val rows = lines("cells.tsv.gz").map { line ->
            val c = line.split("\t")
            c[0] to {
                val cell = fromCps(c[0])
                val d = QattText.decode(cell)
                val json = if (d == null) "-" else "{\"onset\":\"${d.onset}\",\"rhyme\":\"${d.rhyme}\",\"tone\":${d.tone}" + (d.hop?.let { ",\"hop\":\"$it\"" } ?: "") + "}"
                listOf(c[1] to json) + parsers.mapIndexed { i, q -> c[2 + i] to s(QattText.reading(cell, q)) }
            }
        }
        assertTrue(rows.size > 10000, "golden cells: ${rows.size}")
        check("cells", rows)
    }

    @Test fun misc() {
        val omni = parsers[names.indexOf("omniglot")]
        val all = lines("misc.tsv.gz")
        val rows = all.map { line ->
            val c = line.split("\t")
            if (c[0] == "convert") c[1] to {
                val t = c[1]
                val q = QattText.convert(t, p)
                val cellsJson = "[" + QattText.cells(q).joinToString(",") { "{\"cell\":\"${it.cell}\",\"index\":${it.index}}" } + "]"
                listOf(
                    c[2] to cps(q), c[3] to cps(QattText.recode(q, p, omni)), c[4] to cps(QattText.recode(q, omni, p)),
                    c[5] to cellsJson,
                    c[6] to cps(PaText.convert(t, p)), c[7] to cps(PaText.convert(t, p, PaText.Options(stacked = false))), c[8] to cps(PaText.convert(t, p, PaText.Options(ngang = true))),
                    c[9] to cps(PaText.read(PaText.convert(t, p), p)), c[10] to cps(PaText.restack(t, p)), c[11] to cps(PaText.restack(q, p)),
                )
            } else c[1] + "@" + c[2] + "/" + c[3] to {
                val text = fromCps(c[1])
                val caret = c[2].toInt(); val len = c[3].toInt()
                val after = text.substring(0, caret) + text.substring(caret + len)
                val r = QattText.repairDeletion(text, after, caret)
                listOf(c[4] to (if (r == null) "null" else "{\"v\":\"${cps(r.value)}\",\"c\":${r.caret}}"))
            }
        }
        assertTrue(rows.size > 400, "golden misc: ${rows.size}")
        check("misc", rows)
    }
}
