package helium314.keyboard.qatt.vi

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViSuggesterTest {
    private val s = ViSuggester().load(File("src/main/assets/vi/words.txt").takeIf { it.exists() }?.reader()
        ?: File("app/src/main/assets/vi/words.txt").reader())

    @Test
    fun completes() {
        val r = s.complete("ngư")
        assertTrue(r.isNotEmpty() && r.all { it.startsWith("ngư") && it != "ngư" }, r.toString())
        assertEquals(emptyList(), s.complete(""))
        assertEquals(emptyList(), s.complete("abcdefgh"))        // more than seven letters
        assertTrue(s.complete("c").first().startsWith("c"))
        assertTrue(s.complete("Ngư").all { it.first().isUpperCase() })
        assertTrue(s.complete("NGƯ").all { it == it.uppercase() })
        assertTrue(s.contains("việt") || s.contains("người"))
    }
}
