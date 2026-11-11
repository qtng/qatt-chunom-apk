// Builds app/src/main/assets/nom/nom.txt, the Chữ Nôm dictionary of the keyboard, from js/ime.js, js/generated_chars.json and js/base_chars.json
// of qtng/chunom (the data of the candidate list of the website).
//   node tools/nom/build_nom.mjs [--chunom ../chunom]
// Format: one line per dictionary entry, in the order in which VietIme adds them: reading, a tab, the glyphs separated by spaces; a glyph that has a
// definition ends with "!" (the order of the candidates depends on it). The reading is as in the dictionaries: a multi-syllable word is written in
// camelCase ("chữNôm"), which is how its syllables are told apart.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { CHUNOM } from '../golden/load_ime.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const ime = fs.readFileSync(path.join(CHUNOM, 'js/ime.js'), 'utf8');
const builtin = new Function('return ' + /const BUILTIN = ("(?:[^"\\]|\\.)*");/.exec(ime)[1])();
const sources = [{ dict: builtin, defs: {} }, ...['generated_chars.json', 'base_chars.json'].map(f => JSON.parse(fs.readFileSync(path.join(CHUNOM, 'js', f), 'utf8')))];

const defs = new Map();
for (const s of sources) {                         // VietIme.add() sets the definitions of all sources (later ones overwrite)
  const d = s.defs || {};
  const items = Array.isArray(d) ? d : Object.entries(d);
  for (const [k, v] of items) defs.set(k, v);
}
const pairs = new Set(), lines = [];
for (const s of sources) {
  for (const entry of s.dict.replace(/[\r\n]/g, '').split('|')) {
    const sep = entry.indexOf(':', 1);
    if (sep < 0) continue;
    const key = entry.slice(0, sep), glyphs = [];
    for (const glyph of entry.slice(sep + 1).split(',')) {
      if (!glyph || pairs.has(key + ':' + glyph)) continue;
      pairs.add(key + ':' + glyph);
      glyphs.push(glyph + (defs.get(key + glyph) ? '!' : ''));
    }
    if (glyphs.length) lines.push(key + '\t' + glyphs.join(' '));
  }
}
const out = path.join(root, 'app/src/main/assets/nom/nom.txt');
fs.writeFileSync(out, lines.join('\n') + '\n');
console.log(lines.length, 'entries,', pairs.size, 'pairs,', fs.statSync(out).size, 'bytes');
