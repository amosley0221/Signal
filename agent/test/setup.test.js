import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import {
  resolveConfigPath, userConfigDir, isPackagedRuntime, validateSetup, isTailscaleAddress, normalizeConfig, DEFAULT_CONFIG_PATH,
} from '../src/config.js';
import { StartupEntry, launchCommand, vbsLauncher, listFolders } from '../src/desktop.js';
import { Agent } from '../src/agent.js';

describe('config location', () => {
  test('packaged on Windows → %APPDATA%\\SignalAgent', () => {
    const p = resolveConfigPath([], { APPDATA: 'C:\\Users\\Ann\\AppData\\Roaming' }, { packaged: true, platform: 'win32' });
    assert.equal(p, 'C:\\Users\\Ann\\AppData\\Roaming\\SignalAgent\\signal-agent.config.json');
  });
  test('packaged on Windows without APPDATA falls back to the profile', () => {
    assert.equal(userConfigDir({ platform: 'win32', env: {}, home: 'C:\\Users\\Ann' }), 'C:\\Users\\Ann\\AppData\\Roaming\\SignalAgent');
  });
  test('packaged elsewhere → ~/.config/signal-agent (XDG_CONFIG_HOME honoured)', () => {
    assert.equal(resolveConfigPath([], {}, { packaged: true, platform: 'linux', home: '/home/ann' }), '/home/ann/.config/signal-agent/signal-agent.config.json');
    assert.equal(resolveConfigPath([], { XDG_CONFIG_HOME: '/x' }, { packaged: true, platform: 'darwin', home: '/Users/ann' }), '/x/signal-agent/signal-agent.config.json');
  });
  test('from source → next to the agent folder', () => {
    assert.equal(resolveConfigPath([], {}, { packaged: false }), DEFAULT_CONFIG_PATH);
  });
  test('--config and SIGNAL_AGENT_CONFIG still win when packaged', () => {
    assert.equal(resolveConfigPath(['--config', '/a/b.json'], { SIGNAL_AGENT_CONFIG: '/c.json' }, { packaged: true, platform: 'linux' }), path.resolve('/a/b.json'));
    assert.equal(resolveConfigPath(['--config=/a/d.json'], {}, { packaged: true }), path.resolve('/a/d.json'));
    assert.equal(resolveConfigPath([], { SIGNAL_AGENT_CONFIG: '/c.json' }, { packaged: true, platform: 'linux' }), path.resolve('/c.json'));
  });
  test('data dir defaults next to the config file', () => {
    const cfg = normalizeConfig({}, '/home/ann/.config/signal-agent');
    assert.equal(cfg.dataDir, path.resolve('/home/ann/.config/signal-agent', 'data'));
  });
  test('packaged detection', () => {
    assert.equal(isPackagedRuntime({ isSea: true }), true);
    assert.equal(isPackagedRuntime({ isSea: false, execPath: 'C:\\x\\SignalAgent.exe' }), false);
    assert.equal(isPackagedRuntime({ execPath: '/usr/bin/node' }), false);
    assert.equal(isPackagedRuntime({ execPath: 'C:\\Program Files\\nodejs\\node.exe' }), false);
    assert.equal(isPackagedRuntime({ execPath: 'C:\\Users\\a\\Downloads\\SignalAgent.exe' }), true);
  });
  test('Tailscale addresses are 100.64.0.0/10', () => {
    assert.equal(isTailscaleAddress('100.101.1.2'), true);
    assert.equal(isTailscaleAddress('100.64.0.1'), true);
    assert.equal(isTailscaleAddress('100.128.0.1'), false);
    assert.equal(isTailscaleAddress('192.168.1.5'), false);
  });
});

