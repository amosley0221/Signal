// Filename-based metadata for movies and TV episodes (used when Plex is not configured or has no match).
import path from 'node:path';

const JUNK_RE = /\b(2160p|1080p|720p|480p|4k|uhd|hdr10?\+?|hdr|dv|dolby[ .]?vision|bluray|blu-ray|brrip|bdrip|web[ .-]?dl|webrip|web|hdtv|dvdrip|remux|x264|x265|h\.?264|h\.?265|hevc|avc|aac|ac3|dts|atmos|truehd|10bit|proper|repack|extended|unrated|imax)\b.*$/i;

function tidy(s) {
  let t = String(s || '');
  if (!/\s/.test(t) && /[._]/.test(t)) t = t.replace(/[._]/g, ' ');
  else t = t.replace(/_/g, ' ');
  return t.replace(/\s+/g, ' ').replace(/^[\s\-.–]+|[\s\-.–([]+$/g, '').trim();
}

/**
 * Parse "Title (Year).ext", "Title.Year.1080p.BluRay.mkv", "Title [Year].mkv"; falls back to the parent folder.
 * @returns {{title:string, year:number|null}}
 */
export function parseMovieName(fileName, parentDir = '') {
  const base = path.basename(fileName, path.extname(fileName));
  const tryParse = (s) => {
    // Prefer an explicit "(2017)" / "[2017]"; otherwise the last bare year token ("Blade.Runner.2049.2017.1080p").
    const p = /^(.*?)[\s._-]*[([]((?:19|20)\d{2})[)\]]/.exec(s);
    if (p && p[1].trim()) return { title: tidy(p[1]), year: Number(p[2]) };
    const re = /(?:^|[\s._-])((?:19|20)\d{2})(?=$|[\s._\-[(])/g;
    let best = null;
    let m;
    while ((m = re.exec(s))) {
      const title = s.slice(0, m.index);
      if (title.trim()) best = { title: tidy(title), year: Number(m[1]) };
    }
    return best;
  };
  const r = tryParse(base) || (parentDir ? tryParse(parentDir) : null);
  if (r) return r;
  return { title: tidy(base.replace(JUNK_RE, '')) || base, year: null };
}

/**
 * Parse TV episode names such as "Show - S01E02 - Title.mkv", "Show.S01E02.Title.720p.mkv", "Show 1x02 Title.mkv".
 * `relPath` (library-relative, '/'-separated) is used for "Show (Year)/Season 01/…" folders.
 * @returns {{show:string, showYear:number|null, season:number, episode:number|null, title:string|null, showFolder:string|null}}
 */
export function parseEpisodeName(relPath) {
  const parts = String(relPath).split('/').filter(Boolean);
  const fileName = parts[parts.length - 1] || '';
  const base = path.basename(fileName, path.extname(fileName));
  const dirs = parts.slice(0, -1);

  let season = null;
  let episode = null;
  let showPart = '';
  let titlePart = '';
  let m = /^(.*?)[\s._-]*\bS(\d{1,2})[\s._-]?E(\d{1,3})(?:[\s-]?E?\d{1,3})*(.*)$/i.exec(base);
  if (!m) m = /^(.*?)[\s._-]*\b(\d{1,2})x(\d{2,3})\b(.*)$/i.exec(base);
  if (m) {
    showPart = m[1];
    season = Number(m[2]);
    episode = Number(m[3]);
    titlePart = m[4];
  } else {
    const e = /^(.*?)[\s._-]*\b(?:E|Ep|Episode)[\s._]?(\d{1,3})\b(.*)$/i.exec(base);
    if (e) {
      showPart = e[1];
      episode = Number(e[2]);
      titlePart = e[3];
    } else {
      titlePart = base;
    }
  }

  // Season folder
  const seasonDir = dirs.length ? dirs[dirs.length - 1] : '';
  const sm = /^(?:season|series|staffel|temporada|saison)[\s._-]*(\d{1,3})$/i.exec(seasonDir) || /^s(\d{1,2})$/i.exec(seasonDir);
  if (season == null) {
    if (sm) season = Number(sm[1]);
    else if (/^specials?$/i.test(seasonDir)) season = 0;
    else season = 1;
  }

  const showFolder = dirs.length ? dirs[0] : null;
  let show = null;
  let showYear = null;
  if (showFolder && !(dirs.length === 1 && (sm || /^specials?$/i.test(showFolder)))) {
    const y = /^(.*?)\s*[([]((?:19|20)\d{2})[)\]]\s*$/.exec(showFolder);
    show = tidy(y ? y[1] : showFolder);
    showYear = y ? Number(y[2]) : null;
  }
  if (!show) {
    const y = /^(.*?)[\s._]*[([]?((?:19|20)\d{2})[)\]]?$/.exec(tidy(showPart));
    show = tidy(y && y[1] ? y[1] : showPart) || 'Unknown Show';
    if (y && y[1]) showYear = Number(y[2]);
  }
  let title = tidy(String(titlePart).replace(JUNK_RE, ''));
  if (!title) title = null;
  return { show, showYear, season, episode, title, showFolder };
}
