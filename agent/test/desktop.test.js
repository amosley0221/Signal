import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import {
  peSubsystemOffset, readPeSubsystem, setPeSubsystem, patchPeFileSubsystem, SUBSYSTEM_WINDOWS_CUI, SUBSYSTEM_WINDOWS_GUI,
} from '../scripts/pe-subsystem.mjs';
import { LogFile, redirectConsole } from '../src/logfile.js';
import { shouldOpenBrowser, showMessageBox } from '../src/desktop.js';
import { normalizeConfig } from '../src/config.js';
import { Agent } from '../src/agent.js';

/** A minimal PE header: MZ, e_lfanew, "PE\0\0", COFF header, optional header with Subsystem. */
function fakePe({ magic = 0x20b, subsystem = SUBSYSTEM_WINDOWS_CUI, peOff = 0x80, optSize = 240 } = {}) {
  const buf = Buffer.alloc(1024);
  buf.write('MZ', 0, 'latin1');
  buf.writeUInt32LE(peOff, 0x3c);
  buf.write('PE\0\0', peOff, 'latin1');
  buf.writeUInt16LE(0x8664, peOff + 4); // Machine
  buf.writeUInt16LE(optSize, peOff + 4 + 16); // SizeOfOptionalHeader
  buf.writeUInt16LE(magic, peOff + 24);
  buf.writeUInt16LE(subsystem, peOff + 24 + 68);
  return buf;
}

describe('PE subsystem patch', () => {
  test('PE32+ and PE32: console → GUI at optional header + 68', () => {
    for (const magic of [0x20b, 0x10b]) {
      const buf = fakePe({ magic });
      assert.equal(peSubsystemOffset(buf), 0x80 + 24 + 68);
      assert.equal(readPeSubsystem(buf), SUBSYSTEM_WINDOWS_CUI);
      const before = Buffer.from(buf);
      setPeSubsystem(buf);
      assert.equal(readPeSubsystem(buf), SUBSYSTEM_WINDOWS_GUI);
      // nothing else changed
      let diffs = 0;
      for (let i = 0; i < buf.length; i++) if (buf[i] !== before[i]) diffs++;
      assert.equal(diffs, 1);
    }
  });
  test('refuses non-PE files, bad magic, and an unexpected current value', () => {
    assert.throws(() => setPeSubsystem(Buffer.from('\x7fELF' + '\0'.repeat(100), 'latin1')), /MZ/);
    const noSig = fakePe(); noSig.write('XX', 0x80, 'latin1');
    assert.throws(() => setPeSubsystem(noSig), /signature/);
    assert.throws(() => setPeSubsystem(fakePe({ magic: 0x107 })), /magic/);
    assert.throws(() => setPeSubsystem(fakePe({ subsystem: SUBSYSTEM_WINDOWS_GUI })), /expected PE subsystem 3, found 2/);
    assert.throws(() => setPeSubsystem(fakePe({ optSize: 20 })), /too small/);
    const farPe = fakePe(); farPe.writeUInt32LE(5000, 0x3c);
    assert.throws(() => setPeSubsystem(farPe), /outside/);
  });
  test('patches a file in place', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-pe-'));
    try {
      const f = path.join(dir, 'x.exe');
      const big = Buffer.concat([fakePe(), Buffer.alloc(10000, 7)]);
      fs.writeFileSync(f, big);
      patchPeFileSubsystem(f);
      const after = fs.readFileSync(f);
      assert.equal(after.length, big.length);
      assert.equal(readPeSubsystem(after), SUBSYSTEM_WINDOWS_GUI);
      assert.throws(() => patchPeFileSubsystem(f), /found 2/); // a second run fails clearly
    } finally {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });
});

describe('agent.log', () => {
  test('rotates to agent.log.1 past the size limit, keeping one old file', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-log-'));
    try {
      const f = path.join(dir, 'agent.log');
      const log = new LogFile(f, { maxBytes: 100 });
      for (let i = 0; i < 30; i++) log.write(`line ${String(i).padStart(2, '0')} xxxxxxxxxx`);
      assert.ok(fs.statSync(f).size <= 100);
      assert.ok(fs.existsSync(`${f}.1`));
      assert.ok(!fs.existsSync(`${f}.2`));
      assert.match(fs.readFileSync(f, 'utf8'), /line 29/);
    } finally {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });
  test('console redirect writes timestamped lines and can be undone', () => {
    const lines = [];
    const restore = redirectConsole({ write: (l) => lines.push(l) });
    try {
      console.log('hello %d', 5);
      console.error('oops');
    } finally {
      restore();
    }
    assert.equal(lines.length, 2);
    assert.match(lines[0], /^\d{4}-\d\d-\d\dT.* hello 5$/);
    assert.match(lines[1], / oops$/);
  });
});

