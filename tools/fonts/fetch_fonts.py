#!/usr/bin/env python3
"""Fetches the fonts the composition field draws with from cdn.qatt.org (WOFF2) and writes them as TTF (Android cannot read WOFF2) to app/src/main/assets/fonts/.

    pip install fonttools brotli
    python3 tools/fonts/fetch_fonts.py

QATT letters (U+F0000..), PA (U+F00B0..) and the most frequent Chữ Nôm characters (slice 00 of QATT Pen). Everything else is drawn with the fonts of the system.
The fonts are NOT under the licence of the app (GPL-3.0): see app/src/main/assets/fonts/LICENSE.txt.
"""
import io, os, sys, urllib.request
from fontTools.ttLib import TTFont

BASE = "https://cdn.qatt.org/fonts/"
FONTS = {                       # file written -> file on the CDN
    "qatt-pen-letters.ttf": "qatt-pen-letters.woff2",
    "pa-pen.ttf": "pa-pen.woff2",
    "qatt-pen-00.ttf": "web/qatt-pen-00.woff2",
}
out = os.path.join(os.path.dirname(__file__), "../../app/src/main/assets/fonts")
os.makedirs(out, exist_ok=True)
for name, remote in FONTS.items():
    data = urllib.request.urlopen(urllib.request.Request(BASE + remote, headers={"User-Agent": "font-fetch/1.0"})).read()
    font = TTFont(io.BytesIO(data))
    font.flavor = None
    path = os.path.join(out, name)
    font.save(path)
    print(name, len(data), "->", os.path.getsize(path), "bytes,", len(font.getBestCmap()), "characters")
