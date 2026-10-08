// HTTP server (node:http only) implementing docs/API.md plus the localhost-only admin page.
import http from 'node:http';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import zlib from 'node:zlib';
import { promisify } from 'node:util';
import { spawn } from 'node:child_process';
import { parseRange } from './range.js';
import { suggestArtists } from './suggest.js';
import { mimeFor, hasExecutable } from './util.js';
import { isLosslessCodec } from './media.js';
import { adminPage } from './admin.js';
import { validateSetup, saveSetup } from './config.js';
import { Plex } from './plex.js';
import { PlexImages, IMAGE_WIDTHS } from './images.js';
import { listFolders } from './desktop.js';

const MAX_JSON = 1024 * 1024;

function sendJson(req, res, status, obj) {
  const body = Buffer.from(JSON.stringify(obj));
  const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' };
  if (body.length > 8192 && /\bgzip\b/.test(req.headers['accept-encoding'] || '')) {
    const gz = zlib.gzipSync(body);
    res.writeHead(status, { ...headers, 'Content-Encoding': 'gzip', 'Content-Length': gz.length, Vary: 'Accept-Encoding' });
    res.end(req.method === 'HEAD' ? undefined : gz);
    return;
  }
  res.writeHead(status, { ...headers, 'Content-Length': body.length });
  res.end(req.method === 'HEAD' ? undefined : body);
}

const gzip = promisify(zlib.gzip);
const notFound = (req, res) => sendJson(req, res, 404, { error: 'not_found' });
const badRequest = (req, res) => sendJson(req, res, 400, { error: 'bad_request' });

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const c of req) {
    size += c.length;
    if (size > MAX_JSON) throw Object.assign(new Error('too large'), { status: 413 });
    chunks.push(c);
  }
  if (!size) return {};
  try {
    const v = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    return v && typeof v === 'object' ? v : {};
  } catch {
    throw Object.assign(new Error('bad json'), { status: 400 });
  }
}

