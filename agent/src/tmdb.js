// Movie & TV details from TMDB (themoviedb.org): Signal's own metadata, so the library doesn't depend on Plex.
// Lookups are asynchronous and cached on disk; the catalog asks synchronously and gets null until a lookup is
// done, then rebuilds (onChange). Results use the same shape as Plex's (title, year, summary, thumb, art, ...).
import fs from 'node:fs';
import path from 'node:path';

const API = 'https://api.themoviedb.org/3';
const RETRY_MISS_MS = 7 * 24 * 3600 * 1000;
const REFRESH_MS = 30 * 24 * 3600 * 1000;

/** Title for comparing: lower case, no accents or punctuation. */
export function normTitle(s) {
  return String(s || '').normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase()
    .replace(/&/g, ' and ').replace(/['’`]/g, '').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}

/** Picks the best search result: same title (and year, when known) first, then the most popular. */
export function pickResult(results, title, year, kind) {
  const want = normTitle(title);
  const yearOf = (r) => Number(String((kind === 'tv' ? r.first_air_date : r.release_date) || '').slice(0, 4)) || null;
  const nameOf = (r) => (kind === 'tv' ? [r.name, r.original_name] : [r.title, r.original_title]);
  let best = null; let bestScore = -1;
  for (const [i, r] of (results || []).entries()) {
    const names = nameOf(r).map(normTitle);
    let s = 0;
    if (names.includes(want)) s += 10;
    else if (names.some((n) => n && (n.startsWith(want) || want.startsWith(n)))) s += 4;
    else continue; // a different title: never a match, whatever the year
    const y = yearOf(r);
    if (year && y) s += y === year ? 6 : Math.abs(y - year) === 1 ? 3 : -4;
    s += Math.max(0, 3 - i) * 0.5; // TMDB's own ranking
    s += Math.min(2, (r.popularity || 0) / 50);
    if (s > bestScore) { bestScore = s; best = r; }
  }
  return bestScore >= 4 ? best : null;
}

export class Tmdb {
  /** @param {{key:string, dataDir:string, log?:Function, onChange?:Function, fetchImpl?:Function}} o */
  constructor({ key, dataDir, log = () => {}, onChange = () => {}, fetchImpl = globalThis.fetch }) {
    this.key = String(key || '').trim();
    this.file = path.join(dataDir, 'tmdb.json');
    this.log = log;
    this.onChange = onChange;
    this.fetch = fetchImpl;
    this.data = { find: {}, movie: {}, tv: {}, season: {} };
    try { Object.assign(this.data, JSON.parse(fs.readFileSync(this.file, 'utf8'))); } catch { /* first run */ }
    this.queue = new Map();
    this.running = new Set();
    this.active = 0;
    this.saveTimer = null;
    this.changeTimer = null;
  }

  get enabled() { return !!this.key; }

  #auth() {
    // A v4 "read access token" is a long JWT and goes in a header; a v3 "API key" goes in the address.
    return this.key.length > 40 && this.key.includes('.') ? { headers: { Authorization: `Bearer ${this.key}` }, query: {} } : { headers: {}, query: { api_key: this.key } };
  }

  async get(p, params = {}) {
    const a = this.#auth();
    const u = new URL(API + p);
    for (const [k, v] of Object.entries({ ...params, ...a.query })) if (v != null && v !== '') u.searchParams.set(k, String(v));
    const r = await this.fetch(u, { headers: { Accept: 'application/json', ...a.headers }, signal: AbortSignal.timeout(15000) });
    if (r.status === 429) { await new Promise((res) => setTimeout(res, 1500)); return this.get(p, params); }
    if (!r.ok) throw new Error(`TMDB ${p} → HTTP ${r.status}`);
    return r.json();
  }

  /** Checks a key: {ok:true} or {ok:false, message}. */
  static async test(key, fetchImpl = globalThis.fetch) {
    try {
      await new Tmdb({ key, dataDir: '.', fetchImpl }).get('/configuration');
      return { ok: true };
    } catch (e) {
      return { ok: false, message: /401/.test(e.message) ? 'TMDB rejected this key. Copy the "API Key" or "API Read Access Token" from themoviedb.org → Settings → API.' : `Couldn't reach TMDB (${e.message}).` };
    }
  }

  // ---- synchronous lookups (null until fetched) -----------------------------------------------

  /** Details for a movie by file title/year, or by a chosen TMDB id. */
  movieFor(title, year, chosenId = null) {
    if (!this.enabled) return null;
    const id = chosenId ?? this.#found('movie', title, year);
    return id ? this.#details('movie', id) : null;
  }

  showFor(name, year, chosenId = null) {
    if (!this.enabled) return null;
    const id = chosenId ?? this.#found('tv', name, year);
    return id ? this.#details('tv', id) : null;
  }

  /** One episode's title, summary and still, from the show's season (fetched on demand). */
  episodeFor(showId, season, episode) {
    if (!this.enabled || !showId || season == null || episode == null) return null;
    const key = `${showId}:${season}`;
    const s = this.data.season[key];
    if (!s || (s.at && Date.now() - s.at > REFRESH_MS && !s.missing)) this.#enqueue(`season:${key}`, () => this.#loadSeason(showId, season));
    const e = s?.episodes?.[episode];
    return e ? { ...e, matchedBy: 'TMDB' } : null;
  }

  #found(kind, title, year) {
    if (!title) return null;
    const k = `${kind}:${normTitle(title)}|${year || ''}`;
    const f = this.data.find[k];
    if (f && (f.id || Date.now() - f.at < RETRY_MISS_MS)) return f.id;
    this.#enqueue(`find:${k}`, () => this.#search(kind, title, year, k));
    return null;
  }

  #details(kind, id) {
    const d = this.data[kind][id];
    if (!d || Date.now() - (d.at || 0) > REFRESH_MS) this.#enqueue(`${kind}:${id}`, () => this.#loadDetails(kind, id));
    return d && d.title ? d : null;
  }

  // ---- background fetching --------------------------------------------------------------------

  #enqueue(key, job) {
    if (this.queue.has(key) || this.running.has(key)) return;
    this.queue.set(key, job);
    this.#pump();
  }

  #pump() {
    while (this.active < 4 && this.queue.size) {
      const [key, job] = this.queue.entries().next().value;
      this.queue.delete(key);
      this.running.add(key);
      this.active++;
      job().catch((e) => this.log(`[tmdb] ${e.message}`)).finally(() => {
        this.running.delete(key);
        this.active--;
        this.#changed();
        this.#pump();
      });
    }
  }

  async #search(kind, title, year, k) {
    const params = kind === 'tv' ? { query: title, first_air_date_year: year } : { query: title, year };
    let j = await this.get(`/search/${kind}`, params);
    let best = pickResult(j.results, title, year, kind);
    if (!best && year) { j = await this.get(`/search/${kind}`, { query: title }); best = pickResult(j.results, title, year, kind); }
    this.data.find[k] = { id: best ? best.id : null, at: Date.now() };
  }

  async #loadDetails(kind, id) {
    const extra = kind === 'tv' ? 'credits,content_ratings' : 'credits,release_dates';
    const d = await this.get(`/${kind}/${id}`, { append_to_response: extra });
    this.data[kind][id] = kind === 'tv' ? mapShow(d) : mapMovie(d);
  }

  async #loadSeason(showId, season) {
    const key = `${showId}:${season}`;
    try {
      const s = await this.get(`/tv/${showId}/season/${season}`);
      const episodes = {};
      for (const e of s.episodes || []) {
        episodes[e.episode_number] = {
          title: e.name || null, summary: e.overview || null,
          thumb: e.still_path ? `tmdb:${e.still_path}` : null,
          airDate: e.air_date || null, durationMs: e.runtime ? e.runtime * 60000 : null,
        };
      }
      this.data.season[key] = { episodes, poster: s.poster_path ? `tmdb:${s.poster_path}` : null, at: Date.now() };
    } catch (e) {
      this.data.season[key] = { episodes: {}, missing: true, at: Date.now() };
      throw e;
    }
  }

  #changed() {
    clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => {
      try { fs.mkdirSync(path.dirname(this.file), { recursive: true }); fs.writeFileSync(this.file, JSON.stringify(this.data)); } catch { /* next time */ }
    }, 2000);
    // Many lookups finish together while a library is first matched: rebuild once they settle.
    clearTimeout(this.changeTimer);
    this.changeTimer = setTimeout(() => this.onChange(), this.queue.size ? 3000 : 500);
  }

  /** Waits until queued lookups are done (tests, and a scan that wants complete details). */
  async idle(timeoutMs = 60000) {
    const end = Date.now() + timeoutMs;
    while ((this.queue.size || this.active) && Date.now() < end) await new Promise((r) => setTimeout(r, 50));
  }

  // ---- "Fix match" --------------------------------------------------------------------------------

  /** Search results for the Fix match screen. */
  async search(kind, query, year) {
    const j = await this.get(`/search/${kind}`, kind === 'tv' ? { query, first_air_date_year: year } : { query, year });
    return (j.results || []).slice(0, 12).map((r) => ({
      tmdbId: r.id,
      title: kind === 'tv' ? r.name : r.title,
      year: Number(String((kind === 'tv' ? r.first_air_date : r.release_date) || '').slice(0, 4)) || null,
      summary: r.overview || null,
      posterUrl: r.poster_path ? `https://image.tmdb.org/t/p/w342${r.poster_path}` : null,
    }));
  }

  /** Loads a chosen match now (so the catalog has it right away). */
  async load(kind, id) {
    await this.#loadDetails(kind, id);
    this.#changed();
    return this.data[kind][id];
  }
}

