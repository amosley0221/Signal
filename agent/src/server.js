// HTTP server (node:http only) implementing docs/API.md plus the localhost-only admin page.
import http from 'node:http';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import zlib from 'node:zlib';
import { spawn } from 'node:child_process';
import { parseRange } from './range.js';
import { suggestArtists } from './suggest.js';
import { mimeFor, hasExecutable } from './util.js';
import { isLosslessCodec } from './media.js';
import { adminPage } from './admin.js';
import { validateSetup, saveSetup } from './config.js';
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
  const stream = fs.createReadStream(abs, { start, end });
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
  route('GET', '/api/catalog', (req, res) => sendJson(req, res, 200, agent.catalog.getCatalog()));
  route('GET', '/api/activity', (req, res) => sendJson(req, res, 200, activity.list()));

  route('POST', '/api/rescan', (req, res) => {
    agent.catalog.scan('requested by app').catch(() => {});
    sendJson(req, res, 200, { ok: true });
  });

  const streamHandler = (req, res, p) => {
    const it = agent.catalog.get(p.id);
    if (!it || it.type === 'show') return notFound(req, res);
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

  route('GET', '/api/art/:id', async (req, res, p, q) => {
    const a = await agent.catalog.art(p.id, q.get('kind') || 'cover');
    if (!a) return notFound(req, res);
    if (a.data) {
      res.writeHead(200, { 'Content-Type': a.mime, 'Content-Length': a.data.length, 'Cache-Control': 'max-age=3600' });
      return res.end(a.data);
    }
    if (a.plex) {
      try {
        return await agent.plex.proxyImage(a.plex, res);
      } catch {
        return notFound(req, res);
      }
    }
    return sendFile(req, res, a.file, { headers: { 'Cache-Control': 'max-age=3600' } });
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
    sendJson(req, res, 200, { ok: true, setup: { ...agent.setupState(), name: cfg.name, libraries: v.setup.libraries, plex: v.setup.plex } });
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
