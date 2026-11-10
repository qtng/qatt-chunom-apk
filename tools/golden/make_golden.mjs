// Writes the golden files for the Kotlin port (app/src/test/resources/golden/) from the JavaScript of qtng/qatt, and the rhyme tables as Kotlin source.
//   node tools/golden/make_golden.mjs [--qatt ../qatt]
// The Kotlin tests (app/src/test/java/helium314/keyboard/qatt/) must give exactly these results.
import fs from 'node:fs';
import zlib from 'node:zlib';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { VietnameseParser as P, QattText as Q, PaText as A, QATT } from './load.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const out = path.join(root, 'app/src/test/resources/golden');
fs.mkdirSync(out, { recursive: true });
const write = (name, text) => fs.writeFileSync(path.join(out, name), zlib.gzipSync(Buffer.from(text, 'utf8'), { level: 9 }));
const cps = s => s == null ? '-' : Array.from(s, c => c.codePointAt(0).toString(16).toUpperCase()).join(' ') || '.';
const S = v => v == null ? '-' : v === '' ? '.' : v;   // - = null, . = empty string

// ---- 1. words: every syllable shape x onset x tone -------------------------------------------------------------------------------------------------
const parsers = Object.fromEntries(Object.keys(P.DECODINGS).map(n => [n, new P(n)]));
const p = parsers.qatt;
const vowels = P.VALID_VOWELS.map(v => v.normalize('NFC'));
const tableRhymes = Object.values(P.DECODINGS).flat(2).filter(Boolean);
const rhymes = new Set(tableRhymes);
for (const g of ['', 'u', 'o'])
  for (const v of vowels)
    for (const c of ['', ...P.CODAS, 'i', 'y', 'o', 'u']) rhymes.add((g + v + c).normalize('NFC'));
for (const r of ['uya', 'uyê', 'oa', 'oe', 'oă', 'uê', 'uâ', 'uơ', 'uy', 'uyn', 'uynh', 'ong', 'ung', 'uông', 'ông', 'ôc', 'âc']) rhymes.add(r);
const onsets = ['', ...P.ONSETS, 'q', 'k', 'gh', 'ngh'];
const tones = [null, 'sac', 'huyen', 'hoi', 'nga', 'nang'];
const upper = w => w.charAt(0).toUpperCase() + w.slice(1);

const rows = [];
let n = 0, sampled = 0;
for (const on of onsets) for (const rh of rhymes) for (let t = 0; t < tones.length; t++) {
  // the tone mark on a vowel of the rhyme (parse takes it from wherever it stands)
  if ([2, 3, 4].includes(t) && ++sampled % 3) continue;                           // huyền, hỏi, ngã: every third word (the size of the file)
  const marked = p._tonePlaced(rh, tones[t]);
  for (const w of [on + marked, ...(n++ % 41 === 0 ? [upper(on + marked)] : [])]) {
    const r = p.parse(w);
    if (!r) { if (n % 29 === 0) rows.push([w, '-'].join('\t')); continue; }       // a sample of the refused words
    const qatt = Q.word(w, p);
    const cell = qatt == null ? null : Q.cells(qatt).map(c => c.cell)[0] ?? null;
    const read = Object.values(parsers).map(q => qatt == null ? '-' : S(Q.reading(qatt, q)));
    rows.push([w, S(r.code), S(r.qatt), S(r.hop), S(r.onset), S(r.glide), S(r.vowel), S(r.coda), r.tone,
      cps(qatt), ...read, cps(A.word(w, p)), cps(A.word(w, p, { stacked: false })), cps(A.word(w, p, { ngang: true })),
      S(A.read(A.word(w, p) ?? '', p))].join('\t'));
  }
}
write('words.tsv.gz', rows.join('\n') + '\n');
console.log('words', rows.length);

// ---- 2. cells: reading of QATT cells after every decoding ----------------------------------------------------------------------------------------
const chr = o => String.fromCodePoint(0xF0000 + o);
const cellRows = [];
const toneSets = [0, 1, 2, 5, 6, 7, 8];
const add = (cell) => {
  const d = Q.decode(cell);
  cellRows.push([cps(cell), d ? JSON.stringify(d) : '-', ...Object.values(parsers).map(q => S(Q.reading(cell, q)))].join('\t'));
};
for (const t of toneSets) {
  const tm = t ? chr([0, 160, 157, 158, 159, 161, 162, 163, 156][t]) : '';
  for (let a = 0; a < 154; a++) add(chr(a) + tm);
  for (let a = 0; a < 154; a += 7) for (let b = 0; b < 154; b++) add(chr(a) + chr(154) + chr(b) + tm);                 // plain first shape + any second letter
  for (let a = 0; a < 154; a++) { const sh = a - a % 7; if (a % 7) add(chr(a) + chr(154) + chr(sh) + tm); }          // marked + its plain shape (Hợp Thư)
}
add(chr(0) + chr(155) + chr(7)); add(chr(160)); add('');
// not a cell at all ('x'): decode() of the JavaScript returns a garbage object (a negative index), the port returns null; the reading is "?" in both, so it is left out
write('cells.tsv.gz', cellRows.join('\n') + '\n');
console.log('cells', cellRows.length);

// ---- 3. texts: convert, recode, repairDeletion, PA convert / read / restack, cells ---------------------------------------------------------------
const texts = ['tự', 'Quốc Ngữ', 'người ta đi chơi', 'Việt Nam, hello world 123', 'ông bông vong cung đường', 'quốc qua cua chia yêu gì',
  'nghe nghĩ ghi gì kể', 'Phú Thọ trăng sáng', 'a b c', 'ph tr th', 'fọ jọ wọ zọ', 'xin chào các bạn', 'ơ', 'x', 'buồn', 'xanh ngát'];
const misc = [];
for (const t of texts) {
  const q = Q.convert(t, p);
  misc.push(['convert', t, cps(q), cps(Q.recode(q, p, parsers.omniglot)), cps(Q.recode(q, parsers.omniglot, p)),
    JSON.stringify(Q.cells(q)), cps(A.convert(t, p)), cps(A.convert(t, p, { stacked: false })), cps(A.convert(t, p, { ngang: true })),
    cps(A.read(A.convert(t, p), p)), cps(A.restack(t, p)), cps(A.restack(q, p))].join('\t'));
}
const dels = [];
for (const t of ['tự', 'người ta', 'ông']) {
  const q = Q.convert(t, p), pa = A.convert(t, p);
  for (const text of [q, pa]) for (let caret = 0; caret <= text.length; caret++) for (let len = 1; caret + len <= text.length; len++) {
    const after = text.slice(0, caret) + text.slice(caret + len);
    dels.push(['repair', cps(text), caret, len, JSON.stringify(Q.repairDeletion(text, after, caret) && { v: cps(Q.repairDeletion(text, after, caret).value), c: Q.repairDeletion(text, after, caret).caret })].join('\t'));
  }
}
write('misc.tsv.gz', [...misc, ...dels].join('\n') + '\n');
console.log('misc', misc.length, dels.length);
