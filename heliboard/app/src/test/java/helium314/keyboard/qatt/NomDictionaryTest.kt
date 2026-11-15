package helium314.keyboard.qatt

import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The candidates against VietIme.lookup of js/ime.js (qtng/chunom): golden/nom.tsv.gz, made by tools/golden/make_golden.mjs.
 * The keyboard has more than the website (tools/nom/add_dict_csv.py merged the entries of dict.csv into nom.txt): every candidate of the website must be there
 * in the same order (the extra ones lie between them or after them), and the number of candidates may only be higher.
 */
class NomDictionaryTest {
    private fun dictionary(): NomDictionary {
        val file = listOf("src/main/assets/nom/nom.txt", "app/src/main/assets/nom/nom.txt").map { File(it) }.firstOrNull { it.exists() }
            ?: fail("assets/nom/nom.txt not found (run tools/nom/build_nom.mjs)")
        return file.bufferedReader().use { NomDictionary().load(it) }
    }

    @Test fun candidatesAsInTheWebsite() {
        val d = dictionary()
        val rows = GZIPInputStream(javaClass.getResourceAsStream("/golden/nom.tsv.gz") ?: fail("missing golden/nom.tsv.gz")).bufferedReader().readLines().filter { it.isNotEmpty() }
        assertTrue(rows.size > 5000, "golden rows: ${rows.size}")
        var bad = 0
        val shown = StringBuilder()
        for (line in rows) {
            val c = line.split("\t")
            val word = c[0].removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")   // JSON.stringify of the probe
            val all = d.lookup(word)
            val got = all.map { "${it.glyph}|${it.reading}|${if (it.defined) 1 else 0}" }
            val expectedCount = c[1].toInt()
            val expected = c.drop(2)
            // the candidates of the website are an ordered part of ours
            var at = 0
            val inOrder = expected.all { e -> val i = got.subList(at, got.size).indexOf(e); if (i < 0) false else { at += i + 1; true } }
            if (!inOrder || all.size < expectedCount) {
                bad++
                if (bad <= 5) shown.append("\n  '$word': expected (website) ${expected.take(4)} of $expectedCount but was ${got.take(4)} of ${all.size}")
            }
        }
        assertTrue(bad == 0, "$bad of ${rows.size} words differ$shown")
    }

    @Test fun details() {
        val d = dictionary()
        assertTrue(d.size > 30000, "pairs: ${d.size}")
        assertEquals("chữ nôm", NomDictionary.readingOf("chữNôm"))
        assertEquals("𡦂喃", d.lookup("chữ nôm").first().glyph)
        assertTrue(d.lookup("   ").isEmpty())
    }

    /** The search by the letters without marks (js/ime.js does the same; the golden file covers only readings as written). */
    @Test fun lettersWithoutMarks() {
        val d = NomDictionary().load("""
            nước	渃!
            nuộc	縟
            nam	南!
            năm	𢆥
            nắm	揇
            nấm	菍
            hoà	和!
            đường	塘!
            dương	洋!
            chữNôm	𡦂喃!
        """.trimIndent().reader())
        fun glyphs(w: String) = d.lookup(w).map { it.glyph }
        assertEquals(listOf("渃", "縟"), glyphs("nuoc"))                      // nước (defined) before nuộc
        assertEquals(listOf("渃"), glyphs("nươc"))                            // a typed mark must be there
        assertEquals(listOf("南", "𢆥", "揇", "菍"), glyphs("nam"))             // written as typed first, then ă before â
        assertEquals(listOf("揇"), glyphs("nắm"))                             // a typed tone and mark must be there
        assertEquals(listOf("洋", "塘"), glyphs("duong"))                      // d finds đ too; both defined: d before đ (the alphabet)
        assertEquals(listOf("塘"), glyphs("đuong"))                           // a typed đ only finds đ
        assertEquals(glyphs("hoà"), glyphs("hòa"))                            // the tone counts for the syllable, wherever it is written
        assertEquals(listOf("𡦂喃"), glyphs("chunom"))
        assertEquals(listOf("𡦂喃"), glyphs("chữ n"))
    }
}
