import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseLrc, detectLanguage, unsyncedLines, embeddedToLines } from '../src/lyrics.js';

test('parses basic [mm:ss.xx] lines', () => {
  const r = parseLrc('[ti:Baja]\n[00:01.50]Baja la marea\n[00:05.25]Y el faro se apaga\n');
  assert.equal(r.synced, true);
  assert.deepEqual(r.lines, [{ t: 1.5, text: 'Baja la marea' }, { t: 5.25, text: 'Y el faro se apaga' }]);
  assert.equal(r.meta.ti, 'Baja');
});

test('multiple timestamps per line are expanded and sorted', () => {
  const r = parseLrc('[00:10.00][00:30.00]Chorus\n[00:20.000]Verse\n');
  assert.deepEqual(r.lines.map((l) => [l.t, l.text]), [[10, 'Chorus'], [20, 'Verse'], [30, 'Chorus']]);
});

test('language tags [la:] and [lang:] and offset', () => {
  assert.equal(parseLrc('[la:es]\n[00:01.00]Hola').lang, 'es');
  assert.equal(parseLrc('[lang:pt-BR]\n[00:01.00]Olá').lang, 'pt');
  assert.equal(parseLrc('[00:01.00]x').lang, null);
  const r = parseLrc('[offset:500]\n[00:02.00]late');
  assert.equal(r.lines[0].t, 1.5);
});

test('handles [mm:ss], three-digit fractions, CRLF, BOM and enhanced word stamps', () => {
  const r = parseLrc('﻿[01:02]One\r\n[01:03.123]<01:03.123>Two <01:04.00>words\r\n');
  assert.deepEqual(r.lines, [{ t: 62, text: 'One' }, { t: 63.123, text: 'Two words' }]);
});

test('plain text without timestamps becomes unsynced lines (t = index * 4)', () => {
  const r = parseLrc('first\nsecond\n');
  assert.equal(r.synced, false);
  assert.deepEqual(r.lines, [{ t: 0, text: 'first' }, { t: 4, text: 'second' }]);
  assert.deepEqual(unsyncedLines('a\n\nb'), [{ t: 0, text: 'a' }, { t: 4, text: 'b' }]);
});

test('embedded lyrics fallback', () => {
  const r = embeddedToLines([{ text: 'uno\ndos', syncText: [], language: 'spa' }]);
  assert.equal(r.synced, false);
  assert.equal(r.lang, 'es');
  assert.deepEqual(r.lines, [{ t: 0, text: 'uno' }, { t: 4, text: 'dos' }]);
  const s = embeddedToLines([{ syncText: [{ text: 'a', timestamp: 1500 }], language: 'eng' }]);
  assert.deepEqual(s.lines, [{ t: 1.5, text: 'a' }]);
  assert.equal(s.synced, true);
});

test('language heuristic', () => {
  assert.equal(detectLanguage('Baja la marea y el faro se apaga, no queda nada de la noche'), 'es');
  assert.equal(detectLanguage('Eu não sei o que fazer com o meu coração, você é tudo'), 'pt');
  assert.equal(detectLanguage('Je ne sais pas pourquoi tu es dans mon cœur, c\'est la vie'), 'fr');
  assert.equal(detectLanguage('Ich weiß nicht, was du mit mir und dem Herz machst, das ist es'), 'de');
  assert.equal(detectLanguage('Headlights bleeding through the rain, every exit looks the same'), 'en');
  assert.equal(detectLanguage(''), 'en');
});
