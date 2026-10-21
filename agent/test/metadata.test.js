import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { Agent } from '../src/agent.js';
import { normalizeConfig } from '../src/config.js';

// No Plex at all: movie and TV details come from (a fake) TMDB.
let tmp; let agent; let base; let token; const realFetch = globalThis.fetch;
const png = Buffer.from('89504e470d0a1a0a', 'hex');

before(async () => {
  globalThis.fetch = async (u, o) => {
    const url = new URL(String(u));
    const json = (x) => ({ ok: true, status: 200, json: async () => x, headers: new Map([['content-type', 'application/json']]) });
    if (url.hostname === 'image.tmdb.org') return { ok: true, status: 200, headers: new Map([['content-type', 'image/jpeg']]), arrayBuffer: async () => png };
    if (url.hostname !== 'api.themoviedb.org') return realFetch(u, o);
    const q = url.searchParams.get('query');
    switch (url.pathname) {
      case '/3/configuration': return json({});
      case '/3/search/movie': return json({ results: q === 'Low Orbit' ? [{ id: 11, title: 'Low Orbit', release_date: '2025-03-01' }] : [{ id: 12, title: q, release_date: '2001-01-01' }] });
      case '/3/movie/11': return json({ id: 11, title: 'Low Orbit', release_date: '2025-03-01', overview: 'Space.', poster_path: '/lo.jpg', backdrop_path: '/lob.jpg', runtime: 101, genres: [{ name: 'Sci-Fi' }] });
      case '/3/movie/99': return json({ id: 99, title: 'Low Orbit: Director\'s Cut', release_date: '2026-01-01', poster_path: '/dc.jpg' });
      case '/3/search/tv': return json({ results: [{ id: 21, name: 'The Ballast', first_air_date: '2024-01-01' }] });
      case '/3/tv/21': return json({ id: 21, name: 'The Ballast', first_air_date: '2024-01-01', overview: 'Ships.', poster_path: '/tb.jpg' });
      case '/3/tv/21/season/1': return json({ episodes: [{ episode_number: 1, name: 'Pilot Light', overview: 'It begins.', still_path: '/e1.jpg' }] });
      default: return { ok: false, status: 404, json: async () => ({}) };
    }
  };
  tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-meta-test-'));
  fs.mkdirSync(path.join(tmp, 'Movies'), { recursive: true });
  fs.mkdirSync(path.join(tmp, 'TV', 'The Ballast', 'Season 01'), { recursive: true });
  fs.writeFileSync(path.join(tmp, 'Movies', 'Low Orbit (2025).mkv'), 'x');
  fs.writeFileSync(path.join(tmp, 'TV', 'The Ballast', 'Season 01', 'The Ballast - S01E01.mkv'), 'x');
  const config = normalizeConfig({
    name: 'TEST-PC', port: 0, dataDir: path.join(tmp, 'data'),
    libraries: [{ id: 'm', name: 'Movies', type: 'movies', path: path.join(tmp, 'Movies') }, { id: 't', name: 'TV', type: 'tv', path: path.join(tmp, 'TV') }],
  }, tmp);
  agent = new Agent(config, { mdns: false, watch: false, periodic: false, logFile: false, quiet: true });
  const port = await agent.start({ port: 0, host: '127.0.0.1' });
  await agent.initialScan;
  base = `http://127.0.0.1:${port}`;
  const s = await realFetch(`${base}/api/pair/start`, { method: 'POST', body: JSON.stringify({ deviceName: 'Fold' }) });
  const { requestId, code } = await s.json();
  agent.approvePairing(code);
  token = (await (await realFetch(`${base}/api/pair/status/${requestId}`)).json()).token;
});

after(async () => {
  globalThis.fetch = realFetch;
  await agent?.stop();
  fs.rmSync(tmp, { recursive: true, force: true });
});

const api = (p, opts = {}) => realFetch(base + p, { ...opts, headers: { Authorization: `Bearer ${token}`, ...(opts.headers || {}) } });
const settle = async () => { for (let i = 0; i < 4; i++) { agent.catalog.build(); await agent.tmdb.idle(); } };

test('without a TMDB key, titles come from file names', async () => {
  const cat = await (await api('/api/catalog')).json();
  assert.equal(cat.movies[0].title, 'Low Orbit');
  assert.equal(cat.movies[0].matchedBy, 'FILENAME');
  assert.equal(cat.movies[0].posterUrl, null);
  assert.deepEqual(await (await api('/api/metadata')).json(), { tmdb: false, plex: false, prefer: 'plex' });
});

test('with a TMDB key: details, posters and episode titles from TMDB', async () => {
  const r = await api('/api/metadata', { method: 'POST', body: JSON.stringify({ tmdbKey: 'key123' }) });
  assert.equal(r.status, 200);
  await settle();
  const cat = await (await api('/api/catalog')).json();
  const mv = cat.movies[0];
  assert.equal(mv.matchedBy, 'TMDB');
  assert.equal(mv.synopsis, 'Space.');
  assert.deepEqual(mv.genres, ['Sci-Fi']);
  assert.ok(mv.posterUrl);
  const poster = await api(mv.posterUrl);
  assert.equal(poster.status, 200);
  const show = cat.shows[0];
  assert.equal(show.title, 'The Ballast');
  assert.equal(show.synopsis, 'Ships.');
  const ep = show.seasons[0].episodes[0];
  assert.equal(ep.title, 'Pilot Light');
  assert.equal(ep.summary, 'It begins.');
  assert.ok(ep.thumbUrl);
});

test('Fix match: search TMDB and pin a different result', async () => {
  const cat = await (await api('/api/catalog')).json();
  const id = cat.movies[0].id;
  const found = await (await api('/api/match/search?kind=movie&q=Low%20Orbit')).json();
  assert.equal(found[0].tmdbId, 11);
  const r = await api(`/api/match/${id}`, { method: 'POST', body: JSON.stringify({ tmdbId: 99 }) });
  assert.equal(r.status, 200);
  await settle();
  const after2 = await (await api('/api/catalog')).json();
  assert.equal(after2.movies[0].title, "Low Orbit: Director's Cut");
  assert.equal(agent.state.data.matches[id].tmdbId, 99);
});

test('a bad TMDB key is refused', async () => {
  globalThis.fetch = async (u, o) => (new URL(String(u)).hostname === 'api.themoviedb.org' ? { ok: false, status: 401, json: async () => ({}) } : realFetch(u, o));
  const r = await api('/api/metadata', { method: 'POST', body: JSON.stringify({ tmdbKey: 'wrong' }) });
  assert.equal(r.status, 400);
  assert.match((await r.json()).message, /rejected/);
});
