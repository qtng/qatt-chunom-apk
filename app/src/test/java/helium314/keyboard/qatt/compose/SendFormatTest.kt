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
        assertEquals(SendChoice.TEXT, SendFormatPolicy.selected(SendChoice.TEXT, true, true, false))
        assertEquals(SendChoice.IMAGE, SendFormatPolicy.selected(SendChoice.IMAGE, false, false, true))
        assertEquals(SendChoice.IMAGE, SendFormatPolicy.selected(null, true, true, true))
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
        assertFalse(ChatApps.isChatOrSocial("com.gonzalo.app"))                  // ".zalo" needs the dot
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
        c["com.whatsapp"] = SendChoice.TEXT
        c["com.example.notes"] = SendChoice(I, ImageStyle.WHITE_ON_BLACK)
        assertEquals(SendChoice.TEXT, c["com.whatsapp"])
        assertEquals(SendChoice(I, ImageStyle.WHITE_ON_BLACK), c["com.example.notes"])
        assertEquals(null, c["org.telegram.messenger"])           // no choice: the start value decides
        assertEquals(null, c[null])
        c["com.whatsapp"] = SendChoice.IMAGE                      // changed by the user
        assertEquals(SendChoice(I, ImageStyle.BLACK_ON_WHITE), c["com.whatsapp"])
        c.forget("com.whatsapp")
        assertEquals(null, c["com.whatsapp"])
        c[""] = SendChoice.TEXT                                   // no package name: nothing is stored
        assertEquals(1, c.size)
        // the same after a round trip through the settings
        val d = SendFormatChoices.deserialize(c.serialize())
        assertEquals(SendChoice(I, ImageStyle.WHITE_ON_BLACK), d["com.example.notes"])
        assertEquals(1, d.size)
        assertEquals(0, SendFormatChoices.deserialize(null).size)
        assertEquals(0, SendFormatChoices.deserialize("garbage\n=TEXT\npkg=NOPE").size)
        assertEquals(SendChoice.IMAGE, SendFormatChoices.deserialize("pkg=IMAGE\nother=IMAGE:NOPE")["pkg"])   // an older or unknown style: the default
        assertEquals(SendChoice.IMAGE, SendFormatChoices.deserialize("other=IMAGE:NOPE")["other"])
        c.clear()
        assertEquals(0, c.size)
    }

    @Test fun choicesKeepTheMostRecentApps() {
        val c = SendFormatChoices(limit = 3)
        for (i in 1..4) c["app$i"] = SendChoice.TEXT
        assertEquals(listOf("app2", "app3", "app4"), c.apps)
        c["app2"] = SendChoice.IMAGE                              // used again: now the most recent
        c["app5"] = SendChoice.TEXT
        assertEquals(listOf("app4", "app2", "app5"), c.apps)
    }

    @Test fun selectorEntriesAndStyles() {
        assertEquals(1 + ImageStyle.values().size, SendChoice.ALL.size)
        assertEquals(SendChoice.TEXT, SendChoice.ALL[0])
        assertEquals(ImageStyle.BLACK_ON_WHITE, SendChoice.ALL[1].style)         // the order of the first entries is kept
        assertEquals(ImageStyle.WHITE_ON_BLACK, SendChoice.ALL[2].style)
        assertTrue(SendChoice.ALL.drop(1).all { it.format == SendFormat.IMAGE })
        assertEquals(0xFF000000.toInt(), ImageStyle.BLACK_ON_WHITE.foreground)
        assertEquals(0xFFFFFFFF.toInt(), ImageStyle.BLACK_ON_WHITE.background)
        assertEquals(ImageStyle.BLACK_ON_WHITE.foreground, ImageStyle.WHITE_ON_BLACK.background)
        // solid colours and gradients
        assertFalse(ImageStyle.GOLD_ON_DARK.isGradient)
        assertTrue(ImageStyle.PINK_GRADIENT.isGradient)
        assertTrue(ImageStyle.values().filter { it.isGradient }.all { it.backgroundEnd != it.background })
        // the text stands out from the background (not the same colour)
        assertTrue(ImageStyle.values().all { it.foreground != it.background })
        // the names are stored: a few must never change
        assertEquals(listOf("BLACK_ON_WHITE", "WHITE_ON_BLACK"), ImageStyle.values().take(2).map { it.name })
        // no choice: the start value in the default style
        assertEquals(SendChoice(I, ImageStyle.WHITE_ON_BLACK), SendFormatPolicy.selected(null, true, true, true, ImageStyle.WHITE_ON_BLACK))
        assertEquals(SendChoice.TEXT, SendFormatPolicy.selected(null, false, false, true))
        assertEquals(SendChoice(I, ImageStyle.WHITE_ON_BLACK), SendFormatPolicy.selected(SendChoice(I, ImageStyle.WHITE_ON_BLACK), false, false, true))
    }

    @Test fun everyStyleSurvivesTheSettings() {
        val c = SendFormatChoices()
        ImageStyle.values().forEachIndexed { i, st -> c["app$i"] = SendChoice(I, st) }
        val d = SendFormatChoices.deserialize(c.serialize())
        ImageStyle.values().forEachIndexed { i, st -> assertEquals(SendChoice(I, st), d["app$i"]) }
    }

    @Test fun fonts() {
        assertEquals(listOf("QATT Pen", "Migoi", "Phong Thảo"), ComposeFont.values().map { it.label })
        assertTrue(ComposeFont.values().all { it.letters.endsWith(".ttf") && it.pa.endsWith(".ttf") && it.nom.endsWith(".ttf") })
        assertEquals(ComposeFont.values().size * 3, ComposeFont.values().flatMap { listOf(it.letters, it.pa, it.nom) }.toSet().size)   // no file twice
    }
}
