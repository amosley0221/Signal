// Optional Plex Media Server integration (HTTP API, JSON).
import path from 'node:path';
import { Readable } from 'node:stream';

const TYPE_MAP = { artist: 'music', movie: 'movies', show: 'tv' };

/** Normalise a file path for cross-platform comparison. */
export function normPath(p) {
  return String(p || '').replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase();
}
const tail = (p, n = 2) => normPath(p).split('/').slice(-n).join('/');

function guidSource(item) {
  const ids = [...(item.Guid || []).map((g) => g.id), item.guid || ''].join(' ').toLowerCase();
  if (/tmdb:\/\/|themoviedb/.test(ids)) return 'TMDB';
  if (/tvdb:\/\/|thetvdb/.test(ids)) return 'TVDB';
  if (/imdb:\/\/|imdb/.test(ids)) return 'IMDB';
  return null;
}

export class Plex {
  constructor({ url, token }, { clientId = 'signal-agent', log = () => {} } = {}) {
    this.url = String(url || '').replace(/\/+$/, '');
    this.token = token;
    this.clientId = clientId;
    this.log = log;
    this.byPath = new Map();
    this.byTail = new Map();
    this.shows = new Map();
    this.sections = [];
    this.lastRefresh = 0;
    this.reachable = false;
  }

  get enabled() { return !!(this.url && this.token); }

  headers() {
    return {
      Accept: 'application/json',
      'X-Plex-Token': this.token,
      'X-Plex-Client-Identifier': this.clientId,
      'X-Plex-Product': 'Signal Agent',
      'X-Plex-Version': '1.0.0',
    };
  }

  async get(p, params = {}, { json = true, timeoutMs = 20000 } = {}) {
    const u = new URL(this.url + p);
    for (const [k, v] of Object.entries(params)) if (v != null) u.searchParams.set(k, String(v));
    const res = await fetch(u, { headers: this.headers(), signal: AbortSignal.timeout(timeoutMs) });
    if (!res.ok) throw new Error(`Plex ${p} → HTTP ${res.status}`);
    return json ? res.json() : res;
  }

/**
   * Check an address + token: {ok:true, libraries:n} or {ok:false, message} in plain words.
   */
  static async test({ url, token }, { timeoutMs = 4000 } = {}) {
    const plex = new Plex({ url, token });
    try {
      const j = await plex.get('/library/sections', {}, { timeoutMs });
      return { ok: true, libraries: (j.MediaContainer?.Directory || []).length };
    } catch (e) {
      const m = /HTTP (\d+)/.exec(e.message);
      if (m && (m[1] === '401' || m[1] === '403')) return { ok: false, message: 'Plex rejected the token. Copy it again from Plex (Get Info → View XML).' };
      if (m) return { ok: false, message: `Plex answered with an error (HTTP ${m[1]}) at ${plex.url}.` };
      return { ok: false, message: `Plex didn't answer at ${plex.url}. Is Plex Media Server running, and is the address right?` };
    }
  }

  /** Library sections with their folder paths. */
  async getSections() {
    const j = await this.get('/library/sections');
    this.reachable = true;
    this.sections = (j.MediaContainer?.Directory || []).map((d) => ({
      key: String(d.key), title: d.title, plexType: d.type, type: TYPE_MAP[d.type] || null,
      agent: d.agent, locations: (d.Location || []).map((l) => l.path),
    }));
    return this.sections;
  }

  /** Libraries (in Signal form) derived from Plex sections, for when none are configured. */
  async offerLibraries() {
    const secs = await this.getSections();
    const out = [];
    for (const s of secs) {
      if (!s.type) continue;
      s.locations.forEach((loc, i) => {
        out.push({ id: `plex${s.key}${s.locations.length > 1 ? `-${i + 1}` : ''}`, name: s.locations.length > 1 ? `${s.title} (${i + 1})` : s.title, type: s.type, path: loc, plexSection: s.key });
      });
    }
    return out;
  }

  sectionForPath(p) {
    const n = normPath(p);
    return this.sections.find((s) => s.locations.some((l) => n.startsWith(normPath(l)))) || null;
  }

