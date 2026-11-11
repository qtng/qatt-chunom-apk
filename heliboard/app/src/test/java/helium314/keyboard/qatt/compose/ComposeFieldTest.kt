package helium314.keyboard.qatt.compose

import helium314.keyboard.qatt.NomDictionary
import helium314.keyboard.qatt.PaText
import helium314.keyboard.qatt.QattText
import helium314.keyboard.qatt.VietnameseParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ComposeFieldTest {
    private val parser = VietnameseParser("qatt")
    private val nom = listOf("src/main/assets/nom/nom.txt", "app/src/main/assets/nom/nom.txt").map { File(it) }.firstOrNull { it.exists() }
        ?.bufferedReader()?.use { NomDictionary().load(it) } ?: fail("assets/nom/nom.txt not found")
    private fun field(settings: ComposeSettings = ComposeSettings()) = ComposeField(parser, nom, settings)

    @Test fun telexInTheField() {
        val f = field()
        f.type("nguwowif")
        assertEquals("người", f.word)
        assertEquals("người", f.text)
        assertEquals(0, f.composingStart)
        f.type(' '.code); f.type("Vieetj")
        assertEquals("người Việt", f.text.replace("  ", " "))
    }

    @Test fun candidatesInTheOrderPaQattNom() {
        val f = field()
        f.type("nguwowif")
        val c = f.candidates()
        assertEquals(5, c.size)
        assertEquals(listOf(Kind.PA, Kind.QATT, Kind.NOM, Kind.NOM, Kind.NOM), c.map { it.kind })
        assertEquals(PaText.word("người", parser), c[0].text)
        assertEquals(QattText.word("người", parser), c[1].text)
        assertEquals(nom.lookup("người").take(3).map { it.glyph }, c.drop(2).map { it.text })
        assertTrue(c.drop(2).all { it.reading != null })
    }

    @Test fun moreCandidatesThanPlaces() {
        val f = field()
        f.type("nguwowif")
        val all = f.allCandidates()
        assertTrue(all.size > 5, "all: ${all.size}")
        assertTrue(f.hasMoreCandidates())
        assertEquals(all.take(5), f.candidates())
        assertEquals(all.map { it.text }.toSet().size, all.size)                 // no candidate twice
        assertTrue(all.size <= 2 + ComposeField.MAX_NOM)
        assertEquals(listOf(Kind.PA, Kind.QATT), all.take(2).map { it.kind })
        assertTrue(all.drop(2).all { it.kind == Kind.NOM })
        // a candidate from the list that is opened
        val sixth = all[7]
        assertEquals(sixth, f.pick(7))
        assertEquals(sixth.text, f.text)
        // a word with few candidates: nothing more
        val g = field()
        g.type("hello")
        assertFalse(g.hasMoreCandidates())
        assertTrue(g.allCandidates().size <= 5)
    }

    @Test fun hiddenScriptsMakeRoomForNom() {
        val f = field(ComposeSettings(pa = false))
        f.type("nguwowif")
        assertEquals(listOf(Kind.QATT, Kind.NOM, Kind.NOM, Kind.NOM, Kind.NOM), f.candidates().map { it.kind })
        f.settings = ComposeSettings(pa = false, qatt = false)
        assertEquals(List(5) { Kind.NOM }, f.candidates().map { it.kind })
        assertEquals(nom.lookup("người").take(5).map { it.glyph }, f.candidates().map { it.text })
    }

    @Test fun noCandidatesForNothingOrForAWordWithoutGlyphs() {
        val f = field()
        assertTrue(f.candidates().isEmpty())
        f.type("hello")
        assertTrue(f.candidates().none { it.kind != Kind.NOM })
    }

    @Test fun pickReplacesTheWord() {
        val f = field()
        f.type("tuwj")
        val first = assertNotNull(f.pick(0))
        assertEquals(Kind.PA, first.kind)
        assertEquals(first.text, f.text)
        assertEquals("", f.word)
        assertEquals(null, f.pick(0))
        f.type("tuwj")
        f.pick(2)
        assertEquals(first.text + nom.lookup("tự").first().glyph, f.text)
    }

    @Test fun spaceNothingIsARealSpace() {
        val f = field()
        f.type("xin"); f.space(); f.type("chaof"); f.space()
        assertEquals("xin chào ", f.text)
    }

    @Test fun spaceConvertsAndIsSwallowed() {
        for ((mode, conv) in listOf<Pair<SpaceMode, (String) -> String?>>(
            SpaceMode.PA to { PaText.word(it, parser) }, SpaceMode.QATT to { QattText.word(it, parser) }, SpaceMode.NOM to { nom.lookup(it).firstOrNull()?.glyph })) {
            val f = field(ComposeSettings(space = mode))
            f.type("xin"); f.space(); f.type("chaof"); f.space()
            assertEquals(conv("xin") + conv("chào"), f.text, "$mode")
            f.space()                                   // no word before the cursor: a real space
            assertEquals(conv("xin") + conv("chào") + " ", f.text, "$mode")
        }
    }

    @Test fun spaceWithoutAGlyphIsARealSpace() {
        val f = field(ComposeSettings(space = SpaceMode.PA))
        f.type("hello"); f.space()
        assertEquals("hello ", f.text)
    }

    @Test fun enterAndOtherCharacters() {
        val f = field()
        f.type("xin"); f.enter(); f.type("chaof")
        assertEquals("xin\nchào", f.text)
        assertEquals(4, f.composingStart)
        f.type("."); f.type("a")
        assertEquals("xin\nchào.a", f.text)
    }

    @Test fun backspaceDeletesCharactersThenWholeCells() {
        val f = field(ComposeSettings(space = SpaceMode.QATT))
        f.type("tuwj"); f.backspace()
        assertEquals("t", f.word)
        f.backspace()
        assertTrue(f.isEmpty)
        f.type("tuwj"); f.space()
        val cell = f.text
        assertTrue(cell.codePointCount(0, cell.length) > 1)       // onset, joiner, rhyme, tone: one cell
        f.type("a"); f.space()                                     // the "a" is a QATT cell too
        assertTrue(f.text.length > cell.length)
        f.backspace()                                              // only the cell of the "a" goes
        assertEquals(cell, f.text)
        f.backspace()
        assertEquals("", f.text)

        val g = field(ComposeSettings(space = SpaceMode.PA))
        g.type("tuwj"); g.space(); g.type("xin"); g.space()
        g.backspace()
        assertEquals(PaText.word("tự", parser), g.text)
        g.backspace(); g.backspace()
        assertTrue(g.isEmpty)
    }

    @Test fun clear() {
        val f = field()
        f.type("xin"); f.space(); f.type("cha")
        f.clear()
        assertTrue(f.isEmpty)
        assertEquals("", f.text)
    }

    // ---------- the cursor ----------

    @Test fun typingAtTheCursor() {
        val f = field()
        f.type("xin"); f.space(); f.type("chaof")
        assertEquals(8, f.cursor)
        f.moveLeft(); f.moveLeft(); f.moveLeft(); f.moveLeft(); f.moveLeft()            // behind "xin"
        assertEquals(3, f.cursor)
        f.type("h")                                                                      // "xinh chào"
        assertEquals("xinh chào", f.text)
        assertEquals(4, f.cursor)
        f.moveToEnd()
        assertEquals(9, f.cursor)
        f.moveRight()                                                                    // nothing to the right
        assertEquals(9, f.cursor)
        f.moveToStart(); f.moveLeft()
        assertEquals(0, f.cursor)
        f.type("a")
        assertEquals("axinh chào", f.text)
    }

    @Test fun telexWorksAtTheCursor() {
        val f = field()
        f.type("xin"); f.type(" "); f.type("chao")
        for (i in 0 until 5) f.moveLeft()                                                // behind "xin"
        assertEquals(3, f.cursor)
        f.type("f")                                                                      // the tone goes to the word before the cursor
        assertEquals("xìn chao", f.text)
        assertEquals(3, f.cursor)
    }

    @Test fun backspaceAtTheCursorDeletesBeforeIt() {
        val f = field()
        f.type("xin chao")
        f.moveLeft(); f.moveLeft()                                                       // between "h" and "a"
        f.backspace()
        assertEquals("xin cao", f.text)
        assertEquals(5, f.cursor)
        f.moveToStart(); f.backspace()                                                   // nothing before the cursor
        assertEquals("xin cao", f.text)
    }

    @Test fun noCandidatesInsideAWordButAfterIt() {
        val f = field()
        f.type("nguwowif")
        assertTrue(f.candidates().isNotEmpty())
        f.moveLeft()                                                                     // inside the word: a letter follows
        assertTrue(f.candidates().isEmpty())
        f.moveRight()
        assertTrue(f.candidates().isNotEmpty())
        f.type(" "); f.moveLeft()                                                        // behind the word, a space follows: candidates again
        assertEquals("người", f.word)
        assertTrue(f.candidates().isNotEmpty())
    }

    @Test fun pickReplacesTheWordBeforeTheCursorOnly() {
        val f = field()
        f.type("tuwj"); f.type(" "); f.type("xin")
        f.moveToStart(); f.moveRight(); f.moveRight()                                    // behind "tự"? the cells are single characters
        assertEquals(2, f.cursor)
        assertEquals("tự", f.word)
        val first = assertNotNull(f.pick(0))
        assertEquals(first.text + " xin", f.text)
        assertEquals(first.text.length, f.cursor)
    }

    @Test fun cellsAreNotSplitByTheCursor() {
        val f = field(ComposeSettings(space = SpaceMode.QATT))
        f.type("tuwj"); f.space(); f.type("a"); f.space()
        val all = f.text
        val cell1 = QattText.word("tự", parser)!!
        assertTrue(all.startsWith(cell1))
        f.moveToStart()
        f.moveRight()                                                                    // behind the whole first cell
        assertEquals(cell1.length, f.cursor)
        f.moveLeft()
        assertEquals(0, f.cursor)
        f.setCursor(1)                                                                   // inside the first cell: to the nearest border
        assertTrue(f.cursor == 0 || f.cursor == cell1.length)
        f.setCursor(cell1.length)
        f.backspace()                                                                    // the whole cell before the cursor
        assertEquals(all.substring(cell1.length), f.text)
        assertEquals(0, f.cursor)
    }

    @Test fun setCursorClampsAndClearResets() {
        val f = field()
        f.type("xin")
        f.setCursor(99)
        assertEquals(3, f.cursor)
        f.setCursor(-5)
        assertEquals(0, f.cursor)
        f.setCursor(2)
        f.clear()
        assertEquals(0, f.cursor)
        assertTrue(f.isEmpty)
    }

    @Test fun spaceConvertsTheWordBeforeTheCursorInTheMiddle() {
        val f = field(ComposeSettings(space = SpaceMode.NOM))
        f.type("xin"); f.type(" "); f.type("chaof")
        for (i in 0 until 5) f.moveLeft()                                                // behind "xin"
        assertEquals(3, f.cursor)
        f.space()
        val glyph = nom.lookup("xin").first().glyph
        assertEquals(glyph + " chào", f.text)
        assertEquals(glyph.length, f.cursor)
    }
}
