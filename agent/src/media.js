// Media metadata extraction (music-metadata, optionally ffprobe) and sidecar discovery.
import path from 'node:path';
import fsp from 'node:fs/promises';
import { parseFile } from 'music-metadata';
import { hasExecutable, run, IMAGE_EXTS } from './util.js';
import { embeddedToLines, detectLanguage, parseLrc } from './lyrics.js';

const CONTAINERS = { '.wav': 'WAV', '.flac': 'FLAC', '.m4a': 'M4A', '.mp3': 'MP3', '.aac': 'AAC', '.ogg': 'OGG', '.opus': 'OPUS', '.aiff': 'AIFF', '.aif': 'AIFF', '.mp4': 'MP4', '.mkv': 'MKV', '.m4v': 'M4V', '.mov': 'MOV', '.webm': 'WEBM', '.avi': 'AVI' };
export const containerFor = (file) => CONTAINERS[path.extname(file).toLowerCase()] || path.extname(file).slice(1).toUpperCase();

export function normalizeCodec(codec, file) {
  const c = String(codec || '').toUpperCase();
  const ext = path.extname(file).toLowerCase();
  if (!c) return { '.wav': 'PCM', '.aiff': 'PCM', '.aif': 'PCM', '.flac': 'FLAC', '.mp3': 'MP3', '.aac': 'AAC', '.opus': 'OPUS', '.ogg': 'VORBIS' }[ext] || null;
  if (c.includes('PCM') || c === 'LPCM' || c.includes('TWOS') || c.includes('SOWT') || c.includes('IEEE_FLOAT')) return 'PCM';
  if (c.includes('FLAC')) return 'FLAC';
  if (c.includes('ALAC')) return 'ALAC';
  if (c.includes('AAC') || c.includes('MP4A')) return 'AAC';
  if (c.includes('LAYER 3') || c === 'MP3') return 'MP3';
  if (c.includes('OPUS')) return 'OPUS';
  if (c.includes('VORBIS')) return 'VORBIS';
  return c;
}

const LOSSLESS_CODECS = new Set(['PCM', 'FLAC', 'ALAC']);
export const isLosslessCodec = (codec) => LOSSLESS_CODECS.has(codec);

function splitGenres(list) {
  const out = [];
  for (const g of list || []) {
    for (const part of String(g).split(/\s*[;,]\s*/)) {
      const t = part.trim();
      if (t && !out.includes(t)) out.push(t);
    }
  }
  return out;
}

/** Extract audio metadata for the catalogue cache. */
export async function readAudioMeta(abs) {
  const mm = await parseFile(abs, { skipCovers: false, duration: false });
  const { common, format } = mm;
  let embeddedLang = null;
  const emb = embeddedToLines(common.lyrics);
  if (emb && emb.lines.length) embeddedLang = emb.lang || detectLanguage(emb.lines.map((l) => l.text).join('\n'));
  const codec = normalizeCodec(format.codec, abs);
  return {
    title: common.title || null,
    artist: common.artist || (common.artists && common.artists[0]) || null,
    album: common.album || null,
    albumArtist: common.albumartist || null,
    year: common.year || (common.date ? Number(String(common.date).slice(0, 4)) || null : null),
    disc: common.disk?.no ?? null,
    track: common.track?.no ?? null,
    durationMs: format.duration ? Math.round(format.duration * 1000) : null,
    codec,
    lossless: format.lossless ?? isLosslessCodec(codec),
    bitDepth: format.bitsPerSample || null,
    sampleRate: format.sampleRate || null,
    genres: splitGenres(common.genre),
    hasPicture: !!(common.picture && common.picture.length),
    hasEmbeddedLyrics: !!(emb && emb.lines.length),
    embeddedLang,
  };
}

