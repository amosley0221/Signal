// Self-update for the packaged Windows program (SignalAgent.exe).
// Checks the latest GitHub release at startup and every 6 hours. Installing downloads the new exe into
// <dataDir>\update\, verifies its size and SHA-256 (SignalAgent.exe.sha256 asset: "<hex>  SignalAgent.exe"),
// renames the running exe to SignalAgent.old.exe (Windows allows renaming a running exe), moves the new
// one into its place, starts it with --updated and exits. The new copy deletes SignalAgent.old.exe.
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

export const RELEASES_URL = 'https://api.github.com/repos/amosley0221/Signal/releases/latest';
export const EXE_ASSET = 'SignalAgent.exe';
export const SHA_ASSET = 'SignalAgent.exe.sha256';
export const CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000;
const USER_AGENT = 'SignalAgent-updater';

// ---- pure helpers -----------------------------------------------------------------------------

/** "v1.2.3" / "1.2.3-beta.1" → {major, minor, patch, pre} or null. */
export function parseVersion(v) {
  const m = /^\s*v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?\s*$/.exec(String(v ?? ''));
  if (!m) return null;
  return { major: Number(m[1]), minor: Number(m[2]), patch: Number(m[3]), pre: m[4] || '' };
}

/** Semver compare: -1, 0 or 1. A pre-release sorts before its release. Unparseable versions sort lowest. */
export function compareVersions(a, b) {
  const x = parseVersion(a);
  const y = parseVersion(b);
  if (!x || !y) return !x && !y ? 0 : !x ? -1 : 1;
  for (const k of ['major', 'minor', 'patch']) if (x[k] !== y[k]) return x[k] < y[k] ? -1 : 1;
  if (x.pre === y.pre) return 0;
  if (!x.pre) return 1;
  if (!y.pre) return -1;
  const xp = x.pre.split('.');
  const yp = y.pre.split('.');
  for (let i = 0; i < Math.max(xp.length, yp.length); i++) {
    if (xp[i] === undefined) return -1;
    if (yp[i] === undefined) return 1;
    const xn = /^\d+$/.test(xp[i]);
    const yn = /^\d+$/.test(yp[i]);
    if (xn && yn && Number(xp[i]) !== Number(yp[i])) return Number(xp[i]) < Number(yp[i]) ? -1 : 1;
    if (xn !== yn) return xn ? -1 : 1;
    if (xp[i] !== yp[i]) return xp[i] < yp[i] ? -1 : 1;
  }
  return 0;
}

/** The first token of a .sha256 file ("<hex>  SignalAgent.exe", or just "<hex>"), lower-cased; null if not a SHA-256. */
export function parseSha256File(text) {
  const tok = String(text ?? '').replace(/^﻿/, '').trim().split(/\s+/)[0] || '';
  const hex = tok.replace(/^\*/, '').toLowerCase();
  return /^[0-9a-f]{64}$/.test(hex) ? hex : null;
}

/** Release notes for display: trimmed, capped. */
export function trimNotes(body, max = 4000) {
  const s = String(body ?? '').replace(/\r\n/g, '\n').trim();
  return s.length > max ? `${s.slice(0, max - 1).trimEnd()}…` : s;
}

/** Pick what we need out of a GitHub "latest release" response. */
export function parseRelease(release) {
  if (!release || typeof release !== 'object') return null;
  const v = parseVersion(release.tag_name);
  if (!v) return null;
  const assets = Array.isArray(release.assets) ? release.assets : [];
  const find = (n) => assets.find((a) => a && String(a.name).toLowerCase() === n.toLowerCase()) || null;
  const exe = find(EXE_ASSET);
  const sha = find(SHA_ASSET);
  return {
    version: `${v.major}.${v.minor}.${v.patch}${v.pre ? `-${v.pre}` : ''}`,
    tag: release.tag_name,
    notes: trimNotes(release.body),
    url: release.html_url || null,
    exe: exe && exe.browser_download_url ? { url: exe.browser_download_url, size: Number(exe.size) || 0 } : null,
    shaUrl: sha?.browser_download_url || null,
  };
}

/** SignalAgent.exe → SignalAgent.old.exe (same folder). */
export function oldExePath(execPath) {
  const p = /^[a-z]:\\|\\\\/i.test(execPath) ? path.win32 : path;
  const ext = p.extname(execPath);
  return p.join(p.dirname(execPath), `${p.basename(execPath, ext)}.old${ext}`);
}

