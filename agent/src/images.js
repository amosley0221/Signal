import fsp from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

/** Pixel width the phone needs for each kind of Plex picture (posters fill a grid tile; backdrops a screen). */
export const IMAGE_WIDTHS = { poster: 500, thumb: 640, cover: 600, backdrop: 1280 };

/**
 * Plex posters and backdrops for the phone: resized by Plex's own photo transcoder (a few hundred KB → ~50 KB),
 * kept on disk so each one is fetched from Plex only once, and at most a few fetched at a time so a scrolling
 * grid doesn't swamp Plex or the home upload.
 */
export class PlexImages {
  constructor(plex, dir, { parallel = 4 } = {}) {
    this.plex = plex;
    this.dir = dir;
    this.parallel = parallel;
    this.active = 0;
    this.waiting = [];
    this.pending = new Map();
  }

  key(imgPath, width) {
    return crypto.createHash('sha1').update(`${imgPath}@${width || 0}`).digest('hex');
  }

  /** {data, mime} for a Plex image path (e.g. /library/metadata/12/thumb/1700000000). */
  async get(imgPath, width) {
    const file = path.join(this.dir, `${this.key(imgPath, width)}.jpg`);
    try {
      return { data: await fsp.readFile(file), mime: 'image/jpeg', cached: true };
    } catch { /* not cached yet */ }
    // Several tiles asking for the same picture share one fetch.
    const k = file;
    if (this.pending.has(k)) return this.pending.get(k);
    const p = this.#limited(() => this.#fetch(imgPath, width)).then(async (img) => {
      if (/jpe?g/i.test(img.mime)) {
        await fsp.mkdir(this.dir, { recursive: true }).catch(() => {});
        await fsp.writeFile(file, img.data).catch(() => {});
      }
      return img;
    }).finally(() => this.pending.delete(k));
    this.pending.set(k, p);
    return p;
  }

  async #fetch(imgPath, width) {
    if (width) {
      try {
        const r = await this.plex.get('/photo/:/transcode', { url: imgPath, width, height: width * 2, minSize: 0, upscale: 0 }, { json: false, timeoutMs: 30000 });
        const data = Buffer.from(await r.arrayBuffer());
        if (data.length) return { data, mime: r.headers.get('content-type') || 'image/jpeg' };
      } catch { /* older Plex or no transcoder: fall back to the original picture */ }
    }
    const r = await this.plex.get(imgPath, {}, { json: false, timeoutMs: 30000 });
    return { data: Buffer.from(await r.arrayBuffer()), mime: r.headers.get('content-type') || 'image/jpeg' };
  }

  async #limited(fn) {
    if (this.active >= this.parallel) await new Promise((resolve) => this.waiting.push(resolve));
    this.active++;
    try {
      return await fn();
    } finally {
      this.active--;
      this.waiting.shift()?.();
    }
  }
}
