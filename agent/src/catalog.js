// Library scanning, catalogue cache (dataDir/catalog.json) and catalogue building.
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { pipeline } from 'node:stream/promises';
import {
  AUDIO_EXTS, VIDEO_EXTS, stableId, toPosix, writeJsonAtomic, readJsonSync, mapLimit, debounce, sanitizeFileName,
} from './util.js';
import {
  readAudioMeta, readVideoMeta, findSidecars, showArt, lrcLanguage, containerFor, readEmbeddedCover, readEmbeddedLyrics,
} from './media.js';
import { parseMovieName, parseEpisodeName } from './filenames.js';
import { loadLyrics, lrcPaths } from './lyrics.js';
import { writeTags, TAG_FIELDS } from './tags.js';

const CACHE_VERSION = 1;
const SKIP_DIRS = new Set(['$recycle.bin', 'system volume information', '@eadir', '#recycle', 'node_modules', '.git']);
const stripTrackNo = (s) => s.replace(/^\d{1,3}(?:[\s.\-_]+|\s*-\s*)/, '').trim() || s;

const PUBLISH_EVERY = 400;
const META_TIMEOUT_MS = 30000;
function withTimeout(promise, ms, message) {
  let t;
  return Promise.race([promise, new Promise((_, rej) => { t = setTimeout(() => rej(new Error(message)), ms); t.unref?.(); })]).finally(() => clearTimeout(t));
}

export class Catalog {
  /**
   * @param {{config:any, state:import('./state.js').State, activity:import('./activity.js').Activity, plex:import('./plex.js').Plex|null, log:Function}} deps
   */
  constructor({ config, state, activity, plex, log }) {
    this.config = config;
    this.state = state;
    this.activity = activity;
    this.plex = plex && plex.enabled ? plex : null;
    this.log = log || (() => {});
    this.cacheFile = path.join(config.dataDir, 'catalog.json');
    this.cache = { version: CACHE_VERSION, libs: {} };
    this.libraries = [];
    this.index = new Map();
    this.catalog = { generatedAt: Date.now(), tracks: [], videos: [], movies: [], shows: [] };
    this.dirty = true;
    this.lastScanAt = null;
    this.scanning = null;
    this.rescanQueued = false;
    this.watchers = [];
    this.coverCache = new Map();
  }

  async init() {
    const c = readJsonSync(this.cacheFile, null);
    if (c && c.version === CACHE_VERSION && c.libs) {
      this.cache = c;
      this.lastScanAt = c.lastScanAt || null;
    }
    this.libraries = this.config.libraries.slice();
    if (this.plex) {
      try {
        if (!this.libraries.length) {
          this.libraries = await this.plex.offerLibraries();
          if (this.libraries.length) {
            this.log(`[plex] no libraries configured — using Plex sections: ${this.libraries.map((l) => `${l.name} (${l.type}) ${l.path}`).join('; ')}`);
            this.log('[plex] copy them into "libraries" in the config file to make this permanent.');
          }
        } else {
          await this.plex.getSections();
        }
      } catch (e) {
        this.log(`[plex] not reachable: ${e.message}`);
      }
    }
    if (!this.libraries.length) this.log('[config] no libraries configured yet — add your folders on the setup page (http://localhost:<port>/ on this PC)');
    // drop cache for libraries that no longer exist
    for (const id of Object.keys(this.cache.libs)) if (!this.libraries.some((l) => l.id === id)) delete this.cache.libs[id];
    this.build();
  }

  /** Stop a running scan early (used when settings change); resolves once it has wound down. */
  cancelScan() {
    this.stopRequested = true;
    this.rescanQueued = false;
    return (this.scanning || Promise.resolve()).catch(() => {});
  }

  lib(id) { return this.libraries.find((l) => l.id === id) || null; }

  // ---- scanning -------------------------------------------------------------------------------

  /** Start (or queue) an incremental rescan; resolves when the scan finishes. */
  scan(reason = 'manual') {
    if (this.scanning) { this.rescanQueued = true; return this.scanning; }
    this.scanning = (async () => {
      try {
        await this.#scanAll(reason);
      } finally {
        this.scanning = null;
      }
      if (this.rescanQueued) { this.rescanQueued = false; await this.scan('queued'); }
    })();
    return this.scanning;
  }

