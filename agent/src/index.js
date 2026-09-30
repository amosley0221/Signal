#!/usr/bin/env node
// Entry point: `node src/index.js [--config path/to/signal-agent.config.json] [--no-browser]`
// Also the entry of the single-executable build (SignalAgent.exe), see scripts/build-exe.mjs.
import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import { pathToFileURL } from 'node:url';
import { loadConfig, resolveConfigPath, normalizeConfig, isPackaged } from './config.js';
import { Agent, VERSION } from './agent.js';
import { openBrowser, showMessageBox, shouldOpenBrowser } from './desktop.js';
import { startTray } from './tray.js';
import { logFileFor, redirectConsole } from './logfile.js';

export { Agent, VERSION, loadConfig, normalizeConfig };

const HELP = `Commands:
  approve <code>   approve a pending pair request (6-digit code shown on the phone)
  deny <code>      deny a pending pair request
  pending          list pending pair requests
  devices          list paired devices
  revoke <id>      revoke a paired device
  rescan           rescan all libraries
  status           show libraries and last scan
  quit             stop the agent`;

function startConsole(agent) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout, terminal: !!process.stdin.isTTY });
  rl.on('line', async (line) => {
    const [cmd, arg] = line.trim().split(/\s+/);
    switch ((cmd || '').toLowerCase()) {
      case '':
        break;
      case 'approve':
      case 'a': {
        const r = agent.approvePairing(arg);
        if (!r) console.log(`No pending request with code ${arg || '(none)'}`);
        break;
      }
      case 'deny':
      case 'd': {
        const r = agent.denyPairing(arg);
        if (!r) console.log(`No pending request with code ${arg || '(none)'}`);
        break;
      }
      case 'pending':
      case 'list': {
        const p = agent.state.pendingPairs();
        console.log(p.length ? p.map((r) => `  ${r.code}  ${r.deviceName}  (${r.ip || '?'})`).join('\n') : '  no pending requests');
        break;
      }
      case 'devices': {
        const d = agent.state.listDevices();
        console.log(d.length ? d.map((x) => `  ${x.id}  ${x.name}  paired ${new Date(x.createdAt).toLocaleString()}`).join('\n') : '  no paired devices');
        break;
      }
      case 'revoke':
        console.log(agent.state.revokeDevice(arg) ? `Revoked ${arg}` : `No device ${arg}`);
        break;
      case 'rescan':
        agent.catalog.scan('console').then(() => console.log('Rescan finished'));
        break;
      case 'status':
        for (const l of agent.catalog.getLibraries()) console.log(`  ${l.id}  ${l.name} (${l.type})  ${l.count} items  ${l.path}`);
        console.log(`  last scan: ${agent.catalog.lastScanAt ? new Date(agent.catalog.lastScanAt).toLocaleString() : 'never'}`);
        break;
      case 'quit':
      case 'exit':
        process.kill(process.pid, 'SIGINT');
        break;
      default:
        console.log(HELP);
    }
  });
  return rl;
}

/**
 * The Windows program is built without a console window (see scripts/build-exe.mjs): its output goes
 * to <dataDir>/agent.log and fatal errors are shown in a message box.
 */
export const isWindowless = (packaged = isPackaged(), platform = process.platform) => packaged && platform === 'win32';

/** Report a startup error and exit: a message box for the windowless program, else the console. */
async function fatal(msg) {
  console.error(`\n${msg}\n`);
  if (isWindowless()) {
    showMessageBox(msg, { title: 'Signal Agent could not start' });
  } else if (isPackaged() && process.stdin.isTTY) {
    console.error('Press Enter to close this window.');
    await new Promise((r) => {
      const rl = readline.createInterface({ input: process.stdin });
      rl.once('line', () => { rl.close(); r(); });
      setTimeout(r, 10 * 60 * 1000).unref();
    });
  }
  process.exit(1);
}

/** Is a Signal Agent already answering on this port? Returns its /api/info or null. */
export async function probeExisting(port, host = '127.0.0.1') {
  try {
    const r = await fetch(`http://${host}:${port}/api/info`, { signal: AbortSignal.timeout(1500) });
    if (!r.ok) return null;
    const j = await r.json();
    return j && typeof j.id === 'string' && typeof j.version === 'string' ? j : null;
  } catch {
    return null;
  }
}