describe('startup browser + error box', () => {
  test('packaged: browser only on first run or while setup is needed', () => {
    assert.equal(shouldOpenBrowser({ packaged: true, needsSetup: false, firstRun: false }), false);
    assert.equal(shouldOpenBrowser({ packaged: true, needsSetup: true, firstRun: false }), true);
    assert.equal(shouldOpenBrowser({ packaged: true, needsSetup: false, firstRun: true }), true);
    assert.equal(shouldOpenBrowser({ argv: ['--no-browser'], packaged: true, needsSetup: true, firstRun: true }), false);
    assert.equal(shouldOpenBrowser({ argv: ['--open-browser'], packaged: true, needsSetup: false }), true);
  });
  test('from source: only with --open-browser', () => {
    assert.equal(shouldOpenBrowser({ packaged: false, needsSetup: true, firstRun: true }), false);
    assert.equal(shouldOpenBrowser({ argv: ['--open-browser'], packaged: false }), true);
  });
  test('message box: PowerShell with the text encoded, nothing off Windows', () => {
    let call = null;
    const spawnSyncFn = (cmd, args, opts) => { call = { cmd, args, opts }; return {}; };
    assert.equal(showMessageBox("It's broken", { platform: 'linux', spawnSyncFn }), false);
    assert.equal(call, null);
    assert.equal(showMessageBox("Port 8765 isn't free\nClose it", { platform: 'win32', spawnSyncFn, title: 'Signal Agent' }), true);
    assert.equal(call.cmd, 'powershell.exe');
    assert.equal(call.opts.windowsHide, true);
    const script = Buffer.from(call.args[call.args.indexOf('-EncodedCommand') + 1], 'base64').toString('utf16le');
    assert.match(script, /PresentationFramework/);
    assert.match(script, /MessageBox\]::Show\('Port 8765 isn''t free\nClose it', 'Signal Agent', 'OK', 'Error'\)/);
  });
});

describe('admin quit + tray state', () => {
  let tmp; let agent; let port; let quitCalls = 0; let stopped;

  before(async () => {
    tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-quit-test-'));
    const config = normalizeConfig({ name: 'QUIT-PC', port: 0, libraries: [] }, tmp);
    stopped = new Promise((resolve) => {
      agent = new Agent(config, {
        mdns: false, watch: false, periodic: false, logFile: false, quiet: true,
        // Instead of process.exit: stop the agent and tell the test.
        onQuit: async () => { quitCalls++; await agent.stop(); resolve(); },
      });
    });
    port = await agent.start({ port: 0, host: '127.0.0.1' });
  });

  after(async () => {
    await agent?.stop();
    fs.rmSync(tmp, { recursive: true, force: true });
  });

  const req = (method, p, { headers = {} } = {}) => new Promise((resolve, reject) => {
    const r = http.request({ host: '127.0.0.1', port, method, path: p, headers: { Host: `localhost:${port}`, ...headers } }, (res) => {
      let data = '';
      res.on('data', (c) => { data += c; });
      res.on('end', () => resolve({ status: res.statusCode, json: data ? JSON.parse(data) : null }));
    });
    r.on('error', reject);
    r.end();
  });
  const H = { 'X-Signal-Admin': '1' };

  test('tray-state needs localhost and the admin header', async () => {
    assert.equal((await req('GET', '/admin/tray-state')).status, 403);
    assert.equal((await req('GET', '/admin/tray-state', { headers: { ...H, Host: 'evil.example' } })).status, 403);
    const ok = await req('GET', '/admin/tray-state', { headers: H });
    assert.equal(ok.status, 200);
    assert.equal(ok.json.pending, 0);
    assert.equal(ok.json.devices, 0);
    assert.equal(typeof ok.json.startup, 'boolean');
    assert.equal(ok.json.name, 'QUIT-PC');
  });

  test('quit needs localhost and the admin header, then stops the agent', async () => {
    assert.equal((await req('POST', '/admin/quit')).status, 403);
    assert.equal((await req('POST', '/admin/quit', { headers: { ...H, Host: `192.168.1.5:${port}` } })).status, 403);
    assert.equal((await req('GET', '/admin/quit', { headers: H })).status, 405);
    assert.equal(quitCalls, 0);
    const r = await req('POST', '/admin/quit', { headers: H });
    assert.equal(r.status, 200);
    assert.deepEqual(r.json, { ok: true });
    await stopped;
    assert.equal(quitCalls, 1);
    assert.equal(agent.server, null);
    await assert.rejects(fetch(`http://127.0.0.1:${port}/api/info`));
  });
});

test('crash handling: dropped connections are ignored, restarts are capped', async () => {
  const { isConnectionError, shouldRestart } = await import('../src/index.js');
  assert.equal(isConnectionError(Object.assign(new Error('x'), { code: 'ECONNRESET' })), true);
  assert.equal(isConnectionError(new Error('boom')), false);
  const now = 1_000_000_000;
  assert.equal(shouldRestart([], now), true);
  assert.equal(shouldRestart([now - 1000, now - 2000], now), true);
  assert.equal(shouldRestart([now - 1000, now - 2000, now - 3000], now), false);
  assert.equal(shouldRestart([now - 3600_000, now - 3700_000, now - 3800_000], now), true);
});
