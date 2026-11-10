# QATT keyboard for Android: plan

An Android keyboard (IME) that types like Gboard (several languages, autocompletion) and, in a composition field of its own, writes Chữ Nôm, Quốc Âm Tân Tự (QATT) and PA, mixed, and inserts the result as text or as an image.

Status: planning (2026-11-10). Base: HeliBoard, the app is open source under GPL-3.0.

## 1. Base keyboard

Gboard is proprietary and cannot be extended; "compatible with Gboard" only means that both are installed and the user switches with the globe key (`switchToNextInputMethod`), which we support.

The keyboard is a fork of an open-source keyboard:

- **HeliBoard** (fork of AOSP LatinIME / OpenBoard, GPL-3.0), **chosen**: closest to Gboard, dictionaries and suggestions for English, German and many more, layouts (QWERTY, QWERTZ, …), the `Combiner` architecture for input logic (Hangul exists; Telex fits there). The app is therefore open source under GPL-3.0.
- FlorisBoard (Apache-2.0, Kotlin and Compose, weaker suggestions) was the alternative.

What is special lives in a panel of our own (the composition field), so the base only has to type and suggest well.

## 2. Normal mode

```
[⚙] [✎] │ người │ ngươi │ ngừng │ …
 q  w  e  r  t  y  u  i  o  p
```

- The keyboard writes plain text into the app, with the autocompletion of the active language (Vietnamese with Telex, English, German, …).
- **✎** shows the composition field. The current word in the app is **not** carried into the field.
- **⚙** settings: languages, whether PA and QATT appear among the candidates, what Space does in the field (see below), text or image (also switchable in the field).
- Vietnamese needs Telex in normal mode too, and a word list for the suggestions (check whether HeliBoard has a usable one, else build one from our data).

## 3. Composition mode

```
[←] ┌──────────────────────────────┐ [Text|Image]
    │ 𡨸喃 [QATT] [PA] ngươi▏        │
    └──────────────────────────────┘
[PA] [QATT] [字] [字] [字]                       [↑]
 q  w  e  r  t  y  u  i  o  p
                                                 [⏎]
```

- Shown above the candidate bar, like the translation field of Gboard.
- **Selected** (a tap into the field): the keys type into the field. **Not selected** (a tap into the text field of the app): the keys type into the app again, the bar shows the normal autocompletion; the field stays with its content.
- In the field one types Telex; the current word stands underlined as Quốc Ngữ.
- The candidate bar has 5 places, always in this order: **PA, QATT, then Chữ Nôm**. PA and QATT can be hidden in ⚙; the Nôm candidates then move up.
- A tap on a candidate replaces the word. Everything can be mixed in the field.
- **Space** in the field, a setting in ⚙ (vi in brackets): *Nothing* (Không; the default: a real space, the word stays Quốc Ngữ, candidates are taken only by tapping), *PA*, *QATT*, *Chữ Nôm* (the first Nôm candidate). With PA, QATT or Chữ Nôm, Space converts the word before the cursor and is swallowed (no space between cells, as in the web editor `js/qatt-writer.js`); without a word before the cursor (or without a glyph for it) it is a real space.
- The field draws every run in its font: PA, QATT, CJK (Chữ Nôm), Latin. Horizontal text only.
- **Text | Image** switch, right of the field:
  - Text: the field looks like a normal input, with a blinking cursor; sending commits the raw text (the user needs the right font to read it; that is left to the user).
  - Image: the field looks like an image (a sticker), the cursor does not blink; sending renders the text **black on white** and inserts it with `commitContent`. If the app accepts no images (`EditorInfo.contentMimeTypes`), the raw text is committed instead.
- **Several lines and sending**:
  - The first ⏎ inserts a line break and shows a hint "⏎ again to send" (vi "⏎ lần nữa để gửi").
  - A second ⏎ directly after it sends (the line break just inserted is removed). Any other key in between ends that chance.
  - In addition, **↑** at the right end of the bar always sends.
- **←** discards the content of the field and returns to normal mode.

## 4. The logic

The logic exists in the website (`qtng/qatt`): `js/parser.js` (Telex, syllable parts, Hợp Thư), `js/qatt-text.js` (QATT cells, from U+F0000), `js/pa-text.js` (PA), and in `qtng/chunom`: `js/ime.js` and `dict.csv` (the Nôm candidates).

- **Port to Kotlin** (about 40 KB of logic; a keyboard needs low latency), not an embedded JS engine.
- **Golden tests**: a Node script runs the JS files over all syllables (Quốc Ngữ → QATT, → PA, the Nôm candidates) and writes JSON; the Kotlin tests compare against it. A change of the JS shows up as a failing test.
- **Nôm data**: a build step turns `dict.csv` / the data of `ime.js` into a compact store in the app (SQLite or a trie, sorted by frequency).
- Editing like the web editor: Backspace removes a whole QATT/PA cell with its joiners and combiners (`repairDeletion`, `tidy`).

## 5. Fonts and images

- A keyboard cannot install fonts for other apps. Inside the keyboard we draw with our own fonts (bundled in the app, from the files of cdn.qatt.org): QATT Pen, the PA font, a CJK font with the Nôm characters (Ext. B–H).
- The image: PNG (or WebP), black on white, rendered with the same fonts, offered through a `FileProvider`. To test: WhatsApp, Signal, Telegram, Messages, Gmail.

## 6. Steps

1. Fork of the base keyboard in this repository, builds and runs; Telex for Vietnamese.
2. Kotlin port of `parser.js`, `qatt-text.js`, `pa-text.js` with the golden tests from Node.
3. Nôm data build and candidate lookup.
4. The composition field: selection, candidates PA/QATT/Nôm, mixed rendering, ⏎ ⏎ / ↑, ←.
5. Sending as an image (`commitContent`) with the fallback to text.
6. Settings (⚙), Vietnamese translation of the interface, release (F-Droid / Play Store).

Later, perhaps: multi-syllable Nôm words (e.g. "người ta"), learning of frequencies, variants (Tham Thư, neutral tone), a choice of fonts for the image.
