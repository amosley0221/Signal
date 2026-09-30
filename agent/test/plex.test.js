import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import { Agent } from '../src/agent.js';
import { normalizeConfig } from '../src/config.js';

// A tiny fake Plex Media Server.
let tmp; let plexSrv; let agent; let base; let token; const hits = [];

before(async () => {
  tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-plex-test-'));
  const movies = path.join(tmp, 'Movies');
  const tv = path.join(tmp, 'TV', 'The Ballast', 'Season 01');
  fs.mkdirSync(movies, { recursive: true });
  fs.mkdirSync(tv, { recursive: true });
  const movieFile = path.join(movies, 'Low Orbit (2025).mkv');
  const epFile = path.join(tv, 'The Ballast - S01E01 - Pilot.mkv');
  fs.writeFileSync(movieFile, 'x');
  fs.writeFileSync(epFile, 'x');
  plexSrv = http.createServer((req, res) => {
    const u = new URL(req.url, 'http://p');
    hits.push(u.pathname + u.search);
    if (u.searchParams.get('X-Plex-Token') !== null || req.headers['x-plex-token'] !== 'tok') { res.writeHead(401); return res.end(); }
    const json = (o) => { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(o)); };
    if (u.pathname === '/library/sections') {
      return json({ MediaContainer: { Directory: [
        { key: '1', title: 'Movies', type: 'movie', Location: [{ path: movies }] },
        { key: '2', title: 'TV Shows', type: 'show', Location: [{ path: path.join(tmp, 'TV') }] },
      ] } });
    }
    if (u.pathname === '/library/sections/1/all') {
      return json({ MediaContainer: { Metadata: [{
        ratingKey: '101', title: 'Low Orbit', year: 2025, summary: 'Space.', audienceRating: 7.9, contentRating: 'PG-13',
        Genre: [{ tag: 'Sci-fi' }], Director: [{ tag: 'A. Director' }], Role: [{ tag: 'Actor One' }, { tag: 'Actor Two' }],
        thumb: '/library/metadata/101/thumb/1', art: '/library/metadata/101/art/1', viewOffset: 2500000, duration: 7440000,
        Guid: [{ id: 'tmdb://123' }], Media: [{ Part: [{ file: movieFile }] }],
      }] } });
    }
    if (u.pathname === '/library/sections/2/all' && u.searchParams.get('type') === '4') {
      return json({ MediaContainer: { Metadata: [{
        ratingKey: '201', grandparentRatingKey: '200', parentIndex: 1, index: 1, title: 'Pilot', summary: 'It begins.',
        duration: 2880000, thumb: '/library/metadata/201/thumb/1', Media: [{ Part: [{ file: epFile }] }],
      }] } });
    }
    if (u.pathname === '/library/sections/2/all') {
      return json({ MediaContainer: { Metadata: [{ ratingKey: '200', title: 'The Ballast', year: 2024, summary: 'Boats.', contentRating: 'TV-14', Guid: [{ id: 'tvdb://9' }], thumb: '/library/metadata/200/thumb/1' }] } });
    }
    if (u.pathname.startsWith('/library/metadata/101/thumb')) { res.writeHead(200, { 'Content-Type': 'image/jpeg' }); return res.end('JPEGDATA'); }
    if (u.pathname === '/:/timeline' || u.pathname === '/:/scrobble') { res.writeHead(200); return res.end(); }
    res.writeHead(404); return res.end();
  });
  await new Promise((r) => plexSrv.listen(0, '127.0.0.1', r));
  const config = normalizeConfig({
    name: 'PLEX-PC', dataDir: path.join(tmp, 'data'), libraries: [],
    plex: { url: `http://127.0.0.1:${plexSrv.address().port}`, token: 'tok' },
  }, tmp);
  agent = new Agent(config, { mdns: false, watch: false, periodic: false, logFile: false, quiet: true });
  const port = await agent.start({ port: 0, host: '127.0.0.1' });
  await agent.initialScan;
  base = `http://127.0.0.1:${port}`;
  const { requestId, code } = await (await fetch(`${base}/api/pair/start`, { method: 'POST', body: '{"deviceName":"t"}' })).json();
  agent.approvePairing(code);
  token = (await (await fetch(`${base}/api/pair/status/${requestId}`)).json()).token;
});

after(async () => {
  await agent?.stop();
  plexSrv?.close();
  fs.rmSync(tmp, { recursive: true, force: true });
});

const api = (p, opts = {}) => fetch(base + p, { ...opts, headers: { Authorization: `Bearer ${token}`, ...(opts.headers || {}) } });

test('libraries are offered from Plex sections when none are configured', async () => {
  const libs = await (await api('/api/libraries')).json();
  assert.deepEqual(libs.map((l) => [l.name, l.type, l.count]), [['Movies', 'movies', 1], ['TV Shows', 'tv', 1]]);
  assert.equal((await (await fetch(`${base}/api/info`)).json()).plex, true);
});

test('movies and shows are enriched from Plex by file path', async () => {
  const c = await (await api('/api/catalog')).json();
  const m = c.movies[0];
  assert.equal(m.title, 'Low Orbit');
  assert.equal(m.matchedBy, 'PLEX · TMDB');
  assert.deepEqual(m.cast, ['Actor One', 'Actor Two']);
  assert.equal(m.director, 'A. Director');
  assert.equal(m.rating, 7.9);
  assert.equal(m.certificate, 'PG-13');
  assert.equal(m.viewOffsetMs, 2500000);
  assert.equal(m.posterUrl, `/api/art/${m.id}?kind=poster`);
  const sh = c.shows[0];
  assert.equal(sh.matchedBy, 'PLEX · TVDB');
  assert.equal(sh.certificate, 'TV-14');
  assert.equal(sh.seasons[0].episodes[0].summary, 'It begins.');
  const art = await api(`/api/art/${m.id}?kind=poster`);
  assert.equal(art.status, 200);
  assert.equal(await art.text(), 'JPEGDATA');
});

test('progress is forwarded to Plex', async () => {
  const c = await (await api('/api/catalog')).json();
  const m = c.movies[0];
  assert.deepEqual(await (await api('/api/progress', { method: 'POST', body: JSON.stringify({ id: m.id, positionMs: 123000, watched: false }) })).json(), { ok: true });
  await (await api('/api/progress', { method: 'POST', body: JSON.stringify({ id: m.id, positionMs: 0, watched: true }) })).json();
  await new Promise((r) => setTimeout(r, 100));
  assert.ok(hits.some((h) => h.startsWith('/:/timeline') && h.includes('ratingKey=101') && h.includes('time=123000')));
  assert.ok(hits.some((h) => h.startsWith('/:/scrobble') && h.includes('key=101')));
  const after = await (await api('/api/catalog')).json();
  assert.equal(after.movies[0].watched, true);
});

test('Plex.test reports connected / bad token / unreachable in plain words', async () => {
  const { Plex } = await import('../src/plex.js');
  const url = `http://127.0.0.1:${plexSrv.address().port}`;
  assert.deepEqual(await Plex.test({ url, token: 'tok' }), { ok: true, libraries: 2 });
  const bad = await Plex.test({ url, token: 'nope' });
  assert.equal(bad.ok, false);
  assert.match(bad.message, /rejected the token/);
  const down = await Plex.test({ url: 'http://127.0.0.1:9', token: 'tok' }, { timeoutMs: 1500 });
  assert.equal(down.ok, false);
  assert.match(down.message, /didn't answer/);
});
