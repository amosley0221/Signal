import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { Agent } from '../src/agent.js';
import { normalizeConfig } from '../src/config.js';

/** Minimal valid 16-bit PCM WAV: 44-byte RIFF header + silence. */
function writeWav(file, { seconds = 1, sampleRate = 44100, channels = 2 } = {}) {
  const bytesPerSample = 2;
  const dataLen = seconds * sampleRate * channels * bytesPerSample;
  const b = Buffer.alloc(44 + dataLen);
  b.write('RIFF', 0, 'ascii');
  b.writeUInt32LE(36 + dataLen, 4);
  b.write('WAVE', 8, 'ascii');
  b.write('fmt ', 12, 'ascii');
  b.writeUInt32LE(16, 16); // PCM fmt chunk size
  b.writeUInt16LE(1, 20); // PCM
  b.writeUInt16LE(channels, 22);
  b.writeUInt32LE(sampleRate, 24);
  b.writeUInt32LE(sampleRate * channels * bytesPerSample, 28);
  b.writeUInt16LE(channels * bytesPerSample, 32);
  b.writeUInt16LE(16, 34);
  b.write('data', 36, 'ascii');
  b.writeUInt32LE(dataLen, 40);
  fs.writeFileSync(file, b);
  return b.length;
}

let tmp;
let agent;
let base;
let token;
let wavSize;

before(async () => {
  tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-agent-test-'));
  const music = path.join(tmp, 'Music', 'Neon Cathedral');
  fs.mkdirSync(music, { recursive: true });
  wavSize = writeWav(path.join(music, '01 Chrome Hearts.wav'));
  fs.writeFileSync(path.join(music, '01 Chrome Hearts.lrc'), '[la:es]\n[00:01.50]Baja la marea\n[00:04.00][00:08.00]Y el faro\n');
  fs.writeFileSync(path.join(music, '01 Chrome Hearts.en.lrc'), '[00:01.50]The tide goes out\n');
  const config = normalizeConfig({
    name: 'TEST-PC',
    port: 0,
    dataDir: path.join(tmp, 'data'),
    libraries: [{ id: 'l1', name: 'Suno Music', type: 'music', path: path.join(tmp, 'Music') }],
  }, tmp);
  agent = new Agent(config, { mdns: false, watch: false, periodic: false, logFile: false, quiet: true });
  const port = await agent.start({ port: 0, host: '127.0.0.1' });
  await agent.initialScan;
  base = `http://127.0.0.1:${port}`;
});

after(async () => {
  await agent?.stop();
  fs.rmSync(tmp, { recursive: true, force: true });
});

const api = (p, opts = {}) => fetch(base + p, { ...opts, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(opts.headers || {}) } });

test('GET /api/info is public', async () => {
  const r = await fetch(`${base}/api/info`);
  assert.equal(r.status, 200);
  const j = await r.json();
  assert.equal(j.name, 'TEST-PC');
  assert.equal(j.version, '1.0.0');
  assert.equal(typeof j.id, 'string');
  assert.equal(j.plex, false);
  assert.ok(Array.isArray(j.addresses));
});

test('protected endpoints need a token', async () => {
  const r = await fetch(`${base}/api/catalog`);
  assert.equal(r.status, 401);
  assert.deepEqual(await r.json(), { error: 'unauthorized' });
});

test('pairing: start → pending → approve → token', async () => {
  const s = await fetch(`${base}/api/pair/start`, { method: 'POST', body: JSON.stringify({ deviceName: 'Galaxy Z Fold6' }) });
  const { requestId, code } = await s.json();
  assert.match(code, /^\d{6}$/);
  let st = await (await fetch(`${base}/api/pair/status/${requestId}`)).json();
  assert.deepEqual(st, { status: 'pending' });
  assert.ok(agent.approvePairing(code));
  st = await (await fetch(`${base}/api/pair/status/${requestId}`)).json();
  assert.equal(st.status, 'approved');
  assert.match(st.token, /^[0-9a-f]{64}$/);
  token = st.token;
  assert.equal(agent.state.listDevices()[0].name, 'Galaxy Z Fold6');
  // token is stored hashed
  await agent.state.save();
  const saved = fs.readFileSync(path.join(agent.config.dataDir, 'state.json'), 'utf8');
  assert.ok(saved.includes('Galaxy Z Fold6'));
  assert.ok(!saved.includes(token));
  // query-string token works too
  const q = await fetch(`${base}/api/libraries?token=${token}`);
  assert.equal(q.status, 200);
  const libs = await q.json();
  assert.deepEqual(libs.map((l) => [l.id, l.type, l.count, l.bytes]), [['l1', 'music', 1, wavSize]]);
});