// ---- file-system steps (injectable for tests) -----------------------------------------------------

export const realFs = {
  rename: (a, b) => fsp.rename(a, b),
  copyFile: (a, b) => fsp.copyFile(a, b),
  rm: (p) => fsp.rm(p, { force: true }),
  exists: (p) => fs.existsSync(p),
};

/**
 * Put `newFile` in place of the running `execPath` and start it. Steps:
 *   1. remove a leftover SignalAgent.old.exe
 *   2. rename execPath → SignalAgent.old.exe           (fails → nothing changed)
 *   3. move newFile → execPath (rename, else copy)     (fails → rename old back)
 *   4. start execPath --updated --no-browser …          (fails → restore old)
 * Returns the spawned child. Throws with everything restored when a step fails.
 */
export async function swapAndRestart({ newFile, execPath, args = [], fsOps = realFs, spawnFn = spawn }) {
  const old = oldExePath(execPath);
  try { await fsOps.rm(old); } catch { /* still locked by an older copy: the rename below will say so */ }
  try {
    await fsOps.rename(execPath, old);
  } catch (e) {
    throw new Error(`could not rename the running program: ${e.message}`);
  }
  try {
    try {
      await fsOps.rename(newFile, execPath);
    } catch {
      await fsOps.copyFile(newFile, execPath); // e.g. data folder on another drive
      try { await fsOps.rm(newFile); } catch { /* ignore */ }
    }
  } catch (e) {
    try { await fsOps.rm(execPath); } catch { /* ignore */ }
    await fsOps.rename(old, execPath);
    throw new Error(`could not put the new version in place: ${e.message}`);
  }
  try {
    const child = spawnFn(execPath, ['--updated', '--no-browser', ...args], {
      detached: true, windowsHide: true, stdio: 'ignore', cwd: (/^[a-z]:\\/i.test(execPath) ? path.win32 : path).dirname(execPath),
    });
    child.unref?.();
    return child;
  } catch (e) {
    try { await fsOps.rm(execPath); } catch { /* ignore */ }
    try { await fsOps.rename(old, execPath); } catch { /* ignore */ }
    throw new Error(`could not start the new version: ${e.message}`);
  }
}

/** Delete SignalAgent.old.exe left by an update (it may stay locked for a moment while the old copy exits). */
export async function cleanupOldExe(execPath, { fsOps = realFs, tries = 10, delayMs = 1500 } = {}) {
  const old = oldExePath(execPath);
  for (let i = 0; i < tries; i++) {
    if (!fsOps.exists(old)) return true;
    try { await fsOps.rm(old); if (!fsOps.exists(old)) return true; } catch { /* locked */ }
    await new Promise((r) => { setTimeout(r, delayMs).unref?.(); });
  }
  return false;
}

// ---- the updater --------------------------------------------------------------------------------

export class Updater {
  /**
   * @param {{current:string, packaged:boolean, platform?:string, dataDir:string, execPath?:string, args?:string[],
   *   log?:(m:string)=>void, fetchFn?:typeof fetch, fsOps?:typeof realFs, spawnFn?:typeof spawn,
   *   onRestart?:()=>any}} opts
   */
  constructor(opts) {
    this.opts = { platform: process.platform, execPath: process.execPath, args: [], log: () => {}, fetchFn: fetch, fsOps: realFs, spawnFn: spawn, onRestart: null, ...opts };
    this.latest = null; // parseRelease() result
    this.checkedAt = null;
    this.error = null;
    this.downloading = false;
    this.progress = null; // 0..1 while downloading
    this.timer = null;
  }

  /** Why updating is impossible here, or null. */
  get unsupportedReason() {
    if (!this.opts.packaged) return 'running from source';
    if (this.opts.platform !== 'win32') return 'only the Windows program updates itself';
    return null;
  }

  get available() {
    return !this.unsupportedReason && !!this.latest?.exe && compareVersions(this.latest.version, this.opts.current) > 0;
  }

  state() {
    const reason = this.unsupportedReason;
    return {
      current: this.opts.current,
      latest: this.latest?.version || null,
      available: this.available,
      notes: this.latest && this.available ? this.latest.notes : '',
      downloading: this.downloading,
      progress: this.progress,
      error: this.error,
      checkedAt: this.checkedAt,
      ...(reason ? { reason } : {}),
    };
  }

