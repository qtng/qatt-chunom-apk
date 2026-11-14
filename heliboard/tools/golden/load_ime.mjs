// Loads VietIme of qtng/chunom (js/ime.js, with its dictionaries) in Node: browser objects are stubbed, fetch reads the files.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const arg = process.argv.indexOf('--chunom');
export const CHUNOM = path.resolve(arg > 0 ? process.argv[arg + 1] : (process.env.CHUNOM_PUBLIC || path.join(here, '../../../chunom')));

export async function loadIme() {
  const file = path.join(CHUNOM, 'js/ime.js');
  globalThis.window = globalThis;
  globalThis.document = { currentScript: { src: pathToFileURL(file).href }, getElementById: () => null };
  globalThis.localStorage = { getItem: () => null, setItem: () => {} };
  const realFetch = globalThis.fetch;
  globalThis.fetch = async url => String(url).startsWith('file:')
    ? { ok: true, json: async () => JSON.parse(fs.readFileSync(fileURLToPath(url), 'utf8')) } : realFetch(url);
  (0, eval)(fs.readFileSync(file, 'utf8'));
  await globalThis.VietIme.load();
  return globalThis.VietIme;
}
