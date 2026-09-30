// Config file handling: signal-agent.config.json (created with defaults on first run).
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const AGENT_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const CONFIG_FILE_NAME = 'signal-agent.config.json';
export const DEFAULT_CONFIG_PATH = path.join(AGENT_DIR, CONFIG_FILE_NAME);
export const LIBRARY_TYPES = ['music', 'musicvideos', 'movies', 'tv'];

/**
 * Pure check: are we running as a packaged program (Node single executable) rather than `node src/index.js`?
 * `isSea` is the result of `require('node:sea').isSea()` when available (null when unknown).
 */
export function isPackagedRuntime({ isSea = null, execPath = process.execPath } = {}) {
  if (typeof isSea === 'boolean') return isSea;
  const base = path.basename(String(execPath || '')).toLowerCase().replace(/\.exe$/, '');
  return !/^(node|nodejs|node\d+)$/.test(base);
}

let packagedCache;
/** True when running as the SignalAgent.exe / signal-agent single executable. */
export function isPackaged() {
  if (packagedCache !== undefined) return packagedCache;
  let isSea = null;
  try {
    const sea = process.getBuiltinModule?.('node:sea');
    if (sea && typeof sea.isSea === 'function') isSea = sea.isSea();
  } catch { /* older Node: fall back to the executable name */ }
  packagedCache = isPackagedRuntime({ isSea });
  return packagedCache;
}

/** Per-user folder for config + data when packaged: %APPDATA%\SignalAgent (Windows) or ~/.config/signal-agent. */
export function userConfigDir({ platform = process.platform, env = process.env, home = os.homedir() } = {}) {
  const p = platform === 'win32' ? path.win32 : path.posix;
  if (platform === 'win32') return p.join(env.APPDATA || p.join(home, 'AppData', 'Roaming'), 'SignalAgent');
  return p.join(env.XDG_CONFIG_HOME || p.join(home, '.config'), 'signal-agent');
}

export function defaultConfig() {
  return {
    name: os.hostname(),
    port: 8765,
    dataDir: './data',
    libraries: [],
    plex: { url: '', token: '' },
  };
}

/** The explicit config override from `--config <file>` / `--config=<file>` / SIGNAL_AGENT_CONFIG, or null. */
export function configOverride(argv = process.argv.slice(2), env = process.env) {
  const i = argv.indexOf('--config');
  if (i >= 0 && argv[i + 1]) return path.resolve(argv[i + 1]);
  const eq = argv.find((a) => a.startsWith('--config='));
  if (eq) return path.resolve(eq.slice('--config='.length));
  if (env.SIGNAL_AGENT_CONFIG) return path.resolve(env.SIGNAL_AGENT_CONFIG);
  return null;
}

/**
 * Resolve the config path: `--config <file>`, SIGNAL_AGENT_CONFIG, then the default — the per-user
 * folder when packaged (see userConfigDir), or next to ./data in the agent folder when run from source.
 */
export function resolveConfigPath(argv = process.argv.slice(2), env = process.env, opts = {}) {
  const o = configOverride(argv, env);
  if (o) return o;
  const packaged = opts.packaged ?? isPackaged();
  if (!packaged) return DEFAULT_CONFIG_PATH;
  const platform = opts.platform || process.platform;
  const dir = userConfigDir({ platform, env, home: opts.home || os.homedir() });
  return (platform === 'win32' ? path.win32 : path.posix).join(dir, CONFIG_FILE_NAME);
}

/** 100.64.0.0/10 (carrier-grade NAT) is what Tailscale hands out. */
export function isTailscaleAddress(ip) {
  const m = /^(\d+)\.(\d+)\.\d+\.\d+$/.exec(String(ip || ''));
  return !!m && Number(m[1]) === 100 && Number(m[2]) >= 64 && Number(m[2]) <= 127;
}

/**
 * Validate the setup form posted by the admin page. Pure apart from `isDir(path)` (injected).
 * Returns { ok, errors: [{field, row?, message}], setup: {name, libraries, plex} }.
 * Existing library ids are kept (item ids derive from them); new rows get the next free `lN` id.
 */
