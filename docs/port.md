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
