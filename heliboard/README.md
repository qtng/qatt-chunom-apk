# qatt-chunom-apk

An Android keyboard that types like Gboard and has a composition field for Chữ Nôm, Quốc Âm Tân Tự (QATT) and PA.
Plan: [`docs/plan.md`](docs/plan.md).

It is a fork of [HeliBoard](https://github.com/Helium314/HeliBoard) (GPL-3.0, upstream commit `3e91b4f`); the licences of HeliBoard are kept (`LICENSE*`). The original README of HeliBoard is in the history of the first fork commit.

## Build

Needs JDK 21 and the Android SDK (platform `android-37.0`, NDK `28.0.13004108`); `local.properties` with `sdk.dir=…` (not committed).

    ./gradlew assembleDebug -x lint

The APK is `app/build/outputs/apk/debug/`.

## Fonts and network

`app/src/main/assets/fonts/` holds fonts of qatt.org (QATT Pen, 27 MB, and the PA fonts) for the composition field and the images. They are **not** under the GPL-3.0 of the code but under the licence in `app/src/main/assets/fonts/LICENSE.txt` (copyright qatt.org). `python3 tools/fonts/fetch_fonts.py` fetches them from cdn.qatt.org (the PA fonts are converted to TTF).

The keyboard uses the network for one thing only: when the user chooses it in the settings of the composition field (⚙), it downloads or updates a big font (QATT Pen, Migoi, Phong Thảo) from `https://cdn.qatt.org/fonts/` (permission INTERNET). Nothing but the address of the file and the version it has is sent; typed text never leaves the device. A font file can also be imported from a file instead.

## Vietnamese dictionary

`app/src/main/assets/dicts/main_vi.dict` (11,755 words with next-word data) is the experimental Vietnamese dictionary of the HeliBoard dictionary repository (https://codeberg.org/Helium314/aosp-dictionaries, `main_vi__experimental.dict`, 2023-09-16), built from the word lists of the Leipzig Corpora Collection, licensed under CC BY 4.0. It gives suggestions and autocorrection for Vietnamese (the Telex input itself needs no dictionary).
