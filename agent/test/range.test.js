import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseRange } from '../src/range.js';

test('absent or malformed Range → null (serve full)', () => {
  assert.equal(parseRange(undefined, 100), null);
  assert.equal(parseRange('items=0-1', 100), null);
  assert.equal(parseRange('bytes=abc', 100), null);
  assert.equal(parseRange('bytes=-', 100), null);
  assert.equal(parseRange('bytes=50-10', 100), null);
});

test('closed, open-ended and suffix ranges', () => {
  assert.deepEqual(parseRange('bytes=0-99', 1000), { start: 0, end: 99 });
  assert.deepEqual(parseRange('bytes=500-', 1000), { start: 500, end: 999 });
  assert.deepEqual(parseRange('bytes=-100', 1000), { start: 900, end: 999 });
  assert.deepEqual(parseRange('bytes=-5000', 1000), { start: 0, end: 999 });
  assert.deepEqual(parseRange('bytes=900-5000', 1000), { start: 900, end: 999 });
  assert.deepEqual(parseRange('bytes=0-1, 5-6', 1000), { start: 0, end: 1 });
});

test('unsatisfiable ranges', () => {
  assert.equal(parseRange('bytes=1000-', 1000), 'unsatisfiable');
  assert.equal(parseRange('bytes=-0', 1000), 'unsatisfiable');
  assert.equal(parseRange('bytes=0-', 0), 'unsatisfiable');
});
