import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { readAudioMeta } from '../src/media.js';

const chunk = (id, data, pad = true) => {
  const h = Buffer.alloc(8); h.write(id, 0, 'latin1'); h.writeUInt32LE(data.length, 4);
  return Buffer.concat([h, data, pad && data.length % 2 ? Buffer.alloc(1) : Buffer.alloc(0)]);
};

test('reads tags and length from damaged WAV files', async () => {
  const fmt = Buffer.alloc(16);
  fmt.writeUInt16LE(1, 0); fmt.writeUInt16LE(2, 2); fmt.writeUInt32LE(44100, 4); fmt.writeUInt32LE(176400, 8); fmt.writeUInt16LE(4, 12); fmt.writeUInt16LE(16, 14);
  const info = chunk('LIST', Buffer.concat([Buffer.from('INFO'), ...[['INAM', 'Low Light'], ['IART', 'Andre Sinclair'], ['IPRD', 'Vacancy: The Soundtrack'], ['ITRK', '31']]
    .map(([k, v]) => chunk(k, Buffer.from(v), false))]));
  const dataHead = Buffer.alloc(8); dataHead.write('data', 0, 'latin1'); dataHead.writeUInt32LE(0xffffffff, 4);
  const body = Buffer.concat([Buffer.from('WAVE'), chunk('fmt ', fmt), info, dataHead, Buffer.alloc(176400 * 2)]);
  const riff = Buffer.alloc(8); riff.write('RIFF', 0, 'latin1'); riff.writeUInt32LE(0xffffffff, 4);
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'signal-wav-')), 'x.wav');
  fs.writeFileSync(file, Buffer.concat([riff, body]));
  const m = await readAudioMeta(file);
  assert.equal(m.title, 'Low Light');
  assert.equal(m.artist, 'Andre Sinclair');
  assert.equal(m.album, 'Vacancy: The Soundtrack');
  assert.equal(m.track, 31);
  assert.equal(m.durationMs, 2000);
});
