// Desktop helpers for the double-click Windows program: open the browser, "Start with Windows"
// (a hidden .vbs launcher in the per-user Startup folder — no admin rights, no shortcut), and the
// folder browser behind the setup page's "Browse…" button.
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';

export const STARTUP_FILE_NAME = 'Signal Agent.vbs';

/** Open a URL in the default browser. Failures are ignored. */
export function openBrowser(url, { platform = process.platform } = {}) {
  try {
    let child;
    if (platform === 'win32') {
      // `start` is a cmd builtin; the empty "" is the window title. Verbatim so Node does not re-quote it.
      child = spawn('cmd', ['/c', 'start', '""', url], { detached: true, stdio: 'ignore', windowsHide: true, windowsVerbatimArguments: true });
    } else if (platform === 'darwin') {
      child = spawn('open', [url], { detached: true, stdio: 'ignore' });
    } else {
      child = spawn('xdg-open', [url], { detached: true, stdio: 'ignore' });
    }
    child.on('error', () => {});
    child.unref();
    return true;
  } catch {
    return false;
  }
}

// ---- Start with Windows ---------------------------------------------------------------------------

/** %APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup */
export function startupDir({ env = process.env, home = os.homedir() } = {}) {
  const appData = env.APPDATA || path.win32.join(home, 'AppData', 'Roaming');
  return path.win32.join(appData, 'Microsoft', 'Windows', 'Start Menu', 'Programs', 'Startup');
}

/**
 * The command the Startup launcher runs. Packaged: the exe itself. From source: node + src/index.js.
 * `--no-browser` so logging on does not pop a browser tab; `--config` is kept when one was given.
 */
export function launchCommand({ packaged, execPath = process.execPath, scriptPath = process.argv[1], configOverride = null }) {
  const args = packaged ? [execPath] : [execPath, scriptPath];
  args.push('--no-browser');
  if (configOverride) args.push('--config', configOverride);
  return args;
}

/** VBScript that starts the command with a hidden window (0) and does not wait (False). */
export function vbsLauncher(args) {
  // Inside a VBScript string "" is one quote, so each argument becomes "arg" on the command line.
  // (Windows paths cannot contain quotes.)
  const cmd = args.map((a) => `""${String(a).replace(/"/g, '')}""`).join(' ');
  return [
    "' Starts Signal Agent hidden when you sign in to Windows.",
    "' Created by the Signal Agent setup page (Start with Windows). Delete this file to turn it off.",
    'Set sh = CreateObject("WScript.Shell")',
    `sh.Run "${cmd}", 0, False`,
    '',
  ].join('\r\n');
}

/** UTF-16LE with BOM, which Windows Script Host reads correctly even for non-ASCII user folders. */
const encodeVbs = (text) => Buffer.concat([Buffer.from([0xff, 0xfe]), Buffer.from(text, 'utf16le')]);

export class StartupEntry {
  /**
   * @param {{platform?:string, dir?:string, command:string[]}} opts
   */
  constructor({ platform = process.platform, dir = null, command }) {
    this.platform = platform;
    this.dir = dir || (platform === 'win32' ? startupDir() : null);
    this.command = command;
  }

  get supported() { return this.platform === 'win32' && !!this.dir; }

  get file() { return this.dir ? path.join(this.dir, STARTUP_FILE_NAME) : null; }

  expected() { return encodeVbs(vbsLauncher(this.command)); }

  status() {
    if (!this.supported) return { supported: false, enabled: false, message: 'Start with Windows is only available on Windows.' };
    let current = null;
    try { current = fs.readFileSync(this.file); } catch { /* not there */ }
    return {
      supported: true,
      enabled: !!current,
      upToDate: !!current && current.equals(this.expected()),
      file: this.file,
    };
  }

  async set(enabled) {
    if (!this.supported) return { ...this.status(), ok: false };
    if (enabled) {
      await fsp.mkdir(this.dir, { recursive: true });
      await fsp.writeFile(this.file, this.expected());
    } else {
      await fsp.rm(this.file, { force: true });
    }
    return { ...this.status(), ok: true };
  }
}

// ---- folder browser -----------------------------------------------------------------------------

const HIDDEN_DIRS = new Set(['$recycle.bin', 'system volume information', 'recovery', 'config.msi', '$windows.~bt', '$windows.~ws', 'msocache', 'perflogs']);

/** Drive letters that exist, e.g. ["C:\\", "D:\\"]. */
export function windowsDrives(exists = (p) => fs.existsSync(p)) {
  const out = [];
  for (let c = 67; c <= 90; c++) { // C..Z (A:/B: are floppies and can hang)
    const d = `${String.fromCharCode(c)}:\\`;
    if (exists(d)) out.push(d);
  }
  return out;
}

function shortcuts(home) {
  const list = [{ name: 'Home', path: home }];
  for (const n of ['Music', 'Videos', 'Movies', 'Desktop', 'Downloads']) {
    const p = path.join(home, n);
    try { if (fs.statSync(p).isDirectory()) list.push({ name: n, path: p }); } catch { /* skip */ }
  }
  return list;
}

/**
 * List the subfolders of `dir`. An empty `dir` lists the roots: drive letters on Windows, `/` elsewhere.
 * Returns { path, parent, dirs:[{name,path}], shortcuts, error? }. `parent` is '' when going up leads to the roots.
 */
export async function listFolders(dir, { platform = process.platform, home = os.homedir(), drives = windowsDrives } = {}) {
  const p = platform === 'win32' ? path.win32 : path.posix;
  const sc = shortcuts(home);
  const want = String(dir || '').trim();
  if (!want) {
    const roots = platform === 'win32' ? drives().map((d) => ({ name: d, path: d })) : [{ name: '/', path: '/' }];
    return { path: '', parent: null, dirs: roots, shortcuts: sc };
  }
  if (!p.isAbsolute(want)) return { path: want, parent: '', dirs: [], shortcuts: sc, error: 'Not a full folder path.' };
  let abs = p.resolve(want);
  if (platform === 'win32' && /^[a-z]:$/i.test(abs)) abs += '\\';
  const root = p.parse(abs).root;
  const parent = abs === root ? (platform === 'win32' ? '' : null) : p.dirname(abs);
  let ents;
  try {
    ents = await fsp.readdir(abs, { withFileTypes: true });
  } catch (e) {
    return { path: abs, parent, dirs: [], shortcuts: sc, error: e.code === 'ENOENT' ? 'Folder not found.' : e.code === 'EPERM' || e.code === 'EACCES' ? 'Windows did not allow opening this folder.' : `Could not open this folder (${e.code || e.message}).` };
  }
  const dirs = ents
    .filter((e) => (e.isDirectory() || e.isSymbolicLink()) && !e.name.startsWith('.') && !e.name.startsWith('$') && !HIDDEN_DIRS.has(e.name.toLowerCase()))
    .map((e) => ({ name: e.name, path: p.join(abs, e.name) }))
    .sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base', numeric: true }))
    .slice(0, 2000);
  return { path: abs, parent, dirs, shortcuts: sc };
}
