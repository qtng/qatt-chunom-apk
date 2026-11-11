// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt

import kotlin.test.Test
import kotlin.test.assertEquals

/** Words after the qatt.org style guide (qatt) and the decoding qatt.org (qode): the examples of the session that rebuilt the tables of qtng/qatt (PR #738). */
class StyleGuideTest {
    private val qatt = VietnameseParser("qatt")
    private val qode = VietnameseParser("qode")
    private fun cps(s: String?) = s?.codePoints()?.toArray()?.joinToString(" ") { "F%04X".format(it - 0xF0000) } ?: "-"

    private val differ = mapOf(
        "cao" to ("F001C F009A F0007" to "F001C F009A F0083"), "ngao" to ("F0007 F009A F0007" to "F0007 F009A F0083"),
        "kêu" to ("F001C F009A F0054" to "F001C F009A F0092"), "tiêu" to ("F003F F009A F0062" to "F003F F009A F0091"),
        "lưu" to ("F0023 F009A F002A" to "F0023 F009A F002C"), "rượu" to ("F0077 F009A F0031 F00A1" to "F0077 F009A F0033 F00A1"),
        "đâu" to ("F0031 F009A F0038" to "F0031 F009A F003A"), "khuâu" to ("F0069 F009A F003F" to "F0069 F009A F0041"),
        "sau" to ("F0070 F009A F0046" to "F0070 F009A F0048"), "ngoáo" to ("F0007 F009A F000E F00A0" to "F0007 F009A F0084 F00A0"),
        "nghiêu" to ("F0007 F009A F0062" to "F0007 F009A F0091"), "xíu" to ("F0062 F009A F0070 F00A0" to "F0062 F009A F0098 F00A0"),
        "khuỷu" to ("F0069 F009A F0077 F009E" to "F0069 F009A F0099 F009E"), "hôm" to ("F000E F009A F0084" to "F000E F009A F0006"),
        "cũm" to ("F001C F009A F008B F009F" to "F001C F009A F0045 F009F"), "tôm" to ("F003F F009A F0084" to "F003F F009A F0006"),
        "tư" to ("F003F F009A F002C" to "F003F F009A F0021"), "tự" to ("F003F F009A F002C F00A1" to "F003F F009A F0021 F00A1"),
    )
    private val same = mapOf(
        "cơm" to "F001C F009A F0029", "thơm" to "F0046 F009A F0029", "người" to "F0007 F009A F0035 F009D", "mười" to "F007E F009A F0035 F009D",
        "muốn" to "F007E F009A F0096 F00A0", "một" to "F0081 F00A3", "ông" to "F0001", "ong" to "F0002 F009A F0000", "cũng" to "F001F F009A F001C F009F",
        "buồng" to "F0089 F009A F0085 F009D", "ta" to "F003F F009A F0009", "việt" to "F008C F009A F0067 F00A3", "anh" to "F0000 F009A F0026",
        "khoanh" to "F0069 F009A F0003",
    )

    @Test fun styleGuideAndDecoding() {
        for ((w, e) in differ) {
            assertEquals(e.first, cps(QattText.word(w, qatt)), "$w (qatt)")
            assertEquals(e.second, cps(QattText.word(w, qode)), "$w (qode)")
        }
        for ((w, e) in same) {
            assertEquals(e, cps(QattText.word(w, qatt)), "$w (qatt)")
            assertEquals(e, cps(QattText.word(w, qode)), "$w (qode)")
        }
    }

    @Test fun twoPlainShapesAreOnsetAndRhyme() {
        val ngao = QattText.word("ngao", qatt)!!
        assertEquals("ngao", QattText.reading(ngao, qatt))
    }
}
