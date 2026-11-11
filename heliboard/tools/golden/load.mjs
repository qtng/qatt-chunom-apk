// Loads parser.js, qatt-text.js and pa-text.js of the public repository qtng/qatt (a checkout; QATT_PUBLIC or --qatt, default ../qatt next to this repository).
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const arg = process.argv.indexOf('--qatt');
export const QATT = path.resolve(arg > 0 ? process.argv[arg + 1] : (process.env.QATT_PUBLIC || path.join(here, '../../../qatt')));
const require = createRequire(import.meta.url);

export const VietnameseParser = new Function(fs.readFileSync(path.join(QATT, 'js/parser.js'), 'utf8') + '\nreturn VietnameseParser;')();
export const QattText = require(path.join(QATT, 'js/qatt-text.js'));
export const PaText = require(path.join(QATT, 'js/pa-text.js'));