  /** Re-read all movie/show sections and index items by file path. */
  async refresh() {
    const secs = await this.getSections();
    const byPath = new Map();
    const byTail = new Map();
    const shows = new Map();
    const add = (file, info) => {
      byPath.set(normPath(file), info);
      byTail.set(tail(file), info);
    };
    for (const s of secs) {
      if (s.plexType === 'movie') {
        const j = await this.get(`/library/sections/${s.key}/all`, { includeGuids: 1 }, { timeoutMs: 60000 });
        for (const m of j.MediaContainer?.Metadata || []) {
          const info = this.mapMovie(m);
          for (const media of m.Media || []) for (const part of media.Part || []) if (part.file) add(part.file, info);
        }
      } else if (s.plexType === 'show') {
        const js = await this.get(`/library/sections/${s.key}/all`, { includeGuids: 1 }, { timeoutMs: 60000 });
        for (const sh of js.MediaContainer?.Metadata || []) shows.set(String(sh.ratingKey), this.mapShow(sh));
        // type=4 → every episode of the section in one request (cheaper than /library/metadata/:id/children per season)
        const je = await this.get(`/library/sections/${s.key}/all`, { type: 4, includeGuids: 1 }, { timeoutMs: 120000 });
        for (const e of je.MediaContainer?.Metadata || []) {
          const info = this.mapEpisode(e);
          for (const media of e.Media || []) for (const part of media.Part || []) if (part.file) add(part.file, info);
        }
      }
    }
    this.byPath = byPath;
    this.byTail = byTail;
    this.shows = shows;
    this.lastRefresh = Date.now();
    return { items: byPath.size, shows: shows.size };
  }

  /**
   * Plex's own "Continue Watching" row (what the Plex home screen shows), as rating keys in order.
   * Tries the current hub endpoints, then the older On Deck list. Null when none answers.
   */
  async continueWatching() {
    for (const p of ['/hubs/continueWatching/items', '/hubs/home/continueWatching', '/library/onDeck']) {
      try {
        const j = await this.get(p, { count: 50 }, { timeoutMs: 15000 });
        const items = j.MediaContainer?.Metadata || j.MediaContainer?.Hub?.[0]?.Metadata;
        if (Array.isArray(items)) return items.map((m) => String(m.ratingKey));
      } catch {
        // try the next endpoint
      }
    }
    return null;
  }

  /** Children of a metadata item (seasons of a show, episodes of a season). */
  async children(ratingKey) {
    const j = await this.get(`/library/metadata/${ratingKey}/children`);
    return j.MediaContainer?.Metadata || [];
  }

  match(file) {
    return this.byPath.get(normPath(file)) || this.byTail.get(tail(file)) || null;
  }

  common(m) {
    return {
      ratingKey: String(m.ratingKey),
      title: m.title || null,
      year: m.year || null,
      summary: m.summary || null,
      rating: m.audienceRating ?? m.rating ?? null,
      contentRating: m.contentRating || null,
      genres: (m.Genre || []).map((g) => g.tag),
      cast: (m.Role || []).map((r) => r.tag),
      director: (m.Director || []).map((d) => d.tag).join(', ') || null,
      thumb: m.thumb || null,
      art: m.art || null,
      viewOffsetMs: m.viewOffset || 0,
      watched: (m.viewCount || 0) > 0,
      lastViewedAt: m.lastViewedAt ? m.lastViewedAt * 1000 : 0,
      durationMs: m.duration || null,
      source: guidSource(m),
    };
  }

  mapMovie(m) { return { kind: 'movie', ...this.common(m), matchedBy: `PLEX · ${guidSource(m) || 'TMDB'}` }; }

  mapShow(m) { return { kind: 'show', ...this.common(m), matchedBy: `PLEX · ${guidSource(m) || 'TVDB'}` }; }

  mapEpisode(e) {
    return { kind: 'episode', ...this.common(e), showKey: String(e.grandparentRatingKey || ''), season: e.parentIndex ?? null, episode: e.index ?? null };
  }

  /** Stream a Plex image (thumb/art path) to an HTTP response. */
  async proxyImage(imgPath, res) {
    const r = await this.get(imgPath, {}, { json: false, timeoutMs: 20000 });
    res.writeHead(200, { 'Content-Type': r.headers.get('content-type') || 'image/jpeg', 'Cache-Control': 'max-age=86400' });
    Readable.fromWeb(r.body).pipe(res);
  }

  async scrobble(ratingKey) {
    await this.get('/:/scrobble', { key: ratingKey, identifier: 'com.plexapp.plugins.library' }, { json: false });
  }

  async progress(ratingKey, positionMs, durationMs) {
    try {
      await this.get('/:/timeline', { ratingKey, key: `/library/metadata/${ratingKey}`, state: 'stopped', time: Math.round(positionMs), duration: durationMs || undefined, identifier: 'com.plexapp.plugins.library' }, { json: false });
    } catch {
      await this.get('/:/progress', { key: ratingKey, identifier: 'com.plexapp.plugins.library', time: Math.round(positionMs), state: 'stopped' }, { json: false });
    }
  }

  /** Ask Plex to scan a folder (e.g. after an upload). */
  async refreshPath(dir) {
    const s = this.sectionForPath(dir);
    if (!s) return false;
    await this.get(`/library/sections/${s.key}/refresh`, { path: path.resolve(dir) }, { json: false });
    return true;
  }
}