/** Extract video metadata: ffprobe when available, else music-metadata (MP4/Matroska only). */
export async function readVideoMeta(abs) {
  if (await hasExecutable('ffprobe')) {
    try {
      const out = await run('ffprobe', ['-v', 'error', '-print_format', 'json', '-show_format', '-show_streams', '-show_chapters', abs], { timeoutMs: 60000 });
      return fromFfprobe(JSON.parse(out));
    } catch {
      /* fall through */
    }
  }
  try {
    const mm = await parseFile(abs, { skipCovers: true, duration: false, includeChapters: true });
    const { common, format } = mm;
    const v = (format.trackInfo || []).find((t) => t.video && (t.video.pixelWidth || t.video.displayWidth));
    return {
      title: common.title || null,
      artist: common.artist || null,
      album: common.album || null,
      year: common.year || null,
      genres: splitGenres(common.genre),
      durationMs: format.duration ? Math.round(format.duration * 1000) : null,
      width: v?.video?.pixelWidth || v?.video?.displayWidth || null,
      height: v?.video?.pixelHeight || v?.video?.displayHeight || null,
      hdr: false,
      chapters: (format.chapters || []).map((c, i) => ({
        title: c.title || `Chapter ${i + 1}`,
        startMs: c.start != null && c.timeScale ? Math.round((c.start / c.timeScale) * 1000) : c.sampleOffset != null && format.sampleRate ? Math.round((c.sampleOffset / format.sampleRate) * 1000) : 0,
      })),
    };
  } catch {
    return { title: null, artist: null, album: null, year: null, genres: [], durationMs: null, width: null, height: null, hdr: false, chapters: [] };
  }
}

export function fromFfprobe(j) {
  const tags = {};
  for (const [k, v] of Object.entries(j.format?.tags || {})) tags[k.toLowerCase()] = v;
  const vs = (j.streams || []).find((s) => s.codec_type === 'video' && !(s.disposition && s.disposition.attached_pic));
  const hdr = !!vs && (['smpte2084', 'arib-std-b67'].includes(vs.color_transfer) || (vs.side_data_list || []).some((d) => /DOVI|Dolby Vision/i.test(d.side_data_type || '')));
  const dur = Number(j.format?.duration) || Number(vs?.duration) || 0;
  return {
    title: tags.title || null,
    artist: tags.artist || tags.album_artist || null,
    album: tags.album || null,
    year: Number(String(tags.date || tags.year || '').slice(0, 4)) || null,
    genres: splitGenres(tags.genre ? [tags.genre] : []),
    durationMs: dur ? Math.round(dur * 1000) : null,
    width: vs?.width || null,
    height: vs?.height || null,
    hdr,
    chapters: (j.chapters || []).map((c, i) => ({ title: c.tags?.title || `Chapter ${i + 1}`, startMs: Math.round(Number(c.start_time || 0) * 1000) })),
  };
}

/** Embedded front cover (Buffer + mime) or null. */
export async function readEmbeddedCover(abs) {
  try {
    const mm = await parseFile(abs, { skipCovers: false, duration: false });
    const pics = mm.common.picture || [];
    const pic = pics.find((p) => /front/i.test(p.type || '')) || pics[0];
    if (!pic) return null;
    return { data: Buffer.from(pic.data), mime: pic.format && pic.format.includes('/') ? pic.format : `image/${pic.format || 'jpeg'}` };
  } catch {
    return null;
  }
}

export async function readEmbeddedLyrics(abs) {
  try {
    const mm = await parseFile(abs, { skipCovers: true, duration: false });
    return mm.common.lyrics || null;
  } catch {
    return null;
  }
}

// ---- sidecars ---------------------------------------------------------------------------------

function imageNamed(listing, stems) {
  for (const stem of stems) {
    for (const ext of IMAGE_EXTS) {
      const hit = listing.get(`${stem}${ext}`.toLowerCase());
      if (hit) return hit;
    }
  }
  return null;
}

/**
 * Fallback album cover: Windows Media Player's AlbumArt_{GUID}_Large.jpg, or any image whose name suggests a cover,
 * or the only image in the folder. Folders with many unrelated images are ignored.
 */
function anyCover(listing) {
  const images = [...listing.values()].filter((n) => IMAGE_EXTS.includes(path.extname(n).toLowerCase()));
  if (!images.length) return null;
  const pick = (re) => images.find((n) => re.test(n));
  return pick(/^albumart_.*_large\./i) || pick(/cover|front|folder|album/i) || pick(/^albumart/i) || (images.length === 1 ? images[0] : null);
}