  async #scanAll(reason) {
    if (this.plex) {
      const pj = this.activity.add('plex', 'Plex metadata', 'Refreshing from Plex');
      try {
        const r = await this.plex.refresh();
        pj.done(`${r.items} files matched · ${r.shows} shows`);
      } catch (e) {
        pj.fail(e);
        this.log(`[plex] refresh failed: ${e.message}`);
      }
    }
    for (const lib of this.libraries) {
      if (this.stopRequested) break;
      const job = this.activity.add('scan', `Scan ${lib.name}`, reason === 'startup' ? 'Startup scan' : `Rescan (${reason})`);
      try {
        const r = await this.#scanLibrary(lib, job);
        job.done(`${r.total} files · ${r.parsed} updated · ${r.removed} removed`);
        // Show each library as soon as it's done instead of waiting for all of them.
        this.build();
        await this.saveCache();
        if (r.parsed || r.removed) this.log(`[scan] ${lib.name}: ${r.total} files, ${r.parsed} (re)read, ${r.removed} removed`);
      } catch (e) {
        job.fail(e);
        this.log(`[scan] ${lib.name} failed: ${e.message}`);
      }
    }
    this.lastScanAt = Date.now();
    this.cache.lastScanAt = this.lastScanAt;
    this.build();
    await this.saveCache();
  }

  async saveCache() {
    try {
      await writeJsonAtomic(this.cacheFile, this.cache);
    } catch (e) {
      this.log(`[scan] could not save cache: ${e.message}`);
    }
  }

  async #walk(root) {
    const files = [];
    const listings = new Map();
    const dataDir = path.resolve(this.config.dataDir);
    const stack = [root];
    while (stack.length) {
      const dir = stack.pop();
      let ents;
      try {
        ents = await fsp.readdir(dir, { withFileTypes: true });
      } catch {
        continue;
      }
      const listing = new Map();
      for (const e of ents) {
        listing.set(e.name.toLowerCase(), e.name);
        const abs = path.join(dir, e.name);
        if (e.isDirectory()) {
          if (e.name.startsWith('.') || SKIP_DIRS.has(e.name.toLowerCase()) || path.resolve(abs) === dataDir) continue;
          stack.push(abs);
        } else if (e.isFile()) {
          if (e.name.startsWith('.') || e.name.includes('.signal-tmp') || e.name.includes('.signal-upload')) continue;
          const ext = path.extname(e.name).toLowerCase();
          if (AUDIO_EXTS.has(ext) || VIDEO_EXTS.has(ext)) files.push({ abs, dir, name: e.name, kind: AUDIO_EXTS.has(ext) ? 'audio' : 'video' });
        }
      }
      listings.set(dir, listing);
    }
    return { files, listings };
  }

  roleFor(lib, kind) {
    if (kind === 'audio') return lib.type === 'music' || lib.type === 'musicvideos' ? 'audio' : null;
    if (lib.type === 'movies') return 'movie';
    if (lib.type === 'tv') return 'episode';
    return 'musicvideo';
  }

  async #scanLibrary(lib, job) {
    const root = lib.path;
    const st = await fsp.stat(root).catch(() => null);
    if (!st || !st.isDirectory()) throw new Error(`folder not found: ${root}`);
    const prev = this.cache.libs[lib.id]?.files || {};
    const { files, listings } = await this.#walk(root);
    const next = {};
    let parsed = 0;
    let done = 0;
    job.progressTo(0, `${files.length} files`);
    // Publish progress while a big library scans, so the phone sees songs/episodes as they're read
    // and a restart doesn't start over: every PUBLISH_EVERY files, merge what we have into the cache.
    let sincePublish = 0;
    let publishing = false;
    const publish = async () => {
      if (publishing) return;
      publishing = true;
      sincePublish = 0;
      this.cache.libs[lib.id] = { files: { ...prev, ...next }, shows: this.cache.libs[lib.id]?.shows || {}, lastScan: this.cache.libs[lib.id]?.lastScan || null };
      this.build();
      try { await this.saveCache(); } finally { publishing = false; }
    };
    await mapLimit(files, lib.type === 'music' ? 4 : 6, async (f) => {
      if (this.stopRequested) throw new Error('stopped (settings changed)');
      const role = this.roleFor(lib, f.kind);
      if (role) {
        const rel = toPosix(path.relative(root, f.abs));
        const entry = await this.#processFile(lib, f, rel, role, prev[rel], listings.get(f.dir));
        if (entry) {
          next[rel] = entry;
          if (entry._parsed) { parsed++; delete entry._parsed; }
        }
      }
      done++;
      if (done % 25 === 0) job.progressTo(done / files.length, `${done} / ${files.length}`);
      if (++sincePublish >= PUBLISH_EVERY) await publish();
    });
    // show-level artwork for TV libraries
    const shows = {};
    if (lib.type === 'tv') {
      for (const rel of Object.keys(next)) {
        const folder = rel.includes('/') ? rel.split('/')[0] : null;
        if (folder && !shows[folder]) shows[folder] = showArt(listings.get(path.join(root, folder)) || new Map());
      }
    }
    const removed = Object.keys(prev).filter((k) => !next[k]).length;
    this.cache.libs[lib.id] = { files: next, shows, lastScan: Date.now() };
    return { total: Object.keys(next).length, parsed, removed };
  }

  async #processFile(lib, f, rel, role, cached, listing) {
    let st;
    try {
      st = await fsp.stat(f.abs);
    } catch {
      return null;
    }
    const mtime = Math.round(st.mtimeMs);
    let entry;
    if (cached && cached.mtime === mtime && cached.size === st.size && cached.meta) {
      entry = { ...cached };
    } else {
      let meta;
      try {
        // A damaged or huge file must not stall the whole scan.
        meta = await withTimeout(f.kind === 'audio' ? readAudioMeta(f.abs) : readVideoMeta(f.abs), META_TIMEOUT_MS, `reading ${f.name} took too long`);
      } catch (e) {
        meta = { error: String(e.message || e) };
      }
      const birth = st.birthtimeMs > 0 ? Math.round(st.birthtimeMs) : mtime;
      entry = { rel, kind: f.kind, mtime, size: st.size, addedAt: cached?.addedAt || Math.min(birth, Date.now()), meta, _parsed: true };
    }
    const ownFolder = rel.includes('/');
    const side = findSidecars(f.name, listing || new Map(), role, ownFolder);
    entry.side = side;
    if (side.lrc) {
      const lrcAbs = path.join(f.dir, side.lrc);
      const lst = await fsp.stat(lrcAbs).catch(() => null);
      const lm = lst ? Math.round(lst.mtimeMs) : 0;
      if (cached?.lrcMtime === lm && cached?.lrcLang !== undefined) {
        entry.lrcMtime = lm;
        entry.lrcLang = cached.lrcLang;
      } else {
        entry.lrcMtime = lm;
        entry.lrcLang = await lrcLanguage(lrcAbs);
      }
    } else {
      delete entry.lrcMtime;
      delete entry.lrcLang;
    }
    return entry;
  }

  /** Re-read a single file into the cache (after a tag write or upload). */
  async refreshFile(lib, abs) {
    const rel = toPosix(path.relative(lib.path, abs));
    const kind = AUDIO_EXTS.has(path.extname(abs).toLowerCase()) ? 'audio' : 'video';
    const role = this.roleFor(lib, kind);
    if (!role) return null;
    const libCache = this.cache.libs[lib.id] || (this.cache.libs[lib.id] = { files: {}, shows: {}, lastScan: null });
    const listing = await this.#listing(path.dirname(abs));
    const cached = libCache.files[rel];
    const entry = await this.#processFile(lib, { abs, dir: path.dirname(abs), name: path.basename(abs), kind }, rel, role, cached ? { ...cached, mtime: -1 } : null, listing);
    if (entry) { delete entry._parsed; libCache.files[rel] = entry; }
    this.build();
    this.saveCache();
    return entry;
  }

  async #listing(dir) {
    const m = new Map();
    for (const n of await fsp.readdir(dir).catch(() => [])) m.set(n.toLowerCase(), n);
    return m;
  }

  // ---- watching ---------------------------------------------------------------------------------

  startWatching() {
    const trigger = debounce(() => this.scan('file change').catch(() => {}), 5000);
    for (const lib of this.libraries) {
      try {
        const w = fs.watch(lib.path, { recursive: true, persistent: false }, (_ev, filename) => {
          const f = String(filename || '');
          if (f.includes('.signal-tmp') || f.includes('.signal-upload')) return;
          trigger();
        });
        w.on('error', () => {});
        this.watchers.push(w);
      } catch (e) {
        this.log(`[watch] ${lib.name}: file watching unavailable (${e.code || e.message}); relying on periodic rescans`);
      }
    }
    this.stopWatchTrigger = trigger;
  }

  stopWatching() {
    for (const w of this.watchers) { try { w.close(); } catch { /* ignore */ } }
    this.watchers = [];
    this.stopWatchTrigger?.cancel();
  }

  // ---- catalogue --------------------------------------------------------------------------------

  #progressFor(id, plexInfo) {
    const local = this.state.data.progress[id];
    let viewOffsetMs = plexInfo?.viewOffsetMs || 0;
    let watched = !!plexInfo?.watched;
    if (local && (!plexInfo || local.updatedAt >= (plexInfo.lastViewedAt || 0))) {
      viewOffsetMs = local.watched ? 0 : local.positionMs;
      watched = local.watched;
    }
    const lastViewedAt = Math.max(local?.updatedAt || 0, plexInfo?.lastViewedAt || 0) || null;
    return { viewOffsetMs: Math.max(0, Math.round(viewOffsetMs || 0)), watched, lastViewedAt };
  }

  build() {
    const index = new Map();
    const tracks = [];
    const videos = [];
    const movies = [];
    const showsMap = new Map();
    const tracksByDir = new Map();
    const staleOverrides = [];
    const pendingVideos = [];

    for (const lib of this.libraries) {
      const lc = this.cache.libs[lib.id];
      if (!lc) continue;
      for (const [rel, e] of Object.entries(lc.files)) {
        const abs = path.join(lib.path, ...rel.split('/'));
        const dirRel = rel.includes('/') ? rel.slice(0, rel.lastIndexOf('/')) : '';
        const parentName = dirRel ? dirRel.split('/').pop() : null;
        const fileName = rel.split('/').pop();
        const baseName = fileName.slice(0, fileName.length - path.extname(fileName).length);
        const m = e.meta || {};
        const role = this.roleFor(lib, e.kind);
        if (role === 'audio') {
          const id = stableId(lib.id, rel);
          const ov = this.state.data.overrides[id];
          let o = {};
          if (ov) { if (ov.fileMtime === e.mtime) o = ov; else staleOverrides.push(id); }
          const leadNo = /^(\d{1,3})[\s.\-_]/.exec(baseName);
          const t = {
            id, libraryId: lib.id,
            title: o.title ?? m.title ?? stripTrackNo(baseName),
            artist: o.artist ?? m.artist ?? null,
            album: o.album ?? m.album ?? parentName,
            albumArtist: o.albumArtist ?? m.albumArtist ?? null,
            year: o.year ? Number(o.year) : m.year ?? null,
            disc: o.disc ? Number(o.disc) : m.disc ?? null,
            track: o.track ? Number(o.track) : m.track ?? (leadNo ? Number(leadNo[1]) : null),
            durationMs: m.durationMs ?? null,
            container: containerFor(fileName),
            codec: m.codec ?? null,
            bitDepth: m.bitDepth ?? null,
            sampleRate: m.sampleRate ?? null,
            size: e.size, mtime: e.mtime, addedAt: e.addedAt,
            genres: o.genre != null ? String(o.genre).split(/[;,/]/).map((g) => g.trim()).filter(Boolean) : m.genres || [],
            hasArt: !!(m.hasPicture || e.side?.art?.cover),
            hasLyrics: !!(e.side?.lrc || m.hasEmbeddedLyrics),
            hasTranslation: !!e.side?.enLrc,
            lyricsLang: e.side?.lrc ? (e.lrcLang || 'en') : m.hasEmbeddedLyrics ? (m.embeddedLang || 'en') : null,
            relPath: rel,
          };
          tracks.push(t);
          index.set(id, { type: 'track', lib, abs, entry: e, item: t });
          const dk = path.dirname(abs).toLowerCase();
          if (!tracksByDir.has(dk)) tracksByDir.set(dk, []);
          tracksByDir.get(dk).push(t);
        } else if (role === 'musicvideo') {
          pendingVideos.push({ lib, rel, e, abs, parentName, baseName, fileName });
        } else if (role === 'movie') {
          const id = stableId(lib.id, rel);
          const p = this.plex?.match(abs);
          const fn = parseMovieName(fileName, parentName || '');
          const hasPoster = !!(p?.thumb || e.side?.art?.poster);
          const hasBackdrop = !!(p?.art || e.side?.art?.backdrop);
          const mv = {
            id, libraryId: lib.id,
            title: p?.title || fn.title || m.title,
            year: p?.year || fn.year || m.year || null,
            durationMs: m.durationMs || p?.durationMs || null,
            genres: p?.genres?.length ? p.genres : m.genres || [],
            director: p?.director || null,
            synopsis: p?.summary || null,
            cast: p?.cast || [],
            rating: p?.rating ?? null,
            certificate: p?.contentRating || null,
            posterUrl: hasPoster ? `/api/art/${id}?kind=poster` : null,
            backdropUrl: hasBackdrop ? `/api/art/${id}?kind=backdrop` : null,
            matchedBy: p ? p.matchedBy : 'FILENAME',
            ...this.#progressFor(id, p),
            width: m.width || null, height: m.height || null,
            container: containerFor(fileName), size: e.size, addedAt: e.addedAt,
            subtitles: (e.side?.subs || []).map((s) => ({ id: s.id, language: s.language, label: s.label, format: s.file.toLowerCase().endsWith('.vtt') ? 'vtt' : 'srt', url: `/api/subtitle/${id}/${s.id}` })),
            chapters: m.chapters || [],
          };
          movies.push(mv);
          index.set(id, { type: 'movie', lib, abs, entry: e, item: mv, plex: p });
        } else if (role === 'episode') {
          const id = stableId(lib.id, rel);
          const p = this.plex?.match(abs);
          const fn = parseEpisodeName(rel);
          const showKey = fn.showFolder || fn.show;
          const showId = stableId(lib.id, `show:${showKey}`);
          let sh = showsMap.get(showId);
          if (!sh) {
            const art = this.cache.libs[lib.id]?.shows?.[fn.showFolder] || {};
            sh = { id: showId, lib, folder: fn.showFolder, parsed: fn, plex: null, sideArt: art, eps: [] };
            showsMap.set(showId, sh);
          }
          if (p && !sh.plex && p.showKey && this.plex.shows.get(p.showKey)) sh.plex = this.plex.shows.get(p.showKey);
          const season = p?.season ?? fn.season;
          const epNo = p?.episode ?? fn.episode;
          const ep = {
            id, season, episode: epNo,
            title: p?.title || fn.title || (epNo != null ? `Episode ${epNo}` : stripTrackNo(baseName)),
            summary: p?.summary || null,
            durationMs: m.durationMs || p?.durationMs || null,
            ...this.#progressFor(id, p),
            size: e.size, container: containerFor(fileName), addedAt: e.addedAt,
            width: m.width || null, height: m.height || null,
            thumbUrl: p?.thumb || e.side?.art?.thumb ? `/api/art/${id}?kind=thumb` : null,
            subtitles: (e.side?.subs || []).map((s) => ({ id: s.id, language: s.language, label: s.label, format: s.file.toLowerCase().endsWith('.vtt') ? 'vtt' : 'srt', url: `/api/subtitle/${id}/${s.id}` })),
            chapters: m.chapters || [],
          };
          sh.eps.push(ep);
          index.set(id, { type: 'episode', lib, abs, entry: e, item: ep, plex: p, showId });
        }
      }
    }

    // music videos: album from ALBUM tag, else from audio tracks in the same folder, else a matching album name
    const albumNames = new Map();
    for (const t of tracks) if (t.album) albumNames.set(t.album.toLowerCase(), t);
    const mostCommon = (arr) => {
      const c = new Map();
      for (const v of arr) if (v) c.set(v, (c.get(v) || 0) + 1);
      return [...c.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] || null;
    };
    for (const v of pendingVideos) {
      const { lib, rel, e, abs, parentName, baseName, fileName } = v;
      const m = e.meta || {};
      const id = stableId(lib.id, rel);
      let title = m.title || null;
      let artist = m.artist || null;
      if (!title) {
        const dash = /^(.+?)\s+[-–]\s+(.+)$/.exec(stripTrackNo(baseName));
        if (dash && !artist) { artist = dash[1].trim(); title = dash[2].trim(); } else title = stripTrackNo(baseName);
      }
      let album = m.album || null;
      if (!album) {
        const sib = tracksByDir.get(path.dirname(abs).toLowerCase());
        if (sib && sib.length) {
          album = mostCommon(sib.map((t) => t.album));
          if (!artist) artist = mostCommon(sib.map((t) => t.albumArtist || t.artist));
        } else if (parentName && albumNames.has(parentName.toLowerCase())) {
          const t = albumNames.get(parentName.toLowerCase());
          album = t.album;
          if (!artist) artist = t.albumArtist || t.artist;
        }
      }
      const p = this.plex?.match(abs);
      const vid = {
        id, libraryId: lib.id, title, artist, album,
        durationMs: m.durationMs ?? null, width: m.width ?? null, height: m.height ?? null, hdr: !!m.hdr,
        container: containerFor(fileName), size: e.size, addedAt: e.addedAt,
        hasArt: !!(e.side?.art?.thumb || p?.thumb),
      };
      videos.push(vid);
      index.set(id, { type: 'video', lib, abs, entry: e, item: vid, plex: p });
    }

    // shows
    const shows = [];
    for (const sh of showsMap.values()) {
      const p = sh.plex;
      const seasons = new Map();
      sh.eps.sort((a, b) => (a.season - b.season) || ((a.episode ?? 1e9) - (b.episode ?? 1e9)) || a.title.localeCompare(b.title));
      for (const ep of sh.eps) {
        if (!seasons.has(ep.season)) seasons.set(ep.season, []);
        const list = seasons.get(ep.season);
        if (ep.episode == null) ep.episode = list.length + 1;
        list.push(ep);
      }
      const show = {
        id: sh.id, libraryId: sh.lib.id,
        title: p?.title || sh.parsed.show,
        year: p?.year || sh.parsed.showYear || null,
        genres: p?.genres || [],
        synopsis: p?.summary || null,
        cast: p?.cast || [],
        rating: p?.rating ?? null,
        certificate: p?.contentRating || null,
        posterUrl: p?.thumb || sh.sideArt.poster ? `/api/art/${sh.id}?kind=poster` : null,
        backdropUrl: p?.art || sh.sideArt.backdrop ? `/api/art/${sh.id}?kind=backdrop` : null,
        matchedBy: p ? p.matchedBy : 'FILENAME',
        seasons: [...seasons.entries()].sort((a, b) => a[0] - b[0]).map(([number, episodes]) => ({ number, episodes })),
        addedAt: Math.max(0, ...sh.eps.map((x) => x.addedAt || 0)) || null,
      };
      shows.push(show);
      index.set(sh.id, { type: 'show', lib: sh.lib, abs: sh.folder ? path.join(sh.lib.path, sh.folder) : null, item: show, plex: p, sideArt: sh.sideArt });
    }

    for (const id of staleOverrides) this.state.clearOverride(id);
    tracks.sort((a, b) => (a.album || '').localeCompare(b.album || '') || (a.disc || 1) - (b.disc || 1) || (a.track || 0) - (b.track || 0) || a.title.localeCompare(b.title));
    movies.sort((a, b) => a.title.localeCompare(b.title));
    shows.sort((a, b) => a.title.localeCompare(b.title));
    this.index = index;
    this.catalog = { generatedAt: Date.now(), tracks, videos, movies, shows };
    this.dirty = false;
    return this.catalog;
  }

  getCatalog() {
    if (this.dirty) this.build();
    return this.catalog;
  }

  markDirty() { this.dirty = true; }

  getLibraries() {
    return this.libraries.map((l) => {
      const files = Object.values(this.cache.libs[l.id]?.files || {}).filter((e) => this.roleFor(l, e.kind));
      return { id: l.id, name: l.name, type: l.type, path: l.path, count: files.length, bytes: files.reduce((s, e) => s + (e.size || 0), 0) };
    });
  }

  get(id) {
    if (this.dirty) this.build();
    return this.index.get(id) || null;
  }

  // ---- lyrics / art / subtitles ------------------------------------------------------------------

  async lyrics(id) {
    const it = this.get(id);
    if (!it || it.type !== 'track') return null;
    return loadLyrics(it.abs, () => readEmbeddedLyrics(it.abs));
  }

  /**
   * Save lyrics typed/synced on the phone as `<song>.lrc` next to the file. An existing .lrc is
   * kept once as `<song>.lrc.bak`. Returns null when the id isn't a track.
   */
  async saveLyrics(id, lrcText) {
    const it = this.get(id);
    if (!it || it.type !== 'track') return null;
    const { lrc } = lrcPaths(it.abs);
    const text = String(lrcText || '').replace(/\r\n/g, '\n');
    const existing = await fsp.readFile(lrc, 'utf8').catch(() => null);
    if (existing !== null && existing !== text) {
      await fsp.copyFile(lrc, `${lrc}.bak`, fs.constants.COPYFILE_EXCL).catch(() => {});
    }
    await fsp.writeFile(lrc, text.endsWith('\n') ? text : `${text}\n`, 'utf8');
    if (it.entry) {
      it.entry.side = { ...(it.entry.side || {}), lrc: path.basename(lrc) };
      it.entry.lrcMtime = Date.now();
    }
    this.markDirty();
    return { ok: true, file: lrc };
  }

  /**
   * POST /api/art/:trackId — save an album cover (JPEG/PNG bytes) as cover.jpg/cover.png in the song's
   * folder so the whole album (and Plex) picks it up. An existing cover is kept once as <name>.bak.
   */
  async saveCover(id, buf, mime) {
    const it = this.get(id);
    if (!it || it.type !== 'track') return null;
    const ext = /png/i.test(mime || '') ? 'png' : 'jpg';
    const dir = path.dirname(it.abs);
    const dest = path.join(dir, `cover.${ext}`);
    for (const other of ['cover.jpg', 'cover.png', 'folder.jpg']) {
      const f = path.join(dir, other);
      if (await fsp.stat(f).catch(() => null)) await fsp.copyFile(f, `${f}.bak`, fs.constants.COPYFILE_EXCL).catch(() => {});
      if (other !== `cover.${ext}` && other.startsWith('cover.')) await fsp.rm(f, { force: true }).catch(() => {});
    }
    await fsp.writeFile(dest, buf);
    await this.refreshFile(it.lib, it.abs).catch(() => {});
    this.markDirty();
    return { ok: true };
  }

  /** Resolve art: {file} | {plex: path} | {data, mime} | null */
  async art(id, kind) {
    const it = this.get(id);
    if (!it) return null;
    const dir = it.abs ? (it.type === 'show' ? it.abs : path.dirname(it.abs)) : null;
    const sideFile = (name) => (name && dir ? { file: path.join(dir, name) } : null);
    if (it.type === 'track') {
      const e = it.entry;
      if (e.meta?.hasPicture) {
        const key = `${id}:${e.mtime}`;
        let c = this.coverCache.get(key);
        if (!c) {
          c = await readEmbeddedCover(it.abs);
          if (c) {
            this.coverCache.set(key, c);
            if (this.coverCache.size > 64) this.coverCache.delete(this.coverCache.keys().next().value);
          }
        }
        if (c) return c;
      }
      return sideFile(e.side?.art?.cover);
    }
    if (it.type === 'video') return sideFile(it.entry.side?.art?.thumb) || (it.plex?.thumb ? { plex: it.plex.thumb } : null);
    if (it.type === 'movie') {
      if (kind === 'backdrop') return (it.plex?.art ? { plex: it.plex.art } : null) || sideFile(it.entry.side?.art?.backdrop);
      return (it.plex?.thumb ? { plex: it.plex.thumb } : null) || sideFile(it.entry.side?.art?.poster);
    }
    if (it.type === 'episode') return (it.plex?.thumb ? { plex: it.plex.thumb } : null) || sideFile(it.entry.side?.art?.thumb);
    if (it.type === 'show') {
      if (kind === 'backdrop') return (it.plex?.art ? { plex: it.plex.art } : null) || sideFile(it.sideArt?.backdrop);
      return (it.plex?.thumb ? { plex: it.plex.thumb } : null) || sideFile(it.sideArt?.poster);
    }
    return null;
  }

  subtitle(videoId, subId) {
    const it = this.get(videoId);
    if (!it || !it.entry?.side?.subs) return null;
    const s = it.entry.side.subs.find((x) => x.id === subId);
    return s ? path.join(path.dirname(it.abs), s.file) : null;
  }

  // ---- edits -------------------------------------------------------------------------------------

  /**
   * POST /api/tags/:id
   * @returns {{status:number, body:any}}
   */
  async editTags(id, body) {
    const it = this.get(id);
    if (!it || it.type !== 'track') return { status: 404, body: { error: 'not_found' } };
    const fields = {};
    for (const k of TAG_FIELDS) {
      const v = body[k];
      if (typeof v === 'string') fields[k] = v.trim();
      else if (typeof v === 'number' && Number.isFinite(v) && ['year', 'track', 'disc'].includes(k)) fields[k] = String(Math.round(v));
    }
    for (const k of ['year', 'track', 'disc']) if (k in fields && fields[k] !== '' && !/^\d{1,4}$/.test(fields[k])) delete fields[k];
    if (!Object.keys(fields).length) return { status: 400, body: { error: 'bad_request' } };
    const st = await fsp.stat(it.abs).catch(() => null);
    if (!st) return { status: 404, body: { error: 'not_found' } };
    let cur = it;
    if (Math.round(st.mtimeMs) !== it.entry.mtime) {
      await this.refreshFile(it.lib, it.abs); // file changed on the PC since the last scan: re-read it
      cur = this.get(id) || it;
    }
    const curMtime = Math.round(st.mtimeMs);
    const pcArtist = cur.item.artist ?? null;
    const base = body.baseMtime != null ? Math.round(Number(body.baseMtime)) : null;
    const curVal = (k) => (k === 'genre' ? (cur.item.genres || []).join('; ') : cur.item[k] == null ? '' : String(cur.item[k]));
    const differs = Object.entries(fields).some(([k, v]) => curVal(k) !== v);
    if (!body.force && base != null && base !== curMtime && differs) {
      return { status: 409, body: { error: 'conflict', pcArtist } };
    }
    const label = Object.entries(fields).map(([k, v]) => `${k === 'albumArtist' ? 'album artist' : k} → ${v}`).join(', ');
    const job = this.activity.add('tag', `${cur.item.title}`, label);
    let method = null;
    try {
      method = await writeTags(it.abs, fields);
    } catch (e) {
      this.log(`[tags] write failed for ${it.abs}: ${e.message}; storing override`);
      method = null;
    }
    if (method) {
      this.state.clearOverride(id);
      const entry = await this.refreshFile(it.lib, it.abs);
      job.done(`${label} · written to file`);
      this.log(`[tags] ${cur.item.title}: ${label} (${method})`);
      return { status: 200, body: { ok: true, mtime: entry ? entry.mtime : Date.now() } };
    }
    this.state.setOverride(id, fields, cur.entry.mtime);
    this.markDirty();
    job.done(`${label} · saved by agent (file format not writable without ffmpeg)`);
    this.log(`[tags] ${cur.item.title}: ${label} (override)`);
    return { status: 200, body: { ok: true, mtime: cur.entry.mtime } };
  }

  /** POST /api/upload — stream the request body into the library folder. */
  async upload(libId, name, req) {
    const lib = this.lib(libId);
    if (!lib) return { status: 404, body: { error: 'not_found' } };
    const clean = sanitizeFileName(name);
    const ext = path.extname(clean).toLowerCase();
    if (!clean || !(AUDIO_EXTS.has(ext) || VIDEO_EXTS.has(ext)) || !this.roleFor(lib, AUDIO_EXTS.has(ext) ? 'audio' : 'video')) {
      return { status: 400, body: { error: 'bad_request' } };
    }
    await fsp.mkdir(lib.path, { recursive: true });
    const stem = clean.slice(0, clean.length - ext.length);
    let dest = path.join(lib.path, clean);
    for (let i = 1; fs.existsSync(dest); i++) dest = path.join(lib.path, `${stem} (${i})${ext}`);
    const tmp = path.join(lib.path, `.${crypto.randomBytes(6).toString('hex')}.signal-upload`);
    const total = Number(req.headers['content-length']) || 0;
    const job = this.activity.add('upload', path.basename(dest), `to ${lib.name}`);
    let received = 0;
    req.on('data', (d) => {
      received += d.length;
      if (total) job.progressTo(received / total);
    });
    try {
      await pipeline(req, fs.createWriteStream(tmp));
      if (total && received !== total) throw new Error('upload incomplete');
      await fsp.rename(tmp, dest);
    } catch (e) {
      await fsp.rm(tmp, { force: true }).catch(() => {});
      job.fail(e);
      return { status: 500, body: { error: 'upload_failed' } };
    }
    const entry = await this.refreshFile(lib, dest);
    const id = stableId(lib.id, entry ? entry.rel : toPosix(path.relative(lib.path, dest)));
    job.done(`Saved to ${dest}`);
    this.log(`[upload] ${dest}`);
    if (this.plex) {
      this.plex.refreshPath(path.dirname(dest)).then((ok) => { if (ok) this.activity.add('plex', 'Plex library refresh', path.dirname(dest), 'done').done(); }).catch(() => {});
    }
    return { status: 200, body: { ok: true, id } };
  }

  /** POST /api/progress */
  async progress(body) {
    const it = this.get(body.id);
    if (!it) return { status: 404, body: { error: 'not_found' } };
    const positionMs = Math.max(0, Math.round(Number(body.positionMs) || 0));
    const watched = !!body.watched;
    this.state.setProgress(body.id, positionMs, watched);
    this.markDirty();
    if (this.plex && it.plex?.ratingKey) {
      const job = this.activity.add('plex', `${it.item.title}`, watched ? 'Mark watched in Plex' : `Progress ${Math.round(positionMs / 1000)} s → Plex`);
      (watched ? this.plex.scrobble(it.plex.ratingKey) : this.plex.progress(it.plex.ratingKey, positionMs, it.item.durationMs))
        .then(() => job.done())
        .catch((e) => job.fail(e));
    }
    return { status: 200, body: { ok: true } };
  }

  /** Data for POST /api/suggest-artists. */
  async suggestInput(trackId) {
    const it = this.get(trackId);
    if (!it || it.type !== 'track') return null;
    const toTags = (t) => (t.genres || []).map((g) => g.toLowerCase());
    const lyr = await this.lyrics(trackId).catch(() => null);
    const track = { title: it.item.title || '', tags: toTags(it.item), lyrics: lyr?.lines || [], artist: it.item.artist };
    const all = this.getCatalog().tracks.map((t) => ({ artist: t.artist, tags: toTags(t) }));
    return { track, all };
  }
}
