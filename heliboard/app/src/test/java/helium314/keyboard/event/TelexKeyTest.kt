package helium314.keyboard.event

import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** TelexKey against VietIme.telex of ime.js: resources/golden/telex.tsv.gz (tools/golden/make_telex_golden.mjs). */
class TelexKeyTest {
    @Test
    fun matchesImeJs() {
        val stream = javaClass.getResourceAsStream("/golden/telex.tsv.gz") ?: fail("missing resource golden/telex.tsv.gz")
        val rows = GZIPInputStream(stream).bufferedReader().readLines().filter { it.isNotEmpty() }
        val bad = ArrayList<String>()
        for (line in rows) {
            val (raw, expected) = line.split("\t").let { it[0] to it.getOrElse(1) { "" } }
            val got = TelexKey.word(raw)
            if (got != expected) bad.add("$raw: ime.js=$expected kotlin=$got")
        }
        assertEquals(0, bad.size, "${bad.size} of ${rows.size} differ, e.g. " + bad.take(15).joinToString(" | "))
    }

    @Test
    fun examples() {
        fun w(raw: String, expected: String) = assertEquals(expected, TelexKey.word(raw), raw)
        w("vieetj", "việt"); w("nguwowif", "người"); w("dduwowcj", "được"); w("toans", "toán")
        // stateless: the key works on the text that is there
        assertEquals("việt", TelexKey.key("viêt", "j")); assertEquals("viết", TelexKey.key("viêt", "s"))
        assertEquals(null, TelexKey.key("", "s")); assertEquals(null, TelexKey.key("xin ", "s"))
    }
}
