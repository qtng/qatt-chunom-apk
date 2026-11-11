// Golden file for TelexKey (Kotlin): VietIme.telex of js/ime.js over typed words, words followed by one more key, and random key sequences.
// usage: node tools/golden/make_telex_golden.mjs <out.tsv.gz> <words.txt (word and count per line)> [--chunom DIR]
import fs from 'node:fs';
import zlib from 'node:zlib';
import { loadIme } from './load_ime.mjs';
const [out, wordsFile] = process.argv.slice(2);
const ime = await loadIme();
let seed = 12345; const rnd = n => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed % n; };
const TONE = { '́': 's', '̀': 'f', '̉': 'r', '̃': 'x', '̣': 'j' };
const MOD = { 'â': 'aa', 'ă': 'aw', 'ê': 'ee', 'ô': 'oo', 'ơ': 'ow', 'ư': 'uw', 'đ': 'dd' };
const words = fs.readFileSync(wordsFile, 'utf8').split('\n').map(l => l.split(' ')[0]).filter(w => w && /^[a-zà-ỹđ]+$/i.test(w) && w.length <= 8);
const rows = new Set();
const add = raw => rows.add(raw + '\t' + ime.telex(raw));
for (const w of words.slice(0, 12000)) {
  let letters = [], tone = '', at = -1;
  for (const ch of w.normalize('NFC')) {
    const nd = ch.normalize('NFD'); let b = nd[0], mod = '', t = '';
    for (const m of nd.slice(1)) { if (TONE[m]) t = TONE[m]; else mod += m; }
    const letter = mod ? ({ '̂': { a: 'â', e: 'ê', o: 'ô' }, '̆': { a: 'ă' }, '̛': { o: 'ơ', u: 'ư' } }[mod] || {})[b] || b : b;
    if (t) { tone = t; at = letters.length; }
    letters.push(MOD[letter] ?? letter);
  }
  const end = letters.join('') + tone, after = letters.slice(0, at + 1).join('') + tone + letters.slice(at + 1).join('');
  add(end); add(after);
  for (const k of 'sfjrxzaeowdnch') add(w.normalize('NFC') + k);   // a visible word and one more key
}
const alphabet = 'aaeeoouuiiydwwsfrxjzcghmnptqAEOUDW';
for (let n = 0; n < 15000; n++) { let s = ''; const len = 1 + rnd(9); for (let i = 0; i < len; i++) s += alphabet[rnd(alphabet.length)]; add(s); }
fs.writeFileSync(out, zlib.gzipSync([...rows].join('\n') + '\n'));
console.log(rows.size);
