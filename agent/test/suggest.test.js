import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { suggestArtists } from '../src/suggest.js';

const all = [
  { title: 'Chrome Hearts Don’t Break', artist: 'Velvet Static', tags: ['synthwave', 'night drive'] },
  { title: 'Sodium Lights', artist: 'Velvet Static', tags: ['synthwave'] },
  { title: 'Paper Boats', artist: null, tags: ['indie folk', 'female vocal'] },
];
const track = {
  title: 'Midnight Motorway', tags: ['synthwave', 'male vocal'],
  lyrics: [{ t: 0, text: 'Headlights bleeding through the rain' }, { t: 4, text: 'Every exit looks the same' }],
};

test('deterministic output for a sample track', () => {
  const expected = [
    { name: 'Velvet Static', why: 'Existing artist · 2 songs share tag “synthwave”', kind: 'existing' },
    { name: 'City Cathedral', why: 'From your style prompt · “city”', kind: 'prompt' },
    { name: 'Night Cathedral', why: 'Mood · synthwave, male vocal', kind: 'mood' },
    { name: 'Midnight Cathedral', why: 'Title word · “Midnight”', kind: 'title' },
    { name: 'The Headlightss', why: 'Lyric · “Headlights bleeding through the rain”', kind: 'lyric' },
  ];
  const a = suggestArtists(track, [...all, track], 'a duo from a rainy port city');
  assert.deepEqual(a, expected);
  assert.deepEqual(suggestArtists(track, [...all, track], 'a duo from a rainy port city'), a);
});

test('untagged track with no lyrics uses the default bank', () => {
  const t = { title: 'Untitled', tags: [] };
  const r = suggestArtists(t, [t]);
  assert.deepEqual(r.map((s) => s.kind), ['mood', 'title', 'person']);
  assert.equal(r[0].why, 'Mood · untagged');
  assert.equal(r[2].why, 'Solo-artist style · fits the album grouping');
});

// When the design prototype is present (monorepo checkout), check the port against it for every sample track.
const designFile = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../design/design/player-core.js');
test('matches design/player-core.js suggestArtists() on all prototype tracks', { skip: !fs.existsSync(designFile) && 'design prototype not present' }, async () => {
  const tmp = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'signal-design-')), 'player-core.mjs');
  fs.copyFileSync(designFile, tmp);
  const core = await import(pathToFileURL(tmp).href);
  const tracks = core.DATA.tracks;
  assert.ok(tracks.length > 5);
  for (const prompt of ['', 'a duo from a rainy port city', 'Late night harbour ballads']) {
    for (const t of tracks) assert.deepEqual(suggestArtists(t, tracks, prompt), core.suggestArtists(t, tracks, prompt), `${t.title} / ${prompt}`);
  }
});
