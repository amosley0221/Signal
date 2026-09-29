// LRC parsing, embedded-lyrics fallback and a tiny language heuristic.
import fsp from 'node:fs/promises';
import path from 'node:path';

const TIME_RE = /^\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]/;
const META_RE = /^\[([a-zA-Z#][a-zA-Z0-9_-]*):([^\]]*)\]\s*$/;

const round3 = (n) => Math.round(n * 1000) / 1000;

/**
 * Parse LRC text.
 * Supports `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, `[mm:ss:xx]`, several timestamps per line,
 * enhanced-LRC word stamps `<mm:ss.xx>` (stripped), `[offset:+/-ms]`, and metadata tags
 * such as `[ti:]`, `[ar:]`, `[la:es]` / `[lang:es]` / `[language:es]`.
 * @returns {{lang: string|null, meta: Record<string,string>, lines: {t:number,text:string}[], synced: boolean}}
 */
export function parseLrc(text) {
  const meta = {};
  const lines = [];
  const plain = [];
  let offsetMs = 0;
  const src = String(text || '').replace(/^﻿/, '');
  for (const raw of src.split(/\r?\n/)) {
    let line = raw.trim();
    if (!line) continue;
    const stamps = [];
    let m;
    while ((m = TIME_RE.exec(line))) {
      const min = Number(m[1]);
      const sec = Number(m[2]);
      const frac = m[3] ? Number(m[3]) / 10 ** m[3].length : 0;
      stamps.push(min * 60 + sec + frac);
      line = line.slice(m[0].length).trimStart();
    }
    if (stamps.length) {
      const t = line.replace(/<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>/g, '').replace(/\s+/g, ' ').trim();
      for (const s of stamps) lines.push({ t: s, text: t });
      continue;
    }
    const mm = META_RE.exec(line);
    if (mm) {
      const key = mm[1].toLowerCase();
      const val = mm[2].trim();
      meta[key] = val;
      if (key === 'offset') offsetMs = Number(val) || 0;
      continue;
    }
    plain.push(line);
  }
  if (offsetMs) for (const l of lines) l.t = Math.max(0, l.t - offsetMs / 1000);
  lines.sort((a, b) => a.t - b.t);
  for (const l of lines) l.t = round3(l.t);
  const langTag = (meta.la || meta.lang || meta.language || '').toLowerCase().trim();
  if (!lines.length && plain.length) {
    // Not actually synced: treat as plain lyrics.
    return { lang: langTag ? normalizeLang(langTag) : null, meta, lines: unsyncedLines(plain.join('\n')), synced: false };
  }
  return { lang: langTag ? normalizeLang(langTag) : null, meta, lines, synced: lines.length > 0 };
}

/** Plain (unsynced) lyrics → lines with t = index * 4 seconds. */
export function unsyncedLines(text) {
  return String(text || '')
    .replace(/^﻿/, '')
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s, i) => ({ t: i * 4, text: s }));
}

export function looksLikeLrc(text) {
  return /^\s*\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?\]/m.test(String(text || ''));
}

const LANG_ALIASES = {
  eng: 'en', english: 'en', spa: 'es', esp: 'es', spanish: 'es', 'español': 'es', espanol: 'es',
  por: 'pt', portuguese: 'pt', 'português': 'pt', fra: 'fr', fre: 'fr', french: 'fr', 'français': 'fr',
  deu: 'de', ger: 'de', german: 'de', deutsch: 'de', ita: 'it', italian: 'it', jpn: 'ja', japanese: 'ja',
  kor: 'ko', korean: 'ko', chi: 'zh', zho: 'zh', chinese: 'zh', rus: 'ru', russian: 'ru', nld: 'nl', dut: 'nl', dutch: 'nl',
};

export function normalizeLang(tag) {
  const t = String(tag || '').toLowerCase().trim();
  if (!t) return null;
  if (LANG_ALIASES[t]) return LANG_ALIASES[t];
  const base = t.split(/[-_]/)[0];
  if (LANG_ALIASES[base]) return LANG_ALIASES[base];
  return base.length === 2 ? base : base.slice(0, 3);
}

// Distinctive-ish stopwords per language (overlaps kept small on purpose).
const STOPWORDS = {
  en: ['the', 'and', 'you', 'is', 'are', 'my', 'your', 'i', 'me', 'it', 'of', 'to', 'in', 'on', 'we', 'with', 'this', 'that', 'be', 'all', 'love', 'don\'t', 'i\'m', 'what', 'when', 'will', 'can'],
  es: ['el', 'la', 'los', 'las', 'y', 'que', 'de', 'en', 'un', 'una', 'mi', 'tu', 'yo', 'es', 'por', 'con', 'no', 'se', 'lo', 'me', 'te', 'del', 'al', 'pero', 'como', 'más', 'mas', 'porque', 'está', 'esta', 'soy', 'eres', 'noche', 'amor', 'corazón'],
  pt: ['o', 'os', 'as', 'e', 'que', 'de', 'em', 'um', 'uma', 'meu', 'minha', 'eu', 'é', 'não', 'nao', 'com', 'do', 'da', 'no', 'na', 'você', 'voce', 'mais', 'mas', 'coração', 'estou', 'ao', 'pra', 'isso'],
  fr: ['le', 'la', 'les', 'et', 'que', 'de', 'des', 'en', 'un', 'une', 'mon', 'ma', 'je', 'tu', 'est', 'pas', 'ne', 'avec', 'dans', 'pour', 'qui', 'sur', 'nous', 'vous', 'mais', 'toi', 'moi', 'c\'est', 'j\'ai', 'au'],
  de: ['der', 'die', 'das', 'und', 'ich', 'du', 'ist', 'nicht', 'ein', 'eine', 'mit', 'mein', 'dein', 'zu', 'den', 'dem', 'auf', 'wir', 'sie', 'es', 'auch', 'wie', 'noch', 'nur', 'dich', 'mich', 'sind', 'war', 'aber'],
};
const STOPSETS = Object.fromEntries(Object.entries(STOPWORDS).map(([k, v]) => [k, new Set(v)]));