function mapCommon(d) {
  return {
    tmdbId: d.id,
    summary: d.overview || null,
    rating: d.vote_average ? Math.round(d.vote_average * 10) / 10 : null,
    genres: (d.genres || []).map((g) => g.name),
    cast: (d.credits?.cast || []).slice(0, 12).map((c) => c.name),
    thumb: d.poster_path ? `tmdb:${d.poster_path}` : null,
    art: d.backdrop_path ? `tmdb:${d.backdrop_path}` : null,
    matchedBy: 'TMDB',
    source: 'TMDB',
    at: Date.now(),
  };
}

export function mapMovie(d) {
  const us = (d.release_dates?.results || []).find((r) => r.iso_3166_1 === 'US');
  const cert = us?.release_dates?.map((r) => r.certification).find(Boolean) || null;
  return {
    kind: 'movie', ...mapCommon(d),
    title: d.title || null,
    year: Number(String(d.release_date || '').slice(0, 4)) || null,
    contentRating: cert,
    director: (d.credits?.crew || []).filter((c) => c.job === 'Director').map((c) => c.name).join(', ') || null,
    durationMs: d.runtime ? d.runtime * 60000 : null,
  };
}

export function mapShow(d) {
  const us = (d.content_ratings?.results || []).find((r) => r.iso_3166_1 === 'US');
  return {
    kind: 'show', ...mapCommon(d),
    title: d.name || null,
    year: Number(String(d.first_air_date || '').slice(0, 4)) || null,
    contentRating: us?.rating || null,
    director: (d.created_by || []).map((c) => c.name).join(', ') || null,
  };
}
