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
        // parts of the name: any package with facebook, instagram, messenger … in it, in any case
        assertTrue(ChatApps.isChatOrSocial("com.facebook.katana"))
        assertTrue(ChatApps.isChatOrSocial("com.facebook.orca"))
        assertTrue(ChatApps.isChatOrSocial("com.instagram.barcelona"))
        assertTrue(ChatApps.isChatOrSocial("com.example.MyMessenger"))
        assertTrue(ChatApps.isChatOrSocial("com.twitter.android"))               // X
        assertFalse(ChatApps.isChatOrSocial("com.x.app"))                        // not "x" alone
        assertFalse(ChatApps.isChatOrSocial("com.example.xyz.box"))
        assertFalse(ChatApps.isChatOrSocial("com.android.timeline.offline"))     // "line" is no part of a name
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

    @Test fun choicesPerApp() {
        val c = SendFormatChoices()
        assertEquals(null, c["com.whatsapp"])
        c["com.whatsapp"] = T
        c["com.example.notes"] = I
        assertEquals(T, c["com.whatsapp"])
        assertEquals(I, c["com.example.notes"])
        assertEquals(null, c["org.telegram.messenger"])           // no choice: the start value decides
        assertEquals(null, c[null])
        c["com.whatsapp"] = I                                     // changed by the user
        assertEquals(I, c["com.whatsapp"])
        c.forget("com.whatsapp")
        assertEquals(null, c["com.whatsapp"])
        c[""] = T                                                 // no package name: nothing is stored
        assertEquals(1, c.size)
        // the same after a round trip through the settings
        val d = SendFormatChoices.deserialize(c.serialize())
        assertEquals(I, d["com.example.notes"])
        assertEquals(1, d.size)
        assertEquals(0, SendFormatChoices.deserialize(null).size)
        assertEquals(0, SendFormatChoices.deserialize("garbage\n=TEXT\npkg=NOPE").size)
        c.clear()
        assertEquals(0, c.size)
    }

    @Test fun choicesKeepTheMostRecentApps() {
        val c = SendFormatChoices(limit = 3)
        for (i in 1..4) c["app$i"] = T
        assertEquals(listOf("app2", "app3", "app4"), c.apps)
        c["app2"] = I                                             // used again: now the most recent
        c["app5"] = T
        assertEquals(listOf("app4", "app2", "app5"), c.apps)
    }
}
