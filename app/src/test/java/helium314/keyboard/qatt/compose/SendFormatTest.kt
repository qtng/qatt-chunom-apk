package helium314.keyboard.qatt.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendFormatTest {
    private val T = SendFormat.TEXT
    private val I = SendFormat.IMAGE

    @Test fun startValue() {
        // chatApp, acceptsImages, systemShowsText
        assertEquals(I, SendFormatPolicy.startValue(true, true, true))     // chat app: image even if the device could show the text
        assertEquals(I, SendFormatPolicy.startValue(true, true, false))
        assertEquals(T, SendFormatPolicy.startValue(true, false, true))
        assertEquals(T, SendFormatPolicy.startValue(true, false, false))
        assertEquals(T, SendFormatPolicy.startValue(false, true, true))    // another app: text if the system shows it
        assertEquals(I, SendFormatPolicy.startValue(false, true, false))   // else image if the field takes it
        assertEquals(T, SendFormatPolicy.startValue(false, false, false))
        assertEquals(T, SendFormatPolicy.startValue(false, false, true))
    }

    @Test fun theChoiceOfTheUserWinsEverywhere() {
        assertEquals(T, SendFormatPolicy.selected(T, true, true, false))
        assertEquals(I, SendFormatPolicy.selected(I, false, false, true))
        assertEquals(I, SendFormatPolicy.selected(null, true, true, true))
    }

    @Test fun imageFallsBackToTextWithoutImageSupport() {
        assertEquals(T, SendFormatPolicy.effective(I, false))
        assertEquals(I, SendFormatPolicy.effective(I, true))
        assertEquals(T, SendFormatPolicy.effective(T, true))
    }

    @Test fun chatApps() {
        assertTrue(ChatApps.isChatOrSocial("com.whatsapp"))
        assertTrue(ChatApps.isChatOrSocial("com.zing.zalo"))
        assertFalse(ChatApps.isChatOrSocial("com.example.notes"))
        assertFalse(ChatApps.isChatOrSocial(null))
        assertFalse(ChatApps.isChatOrSocial(""))
        assertTrue(ChatApps.isChatOrSocial("com.example.notes", extra = setOf("com.example.notes")))
        // the declared category: social counts, another one or a failure does not
        assertTrue(ChatApps.isChatOrSocial("com.example.club") { ChatApps.CATEGORY_SOCIAL })
        assertFalse(ChatApps.isChatOrSocial("com.example.club") { 7 })
        assertFalse(ChatApps.isChatOrSocial("com.example.club") { null })
        assertFalse(ChatApps.isChatOrSocial("com.example.club") { throw SecurityException("not visible") })
        // the list comes first: the category is not even asked
        assertTrue(ChatApps.isChatOrSocial("com.whatsapp") { throw IllegalStateException("must not be asked") })
    }

    @Test fun glyphSupport() {
        val qattLetter = String(Character.toChars(0xF0007))
        val nomRare = String(Character.toChars(0x219C2))
        // only the characters of other scripts are asked
        val asked = ArrayList<String>()
        assertTrue(GlyphSupport.systemShows("xin chào, 的 ${qattLetter}") { asked.add(it); true })
        assertEquals(listOf("的", qattLetter), asked)
        assertFalse(GlyphSupport.systemShows("xin chào ${qattLetter}") { it != qattLetter })
        assertTrue(GlyphSupport.systemShows("chỉ có chữ quốc ngữ") { false })        // nothing to ask
        // an empty field: the probes of the scripts that are on
        assertEquals(listOf(qattLetter, String(Character.toChars(0xF00B2)), nomRare), GlyphSupport.probes(true, true))
        assertEquals(listOf(nomRare), GlyphSupport.probes(false, false))
        assertFalse(GlyphSupport.systemShows("", true, true) { it != qattLetter })
        assertTrue(GlyphSupport.systemShows("", false, false) { it == nomRare })
    }
}