function contentDisposition(name) {
  const ascii = name.replace(/[^\x20-\x7e]/g, '_').replace(/["\\]/g, '_');
  return `attachment; filename="${ascii}"; filename*=UTF-8''${encodeURIComponent(name)}`;
}

/** Serve a file with Range / 206 support. */
export async function sendFile(req, res, abs, { contentType, headers = {} } = {}) {
  let st;
  try {
    st = await fsp.stat(abs);
  } catch {
    return notFound(req, res);
  }
  const size = st.size;
  const etag = `"${size.toString(16)}-${Math.round(st.mtimeMs).toString(16)}"`;
  const base = {
    'Content-Type': contentType || mimeFor(abs),
    'Accept-Ranges': 'bytes',
    'Last-Modified': st.mtime.toUTCString(),
    ETag: etag,
    ...headers,
  };
  let range = parseRange(req.headers.range, size);
  const ifRange = req.headers['if-range'];
  if (range && ifRange && ifRange !== etag && ifRange !== base['Last-Modified']) range = null;
  if (range === 'unsatisfiable') {
    res.writeHead(416, { ...base, 'Content-Range': `bytes */${size}`, 'Content-Length': 0 });
    return res.end();
  }
  let start = 0;
  let end = size - 1;
  if (range) {
    ({ start, end } = range);
    res.writeHead(206, { ...base, 'Content-Range': `bytes ${start}-${end}/${size}`, 'Content-Length': end - start + 1 });
  } else {
    res.writeHead(200, { ...base, 'Content-Length': size });
  }
  if (req.method === 'HEAD' || size === 0) return res.end();
  // Big reads (1 MB) keep a movie flowing on a busy hard drive; the default 64 KB means many more seeks.
  const stream = fs.createReadStream(abs, { start, end, highWaterMark: 1024 * 1024 });
  stream.on('error', () => res.destroy());
  res.on('close', () => stream.destroy());
  stream.pipe(res);
}

const isLocal = (req) => {
  const a = req.socket.remoteAddress || '';
  return a === '127.0.0.1' || a === '::1' || a === '::ffff:127.0.0.1';
};
const localHost = (req) => /^(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$/i.test(req.headers.host || '');

/**
 * @param {import('./agent.js').Agent} agent
 */
export function createServer(agent) {
  const { state, activity } = agent;
  // agent.catalog is replaced when settings are saved, so always read it through the agent.

  const routes = [];
  const route = (method, pattern, handler, { auth = true } = {}) => {
    const keys = [];
    const re = new RegExp(`^${pattern.replace(/:(\w+)/g, (_, k) => { keys.push(k); return '([^/]+)'; })}$`);
    routes.push({ method, re, keys, handler, auth });
  };

  // ---- public ----
  route('GET', '/api/info', (req, res) => sendJson(req, res, 200, agent.info()), { auth: false });

  route('POST', '/api/pair/start', async (req, res) => {
    const body = await readJson(req);
    if (state.pendingPairs().length >= 20) return sendJson(req, res, 429, { error: 'too_many_requests' });
    const r = state.startPair(body.deviceName, req.socket.remoteAddress);
    sendJson(req, res, 200, { requestId: r.id, code: r.code });
  }, { auth: false });

  route('GET', '/api/pair/status/:id', (req, res, p) => sendJson(req, res, 200, state.pairStatus(p.id)), { auth: false });

  // ---- authenticated ----
  route('GET', '/api/libraries', (req, res) => sendJson(req, res, 200, agent.catalog.getLibraries()));
  // The catalogue can be many MB: serialize and gzip it once per build (asynchronously), not on every request,
  // so the agent keeps answering while a big library scans.
  let catalogBody = null;
  route('GET', '/api/catalog', async (req, res) => {
    const cat = agent.catalog.getCatalog();
    if (catalogBody?.cat !== cat) {
      const body = Buffer.from(JSON.stringify(cat));
      catalogBody = { cat, body, gz: await gzip(body) };
    }
    const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' };
    const useGz = /\bgzip\b/.test(req.headers['accept-encoding'] || '');
    const out = useGz ? catalogBody.gz : catalogBody.body;
    res.writeHead(200, { ...headers, 'Content-Length': out.length, ...(useGz ? { 'Content-Encoding': 'gzip', Vary: 'Accept-Encoding' } : {}) });
    res.end(req.method === 'HEAD' ? undefined : out);
  });
  route('GET', '/api/activity', (req, res) => sendJson(req, res, 200, activity.list()));

  route('POST', '/api/rescan', (req, res) => {
    agent.catalog.scan('requested by app').catch(() => {});
    sendJson(req, res, 200, { ok: true });
  });

  const streamHandler = (req, res, p) => {
    const it = agent.catalog.get(p.id);
    if (!it || it.type === 'show') return notFound(req, res);
    // Tell the scanner a song is playing so it backs off the disk until the response ends.
    if (req.method === 'GET' && agent.catalog.streamStarted) {
      agent.catalog.streamStarted();
      res.once('close', () => agent.catalog.streamEnded());
    }
    return sendFile(req, res, it.abs);
  };
  route('GET', '/api/stream/:id', streamHandler);
  route('HEAD', '/api/stream/:id', streamHandler);

  const downloadHandler = async (req, res, p, q) => {
    const it = agent.catalog.get(p.id);
    if (!it || it.type === 'show') return notFound(req, res);
    const name = path.basename(it.abs);
    const quality = q.get('quality') || 'original';
    if (quality === '16-44' && it.type === 'track') {
      const m = it.entry.meta || {};
      const lossless = isLosslessCodec(m.codec) || /\.(wav|flac|aiff?)$/i.test(it.abs);
      const alreadySmall = m.codec === 'FLAC' && (m.bitDepth || 16) <= 16 && (m.sampleRate || 44100) <= 44100;
      if (lossless && !alreadySmall && (await hasExecutable('ffmpeg'))) {
        const outName = `${path.basename(name, path.extname(name))}.flac`;
        res.writeHead(200, {
          'Content-Type': 'audio/flac',
          'Content-Disposition': contentDisposition(outName),
          'X-Signal-Quality': '16-44',
          'Accept-Ranges': 'none',
          'Cache-Control': 'no-store',
        });
        if (req.method === 'HEAD') return res.end();
        const ff = spawn('ffmpeg', ['-v', 'error', '-nostdin', '-i', it.abs, '-map', '0:a:0', '-map_metadata', '0', '-c:a', 'flac', '-sample_fmt', 's16', '-ar', '44100', '-f', 'flac', 'pipe:1'], { windowsHide: true });
        ff.stdout.pipe(res);
        ff.on('error', () => res.destroy());
        ff.on('exit', (code) => { if (code !== 0) res.destroy(); });
        res.on('close', () => { if (ff.exitCode == null) ff.kill('SIGKILL'); });
        return undefined;
      }
    }
    return sendFile(req, res, it.abs, { headers: { 'Content-Disposition': contentDisposition(name), ...(quality === '16-44' ? { 'X-Signal-Quality': 'original' } : {}) } });
  };
  route('GET', '/api/download/:id', downloadHandler);
  route('HEAD', '/api/download/:id', downloadHandler);

  let plexImages = null;
  route('GET', '/api/art/:id', async (req, res, p, q) => {
    const a = await agent.catalog.art(p.id, q.get('kind') || 'cover');
    if (!a) return notFound(req, res);
    // Versioned URLs (?v=<file mtime>) never change content, so phones may keep them for good.
    const cache = q.get('v') ? 'public, max-age=31536000, immutable' : 'max-age=86400';
    if (a.data) {
      res.writeHead(200, { 'Content-Type': a.mime, 'Content-Length': a.data.length, 'Cache-Control': cache });
      return res.end(a.data);
    }
    if (a.plex) {
      try {
        if (!plexImages || plexImages.plex !== agent.plex) plexImages = new PlexImages(agent.plex, path.join(agent.config.dataDir, 'plex-images'));
        const img = await plexImages.get(a.plex, IMAGE_WIDTHS[q.get('kind') || 'cover'] || 0);
        // Plex picture paths change when the picture does, so a fetched one never goes stale.
        res.writeHead(200, { 'Content-Type': img.mime, 'Content-Length': img.data.length, 'Cache-Control': 'public, max-age=31536000, immutable' });
        return res.end(img.data);
      } catch {
        return notFound(req, res);
      }
    }
    return sendFile(req, res, a.file, { headers: { 'Cache-Control': cache } });
  });

  route('GET', '/api/subtitle/:videoId/:subId', (req, res, p) => {
    const f = agent.catalog.subtitle(p.videoId, p.subId);
    if (!f) return notFound(req, res);
    return sendFile(req, res, f);
  });

  route('GET', '/api/lyrics/:id', async (req, res, p) => {
    const l = await agent.catalog.lyrics(p.id);
    if (!l) return notFound(req, res);
    sendJson(req, res, 200, l);
  });

  route('GET', '/api/update', (req, res) => {
    const { current, latest, available, notes, downloading, error } = agent.updater.state();
    sendJson(req, res, 200, { current, latest, available, notes, downloading, error });
  });
  route('POST', '/api/update', async (req, res) => {
    const r = await agent.updater.install();
    sendJson(req, res, r.ok ? 200 : 409, r);
  });

  route('POST', '/api/lyrics/:id', async (req, res, p) => {
    const body = await readJson(req);
    if (typeof body.lrc !== 'string' || !body.lrc.trim()) return sendJson(req, res, 400, { error: 'bad_request' });
    const r = await agent.catalog.saveLyrics(p.id, body.lrc);
    if (!r) return notFound(req, res);
    sendJson(req, res, 200, { ok: true });
  });

  route('POST', '/api/art/:id', async (req, res, p) => {
    const mime = String(req.headers['content-type'] || '');
    if (!/^image\/(jpeg|png)/i.test(mime)) return sendJson(req, res, 400, { error: 'bad_request' });
    const chunks = [];
    let size = 0;
    for await (const c of req) {
      size += c.length;
      if (size > 20 * 1024 * 1024) return sendJson(req, res, 413, { error: 'too_large' });
      chunks.push(c);
    }
    if (!size) return sendJson(req, res, 400, { error: 'bad_request' });
    const r = await agent.catalog.saveCover(p.id, Buffer.concat(chunks), mime);
    if (!r) return notFound(req, res);
    sendJson(req, res, 200, r);
  });

  route('POST', '/api/tags/:id', async (req, res, p) => {
    const body = await readJson(req);
    const r = await agent.catalog.editTags(p.id, body);
    sendJson(req, res, r.status, r.body);
  });

  route('POST', '/api/suggest-artists', async (req, res) => {
    const body = await readJson(req);
    const input = await agent.catalog.suggestInput(String(body.trackId || ''));
    if (!input) return notFound(req, res);
    sendJson(req, res, 200, suggestArtists(input.track, input.all, typeof body.prompt === 'string' ? body.prompt : ''));
  });

  route('POST', '/api/upload', async (req, res, p, q) => {
    const lib = q.get('library');
    const name = q.get('name');
    if (!lib || !name) return badRequest(req, res);
    const r = await agent.catalog.upload(lib, name, req);
    sendJson(req, res, r.status, r.body);
  });

  route('POST', '/api/progress', async (req, res) => {
    const body = await readJson(req);
    if (!body.id) return badRequest(req, res);
    const r = await agent.catalog.progress(body);
    sendJson(req, res, r.status, r.body);
  });

  // ---- admin (localhost only) ----
  const admin = (method, pattern, handler) => route(method, pattern, handler, { auth: 'admin' });
  admin('GET', '/', (req, res) => {
    const html = adminPage();
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', 'X-Frame-Options': 'DENY' });
    res.end(html);
  });
  admin('GET', '/admin/state', (req, res) => sendJson(req, res, 200, agent.adminState()));
  admin('POST', '/admin/pair/:id/approve', (req, res, p) => {
    const r = agent.approvePairing(p.id);
    sendJson(req, res, r ? 200 : 404, r ? { ok: true } : { error: 'not_found' });
  });
  admin('POST', '/admin/pair/:id/deny', (req, res, p) => {
    const r = agent.denyPairing(p.id);
    sendJson(req, res, r ? 200 : 404, r ? { ok: true } : { error: 'not_found' });
  });
  admin('POST', '/admin/devices/:id/revoke', (req, res, p) => {
    const ok = state.revokeDevice(p.id);
    if (ok) agent.log(`[pair] device ${p.id} revoked`);
    sendJson(req, res, ok ? 200 : 404, ok ? { ok: true } : { error: 'not_found' });
  });
  admin('POST', '/admin/rescan', (req, res) => {
    agent.catalog.scan('admin page').catch(() => {});
    sendJson(req, res, 200, { ok: true });
  });
  admin('GET', '/admin/setup', (req, res) => sendJson(req, res, 200, agent.setupState()));
  admin('POST', '/admin/setup', async (req, res) => {
    const body = await readJson(req);
    const isDir = (p) => { try { return fs.statSync(p).isDirectory(); } catch { return false; } };
    const v = validateSetup(body, { isDir });
    if (!v.ok) return sendJson(req, res, 400, { error: 'invalid', errors: v.errors });
    if (!agent.config.configPath) return sendJson(req, res, 409, { error: 'no_config_file' });
    let cfg;
    try {
      cfg = await saveSetup(agent.config.configPath, v.setup);
    } catch (e) {
      return sendJson(req, res, 500, { error: 'write_failed', errors: [{ field: 'config', message: `Could not save ${agent.config.configPath}: ${e.message}` }] });
    }
    agent.reconfigure(cfg).catch(() => {});
    const plex = v.setup.plex.url && v.setup.plex.token ? await Plex.test(v.setup.plex) : null;
    sendJson(req, res, 200, { ok: true, plex, setup: { ...agent.setupState(), name: cfg.name, libraries: v.setup.libraries, plex: v.setup.plex } });
  });
  // Folder picker for the setup page. Also needs the X-Signal-Admin header (so other web pages can't use it).
  admin('GET', '/admin/browse', async (req, res, p, q) => {
    if (req.headers['x-signal-admin'] !== '1') return sendJson(req, res, 403, { error: 'forbidden' });
    sendJson(req, res, 200, await listFolders(q.get('path') || ''));
  });
  admin('POST', '/admin/startup', async (req, res) => {
    const body = await readJson(req);
    const st = await agent.startup.set(!!body.enabled);
    if (st.ok && st.supported) agent.log(`[startup] Start with Windows ${body.enabled ? 'on' : 'off'} (${st.file})`);
    sendJson(req, res, 200, st);
  });
  // Used by the Windows tray icon (src/tray.ps1). Both also need the X-Signal-Admin header.
  admin('GET', '/admin/tray-state', (req, res) => {
    if (req.headers['x-signal-admin'] !== '1') return sendJson(req, res, 403, { error: 'forbidden' });
    sendJson(req, res, 200, agent.trayState());
  });
  // Self-update: setup page / tray (admin) and the phone app (token).
  admin('GET', '/admin/update', (req, res) => {
    if (req.headers['x-signal-admin'] !== '1') return sendJson(req, res, 403, { error: 'forbidden' });
    sendJson(req, res, 200, agent.updater.state());
  });
  admin('POST', '/admin/update', async (req, res) => {
    const r = await agent.updater.install();
    sendJson(req, res, r.ok ? 200 : 409, r);
  });
  admin('POST', '/admin/quit', (req, res) => {
    res.once('finish', () => setImmediate(() => { agent.quit().catch((e) => agent.log(`[agent] quit failed: ${e.stack || e}`)); }));
    sendJson(req, res, 200, { ok: true });
  });

  const server = http.createServer(async (req, res) => {
    let url;
    try {
      url = new URL(req.url, 'http://x');
    } catch {
      return badRequest(req, res);
    }
    let pathname;
    try {
      pathname = decodeURIComponent(url.pathname);
    } catch {
      return badRequest(req, res);
    }
    let matchedPath = false;
    for (const r of routes) {
      const m = r.re.exec(pathname);
      if (!m) continue;
      matchedPath = true;
      if (r.method !== req.method) continue;
      const params = Object.fromEntries(r.keys.map((k, i) => [k, m[i + 1]]));
      if (r.auth === 'admin') {
        if (!isLocal(req) || !localHost(req)) return sendJson(req, res, 403, { error: 'forbidden' });
        if (req.method !== 'GET' && req.headers['x-signal-admin'] !== '1') return sendJson(req, res, 403, { error: 'forbidden' });
      } else if (r.auth) {
        const h = req.headers.authorization || '';
        const token = /^Bearer\s+(.+)$/i.exec(h)?.[1]?.trim() || url.searchParams.get('token');
        const dev = state.deviceForToken(token);
        if (!dev) return sendJson(req, res, 401, { error: 'unauthorized' });
        req.device = dev;
      }
      try {
        await r.handler(req, res, params, url.searchParams);
      } catch (e) {
        if (!res.headersSent) sendJson(req, res, e.status || 500, { error: e.status === 400 ? 'bad_request' : e.status === 413 ? 'too_large' : 'internal_error' });
        else res.destroy();
        if (!e.status) agent.log(`[http] ${req.method} ${pathname}: ${e.stack || e.message}`);
      }
      return undefined;
    }
    if (matchedPath) return sendJson(req, res, 405, { error: 'method_not_allowed' });
    return notFound(req, res);
  });
  server.keepAliveTimeout = 65000;
  server.requestTimeout = 0; // uploads/streams can be long
  return server;
}
