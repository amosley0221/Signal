// ffmpeg + ffprobe for Windows PCs that don't have them: downloaded once into the agent's data folder.
// ffprobe reads video length, resolution, HDR and audio tracks without Plex; ffmpeg writes tags and converts.
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { hasExecutable, run, setToolDir } from './util.js';

const URL_WIN64 = 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip';

async function findFile(dir, name) {
  for (const e of await fsp.readdir(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) { const f = await findFile(p, name); if (f) return f; } else if (e.name.toLowerCase() === name) return p;
  }
  return null;
}

/**
 * Makes sure ffmpeg and ffprobe can run. Returns true when it installed them now (so videos can be re-read).
 * Only on Windows; elsewhere install them with the system's package manager.
 */
export async function ensureFfmpeg({ dataDir, log = () => {}, activity = null, platform = process.platform, fetchImpl = globalThis.fetch }) {
  const bin = path.join(dataDir, 'bin');
  setToolDir(bin);
  if ((await hasExecutable('ffprobe')) && (await hasExecutable('ffmpeg'))) return false;
  if (platform !== 'win32') { log('[ffmpeg] not found: install ffmpeg to read video details without Plex'); return false; }
  const job = activity?.add?.('plex', 'Installing ffmpeg', 'Needed to read video details without Plex');
  const work = path.join(dataDir, 'ffmpeg-download');
  try {
    log('[ffmpeg] not found: downloading it once (about 100 MB)');
    await fsp.rm(work, { recursive: true, force: true });
    await fsp.mkdir(work, { recursive: true });
    const zip = path.join(work, 'ffmpeg.zip');
    const r = await fetchImpl(URL_WIN64, { signal: AbortSignal.timeout(20 * 60 * 1000) });
    if (!r.ok || !r.body) throw new Error(`download failed (HTTP ${r.status})`);
    await pipeline(Readable.fromWeb(r.body), fs.createWriteStream(zip));
    // Windows 10+ has tar (bsdtar), which unpacks zip files.
    await run('tar', ['-xf', zip, '-C', work], { timeoutMs: 10 * 60 * 1000 });
    await fsp.mkdir(bin, { recursive: true });
    for (const name of ['ffmpeg.exe', 'ffprobe.exe']) {
      const f = await findFile(work, name);
      if (!f) throw new Error(`${name} missing from the download`);
      await fsp.copyFile(f, path.join(bin, name));
    }
    setToolDir(bin);
    const ok = (await hasExecutable('ffprobe')) && (await hasExecutable('ffmpeg'));
    if (!ok) throw new Error('installed, but it does not run');
    log('[ffmpeg] installed');
    job?.done('ffmpeg installed');
    return true;
  } catch (e) {
    log(`[ffmpeg] could not install: ${e.message}`);
    job?.fail(e);
    return false;
  } finally {
    await fsp.rm(work, { recursive: true, force: true }).catch(() => {});
  }
}