export function validateSetup(body, { isDir = () => true, platform = process.platform, fallbackName = os.hostname() } = {}) {
  const p = platform === 'win32' ? path.win32 : path.posix;
  const errors = [];
  const b = body && typeof body === 'object' ? body : {};
  const name = String(b.name ?? '').trim().slice(0, 64) || fallbackName;
  const rows = Array.isArray(b.libraries) ? b.libraries : [];
  if (!Array.isArray(b.libraries) && b.libraries !== undefined) errors.push({ field: 'libraries', message: 'Libraries must be a list.' });
  const usedIds = new Set(rows.map((l) => (l && typeof l.id === 'string' && /^[\w-]{1,32}$/.test(l.id) ? l.id : null)).filter(Boolean));
  const seenIds = new Set();
  const seenPaths = new Map();
  let next = 1;
  const libraries = [];
  rows.forEach((raw, row) => {
    const l = raw && typeof raw === 'object' ? raw : {};
    const folder = String(l.path ?? '').trim().replace(/^"(.*)"$/, '$1');
    if (!folder) { errors.push({ field: 'path', row, message: 'Choose a folder.' }); return; }
    if (!p.isAbsolute(folder)) { errors.push({ field: 'path', row, message: `"${folder}" is not a full folder path (like ${platform === 'win32' ? 'D:\\Music' : '/home/me/Music'}).` }); return; }
    const abs = p.resolve(folder);
    if (!isDir(abs)) { errors.push({ field: 'path', row, message: `The folder "${abs}" was not found.` }); return; }
    const key = platform === 'win32' ? abs.toLowerCase() : abs;
    if (seenPaths.has(key)) { errors.push({ field: 'path', row, message: `This folder is already listed in row ${seenPaths.get(key) + 1}.` }); return; }
    seenPaths.set(key, row);
    const type = String(l.type || '');
    if (!LIBRARY_TYPES.includes(type)) { errors.push({ field: 'type', row, message: 'Pick what kind of media this folder holds.' }); return; }
    let id = typeof l.id === 'string' && /^[\w-]{1,32}$/.test(l.id) && !seenIds.has(l.id) ? l.id : null;
    if (!id) {
      while (usedIds.has(`l${next}`) || seenIds.has(`l${next}`)) next += 1;
      id = `l${next}`;
    }
    seenIds.add(id);
    const libName = String(l.name ?? '').trim().slice(0, 80) || p.basename(abs) || abs;
    libraries.push({ id, name: libName, type, path: abs });
  });
  const plexUrl = String(b.plex?.url ?? '').trim().replace(/\/+$/, '');
  const plexToken = String(b.plex?.token ?? '').trim();
  if (plexUrl) {
    let u = null;
    try { u = new URL(plexUrl); } catch { /* invalid */ }
    if (!u || !/^https?:$/.test(u.protocol)) errors.push({ field: 'plex.url', message: 'The Plex address should look like http://127.0.0.1:32400' });
  }
  if (plexUrl && !plexToken) errors.push({ field: 'plex.token', message: 'Plex also needs a token (or clear the Plex address).' });
  return { ok: errors.length === 0, errors, setup: { name, libraries, plex: { url: plexUrl, token: plexToken } } };
}

/**
 * Merge a validated setup into the config file on disk (keeping port, dataDir, host and unknown keys)
 * and return the new normalised config.
 */
export async function saveSetup(configPath, setup) {
  let raw = {};
  try { raw = JSON.parse(fs.readFileSync(configPath, 'utf8')) || {}; } catch { raw = defaultConfig(); }
  raw.name = setup.name;
  raw.libraries = setup.libraries.map(({ id, name, type, path: p }) => ({ id, name, type, path: p }));
  raw.plex = { url: setup.plex.url, token: setup.plex.token };
  fs.mkdirSync(path.dirname(configPath), { recursive: true });
  const tmp = `${configPath}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, `${JSON.stringify(raw, null, 2)}\n`);
  fs.renameSync(tmp, configPath);
  const cfg = normalizeConfig(raw, path.dirname(configPath));
  cfg.configPath = configPath;
  return cfg;
}

/**
 * Normalise a raw config object. Relative `dataDir` / library paths resolve against `baseDir`
 * (the folder containing the config file).
 */
export function normalizeConfig(raw, baseDir = AGENT_DIR) {
  const d = defaultConfig();
  const c = { ...d, ...(raw || {}) };
  c.name = String(c.name || d.name);
  c.port = Number.isInteger(Number(c.port)) ? Number(c.port) : d.port;
  c.host = c.host || '0.0.0.0';
  c.dataDir = path.resolve(baseDir, c.dataDir || d.dataDir);
  c.plex = { url: String(c.plex?.url || '').replace(/\/+$/, ''), token: String(c.plex?.token || '') };
  const seen = new Set();
  c.libraries = (Array.isArray(c.libraries) ? c.libraries : []).map((l, i) => {
    let id = String(l.id || `l${i + 1}`);
    while (seen.has(id)) id = `${id}_`;
    seen.add(id);
    const type = LIBRARY_TYPES.includes(l.type) ? l.type : 'music';
    return { id, name: String(l.name || path.basename(l.path || '') || id), type, path: path.resolve(baseDir, String(l.path || '.')) };
  });
  return c;
}

/** Load (or create with defaults) the config file. */
export function loadConfig(configPath = resolveConfigPath()) {
  let raw;
  if (fs.existsSync(configPath)) {
    raw = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  } else {
    raw = defaultConfig();
    fs.mkdirSync(path.dirname(configPath), { recursive: true });
    fs.writeFileSync(configPath, `${JSON.stringify(raw, null, 2)}\n`);
    console.log(`[config] created ${configPath}`);
  }
  const cfg = normalizeConfig(raw, path.dirname(configPath));
  cfg.configPath = configPath;
  return cfg;
}
