package helium314.keyboard.qatt

import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** The candidates against VietIme.lookup of js/ime.js (qtng/chunom): golden/nom.tsv.gz, made by tools/golden/make_golden.mjs. */
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
            val got = listOf(all.size.toString()) + all.take(12).map { "${it.glyph}|${it.reading}|${if (it.defined) 1 else 0}" }
            val expected = c.drop(1)
            if (got != expected) {
                bad++
                if (bad <= 5) shown.append("\n  '$word': expected ${expected.take(4)} but was ${got.take(4)}")
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
}