/** Very small language guess: es / pt / fr / de via stopword counts, otherwise "en". */
export function detectLanguage(text) {
  const words = String(text || '').toLowerCase().match(/[\p{L}']+/gu) || [];
  if (!words.length) return 'en';
  const score = { en: 0, es: 0, pt: 0, fr: 0, de: 0 };
  for (const w of words) for (const k of Object.keys(score)) if (STOPSETS[k].has(w)) score[k]++;
  // Diacritic hints break ties between the Romance languages.
  const joined = words.join(' ');
  if (/[ñ¿¡]/.test(joined)) score.es += 3;
  if (/[ãõç]/.test(joined)) score.pt += 3;
  if (/[èêëàâîôûœ]/.test(joined)) score.fr += 3;
  if (/[äöüß]/.test(joined)) score.de += 3;
  let best = 'en';
  let bestScore = 0;
  for (const k of ['es', 'pt', 'fr', 'de']) {
    if (score[k] > bestScore) { best = k; bestScore = score[k]; }
  }
  const threshold = Math.max(2, Math.ceil(words.length * 0.08));
  if (bestScore >= threshold && bestScore > score.en) return best;
  return 'en';
}

/** Sidecar paths for a media file. */
export function lrcPaths(absPath) {
  const dir = path.dirname(absPath);
  const base = path.basename(absPath, path.extname(absPath));
  return { lrc: path.join(dir, `${base}.lrc`), en: path.join(dir, `${base}.en.lrc`) };
}

async function readText(p) {
  try {
    return await fsp.readFile(p, 'utf8');
  } catch {
    return null;
  }
}

/**
 * Load lyrics for a track (sidecar .lrc → embedded tag → none).
 * @param {string} absPath
 * @param {() => Promise<any[]|null>} loadEmbedded returns music-metadata `common.lyrics` or null
 */
export async function loadLyrics(absPath, loadEmbedded) {
  const { lrc, en } = lrcPaths(absPath);
  let result = null;
  const lrcText = await readText(lrc);
  if (lrcText != null) {
    const p = parseLrc(lrcText);
    if (p.lines.length) {
      result = { lang: p.lang || detectLanguage(p.lines.map((l) => l.text).join('\n')), lines: p.lines, synced: p.synced };
    }
  }
  if (!result && loadEmbedded) {
    const emb = embeddedToLines(await loadEmbedded());
    if (emb) result = { lang: emb.lang || detectLanguage(emb.lines.map((l) => l.text).join('\n')), lines: emb.lines, synced: emb.synced };
  }
  if (!result) return { lines: [] };
  const enText = await readText(en);
  if (enText != null) {
    const p = parseLrc(enText);
    if (p.lines.length) result.translation = p.lines;
  }
  return result;
}

/** Convert music-metadata `common.lyrics` (array of ILyricsTag or strings) into lines. */
export function embeddedToLines(lyrics) {
  if (!lyrics || !lyrics.length) return null;
  for (const l of lyrics) {
    if (l && typeof l === 'object' && Array.isArray(l.syncText) && l.syncText.length && l.syncText.some((s) => s.timestamp != null)) {
      // timeStampFormat 2 = milliseconds (ID3 SYLT). Frames (1) cannot be converted reliably; treat as ms.
      const lines = l.syncText
        .filter((s) => s.text != null)
        .map((s) => ({ t: round3((s.timestamp || 0) / 1000), text: String(s.text).trim() }))
        .sort((a, b) => a.t - b.t);
      if (lines.length) return { lang: normalizeLang(l.language && l.language !== 'XXX' ? l.language : ''), lines, synced: true };
    }
  }
  for (const l of lyrics) {
    const text = typeof l === 'string' ? l : l && l.text;
    if (!text || !text.trim()) continue;
    const lang = typeof l === 'object' ? normalizeLang(l.language && !/^x+$/i.test(l.language) ? l.language : '') : null;
    if (looksLikeLrc(text)) {
      const p = parseLrc(text);
      if (p.lines.length) return { lang: p.lang || lang, lines: p.lines, synced: p.synced };
    }
    return { lang, lines: unsyncedLines(text), synced: false };
  }
  return null;
}
