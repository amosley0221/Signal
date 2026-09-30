import crypto from 'node:crypto';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';

export const AUDIO_EXTS = new Set(['.wav', '.flac', '.m4a', '.mp3', '.aac', '.ogg', '.opus', '.aiff', '.aif']);
export const VIDEO_EXTS = new Set(['.mp4', '.mkv', '.m4v', '.mov', '.webm', '.avi']);
export const IMAGE_EXTS = ['.jpg', '.jpeg', '.png', '.webp'];

export const MIME = {
  '.wav': 'audio/wav',
  '.flac': 'audio/flac',
  '.m4a': 'audio/mp4',
  '.mp3': 'audio/mpeg',
  '.aac': 'audio/aac',
  '.ogg': 'audio/ogg',
  '.opus': 'audio/ogg',
  '.aiff': 'audio/aiff',
  '.aif': 'audio/aiff',
  '.mp4': 'video/mp4',
  '.m4v': 'video/x-m4v',
  '.mkv': 'video/x-matroska',
  '.mov': 'video/quicktime',
  '.webm': 'video/webm',
  '.avi': 'video/x-msvideo',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.srt': 'application/x-subrip; charset=utf-8',
  '.vtt': 'text/vtt; charset=utf-8',
  '.lrc': 'text/plain; charset=utf-8',
};

export const mimeFor = (file) => MIME[path.extname(file).toLowerCase()] || 'application/octet-stream';

/** Short, stable id: first 12 hex chars of sha1(libraryId + '/' + relPath). */
export function stableId(libraryId, relPath, prefix = '') {
  const h = crypto.createHash('sha1').update(`${libraryId}/${relPath}`).digest('hex').slice(0, 12);
  return prefix + h;
}

export const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');

export function toPosix(p) {
  return p.split(path.sep).join('/').replace(/\\/g, '/');
}

export async function writeJsonAtomic(file, data, indent = 2) {
  await fsp.mkdir(path.dirname(file), { recursive: true });
  const tmp = `${file}.${process.pid}.${crypto.randomBytes(4).toString('hex')}.tmp`;
  await fsp.writeFile(tmp, JSON.stringify(data, null, indent || undefined));
  await fsp.rename(tmp, file);
}

export function readJsonSync(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

export function lanAddresses() {
  const out = [];
  for (const list of Object.values(os.networkInterfaces())) {
    for (const a of list || []) {
      if (a.internal) continue;
      if (a.family === 'IPv4' || a.family === 4) out.push(a.address);
    }
  }
  return out;
}

const whichCache = new Map();
/** Resolve whether an executable is on PATH by trying to run `<cmd> -version`. */
export function hasExecutable(cmd) {
  if (whichCache.has(cmd)) return whichCache.get(cmd);
  const p = new Promise((resolve) => {
    let done = false;
    try {
      const child = spawn(cmd, ['-version'], { stdio: 'ignore', windowsHide: true });
      child.on('error', () => { if (!done) { done = true; resolve(false); } });
      child.on('exit', (code) => { if (!done) { done = true; resolve(code === 0); } });
    } catch {
      resolve(false);
    }
  });
  whichCache.set(cmd, p);
  return p;
}

/** Run a process, collecting stdout. Rejects on non-zero exit. */
export function run(cmd, args, { timeoutMs = 120000 } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(cmd, args, { windowsHide: true });
    const out = [];
    const err = [];
    const timer = setTimeout(() => child.kill('SIGKILL'), timeoutMs);
    child.stdout.on('data', (d) => out.push(d));
    child.stderr.on('data', (d) => err.push(d));
    child.on('error', (e) => { clearTimeout(timer); reject(e); });
    child.on('exit', (code) => {
      clearTimeout(timer);
      if (code === 0) resolve(Buffer.concat(out).toString('utf8'));
      else reject(new Error(`${cmd} exited ${code}: ${Buffer.concat(err).toString('utf8').slice(-500)}`));
    });
  });
}

export async function statSafe(p) {
  try {
    return await fsp.stat(p);
  } catch {
    return null;
  }
}

/** Case-insensitive lookup of the first existing sibling among `names` in a directory listing. */
export function findInListing(listing, names) {
  if (!listing) return null;
  for (const n of names) {
    const hit = listing.get(n.toLowerCase());
    if (hit) return hit;
  }
  return null;
}

export async function listDirLower(dir) {
  const m = new Map();
  try {
    for (const name of await fsp.readdir(dir)) m.set(name.toLowerCase(), name);
  } catch {
    /* ignore */
  }
  return m;
}

/** Sanitise a client-supplied file name for Windows and POSIX file systems. */
export function sanitizeFileName(name) {
  let base = String(name || '').split(/[\\/]/).pop();
  base = base.replace(/[<>:"|?*\u0000-\u001f]/g, '_').replace(/^[.\s]+/, '').replace(/[.\s]+$/, '');
  if (/^(con|prn|aux|nul|com\d|lpt\d)(\..*)?$/i.test(base)) base = `_${base}`;
  return base.slice(0, 200);
}

export function debounce(fn, ms) {
  let t = null;
  const d = (...args) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...args), ms);
    if (t.unref) t.unref();
  };
  d.cancel = () => clearTimeout(t);
  return d;
}

export async function mapLimit(items, limit, fn) {
  const results = new Array(items.length);
  let i = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (i < items.length) {
      const idx = i++;
      results[idx] = await fn(items[idx], idx);
    }
  });
  await Promise.all(workers);
  return results;
}