const LANG_NAMES = (() => {
  try { return new Intl.DisplayNames(['en'], { type: 'language' }); } catch { return null; }
})();
const LANG3 = { eng: 'en', spa: 'es', por: 'pt', fre: 'fr', fra: 'fr', ger: 'de', deu: 'de', ita: 'it', jpn: 'ja', kor: 'ko', chi: 'zh', zho: 'zh', rus: 'ru', dut: 'nl', nld: 'nl', swe: 'sv', nor: 'no', dan: 'da', fin: 'fi', pol: 'pl', tur: 'tr', ara: 'ar', hin: 'hi' };
const NAME2 = { english: 'en', spanish: 'es', portuguese: 'pt', french: 'fr', german: 'de', italian: 'it', japanese: 'ja', korean: 'ko', chinese: 'zh', russian: 'ru', dutch: 'nl' };

function subtitleInfo(tokens) {
  let language = null;
  const flags = [];
  for (const raw of tokens) {
    const t = raw.toLowerCase();
    if (['forced', 'sdh', 'cc', 'hi'].includes(t)) { flags.push(t === 'hi' ? 'SDH' : t === 'forced' ? 'Forced' : t.toUpperCase()); continue; }
    if (!language) {
      if (/^[a-z]{2}(-[a-z]{2})?$/i.test(t)) language = t.split('-')[0];
      else if (LANG3[t]) language = LANG3[t];
      else if (NAME2[t]) language = NAME2[t];
    }
  }
  let label = language ? (LANG_NAMES?.of(language) || language) : 'Unknown';
  if (flags.length) label += ` (${flags.join(', ')})`;
  return { language: language || 'und', label };
}

/**
 * Discover sidecar files next to a media file.
 * @param {string} fileName media file name
 * @param {Map<string,string>} listing lower-case name → real name for the media file's directory
 * @param {'audio'|'musicvideo'|'movie'|'episode'} role
 * @param {boolean} ownFolder whether the file lives in its own folder (not the library root)
 */
export function findSidecars(fileName, listing, role, ownFolder) {
  const base = path.basename(fileName, path.extname(fileName));
  const lb = base.toLowerCase();
  const out = { art: {}, subs: [], lrc: null, enLrc: null };
  if (role === 'audio') {
    out.lrc = listing.get(`${lb}.lrc`) || null;
    out.enLrc = listing.get(`${lb}.en.lrc`) || null;
    out.art.cover = imageNamed(listing, [base, 'cover', 'folder', 'front', 'album', 'albumart', 'albumartlarge']) || anyCover(listing);
    return out;
  }
  if (role === 'musicvideo') {
    out.art.thumb = imageNamed(listing, [base, `${base}-thumb`, `${base}-poster`]);
  } else if (role === 'movie') {
    const generic = ownFolder ? ['poster', 'folder', 'cover', 'movie'] : [];
    out.art.poster = imageNamed(listing, [`${base}-poster`, base, ...generic]);
    out.art.backdrop = imageNamed(listing, [`${base}-fanart`, `${base}-backdrop`, ...(ownFolder ? ['fanart', 'backdrop', 'background', 'art'] : [])]);
  } else if (role === 'episode') {
    out.art.thumb = imageNamed(listing, [`${base}-thumb`, base]);
  }
  // subtitles: <base>.srt, <base>.en.srt, <base>.eng.forced.srt, <base>.English.vtt …
  const subs = [];
  for (const [lower, real] of listing) {
    if (!(lower.endsWith('.srt') || lower.endsWith('.vtt'))) continue;
    if (!(lower === `${lb}.srt` || lower === `${lb}.vtt` || lower.startsWith(`${lb}.`))) continue;
    const mid = real.slice(base.length, real.length - 4).split('.').filter(Boolean);
    subs.push({ file: real, ...subtitleInfo(mid) });
  }
  subs.sort((a, b) => a.file.localeCompare(b.file));
  out.subs = subs.map((s, i) => ({ id: `s${i}`, ...s }));
  return out;
}

export function showArt(listing) {
  return {
    poster: imageNamed(listing, ['poster', 'folder', 'cover', 'show']),
    backdrop: imageNamed(listing, ['fanart', 'backdrop', 'background', 'art']),
  };
}

/** Language of an .lrc sidecar ([la:] tag, else heuristic). */
export async function lrcLanguage(abs) {
  try {
    const p = parseLrc(await fsp.readFile(abs, 'utf8'));
    if (!p.lines.length) return null;
    return p.lang || detectLanguage(p.lines.map((l) => l.text).join('\n'));
  } catch {
    return null;
  }
}
