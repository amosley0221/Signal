import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {
  compareVersions, parseSha256File, parseRelease, oldExePath, swapAndRestart, Updater, helperArgs, HELPER_PS1, takeLastUpdateError,
} from '../src/updater.js';
import { validateSetup, DEFAULT_PLEX_URL } from '../src/config.js';

describe('versions and release parsing', () => {
  test('semver compare', () => {
    assert.equal(compareVersions('1.0.5', '1.0.4'), 1);
    assert.equal(compareVersions('v1.0.4', '1.0.4'), 0);
    assert.equal(compareVersions('1.0.10', '1.0.9'), 1);
    assert.equal(compareVersions('1.1.0-beta.1', '1.1.0'), -1);
    assert.equal(compareVersions('garbage', '1.0.0'), -1);
  });
  test('sha256 file formats', () => {
    const hex = 'a'.repeat(64);
    assert.equal(parseSha256File(`${hex}  SignalAgent.exe`), hex);
    assert.equal(parseSha256File(`\ufeff*${hex.toUpperCase()}`), hex);
    assert.equal(parseSha256File('nothex'), null);
  });
  test('parseRelease finds the exe and checksum', () => {
    const r = parseRelease({ tag_name: 'v1.0.5', body: ' notes ', assets: [
      { name: 'Signal-1.0.5.apk', browser_download_url: 'x' },
      { name: 'SignalAgent.exe', browser_download_url: 'https://e/exe', size: 10 },
      { name: 'SignalAgent.exe.sha256', browser_download_url: 'https://e/sha' },
    ] });
    assert.equal(r.version, '1.0.5');
    assert.deepEqual(r.exe, { url: 'https://e/exe', size: 10 });
    assert.equal(r.shaUrl, 'https://e/sha');
    assert.equal(r.notes, 'notes');
  });
  test('old exe path on Windows', () => {
    assert.equal(oldExePath('C:\\Apps\\SignalAgent.exe'), 'C:\\Apps\\SignalAgent.old.exe');
  });
});

describe('swapAndRestart', () => {
  const makeFs = (fail = {}) => {
    const files = new Set(['/a/SignalAgent.exe', '/tmp/new.exe']);
    const ops = [];
    return {
      files, ops,
      fsOps: {
        rename: async (a, b) => { ops.push(`rename ${a} ${b}`); if (fail.rename?.(a, b)) throw new Error('locked'); files.delete(a); files.add(b); },
        copyFile: async (a, b) => { ops.push(`copy ${a} ${b}`); if (fail.copy) throw new Error('disk full'); files.add(b); },
        rm: async (p) => { files.delete(p); },
        exists: (p) => files.has(p),
      },
    };
  };
  test('happy path: old renamed, new moved in, started with --updated', async () => {
    const f = makeFs();
    let started = null;
    await swapAndRestart({ newFile: '/tmp/new.exe', execPath: '/a/SignalAgent.exe', fsOps: f.fsOps, spawnFn: (cmd, args) => { started = [cmd, args]; return { unref() {} }; } });
    assert.ok(f.files.has('/a/SignalAgent.exe'));
    assert.ok(f.files.has(oldExePath('/a/SignalAgent.exe')));
    assert.deepEqual(started, ['/a/SignalAgent.exe', ['--updated', '--no-browser']]);
  });
  test('moving the new file fails → old version is put back', async () => {
    const f = makeFs({ rename: (a) => a === '/tmp/new.exe', copy: true });
    await assert.rejects(swapAndRestart({ newFile: '/tmp/new.exe', execPath: '/a/SignalAgent.exe', fsOps: f.fsOps, spawnFn: () => ({}) }), /new version in place/);
    assert.ok(f.files.has('/a/SignalAgent.exe'));
    assert.ok(!f.files.has(oldExePath('/a/SignalAgent.exe')));
  });
  test('cannot rename the running exe → nothing changes', async () => {
    const f = makeFs({ rename: (a) => a === '/a/SignalAgent.exe' });
    await assert.rejects(swapAndRestart({ newFile: '/tmp/new.exe', execPath: '/a/SignalAgent.exe', fsOps: f.fsOps, spawnFn: () => ({}) }), /rename the running program/);
    assert.ok(f.files.has('/a/SignalAgent.exe'));
  });
});

