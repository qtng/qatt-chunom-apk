package helium314.keyboard.qatt.compose

import helium314.keyboard.qatt.PaText
import helium314.keyboard.qatt.QattText
import helium314.keyboard.qatt.VietnameseParser
import kotlin.test.Test
import kotlin.test.assertEquals

class ScriptRunsTest {
    private val parser = VietnameseParser("qatt")

    @Test fun scripts() {
        assertEquals(Script.OTHER, ScriptRuns.scriptOf('a'.code))
        assertEquals(Script.OTHER, ScriptRuns.scriptOf('ệ'.code))
        assertEquals(Script.CJK, ScriptRuns.scriptOf('喃'.code))
        assertEquals(Script.CJK, ScriptRuns.scriptOf(0x219C2))        // Ext. B
        assertEquals(Script.CJK, ScriptRuns.scriptOf('、'.code))
        assertEquals(Script.QATT, ScriptRuns.scriptOf(0xF0000))
        assertEquals(Script.QATT, ScriptRuns.scriptOf(0xF00A3))
        assertEquals(Script.OTHER, ScriptRuns.scriptOf(0xF00A4))
        assertEquals(Script.PA, ScriptRuns.scriptOf(0xF00B2))
        assertEquals(Script.PA, ScriptRuns.scriptOf(0xF00EE))
        assertEquals(Script.OTHER, ScriptRuns.scriptOf(0xE000))       // private use of the BMP
    }

    @Test fun runs() {
        assertEquals(emptyList(), ScriptRuns.split(""))
        assertEquals(listOf(ScriptRuns.Run(0, 3, Script.OTHER)), ScriptRuns.split("xin"))
        val qatt = QattText.word("tự", parser)!!
        val pa = PaText.word("tự", parser)!!
        val text = "xin $qatt$pa\n喃𡦂 ok"
        val runs = ScriptRuns.split(text)
        assertEquals(listOf(Script.OTHER, Script.QATT, Script.PA, Script.OTHER, Script.CJK, Script.OTHER), runs.map { it.script })
        // the runs cover the whole text without a gap
        assertEquals(0, runs.first().start)
        assertEquals(text.length, runs.last().end)
        for ((a, b) in runs.zipWithNext()) assertEquals(a.end, b.start)
        assertEquals(qatt.length, runs[1].end - runs[1].start)
        assertEquals(pa.length, runs[2].end - runs[2].start)
        assertEquals("喃𡦂".length, runs[4].end - runs[4].start)     // a surrogate pair stays whole
    }
}
