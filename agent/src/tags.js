// Tag writing: MP3 via node-id3; FLAC/WAV/M4A/… via ffmpeg (stream copy + replace); otherwise caller stores an override.
import fsp from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import NodeID3 from 'node-id3';
import { hasExecutable, run } from './util.js';

export const TAG_FIELDS = ['artist', 'title', 'album', 'albumArtist', 'genre', 'year', 'track', 'disc'];

const FFMPEG_KEYS = { artist: 'artist', title: 'title', album: 'album', albumArtist: 'album_artist', genre: 'genre', year: 'date', track: 'track', disc: 'disc' };
const ID3_KEYS = { artist: 'artist', title: 'title', album: 'album', albumArtist: 'performerInfo', genre: 'genre', year: 'year', track: 'trackNumber', disc: 'partOfSet' };

/**
 * Write tags into the file if possible.
 * @returns {Promise<'id3'|'ffmpeg'|null>} null when the caller should fall back to an override.
 */
export async function writeTags(abs, fields, { useFfmpeg = true } = {}) {
  const ext = path.extname(abs).toLowerCase();
  if (ext === '.mp3') {
    const tags = {};
    for (const [k, v] of Object.entries(fields)) if (ID3_KEYS[k]) tags[ID3_KEYS[k]] = v;
    const res = NodeID3.update(tags, abs);
    if (res instanceof Error) throw res;
    return 'id3';
  }
  if (useFfmpeg && (await hasExecutable('ffmpeg'))) {
    const dir = path.dirname(abs);
    const tmp = path.join(dir, `.${path.basename(abs, ext)}.signal-tmp-${crypto.randomBytes(3).toString('hex')}${ext}`);
    const args = ['-v', 'error', '-y', '-i', abs, '-map', '0', '-map_metadata', '0', '-c', 'copy'];
    for (const [k, v] of Object.entries(fields)) if (FFMPEG_KEYS[k]) args.push('-metadata', `${FFMPEG_KEYS[k]}=${v}`);
    if (ext === '.m4a' || ext === '.mp4' || ext === '.m4v' || ext === '.mov') args.push('-movflags', '+faststart');
    args.push(tmp);
    try {
      await run('ffmpeg', args, { timeoutMs: 10 * 60 * 1000 });
      const [a, b] = await Promise.all([fsp.stat(abs), fsp.stat(tmp)]);
      if (b.size < a.size * 0.5) throw new Error('ffmpeg output unexpectedly small; original kept');
      await fsp.rename(tmp, abs);
      return 'ffmpeg';
    } catch (e) {
      await fsp.rm(tmp, { force: true }).catch(() => {});
      throw e;
    }
  }
  return null;
}
