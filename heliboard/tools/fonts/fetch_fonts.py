#!/usr/bin/env python3
"""Fetches the fonts of the composition field from cdn.qatt.org into app/src/main/assets/fonts/.

    pip install fonttools brotli
    python3 tools/fonts/fetch_fonts.py                 # what the app contains
    python3 tools/fonts/fetch_fonts.py --extra DIR     # also the big fonts Migoi and Phong Thao into DIR (to copy to a phone and import in the app)

What the app contains:
  - qatt-pen.ttf, the big font QATT Pen (28 MB, as it is): QATT, PA (from U+F00D0, with the ruby annotation U+F00B0/B1), Chu Nom and the Latin of Quoc Ngu.
    The big fonts have PA built in since build 12; the separate PA fonts (pa-*.woff2) are legacy with the old code points and are not used any more.
The fonts are NOT under the licence of the app (GPL-3.0): see app/src/main/assets/fonts/LICENSE.txt.
"""
import os, sys, urllib.request

BASE = "https://cdn.qatt.org/fonts/"
BIG = ["qatt-pen.ttf"]                        # copied as they are
EXTRA = ["migoi.ttf", "phongthao.ttf"]         # 42 MB and 41 MB: not in the app, the user imports them

def get(name, etag=False):
    r = urllib.request.urlopen(urllib.request.Request(BASE + name, headers={"User-Agent": "font-fetch/1.0"}))
    return (r.read(), r.headers.get("ETag")) if etag else r.read()

here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, "../../app/src/main/assets/fonts")
os.makedirs(out, exist_ok=True)
for name in BIG:
    data, etag = get(name, True)
    open(os.path.join(out, name), "wb").write(data)
    if etag: open(os.path.join(out, name + ".etag"), "w").write(etag)   # the app asks the server "is this still the newest?" with it
    print(name, len(data), "bytes, ETag", etag)
if "--extra" in sys.argv:
    target = sys.argv[sys.argv.index("--extra") + 1]
    os.makedirs(target, exist_ok=True)
    for name in EXTRA:
        data = get(name)
        open(os.path.join(target, name), "wb").write(data)
        print(name, len(data), "bytes ->", target)