test('GET /api/catalog lists the WAV with quality and lyric flags', async () => {
  const j = await (await api('/api/catalog')).json();
  assert.equal(typeof j.generatedAt, 'number');
  assert.deepEqual([j.videos, j.movies, j.shows], [[], [], []]);
  assert.equal(j.tracks.length, 1);
  const t = j.tracks[0];
  assert.equal(t.libraryId, 'l1');
  assert.equal(t.title, 'Chrome Hearts');
  assert.equal(t.album, 'Neon Cathedral');
  assert.equal(t.track, 1);
  assert.equal(t.container, 'WAV');
  assert.equal(t.codec, 'PCM');
  assert.equal(t.bitDepth, 16);
  assert.equal(t.sampleRate, 44100);
  assert.equal(t.durationMs, 1000);
  assert.equal(t.size, wavSize);
  assert.equal(t.hasLyrics, true);
  assert.equal(t.hasTranslation, true);
  assert.equal(t.lyricsLang, 'es');
  assert.equal(t.relPath, 'Neon Cathedral/01 Chrome Hearts.wav');
});

test('Range request on /api/stream returns 206', async () => {
  const { tracks: [t] } = await (await api('/api/catalog')).json();
  const r = await api(`/api/stream/${t.id}`, { headers: { Range: 'bytes=0-43' } });
  assert.equal(r.status, 206);
  assert.equal(r.headers.get('content-range'), `bytes 0-43/${wavSize}`);
  assert.equal(r.headers.get('accept-ranges'), 'bytes');
  assert.equal(r.headers.get('content-type'), 'audio/wav');
  const buf = Buffer.from(await r.arrayBuffer());
  assert.equal(buf.length, 44);
  assert.equal(buf.toString('ascii', 0, 4), 'RIFF');
  const full = await api(`/api/stream/${t.id}`);
  assert.equal(full.status, 200);
  assert.equal(Number(full.headers.get('content-length')), wavSize);
  await full.arrayBuffer();
  const bad = await api(`/api/stream/${t.id}`, { headers: { Range: `bytes=${wavSize}-` } });
  assert.equal(bad.status, 416);
  await bad.arrayBuffer();
});

test('lyrics, suggestions, tag edit with conflict, activity', async () => {
  const { tracks: [t] } = await (await api('/api/catalog')).json();
  const l = await (await api(`/api/lyrics/${t.id}`)).json();
  assert.equal(l.lang, 'es');
  assert.equal(l.synced, true);
  assert.deepEqual(l.lines, [{ t: 1.5, text: 'Baja la marea' }, { t: 4, text: 'Y el faro' }, { t: 8, text: 'Y el faro' }]);
  assert.deepEqual(l.translation, [{ t: 1.5, text: 'The tide goes out' }]);

  const s = await (await api('/api/suggest-artists', { method: 'POST', body: JSON.stringify({ trackId: t.id, prompt: 'a duo from a rainy port city' }) })).json();
  assert.ok(s.length >= 3);
  for (const x of s) assert.deepEqual(Object.keys(x), ['name', 'why', 'kind']);

  const ok = await api(`/api/tags/${t.id}`, { method: 'POST', body: JSON.stringify({ artist: 'Glass Orchard', baseMtime: t.mtime }) });
  assert.equal(ok.status, 200);
  const okj = await ok.json();
  assert.equal(okj.ok, true);
  assert.equal(typeof okj.mtime, 'number');
  const conflict = await api(`/api/tags/${t.id}`, { method: 'POST', body: JSON.stringify({ artist: 'Someone Else', baseMtime: 1 }) });
  assert.equal(conflict.status, 409);
  assert.deepEqual(await conflict.json(), { error: 'conflict', pcArtist: 'Glass Orchard' });
  const forced = await api(`/api/tags/${t.id}`, { method: 'POST', body: JSON.stringify({ artist: 'Someone Else', baseMtime: 1, force: true }) });
  assert.equal(forced.status, 200);
  await forced.json();
  const again = await (await api('/api/catalog')).json();
  assert.equal(again.tracks[0].artist, 'Someone Else');

  const saved = await api(`/api/lyrics/${t.id}`, { method: 'POST', body: JSON.stringify({ lrc: '[00:01.00]First line\n[00:03.50]Second line' }) });
  assert.equal(saved.status, 200);
  await saved.json();
  const l2 = await (await api(`/api/lyrics/${t.id}`)).json();
  assert.deepEqual(l2.lines.map((x) => x.text), ['First line', 'Second line']);
  assert.equal(l2.lines[1].t, 3.5);
  const bad = await api(`/api/lyrics/${t.id}`, { method: 'POST', body: JSON.stringify({ lrc: '' }) });
  assert.equal(bad.status, 400);
  await bad.text();

  const acts = await (await api('/api/activity')).json();
  assert.ok(acts.some((a) => a.kind === 'scan' && a.state === 'done'));
  assert.ok(acts.some((a) => a.kind === 'tag'));
  for (const a of acts) assert.deepEqual(Object.keys(a), ['id', 'kind', 'title', 'detail', 'state', 'progress']);
});

test('admin page is served to localhost', async () => {
  const r = await fetch(`${base}/`);
  assert.equal(r.status, 200);
  assert.match(await r.text(), /Signal Agent/);
  const denied = await fetch(`${base}/admin/rescan`, { method: 'POST' });
  assert.equal(denied.status, 403);
  await denied.arrayBuffer();
});
