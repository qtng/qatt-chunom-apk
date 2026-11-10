# qatt-chunom-apk

An Android keyboard that types like Gboard and has a composition field for Chữ Nôm, Quốc Âm Tân Tự (QATT) and PA.
Plan: [`docs/plan.md`](docs/plan.md).

It is a fork of [HeliBoard](https://github.com/Helium314/HeliBoard) (GPL-3.0, upstream commit `3e91b4f`); the licences of HeliBoard are kept (`LICENSE*`). The original README of HeliBoard is in the history of the first fork commit.

## Build

Needs JDK 21 and the Android SDK (platform `android-37.0`, NDK `28.0.13004108`); `local.properties` with `sdk.dir=…` (not committed).

    ./gradlew assembleDebug -x lint

The APK is `app/build/outputs/apk/debug/`.