describe('Updater', () => {
  test('from source: never offers updates', async () => {
    const u = new Updater({ current: '1.0.0', packaged: false, dataDir: '/tmp' });
    assert.equal(u.state().available, false);
    assert.equal(u.state().reason, 'running from source');
    assert.equal((await u.install()).ok, false);
  });
  test('packaged on Windows: sees a newer release; refuses a release without checksum', async () => {
    const release = { tag_name: 'v9.9.9', body: 'new', assets: [{ name: 'SignalAgent.exe', browser_download_url: 'https://e/exe', size: 1 }] };
    const fetchFn = async () => ({ ok: true, json: async () => release });
    const logs = [];
    const u = new Updater({ current: '1.0.4', packaged: true, platform: 'win32', dataDir: '/tmp/signal-upd-test', fetchFn, log: (m) => logs.push(m) });
    await u.check();
    assert.equal(u.state().available, true);
    assert.equal(u.state().latest, '9.9.9');
    assert.equal((await u.install()).ok, true);
    await u.installing;
    assert.match(u.state().error, /no SignalAgent\.exe\.sha256/);
  });
  test('same version is not an update', async () => {
    const fetchFn = async () => ({ ok: true, json: async () => ({ tag_name: 'v1.0.4', assets: [{ name: 'SignalAgent.exe', browser_download_url: 'x', size: 1 }] }) });
    const u = new Updater({ current: '1.0.4', packaged: true, platform: 'win32', dataDir: '/tmp', fetchFn });
    await u.check();
    assert.equal(u.state().available, false);
  });
});

test('Plex token without an address defaults to this PC', () => {
  const v = validateSetup({ name: 'PC', libraries: [], plex: { url: '', token: 'abc' } }, { isDir: () => true });
  assert.equal(v.ok, true);
  assert.equal(v.setup.plex.url, DEFAULT_PLEX_URL);
  const none = validateSetup({ name: 'PC', libraries: [], plex: { url: '', token: '' } }, { isDir: () => true });
  assert.equal(none.setup.plex.url, '');
});

describe('install via helper (no renaming the running exe)', () => {
  test('helper arguments and script', () => {
    const a = helperArgs({ scriptPath: 'C:\\d\\apply-update.ps1', pid: 42, src: 'C:\\d\\new.exe', dst: 'C:\\Users\\Me\\Downloads\\SignalAgent.exe' });
    assert.deepEqual(a.slice(a.indexOf('-AgentPid'), a.indexOf('-AgentPid') + 2), ['-AgentPid', '42']);
    assert.equal(a[a.indexOf('-ArgLine') + 1], '--updated --no-browser');
    assert.match(HELPER_PS1, /Wait-Process -Id \$AgentPid/);
    assert.match(HELPER_PS1, /restoring the previous version/);
    assert.match(HELPER_PS1, /Start-Process -FilePath \$Dst/);
  });
  test('downloads, verifies, hands the file to the helper, then restarts', async () => {
    const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-upd-'));
    try {
      const exe = Buffer.from('new program bytes');
      const sha = crypto.createHash('sha256').update(exe).digest('hex');
      const release = { tag_name: 'v9.0.0', assets: [
        { name: 'SignalAgent.exe', browser_download_url: 'https://e/exe', size: exe.length },
        { name: 'SignalAgent.exe.sha256', browser_download_url: 'https://e/sha' },
      ] };
      const fetchFn = async (url) => {
        if (url.includes('api.github.com')) return { ok: true, json: async () => release };
        if (url === 'https://e/sha') return { ok: true, text: async () => `${sha}  SignalAgent.exe` };
        return { ok: true, body: new Response(exe).body };
      };
      let helper = null; let restarted = false;
      const u = new Updater({ current: '1.0.0', packaged: true, platform: 'win32', dataDir, fetchFn, execPath: 'C:\\A\\SignalAgent.exe',
        startHelper: async (o) => { helper = o; }, onRestart: async () => { restarted = true; } });
      await u.check();
      assert.equal((await u.install()).ok, true);
      await u.installing;
      assert.equal(u.state().error, null);
      assert.ok(helper && fs.readFileSync(helper.newFile).equals(exe));
      assert.equal(helper.execPath, 'C:\\A\\SignalAgent.exe');
      assert.equal(restarted, true);
    } finally { fs.rmSync(dataDir, { recursive: true, force: true }); }
  });
  test('a failed install is reported once on the next start', () => {
    const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-upd-'));
    fs.mkdirSync(path.join(dataDir, 'update'));
    fs.writeFileSync(path.join(dataDir, 'update', 'last-update-error.txt'), 'The update could not be installed: busy');
    assert.match(takeLastUpdateError(dataDir), /busy/);
    assert.equal(takeLastUpdateError(dataDir), null);
    fs.rmSync(dataDir, { recursive: true, force: true });
  });
});