function banner(port, needsSetup, windowless) {
  const url = `http://localhost:${port}/`;
  const line = '='.repeat(72);
  console.log([
    '',
    line,
    `  Signal Agent is running. Setup: ${url}`,
    windowless ? null : '  Keep this window open (or turn on Start with Windows on the setup page).',
    needsSetup ? '  First time? Open the setup page above and add your music/video folders.' : null,
    line,
    '',
  ].filter((l) => l !== null).join('\n'));
}

export async function main(argv = process.argv.slice(2)) {
  const packaged = isPackaged();
  const windowless = isWindowless(packaged);
  const noBrowser = argv.includes('--no-browser');
  if (packaged) process.title = 'Signal Agent';
  const configPath = resolveConfigPath(argv);
  const firstRun = !fs.existsSync(configPath);
  let config;
  try {
    config = loadConfig(configPath);
  } catch (e) {
    return fatal(`Could not read the settings file ${configPath}: ${e.message}\nFix or delete that file, then start Signal Agent again.`);
  }
  // No console window: everything printed from here on goes to <dataDir>/agent.log (rotated at ~5 MB).
  if (windowless) {
    try { fs.mkdirSync(config.dataDir, { recursive: true }); } catch { /* reported by the Agent below */ }
    redirectConsole(logFileFor(path.join(config.dataDir, 'agent.log')));
  }

  // Just installed by the self-updater: the old copy is shutting down, so wait for the port.
  if (argv.includes('--updated')) {
    for (let i = 0; i < 60 && (await probeExisting(config.port)); i++) await new Promise((r) => setTimeout(r, 500));
  }
  // Another copy already running? Point the browser at it instead of starting a second one.
  const existing = await probeExisting(config.port);
  if (existing) {
    console.log(`Signal Agent is already running on this PC (port ${config.port}). Its setup page: http://localhost:${config.port}/`);
    if (!noBrowser) openBrowser(`http://localhost:${config.port}/`);
    process.exit(0);
  }

  let shutdown = null;
  // The console is redirected to the same agent.log, so the Agent must not print as well.
  const agent = new Agent(config, { quiet: windowless, onQuit: () => shutdown?.() });
  try {
    await agent.start();
  } catch (e) {
    if (e.code === 'EADDRINUSE') {
      const again = await probeExisting(config.port);
      if (again) {
        console.log(`Signal Agent is already running on this PC (port ${config.port}).`);
        if (!noBrowser) openBrowser(`http://localhost:${config.port}/`);
        process.exit(0);
      }
      return fatal(`Port ${config.port} is already used by another program, so Signal Agent cannot start.\nClose that program, or set a different "port" in ${config.configPath}.`);
    }
    return fatal(`Could not start: ${e.message}`);
  }
  banner(agent.port, !config.libraries.length, windowless);
  // Tray icon by the clock (Windows): Open / Start with Windows / Update / Quit.
  if (!argv.includes('--no-tray')) {
    startTray({ dataDir: config.dataDir, port: agent.port, name: config.name, log: agent.log });
  }
  if (shouldOpenBrowser({ argv, packaged, needsSetup: !config.libraries.length, firstRun })) openBrowser(`http://localhost:${agent.port}/`);
  let rl = null;
  if (process.stdin.isTTY) {
    rl = startConsole(agent);
    console.log('Type "help" for console commands.');
  }
  let stopping = false;
  shutdown = async () => {
    if (stopping) return;
    stopping = true;
    agent.log('[agent] stopping');
    rl?.close();
    await agent.stop();
    process.exit(0);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
  process.on('uncaughtException', (e) => { agent.log(`[agent] uncaught: ${e.stack || e}`); process.exit(1); });
  process.on('unhandledRejection', (e) => agent.log(`[agent] unhandled rejection: ${e?.stack || e}`));
}

/* global __SIGNAL_BUNDLED__ -- defined by scripts/build-exe.mjs; the bundle calls main() itself */
if (typeof __SIGNAL_BUNDLED__ === 'undefined' && process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
