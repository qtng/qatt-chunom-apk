#!/usr/bin/env python3
"""Adds the Vietnamese words of Wiktionary to assets/vi/words.txt (for the completions of the suggestion strip).
Source: the extract of the English Wiktionary by kaikki.org (https://kaikki.org/dictionary/Vietnamese/), CC BY-SA 4.0 (and GFDL), see README.md.
  python3 tools/nom/add_wiktionary.py kaikki.org-dictionary-Vietnamese.jsonl
Takes every Vietnamese entry written in Quốc Ngữ (letters, spaces, hyphens), lower-cased, that is not in the list yet. The frequency of a word of several syllables
is estimated from those of its syllables (geometric mean / 10, at least 1); a syllable that is not in the list has 1. Nothing is added twice."""
import json, math, os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
WORDS = os.path.join(ROOT, 'app/src/main/assets/vi/words.txt')
latin = re.compile(r"^[^\W\d_]+(?:[ \-][^\W\d_]+)*$")
vi_letters = re.compile(r"^[a-zàáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ \-]+$")
freq = {}
for line in open(WORDS, encoding='utf-8'):
    w, _, f = line.rstrip('\n').partition('\t')
    if w: freq[w.lower()] = max(freq.get(w.lower(), 0), int(f or 1))
new = {}
for line in open(sys.argv[1], encoding='utf-8'):
    d = json.loads(line)
    if d.get('lang_code') != 'vi': continue
    w = d.get('word', '').strip().lower()
    if not w or not latin.match(w) or not vi_letters.match(w) or w in freq or w in new: continue
    syl = re.split(r'[ \-]+', w)
    new[' '.join(syl)] = max(1, round(math.exp(sum(math.log(freq.get(s, 1)) for s in syl) / len(syl)) / 10)) if len(syl) > 1 else 1
new = {w: f for w, f in new.items() if w not in freq}
with open(WORDS, 'a', encoding='utf-8') as out:
    for w in sorted(new): out.write('%s\t%d\n' % (w, new[w]))
print(len(new), 'words added;', sum(1 for w in new if ' ' in w), 'of them with several syllables')
