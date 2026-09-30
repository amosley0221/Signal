import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseMovieName, parseEpisodeName } from '../src/filenames.js';

test('movie: Title (Year).ext', () => {
  assert.deepEqual(parseMovieName('Low Orbit (2025).mkv'), { title: 'Low Orbit', year: 2025 });
  assert.deepEqual(parseMovieName('Low.Orbit.2025.1080p.BluRay.x264.mkv'), { title: 'Low Orbit', year: 2025 });
  assert.deepEqual(parseMovieName('Blade Runner 2049 (2017).mp4'), { title: 'Blade Runner 2049', year: 2017 });
  assert.deepEqual(parseMovieName('movie.mkv', 'Low Orbit (2025)'), { title: 'Low Orbit', year: 2025 });
  assert.deepEqual(parseMovieName('Home Video.mp4'), { title: 'Home Video', year: null });
});

test('episode: Show/Season 01/Show - S01E02 - Title.ext', () => {
  const e = parseEpisodeName('The Ballast/Season 01/The Ballast - S01E02 - Meridian.mkv');
  assert.equal(e.show, 'The Ballast');
  assert.equal(e.season, 1);
  assert.equal(e.episode, 2);
  assert.equal(e.title, 'Meridian');
  assert.equal(e.showFolder, 'The Ballast');
});

test('episode: show year folder, dotted scene names, 1x02 and bare files', () => {
  const a = parseEpisodeName('The Ballast (2024)/Season 2/The.Ballast.S02E10.The.Surveyor.1080p.WEB-DL.mkv');
  assert.deepEqual([a.show, a.showYear, a.season, a.episode, a.title], ['The Ballast', 2024, 2, 10, 'The Surveyor']);
  const b = parseEpisodeName('Slow Kitchen 1x03 Ferment.mp4');
  assert.deepEqual([b.show, b.season, b.episode, b.title], ['Slow Kitchen', 1, 3, 'Ferment']);
  const c = parseEpisodeName('Slow Kitchen/Specials/Slow Kitchen - S00E01 - Behind the Scenes.mkv');
  assert.deepEqual([c.show, c.season, c.episode, c.title], ['Slow Kitchen', 0, 1, 'Behind the Scenes']);
});

test('album covers are found under common and Windows Media Player names', async () => {
  const { findSidecars } = await import('../src/media.js');
  const listing = (names) => new Map(names.map((n) => [n.toLowerCase(), n]));
  assert.equal(findSidecars('01 Song.flac', listing(['01 Song.flac', 'AlbumArt_{ABC}_Large.jpg', 'AlbumArtSmall.jpg']), 'audio').art.cover, 'AlbumArt_{ABC}_Large.jpg');
  assert.equal(findSidecars('01 Song.flac', listing(['01 Song.flac', 'Folder.jpg']), 'audio').art.cover, 'Folder.jpg');
  assert.equal(findSidecars('01 Song.flac', listing(['01 Song.flac', 'scan.png']), 'audio').art.cover, 'scan.png');
  assert.equal(findSidecars('01 Song.flac', listing(['01 Song.flac', 'a.jpg', 'b.jpg']), 'audio').art.cover, null);
});