describe('validateSetup', () => {
  const dirs = new Set(['D:\\Music', 'E:\\TV', 'D:\\Music Videos']);
  const isDir = (p) => dirs.has(p);
  const win = { isDir, platform: 'win32', fallbackName: 'PC' };

  test('accepts good rows, keeps ids, assigns new ids, defaults names', () => {
    const r = validateSetup({
      name: '  Studio ',
      libraries: [
        { id: 'l2', name: 'Songs', type: 'music', path: 'D:\\Music' },
        { name: '', type: 'tv', path: ' "E:\\TV" ' },
        { type: 'musicvideos', path: 'D:\\Music Videos\\' },
      ],
      plex: { url: 'http://127.0.0.1:32400/', token: 'abc' },
    }, win);
    assert.equal(r.ok, true, JSON.stringify(r.errors));
    assert.equal(r.setup.name, 'Studio');
    assert.deepEqual(r.setup.libraries.map((l) => [l.id, l.name, l.type, l.path]), [
      ['l2', 'Songs', 'music', 'D:\\Music'],
      ['l1', 'TV', 'tv', 'E:\\TV'],
      ['l3', 'Music Videos', 'musicvideos', 'D:\\Music Videos'],
    ]);
    assert.deepEqual(r.setup.plex, { url: 'http://127.0.0.1:32400', token: 'abc' });
  });

  test('rejects empty, relative, missing, duplicate (case-insensitive on Windows) and bad types', () => {
    const r = validateSetup({
      libraries: [
        { type: 'music', path: '' },
        { type: 'music', path: 'Music' },
        { type: 'music', path: 'Z:\\Nope' },
        { type: 'music', path: 'D:\\Music' },
        { type: 'music', path: 'd:\\music' },
        { type: 'podcasts', path: 'E:\\TV' },
      ],
    }, win);
    assert.equal(r.ok, false);
    assert.deepEqual(r.errors.map((e) => [e.row, e.field]), [[0, 'path'], [1, 'path'], [2, 'path'], [4, 'path'], [5, 'type']]);
    assert.equal(r.setup.name, 'PC');
  });

  test('duplicate ids in the input get a fresh id', () => {
    const r = validateSetup({ libraries: [{ id: 'l1', type: 'music', path: 'D:\\Music' }, { id: 'l1', type: 'tv', path: 'E:\\TV' }] }, win);
    assert.deepEqual(r.setup.libraries.map((l) => l.id), ['l1', 'l2']);
  });

  test('Plex: bad URL and URL without token are errors; both empty is fine', () => {
    assert.deepEqual(validateSetup({ plex: { url: 'ftp://x', token: 't' } }, win).errors.map((e) => e.field), ['plex.url']);
    assert.deepEqual(validateSetup({ plex: { url: 'http://nas:32400' } }, win).errors.map((e) => e.field), ['plex.token']);
    assert.equal(validateSetup({ plex: { url: '', token: '' }, libraries: [] }, win).ok, true);
  });
});

describe('Start with Windows', () => {
  test('launcher runs the exe hidden with --no-browser', () => {
    const cmd = launchCommand({ packaged: true, execPath: 'C:\\Users\\Zoë\\SignalAgent.exe' });
    assert.deepEqual(cmd, ['C:\\Users\\Zoë\\SignalAgent.exe', '--no-browser']);
    const vbs = vbsLauncher(cmd);
    assert.match(vbs, /sh\.Run """C:\\Users\\Zoë\\SignalAgent\.exe"" ""--no-browser""", 0, False/);
  });
  test('from source it runs node + the script, and keeps --config', () => {
    const cmd = launchCommand({ packaged: false, execPath: 'C:\\node\\node.exe', scriptPath: 'C:\\agent\\src\\index.js', configOverride: 'D:\\cfg.json' });
    assert.deepEqual(cmd, ['C:\\node\\node.exe', 'C:\\agent\\src\\index.js', '--no-browser', '--config', 'D:\\cfg.json']);
  });
  test('writes and removes the Startup-folder file', async () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-startup-'));
    try {
      const e = new StartupEntry({ platform: 'win32', dir, command: ['C:\\a\\SignalAgent.exe', '--no-browser'] });
      assert.equal(e.status().enabled, false);
      const on = await e.set(true);
      assert.equal(on.enabled, true);
      assert.equal(on.upToDate, true);
      const buf = fs.readFileSync(path.join(dir, 'Signal Agent.vbs'));
      assert.deepEqual([...buf.subarray(0, 2)], [0xff, 0xfe]); // UTF-16LE BOM
      assert.match(buf.subarray(2).toString('utf16le'), /SignalAgent\.exe/);
      const moved = new StartupEntry({ platform: 'win32', dir, command: ['C:\\b\\SignalAgent.exe', '--no-browser'] });
      assert.equal(moved.status().upToDate, false);
      const off = await e.set(false);
      assert.equal(off.enabled, false);
      assert.equal(fs.existsSync(path.join(dir, 'Signal Agent.vbs')), false);
    } finally {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });
  test('no-op off Windows', async () => {
    const e = new StartupEntry({ platform: 'linux', command: ['x'] });
    assert.equal(e.status().supported, false);
    assert.equal((await e.set(true)).ok, false);
  });
});

describe('listFolders', () => {
  test('Windows root lists drive letters', async () => {
    const r = await listFolders('', { platform: 'win32', home: os.tmpdir(), drives: () => ['C:\\', 'D:\\'] });
    assert.deepEqual(r.dirs.map((d) => d.path), ['C:\\', 'D:\\']);
    assert.equal(r.parent, null);
  });
  test('lists visible subfolders only, sorted, with parent', async () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-browse-'));
    try {
      for (const n of ['b', 'A', '.hidden', '$RECYCLE.BIN', 'System Volume Information']) fs.mkdirSync(path.join(dir, n));
      fs.writeFileSync(path.join(dir, 'file.txt'), 'x');
      const r = await listFolders(dir, { platform: process.platform === 'win32' ? 'win32' : 'linux' });
      assert.deepEqual(r.dirs.map((d) => d.name), ['A', 'b']);
      assert.equal(r.parent, path.dirname(dir));
      const missing = await listFolders(path.join(dir, 'nope'));
      assert.equal(missing.dirs.length, 0);
      assert.ok(missing.error);
    } finally {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });
});

