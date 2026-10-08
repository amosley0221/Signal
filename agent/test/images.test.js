import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { PlexImages } from '../src/images.js';

function fakePlex({ transcode = true, delayMs = 20 } = {}) {
  const calls = [];
  let active = 0; let peak = 0;
  return {
    calls, get peak() { return peak; },
    async get(p, params) {
      calls.push({ p, params });
      active++; peak = Math.max(peak, active);
      await new Promise((r) => setTimeout(r, delayMs));
      active--;
      if (p === '/photo/:/transcode' && !transcode) throw new Error('Plex /photo/:/transcode → HTTP 404');
      const body = Buffer.from(p === '/photo/:/transcode' ? `small:${params.url}:${params.width}` : `full:${p}`);
      return { headers: new Map([['content-type', 'image/jpeg']]), arrayBuffer: async () => body };
    },
  };
}

test('resizes through Plex, caches on disk, and shares one fetch per picture', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-img-'));
  const plex = fakePlex();
  const imgs = new PlexImages(plex, dir);
  const [a, b] = await Promise.all([imgs.get('/library/metadata/1/thumb/9', 500), imgs.get('/library/metadata/1/thumb/9', 500)]);
  assert.equal(a.data.toString(), 'small:/library/metadata/1/thumb/9:500');
  assert.equal(b.data.toString(), a.data.toString());
  assert.equal(plex.calls.length, 1);
  const again = await new PlexImages(plex, dir).get('/library/metadata/1/thumb/9', 500);
  assert.equal(again.cached, true);
  assert.equal(plex.calls.length, 1);
});

test('falls back to the original picture and limits parallel fetches', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-img-'));
  const plex = fakePlex({ transcode: false });
  const imgs = new PlexImages(plex, dir, { parallel: 3 });
  const out = await Promise.all(Array.from({ length: 12 }, (_, i) => imgs.get(`/library/metadata/${i}/thumb/1`, 500)));
  assert.equal(out[4].data.toString(), '/library/metadata/4/thumb/1'.replace(/^/, 'full:'));
  assert.ok(plex.peak <= 3, `peak ${plex.peak}`);
});
