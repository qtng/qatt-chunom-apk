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
[←] ┌───────────────────────────────┐
    │ 𡨸喃 [QATT] [PA] ngươi▏         │
    └───────────────────────────────┘
[PA] [QATT] [字] [字] [字]      [Text|Image] [↑]
 q  w  e  r  t  y  u  i  o  p
                                      [⏎]
```

- Shown above the candidate bar, like the translation field of Gboard.
- **Selected** (a tap into the field): the keys type into the field. **Not selected** (a tap into the text field of the app): the keys type into the app again, the bar shows the normal autocompletion; the field stays with its content.
- In the field one types Telex; the current word stands underlined as Quốc Ngữ.
- The candidate bar has 5 places, always in this order: **PA, QATT, then Chữ Nôm**. PA and QATT can be hidden in ⚙; the Nôm candidates then move up.
- A tap on a candidate replaces the word. Everything can be mixed in the field.
- **More candidates:** a triangle down right of the five candidates (only when there are more) opens the rest as a grid in the place of the keyboard, as in Gboard; a tap takes one and closes the grid, the triangle (now up) closes it too.
- **Font** of the field and of the images, a setting in ⚙: QATT Pen (default, in the app: the big font of 27 MB plus the PA font, because PA is not in the big fonts), Migoi or Phong Thảo (42 and 41 MB, not in the app). The fonts change often, so the keyboard **downloads and updates them itself** from cdn.qatt.org when the user asks (⚙), straight into its private folder; it asks the server with the ETag of its version first, so an update that finds nothing new costs nothing. This is the only network use of the keyboard (permission INTERNET, HTTPS and cdn.qatt.org only, nothing sent but the file address). A file the user already has can be imported too. WOFF2 slices like the Notes app (11.7 MB for QATT Pen) are left for later: Android cannot read WOFF2.
- **Space** in the field, a setting in ⚙ (vi in brackets): *Nothing* (Không; the default: a real space, the word stays Quốc Ngữ, candidates are taken only by tapping), *PA*, *QATT*, *Chữ Nôm* (the first Nôm candidate). With PA, QATT or Chữ Nôm, Space converts the word before the cursor and is swallowed (no space between cells, as in the web editor `js/qatt-writer.js`); without a word before the cursor (or without a glyph for it) it is a real space.
- The field draws every run in its font: PA, QATT, CJK (Chữ Nôm), Latin. Horizontal text only.
- **Sending** is only done by the **↑** button at the right end of the bar. ⏎ in the field is an ordinary line break (several lines are allowed); there is no "⏎ twice to send".
- **Text | Image** selector, left of ↑ (the format of sending):
  - Text: the field looks like a normal input, with a blinking cursor; ↑ commits the raw text (to read it, the system needs the fonts).
  - Image: the field looks like an image (a sticker), the cursor does not blink; ↑ renders the text, by default **black on white**, or **white on black** (the selector is a dropdown: Text, then Image in a style: black on white, white on black, **black on rice paper** (the classic Asian look), gold on dark, red on cream, pink / sunset / ocean gradient; the style is stored with the choice per app, a default style is a setting) and inserts it with `commitContent`. If the app accepts no images (`EditorInfo.contentMimeTypes`), the raw text is committed instead.
  - **Start value** (as long as the user has not chosen), decided again for every input field:
    1. **Image** if the app is a chat or social app (see below) and the field accepts images, even if this device could show the text (the recipient often cannot);
    2. else **Text** if the system can show the characters with its fonts;
    3. else **Image** if the field accepts images; else Text.
  - **Which app is it?** `EditorInfo.packageName` (no permission needed), in three steps (`ChatApps`): (1) **parts of the name** (lower case): `facebook`, `instagram`, `messenger`, `whatsapp`, `telegram`, `discord`, `snapchat`, `twitter` (this is X: its package is still com.twitter.android; no match on "x" alone, too many false positives), `viber`, `.zalo`, `linkedin`, `pinterest`, `tumblr`, `mastodon`; (2) a **list** of packages whose name has no such part (Signal, Google and Samsung Messages, LINE, Reddit, Slack, Teams, WeChat, TikTok; from memory, to check on a device), extendable in ⚙; (3) the **declared category** `ApplicationInfo.category == CATEGORY_SOCIAL` (API 26); whether the keyboard may read it of another app depends on package visibility (Android 11+): if it cannot, the answer is "no".
- **←** discards the content of the field and returns to normal mode.

## 4. The logic

The logic exists in the website (`qtng/qatt`): `js/parser.js` (Telex, syllable parts, Hợp Thư), `js/qatt-text.js` (QATT cells, from U+F0000), `js/pa-text.js` (PA), and in `qtng/chunom`: `js/ime.js` with `js/generated_chars.json` and `js/base_chars.json` (the Nôm candidates).

- **Port to Kotlin** (about 40 KB of logic; a keyboard needs low latency), not an embedded JS engine.
- **Golden tests**: a Node script runs the JS files over all syllables (Quốc Ngữ → QATT, → PA, the Nôm candidates) and writes JSON; the Kotlin tests compare against it. A change of the JS shows up as a failing test.
- **Nôm data**: a build step turns `dict.csv` / the data of `ime.js` into a compact store in the app (SQLite or a trie, sorted by frequency).
- Editing like the web editor: Backspace removes a whole QATT/PA cell with its joiners and combiners (`repairDeletion`, `tidy`).

## 5. Fonts and images

- A keyboard cannot install fonts for other apps. Inside the keyboard we draw with our own fonts (bundled in the app, from the files of cdn.qatt.org): QATT Pen, the PA font, a CJK font with the Nôm characters (Ext. B–H).
- The image: PNG (or WebP), black on white, rendered with the same fonts, offered through a `FileProvider`. To test: WhatsApp, Signal, Telegram, Messages, Gmail.

## 6. Steps

1. Fork of the base keyboard in this repository, builds and runs; Telex for Vietnamese.
2. Kotlin port of `parser.js`, `qatt-text.js`, `pa-text.js` with golden tests from Node (done, see `docs/port.md`).
3. Nôm data build and candidate lookup (done, see `docs/port.md`; not yet connected to the keyboard).
4. The composition field: selection, candidates PA/QATT/Nôm, mixed rendering, ↑, ← (logic done and tested; the interface is built but not yet tried on a device, see `docs/port.md`).
5. Sending as an image (`commitContent`) with the fallback to text (built, not yet tried on a device).
6. Settings (⚙), Vietnamese translation of the interface, release (F-Droid / Play Store).

Later, perhaps: multi-syllable Nôm words (e.g. "người ta"), learning of frequencies, variants (Tham Thư, neutral tone), a choice of fonts for the image.