describe('admin setup endpoints', () => {
  let tmp; let agent; let port; let startupDir;

  before(async () => {
    tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-setup-test-'));
    fs.mkdirSync(path.join(tmp, 'Music', 'Album'), { recursive: true });
    fs.writeFileSync(path.join(tmp, 'Music', 'Album', '01 One.mp3'), '');
    startupDir = path.join(tmp, 'Startup');
    const configPath = path.join(tmp, 'signal-agent.config.json');
    fs.writeFileSync(configPath, JSON.stringify({ name: 'SETUP-PC', port: 0, libraries: [], custom: 'kept' }));
    const config = normalizeConfig(JSON.parse(fs.readFileSync(configPath, 'utf8')), tmp);
    config.configPath = configPath;
    agent = new Agent(config, {
      mdns: false, watch: false, periodic: false, logFile: false, quiet: true,
      startup: new StartupEntry({ platform: 'win32', dir: startupDir, command: ['C:\\SignalAgent.exe', '--no-browser'] }),
    });
    port = await agent.start({ port: 0, host: '127.0.0.1' });
    await agent.initialScan;
  });

  after(async () => {
    await agent?.stop();
    fs.rmSync(tmp, { recursive: true, force: true });
  });

  /** Raw request so the Host header can be set. */
  const req = (method, p, { headers = {}, body } = {}) => new Promise((resolve, reject) => {
    const r = http.request({ host: '127.0.0.1', port, method, path: p, headers: { Host: `localhost:${port}`, ...headers } }, (res) => {
      let data = '';
      res.on('data', (c) => { data += c; });
      res.on('end', () => resolve({ status: res.statusCode, json: data ? JSON.parse(data) : null }));
    });
    r.on('error', reject);
    if (body) r.end(JSON.stringify(body)); else r.end();
  });
  const H = { 'X-Signal-Admin': '1' };

  test('admin state says setup is needed, with split addresses', async () => {
    const r = await req('GET', '/admin/state');
    assert.equal(r.status, 200);
    assert.equal(r.json.needsSetup, true);
    assert.ok(Array.isArray(r.json.network.lan));
    assert.ok(Array.isArray(r.json.network.tailscale));
  });

  test('folder listing needs localhost Host and the admin header', async () => {
    const q = `/admin/browse?path=${encodeURIComponent(tmp)}`;
    assert.equal((await req('GET', q)).status, 403);
    assert.equal((await req('GET', q, { headers: { ...H, Host: 'evil.example' } })).status, 403);
    const ok = await req('GET', q, { headers: H });
    assert.equal(ok.status, 200);
    assert.ok(ok.json.dirs.some((d) => d.name === 'Music'));
  });

  test('setup POST needs the admin header and rejects bad folders', async () => {
    assert.equal((await req('POST', '/admin/setup', { body: { libraries: [] } })).status, 403);
    const bad = await req('POST', '/admin/setup', { headers: H, body: { libraries: [{ type: 'music', path: path.join(tmp, 'Nope') }] } });
    assert.equal(bad.status, 400);
    assert.equal(bad.json.errors[0].row, 0);
  });

  test('saving writes the config and applies it live', async () => {
    const r = await req('POST', '/admin/setup', { headers: H, body: { name: 'Renamed', libraries: [{ name: 'Tunes', type: 'music', path: path.join(tmp, 'Music') }] } });
    assert.equal(r.status, 200, JSON.stringify(r.json));
    assert.equal(r.json.setup.libraries[0].id, 'l1');
    const onDisk = JSON.parse(fs.readFileSync(path.join(tmp, 'signal-agent.config.json'), 'utf8'));
    assert.equal(onDisk.name, 'Renamed');
    assert.equal(onDisk.custom, 'kept');
    assert.equal(onDisk.port, 0);
    assert.equal(onDisk.libraries[0].path, path.join(tmp, 'Music'));
    await agent.reconfiguring;
    await agent.lastApplyScan;
    const s = await req('GET', '/admin/state');
    assert.equal(s.json.needsSetup, false);
    assert.equal(s.json.info.name, 'Renamed');
    assert.equal(s.json.libraries.length, 1);
    assert.equal(s.json.libraries[0].count, 1);
    const info = await (await fetch(`http://127.0.0.1:${port}/api/info`)).json();
    assert.equal(info.name, 'Renamed');
  });

  test('Start with Windows toggle', async () => {
    assert.equal((await req('POST', '/admin/startup', { body: { enabled: true } })).status, 403);
    const on = await req('POST', '/admin/startup', { headers: H, body: { enabled: true } });
    assert.equal(on.json.enabled, true);
    assert.ok(fs.existsSync(path.join(startupDir, 'Signal Agent.vbs')));
    const s = await req('GET', '/admin/setup');
    assert.equal(s.json.startup.enabled, true);
    const off = await req('POST', '/admin/startup', { headers: H, body: { enabled: false } });
    assert.equal(off.json.enabled, false);
  });
});
