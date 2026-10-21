import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { Tmdb, pickResult, normTitle } from '../src/tmdb.js';

function fakeTmdb() {
  const calls = [];
  const json = (o, status = 200) => ({ ok: status < 400, status, json: async () => o });
  const fetchImpl = async (u) => {
    const url = new URL(u);
    calls.push(url.pathname + url.search);
    const q = url.searchParams.get('query');
    if (url.pathname === '/3/search/movie') {
      return json({ results: q === 'Candyman' ? [
        { id: 565028, title: 'Candyman', release_date: '2021-08-26', popularity: 40 },
        { id: 9529, title: 'Candyman', release_date: '1992-10-16', popularity: 20 },
      ] : [] });
    }
    if (url.pathname === '/3/movie/9529') {
      return json({ id: 9529, title: 'Candyman', release_date: '1992-10-16', overview: 'Bees.', runtime: 99, vote_average: 6.6, genres: [{ name: 'Horror' }],
        poster_path: '/p.jpg', backdrop_path: '/b.jpg', credits: { cast: [{ name: 'Tony Todd' }], crew: [{ job: 'Director', name: 'Bernard Rose' }] },
        release_dates: { results: [{ iso_3166_1: 'US', release_dates: [{ certification: 'R' }] }] } });
    }
    if (url.pathname === '/3/search/tv') return json({ results: [{ id: 113988, name: 'Monster', first_air_date: '2022-09-21' }] });
    if (url.pathname === '/3/tv/113988') return json({ id: 113988, name: 'Monster', first_air_date: '2022-09-21', overview: 'True crime.', poster_path: '/m.jpg', content_ratings: { results: [{ iso_3166_1: 'US', rating: 'TV-MA' }] } });
    if (url.pathname === '/3/tv/113988/season/4') return json({ episodes: [{ episode_number: 1, name: 'Bloodbath', overview: 'Fall River.', still_path: '/s.jpg', runtime: 54 }] });
    return json({}, 404);
  };
  return { calls, fetchImpl };
}

test('picks the right search result by title and year', () => {
  const rs = [{ title: 'Candyman', release_date: '2021-01-01', popularity: 80 }, { title: 'Candyman', release_date: '1992-01-01', popularity: 10 }];
  assert.equal(pickResult(rs, 'Candyman', 1992, 'movie').release_date, '1992-01-01');
  assert.equal(pickResult([{ title: 'Something Else', release_date: '1992-01-01' }], 'Candyman', 1992, 'movie'), null);
  assert.equal(normTitle('Amélie & Co.'), 'amelie and co');
});

test('looks movies, shows and episodes up in the background and caches them', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-tmdb-'));
  const { calls, fetchImpl } = fakeTmdb();
  let changed = 0;
  const t = new Tmdb({ key: 'abc', dataDir: dir, fetchImpl, onChange: () => { changed++; } });
  assert.equal(t.movieFor('Candyman', 1992), null); // not fetched yet
  await t.idle();
  assert.equal(t.movieFor('Candyman', 1992), null); // search done; details now queued
  await t.idle();
  const m = t.movieFor('Candyman', 1992);
  assert.equal(m.tmdbId, 9529);
  assert.equal(m.title, 'Candyman');
  assert.equal(m.year, 1992);
  assert.equal(m.contentRating, 'R');
  assert.equal(m.director, 'Bernard Rose');
  assert.equal(m.durationMs, 99 * 60000);
  assert.equal(m.thumb, 'tmdb:/p.jpg');
  assert.ok(calls.every((c) => c.includes('api_key=abc')));

  t.showFor('Monster', 2022); await t.idle(); const sh = t.showFor('Monster', 2022) || (await t.idle(), t.showFor('Monster', 2022));
  assert.equal(sh.contentRating, 'TV-MA');
  t.episodeFor(sh.tmdbId, 4, 1); await t.idle();
  const ep = t.episodeFor(sh.tmdbId, 4, 1);
  assert.equal(ep.title, 'Bloodbath');
  assert.equal(ep.thumb, 'tmdb:/s.jpg');

  // A miss isn't searched again right away.
  const before = calls.length;
  t.movieFor('Nothing Like This', 2001); await t.idle(); t.movieFor('Nothing Like This', 2001); await t.idle();
  assert.equal(calls.filter((c) => c.includes('Nothing')).length, 2); // with and without the year, once
  assert.ok(calls.length > before);
  await new Promise((r) => setTimeout(r, 2200)); // let the cache save
  const again = new Tmdb({ key: 'abc', dataDir: dir, fetchImpl });
  assert.equal(again.movieFor('Candyman', 1992).tmdbId, 9529); // from the disk cache, no fetch
});

test('a v4 read access token is sent as a bearer header', async () => {
  const seen = [];
  const t = new Tmdb({ key: 'eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ4eHgiLCJzdWIiOiJ5eXkifQ.signature-part', dataDir: os.tmpdir(), fetchImpl: async (u, o) => { seen.push([String(u), o.headers]); return { ok: true, status: 200, json: async () => ({}) }; } });
  await t.get('/configuration');
  assert.ok(!seen[0][0].includes('api_key'));
  assert.match(seen[0][1].Authorization, /^Bearer eyJ/);
});
