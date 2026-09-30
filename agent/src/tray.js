// Windows system-tray icon. The icon itself is a small PowerShell script (src/tray.ps1, using
// System.Windows.Forms) that is written to <dataDir>\tray.ps1 and run hidden. It talks to the agent
// over the localhost-only admin endpoints (/admin/tray-state, /admin/startup, /admin/quit)
// and exits by itself when the agent process is gone. In the single-executable bundle the script text
// is inlined at build time (scripts/build-exe.mjs defines __SIGNAL_TRAY_PS1__).
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';

export const TRAY_SCRIPT_NAME = 'tray.ps1';

/* global __SIGNAL_TRAY_PS1__ -- the contents of src/tray.ps1, defined by scripts/build-exe.mjs */
let cached = null;
/** The PowerShell source of the tray icon. */
export function trayScript() {
  if (cached == null) {
    cached = typeof __SIGNAL_TRAY_PS1__ === 'string'
      ? __SIGNAL_TRAY_PS1__
      : fs.readFileSync(new URL('./tray.ps1', import.meta.url), 'utf8');
  }
  return cached;
}

/** Windows PowerShell 5.1 reads BOM-less scripts as ANSI; the BOM keeps it UTF-8 (the script is ASCII anyway). */
export function trayScriptBytes() {
  return Buffer.concat([Buffer.from([0xef, 0xbb, 0xbf]), Buffer.from(trayScript().replace(/\r?\n/g, '\r\n'), 'utf8')]);
}

/** Write (or refresh) <dataDir>\tray.ps1 and return its path. */
export function writeTrayScript(dataDir) {
  const file = path.join(dataDir, TRAY_SCRIPT_NAME);
  const bytes = trayScriptBytes();
  let current = null;
  try { current = fs.readFileSync(file); } catch { /* not there yet */ }
  if (!current || !current.equals(bytes)) {
    fs.mkdirSync(dataDir, { recursive: true });
    fs.writeFileSync(file, bytes);
  }
  return file;
}

/** PC names go on a command line: no quotes or control characters, and keep them short. */
export function safeTrayName(name) {
  return String(name || '').replace(/["\u0000-\u001f]/g, '').trim().slice(0, 64) || 'this PC';
}

/** powershell.exe arguments for the tray script. */
export function trayArgs({ scriptPath, port, pid = process.pid, name }) {
  return [
    '-NoProfile', '-ExecutionPolicy', 'Bypass', '-WindowStyle', 'Hidden',
    '-File', scriptPath,
    '-Port', String(port),
    '-AgentPid', String(pid),
    '-Name', safeTrayName(name),
  ];
}

/**
 * Start the tray icon (Windows only). Returns { child, stop() } or null when not started.
 * The child is killed when this process exits.
 */
export function startTray({ dataDir, port, name, pid = process.pid, platform = process.platform, spawnFn = spawn, log = () => {} }) {
  if (platform !== 'win32') return null;
  let child;
  try {
    const scriptPath = writeTrayScript(dataDir);
    child = spawnFn('powershell.exe', trayArgs({ scriptPath, port, pid, name }), { windowsHide: true, detached: false, stdio: 'ignore' });
  } catch (e) {
    log(`[tray] could not start the tray icon: ${e.message}`);
    return null;
  }
  child.on?.('error', (e) => log(`[tray] could not start the tray icon: ${e.message}`));
  child.on?.('exit', (code) => { if (code) log(`[tray] tray icon exited with code ${code}`); });
  const stop = () => { try { if (child.exitCode == null) child.kill(); } catch { /* already gone */ } };
  process.once('exit', stop);
  return { child, stop };
}
