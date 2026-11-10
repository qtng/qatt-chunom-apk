# The Kotlin port of the QATT / PA logic

`app/src/main/java/helium314/keyboard/qatt/`: a port of `js/parser.js`, `js/qatt-text.js` and `js/pa-text.js` of qtng/qatt (pure Kotlin, no Android classes; API 21 safe).

| Kotlin | JavaScript |
|---|---|
| `VietnameseParser` (`parse`, `toQuocNgu`; the four rhyme tables in `Decodings.kt`) | `VietnameseParser` |
| `QattText` (`syllable`, `word`, `cells`, `decode`, `reading`, `convert`, `recode`, `repairDeletion`) | `QattText` |
| `PaText` (`word`, `read`, `convert`, `restack`, `CELL`) | `PaText` |

## Keeping it in step with the JavaScript

The JavaScript is the reference. `tools/golden/make_golden.mjs` runs it over about 156,000 words (every rhyme × onset × tone), 25,000 cells and a few texts and writes the results to `app/src/test/resources/golden/*.tsv.gz`. `GoldenTest` (`app/src/test/java/helium314/keyboard/qatt/`) must reproduce every row exactly.

    node tools/golden/make_golden.mjs --qatt ../qatt     # after a change of the JavaScript (a checkout of qtng/qatt)
    node tools/golden/gen_tables.mjs --qatt ../qatt > app/src/main/java/helium314/keyboard/qatt/Decodings.kt   # after a change of a rhyme table

Then run the tests; a difference is a change of the JavaScript that the port does not have yet.

Known difference: `QattText.decode` of a string that is no cell at all (e.g. `"x"`) returns `null`; the JavaScript returns an object with undefined fields (the reading is `"?"` in both).

## The Chữ Nôm dictionary

`NomDictionary` (same folder) gives the candidates for a Quốc Ngữ word in the order of `VietIme` of `js/ime.js` of qtng/chunom: readings that start with the word, short remainders first, then entries with a definition (and, later, the glyphs the user chose most often: `usage`), then the order of the data. A multi-syllable word is found by its letters run together ("chữ nôm" finds `chữNôm`).

The data is `app/src/main/assets/nom/nom.txt` (about 230 KB, 37,000 glyph entries), built from `js/generated_chars.json` and `js/base_chars.json` of qtng/chunom (the data of the website, not `dict.csv`):

    node tools/nom/build_nom.mjs --chunom ../chunom       # after a change of the dictionaries of the website
    node tools/golden/make_golden.mjs --chunom ../chunom --qatt ../qatt   # golden files (also the candidates: 10,000 words)

`NomDictionaryTest` compares the first 12 candidates and the number of results for every reading of the dictionary, its prefixes of up to three letters and a few odd inputs with `VietIme.lookup` of the JavaScript.

## The composition field (logic)

`app/src/main/java/helium314/keyboard/qatt/compose/` (pure Kotlin except `AndroidSources.kt`), tests in `app/src/test/java/helium314/keyboard/qatt/compose/`:

- `ComposeField`: the text of the field and the word being typed (Telex), the candidates (PA, QATT, then Chữ Nôm, 5 places), `pick`, Space with its setting (`SpaceMode`), Enter (a line break), Backspace (a key, then a whole cell), `clear`. The cursor is always at the end.
- `SendFormatPolicy` (`SendFormat` TEXT / IMAGE): the start value and what is sent; `ChatApps`: package list, then the declared category; `GlyphSupport`: can the system show the characters.
- `AndroidSources`: the answers on a device (`EditorInfo.packageName`, `ApplicationInfo.category`, `EditorInfoCompat.getContentMimeTypes`, `Paint.hasGlyph`); each one can fail and then says "no".

Not done yet: the user interface (the button, the field, the bar, the selector and the arrow in HeliBoard's views), drawing the image and `commitContent`, storing the choice of Text/Image and the settings, moving the cursor in the field.
