#!/usr/bin/env python3
"""Generates the keyboard3 layouts from qwertz.xml (the only one FlorisBoard ships): same keys, other letter rows."""
import re, pathlib
D = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/extensions/org.florisboard.k3.foundation/keyboard"
src = (D / "qwertz.xml").read_text()

def swap(s, pairs):
    for a, b in pairs:
        assert a in s, a
        s = s.replace(a, b)
    return s

TOUCH = [  # (base rows, shift rows)
]
def variant(name, label, indicator, locale, rows_base, rows_shift, extra_keys=""):
    s = src
    s = re.sub(r'locale="de"', f'locale="{locale}"', s, 1)
    s = re.sub(r'<info [^>]*/>', f'<info author="Lars Mühlbauer for k3lp, adapted" name="{label}" layout="{name.upper()}" indicator="{indicator}" />', s, 1)
    base_old = ['<row keys="q w e r t z u i o p" />', '<row keys="a s d f g h j k l" />', '<row keys="base-to-shift y x c v b n m bksp" />']
    shift_old = ['<row keys="Q W E R T Z U I O P" />', '<row keys="A S D F G H J K L" />', '<row keys="shift-to-base Y X C V B N M bksp" />']
    for old, new in zip(base_old + shift_old, rows_base + rows_shift):
        assert old in s, old
        s = s.replace(old, f'<row keys="{new}" />', 1)
    if extra_keys:
        s = s.replace('        <key id="bksp" output=', extra_keys + '        <key id="bksp" output=', 1)
    (D / f"{name}.xml").write_text(s)

qwerty_b = ["q w e r t y u i o p", "a s d f g h j k l", "base-to-shift z x c v b n m bksp"]
qwerty_s = ["Q W E R T Y U I O P", "A S D F G H J K L", "shift-to-base Z X C V B N M bksp"]
variant("qwerty", "English (QWERTY)", "EN", "en", qwerty_b, qwerty_s)
variant("qwerty_vi", "Vietnamese (Telex)", "VI", "vi", qwerty_b, qwerty_s)
variant("azerty", "French (AZERTY)", "FR", "fr",
        ["a z e r t y u i o p", "q s d f g h j k l m", "base-to-shift w x c v b n bksp"],
        ["A Z E R T Y U I O P", "Q S D F G H J K L M", "shift-to-base W X C V B N bksp"])
variant("spanish", "Spanish (QWERTY)", "ES", "es",
        ["q w e r t y u i o p", "a s d f g h j k l n-tilde", "base-to-shift z x c v b n m bksp"],
        ["Q W E R T Y U I O P", "A S D F G H J K L N-tilde", "shift-to-base Z X C V B N M bksp"],
        '        <key id="n-tilde" output="ñ" />\n        <key id="N-tilde" output="Ñ" />\n')
print("ok")
