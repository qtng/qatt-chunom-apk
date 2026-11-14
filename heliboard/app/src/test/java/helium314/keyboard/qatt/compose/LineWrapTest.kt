// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import org.junit.Test
import kotlin.test.assertEquals

class LineWrapTest {
    @Test fun latinWordIsNotBroken() {
        // the case of the screenshot: eight cells a line, the word being typed at the end
        assertEquals(listOf("喒越昜𢴑𦌒埃吶", "khó"), LineWrap.wrap("喒越昜𢴑𦌒埃吶khó", 8))
    }

    @Test fun glyphsBreakBetweenEachOther() {
        assertEquals(listOf("喒越昜𢴑𦌒埃", "吶"), LineWrap.wrap("喒越昜𢴑𦌒埃吶", 6))
    }

    @Test fun punctuationStaysWithTheWordBefore() {
        assertEquals(listOf("喒越昜𢴑𦌒", "埃?"), LineWrap.wrap("喒越昜𢴑𦌒埃?", 6))
        assertEquals(listOf("一二三四五", "(六)"), LineWrap.wrap("一二三四五(六)", 6))
    }

    @Test fun wordsAreSeparatedBySpaces() {
        assertEquals(listOf("một hai", "ba bốn"), LineWrap.wrap("một hai ba bốn", 7))
    }

    @Test fun aWordLongerThanALineIsCut() {
        assertEquals(listOf("abcdef", "gh"), LineWrap.wrap("abcdefgh", 6))
        assertEquals(listOf("喒", "abcdef", "gh"), LineWrap.wrap("喒abcdefgh", 6))
    }
}
