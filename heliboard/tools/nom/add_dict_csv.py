#!/usr/bin/env python3
"""Adds the entries of dict.csv of qtng/chunom (the dictionary of the website) to the data of the keyboard:
  - assets/nom/nom.txt: one line "reading TAB glyphs!" for every reading/glyph pair that is not in it yet (camelCase reading, "!" = it has a definition);
  - assets/vi/words.txt: every reading of dict.csv that is not in the word list yet (syllables, and words of several syllables with spaces), frequency 1.
Run again after dict.csv changed (nothing is added twice):  python3 tools/nom/add_dict_csv.py [path/to/chunom/dict.csv]"""
import csv, os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
src = sys.argv[1] if len(sys.argv) > 1 else '/home/user/qtng/chunom/dict.csv'
NOM = os.path.join(ROOT, 'app/src/main/assets/nom/nom.txt')
WORDS = os.path.join(ROOT, 'app/src/main/assets/vi/words.txt')

def camel(syllables):
    return syllables[0] + ''.join(s[:1].upper() + s[1:] for s in syllables[1:])

pairs = set()
for line in open(NOM, encoding='utf-8'):
    k, _, gs = line.rstrip('\n').partition('\t')
    for g in gs.split(' '):
        if g: pairs.add(k + ':' + g.rstrip('!'))
have_words = set(l.split('\t')[0] for l in open(WORDS, encoding='utf-8') if l.strip())

new_nom, new_words = [], []
for row in csv.reader(open(src, encoding='utf-8')):
    if len(row) < 2 or not row[0].strip() or not row[1].strip(): continue
    syl = re.split(r'[\s\-]+', row[0].strip().lower())
    if not all(s and s.isalpha() for s in syl): continue                 # a reading with other characters: skipped
    key, glyph = camel(syl), row[1].strip()
    if key + ':' + glyph not in pairs:
        pairs.add(key + ':' + glyph)
        new_nom.append(key + '\t' + glyph + ('!' if len(row) > 2 and row[2].strip() else ''))
    phrase = ' '.join(syl)
    if phrase not in have_words:
        have_words.add(phrase)
        new_words.append(phrase + '\t1')
with open(NOM, 'a', encoding='utf-8') as f: f.write('\n'.join(new_nom) + ('\n' if new_nom else ''))
with open(WORDS, 'a', encoding='utf-8') as f: f.write('\n'.join(new_words) + ('\n' if new_words else ''))
print(len(new_nom), 'pairs added to nom.txt,', len(new_words), 'words added to words.txt')
