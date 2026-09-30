// agent.log with simple size-based rotation (agent.log → agent.log.1, one old file kept), plus the
// console redirect used by the windowless Windows program, where stdout/stderr go nowhere.
import fs from 'node:fs';
import path from 'node:path';
import util from 'node:util';

export const DEFAULT_MAX_BYTES = 5 * 1024 * 1024;

export class LogFile {
  /** @param {string} file @param {{maxBytes?:number}} [opts] */
  constructor(file, { maxBytes = DEFAULT_MAX_BYTES } = {}) {
    this.file = file;
    this.maxBytes = maxBytes;
    this.size = null; // bytes in the current file, read lazily
  }

  /** Append one line (a newline is added). Never throws. */
  write(line) {
    const data = `${line}\n`;
    const len = Buffer.byteLength(data);
    try {
      if (this.size === null) {
        try { this.size = fs.statSync(this.file).size; } catch { this.size = 0; }
      }
      if (this.size > 0 && this.size + len > this.maxBytes) this.rotate();
      fs.appendFileSync(this.file, data);
      this.size += len;
    } catch {
      // A missing folder or a full disk must never take the agent down.
      try { fs.mkdirSync(path.dirname(this.file), { recursive: true }); } catch { /* ignore */ }
    }
  }

  rotate() {
    try { fs.rmSync(`${this.file}.1`, { force: true }); } catch { /* ignore */ }
    try { fs.renameSync(this.file, `${this.file}.1`); } catch { /* ignore */ }
    this.size = 0;
  }
}

const shared = new Map();
/** One LogFile per path, so the Agent and the console redirect share rotation bookkeeping. */
export function logFileFor(file, opts) {
  const key = path.resolve(file);
  if (!shared.has(key)) shared.set(key, new LogFile(key, opts));
  return shared.get(key);
}

/**
 * Send console.log/info/warn/error/debug to the log file (timestamped). Used when the program has no
 * console window. Returns a function that restores the original console methods.
 */
export function redirectConsole(log) {
  const methods = ['log', 'info', 'warn', 'error', 'debug'];
  const saved = Object.fromEntries(methods.map((m) => [m, console[m]]));
  for (const m of methods) {
    console[m] = (...args) => log.write(`${new Date().toISOString()} ${util.format(...args)}`);
  }
  return () => { for (const m of methods) console[m] = saved[m]; };
}