  /** Start periodic checks (no-op unless packaged on Windows). */
  start() {
    if (this.unsupportedReason) return;
    this.check().catch(() => {});
    this.timer = setInterval(() => this.check().catch(() => {}), CHECK_INTERVAL_MS);
    this.timer.unref?.();
  }

  stop() {
    clearInterval(this.timer);
    this.timer = null;
  }

  async check() {
    if (this.unsupportedReason) return this.state();
    try {
      const r = await this.opts.fetchFn(RELEASES_URL, {
        headers: { 'User-Agent': USER_AGENT, Accept: 'application/vnd.github+json' },
        signal: AbortSignal.timeout(10000),
      });
      if (!r.ok) throw new Error(`GitHub answered ${r.status}`);
      const rel = parseRelease(await r.json());
      if (!rel) throw new Error('unexpected release data');
      const was = this.available ? this.latest.version : null;
      this.latest = rel;
      this.checkedAt = Date.now();
      if (!this.downloading) this.error = null;
      if (this.available && was !== rel.version) this.opts.log(`[update] Signal Agent ${rel.version} is available (running ${this.opts.current})`);
    } catch (e) {
      // Offline, rate-limited…: stay quiet and try again later.
      this.checkedAt = Date.now();
      this.lastCheckError = e.message;
    }
    return this.state();
  }

  /**
   * Start installing the latest version in the background. Returns {ok:true} when it started, or
   * {ok:false, error} when it can't. Progress/errors show up in state().
   */
  async install() {
    const reason = this.unsupportedReason;
    if (reason) return { ok: false, error: 'not_supported', reason };
    if (this.downloading) return { ok: true };
    if (!this.available) await this.check();
    if (!this.available) return { ok: false, error: 'no_update', reason: 'already up to date' };
    this.downloading = true;
    this.progress = 0;
    this.error = null;
    this.installing = this.#install(this.latest).catch((e) => {
      this.error = e.message;
      this.opts.log(`[update] update failed, still running ${this.opts.current}: ${e.message}`);
    }).finally(() => { this.downloading = false; this.progress = null; });
    return { ok: true };
  }

  async #download(url, file, expectedSize) {
    const r = await this.opts.fetchFn(url, {
      headers: { 'User-Agent': USER_AGENT, Accept: 'application/octet-stream' },
      redirect: 'follow',
      signal: AbortSignal.timeout(15 * 60 * 1000),
    });
    if (!r.ok || !r.body) throw new Error(`download failed (${r.status})`);
    const hash = crypto.createHash('sha256');
    let got = 0;
    const src = Readable.fromWeb(r.body);
    src.on('data', (c) => {
      hash.update(c);
      got += c.length;
      if (expectedSize) this.progress = Math.min(1, got / expectedSize);
    });
    await pipeline(src, fs.createWriteStream(file));
    return { size: got, sha256: hash.digest('hex') };
  }

  async #install(rel) {
    const { log } = this.opts;
    let expectedSha = null;
    if (rel.shaUrl) {
      const r = await this.opts.fetchFn(rel.shaUrl, { headers: { 'User-Agent': USER_AGENT }, redirect: 'follow', signal: AbortSignal.timeout(30000) });
      if (!r.ok) throw new Error(`could not download the checksum (${r.status})`);
      expectedSha = parseSha256File(await r.text());
      if (!expectedSha) throw new Error('the checksum file is not a SHA-256');
    }
    const dir = path.join(this.opts.dataDir, 'update');
    await fsp.mkdir(dir, { recursive: true });
    const part = path.join(dir, `SignalAgent-${rel.version}.exe.part`);
    await fsp.rm(part, { force: true });
    log(`[update] downloading Signal Agent ${rel.version}`);
    let got;
    try {
      got = await this.#download(rel.exe.url, part, rel.exe.size);
      if (rel.exe.size && got.size !== rel.exe.size) throw new Error(`download is ${got.size} bytes, expected ${rel.exe.size}`);
      if (expectedSha && got.sha256 !== expectedSha) throw new Error('downloaded file does not match its SHA-256 checksum');
    } catch (e) {
      await fsp.rm(part, { force: true }).catch(() => {});
      throw e;
    }
    log(`[update] verified ${rel.version} (${got.size} bytes${expectedSha ? ', SHA-256 ok' : ', no checksum published'}); restarting`);
    await swapAndRestart({ newFile: part, execPath: this.opts.execPath, args: this.opts.args, fsOps: this.opts.fsOps, spawnFn: this.opts.spawnFn });
    this.restarting = true;
    await this.opts.onRestart?.();
  }
}
