import fs from 'node:fs';
import { loadIme } from './load_ime.mjs';
const S = process.argv[2];
const ime = await loadIme();
const TONE = { '́': 's', '̀': 'f', '̉': 'r', '̃': 'x', '̣': 'j' };
const MOD = { 'â': 'aa', 'ă': 'aw', 'ê': 'ee', 'ô': 'oo', 'ơ': 'ow', 'ư': 'uw', 'đ': 'dd' };
const words = fs.readFileSync(S + '/vi_50k.txt', 'utf8').split('\n').map(l => l.split(' ')[0]).filter(w => w && /^[a-zà-ỹđ]+$/i.test(w) && w.length <= 8);
const out = [];
for (const w of words) {
  const d = w.normalize('NFD');
  // split into letters with tone: NFD gives base + combining marks (breve/circumflex/horn are combining too, so use NFC letters for them)
  let letters = [], tone = '', toneAt = -1;
  for (const ch of w.normalize('NFC')) {
    const nd = ch.normalize('NFD');
    let base = nd[0], marks = nd.slice(1), mod = '';
    let t = '';
    for (const m of marks) { if (TONE[m]) t = TONE[m]; else mod += m; }
    let letter = (mod ? ({ '̂': { a: 'â', e: 'ê', o: 'ô' }, '̆': { a: 'ă' }, '̛': { o: 'ơ', u: 'ư' } }[mod] || {})[base] : base) || base;
    if (base === 'đ' ) letter = 'đ';
    const keys = MOD[letter] ?? letter;
    if (t) { tone = t; toneAt = letters.length; }
    letters.push(keys);
  }
  const A = letters.join('') + tone;                              // tone at the end
  const B = letters.slice(0, toneAt + 1).join('') + tone + letters.slice(toneAt + 1).join('');  // tone right after the toned letter
  for (const [style, raw] of [['end', A], ['after', B]]) {
    if (style === 'after' && B === A) continue;
    out.push([w, style, raw, ime.telex(raw)].join('\t'));
  }
}
fs.writeFileSync(S + '/js.tsv', out.join('\n') + '\n');
console.log(out.length);
