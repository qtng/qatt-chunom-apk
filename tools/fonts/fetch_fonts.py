#!/usr/bin/env python3
"""Fetches the fonts of the composition field from cdn.qatt.org into app/src/main/assets/fonts/.

    pip install fonttools brotli
    python3 tools/fonts/fetch_fonts.py                 # what the app contains
    python3 tools/fonts/fetch_fonts.py --extra DIR     # also the big fonts Migoi and Phong Thao into DIR (to copy to a phone and import in the app)

What the app contains:
  - qatt-pen.ttf, the big font QATT Pen (27 MB, as it is): QATT, Chu Nom and the Latin of Quoc Ngu.
  - pa-pen.ttf, pa-migoi.ttf, pa-phongthao.ttf, the PA fonts (WOFF2 on the CDN, converted to TTF, Android cannot read WOFF2). PA is not in the big fonts.
The fonts are NOT under the licence of the app (GPL-3.0): see app/src/main/assets/fonts/LICENSE.txt.
"""
import io, os, sys, urllib.request
from fontTools.ttLib import TTFont

BASE = "https://cdn.qatt.org/fonts/"
BIG = ["qatt-pen.ttf"]                        # copied as they are
PA = {"pa-pen.ttf": "pa-pen.woff2", "pa-migoi.ttf": "pa-migoi.woff2", "pa-phongthao.ttf": "pa-phongthao.woff2"}   # file written -> file on the CDN
EXTRA = ["migoi.ttf", "phongthao.ttf"]         # 42 MB and 41 MB: not in the app, the user imports them

def get(name):
    return urllib.request.urlopen(urllib.request.Request(BASE + name, headers={"User-Agent": "font-fetch/1.0"})).read()

here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, "../../app/src/main/assets/fonts")
os.makedirs(out, exist_ok=True)
for name in BIG:
    data = get(name)
    open(os.path.join(out, name), "wb").write(data)
    print(name, len(data), "bytes")
for name, remote in PA.items():
    data = get(remote)
    font = TTFont(io.BytesIO(data))
    font.flavor = None
    font.save(os.path.join(out, name))
    print(name, len(data), "->", os.path.getsize(os.path.join(out, name)), "bytes,", len(font.getBestCmap()), "characters")
if "--extra" in sys.argv:
    target = sys.argv[sys.argv.index("--extra") + 1]
    os.makedirs(target, exist_ok=True)
    for name in EXTRA:
        data = get(name)
        open(os.path.join(target, name), "wb").write(data)
        print(name, len(data), "bytes ->", target)
