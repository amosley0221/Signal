// Fictional artist-name suggester — faithful port of suggestArtists() from design/design/player-core.js.

const BANK = {
  bolero: { adj: ['Faro', 'Luna', 'Sal', 'Marea', 'Noche'], noun: ['Azul', 'de Plata', 'del Puerto', 'Amarga', 'Sur'], first: ['Lucía', 'Inés', 'Mar', 'Rocío'], last: ['Serrano', 'Valdés', 'Ferrer', 'del Mar'] },
  'dream pop': { adj: ['Glass', 'Pale', 'Velvet', 'Hollow', 'Lunar', 'Soft'], noun: ['Orchard', 'Meridian', 'Lantern', 'Halo', 'Bloom', 'Weather'], first: ['Ilse', 'Marin', 'Ophelia', 'Wren'], last: ['Vane', 'Kessler', 'Rook', 'Ashby'] },
  synthwave: { adj: ['Neon', 'Chrome', 'Analog', 'Night', 'Sodium', 'Vector'], noun: ['Cathedral', 'Transit', 'Arcade', 'Horizon', 'Signal', 'Motorway'], first: ['Rex', 'Dana', 'Kai', 'Vera'], last: ['Voltage', 'Nakamura', 'Halberd', 'Cross'] },
  'desert rock': { adj: ['Dust', 'Copper', 'Iron', 'Sun', 'Red'], noun: ['Coyote', 'Highway', 'Mirage', 'Saints', 'Canyon', 'Static'], first: ['Cal', 'Rosa', 'Jude', 'Mae'], last: ['Reyes', 'Holloway', 'Buckner', 'Stone'] },
  'dark cabaret': { adj: ['Ghost', 'Velvet', 'Midnight', 'Gilded', 'Crooked'], noun: ['Parlour', 'Waltz', 'Carousel', 'Marionette', 'Radio', 'Orchestra'], first: ['Madame', 'Lucien', 'Odette', 'Balthazar'], last: ['Noir', 'Grimm', 'Valois', 'Pike'] },
  'indie folk': { adj: ['Paper', 'Harbor', 'Cedar', 'Salt', 'Quiet'], noun: ['Compass', 'Lights', 'Letters', 'Fathom', 'Almanac'], first: ['Marlow', 'June', 'Elias', 'Tamsin'], last: ['Vane', 'Harrow', 'Whitlock', 'Beck'] },
  industrial: { adj: ['Ninefold', 'Failsafe', 'Concrete', 'Null', 'Ballistic'], noun: ['Array', 'Protocol', 'Sector', 'Engine', 'Method'], first: ['Unit', 'Axl', 'Sable', 'Kron'], last: ['Zero', 'Voss', 'Mach', 'Theta'] },
};
const DEFAULT_BANK = { adj: ['Hollow', 'Golden', 'Silver', 'Northern', 'Wild'], noun: ['Static', 'Meridian', 'Signal', 'Orchard', 'Company'], first: ['Ada', 'Miles', 'Nova', 'Sol'], last: ['Vane', 'Rook', 'Kessler', 'Ashby'] };
const STOP = new Set(['the', 'a', 'an', 'of', 'over', 'don’t', 'and', 'in', 'on', 'my', 'your', 'at', 'to', 'for']);
export const hash = (s) => { let h = 7; for (const c of s) h = (h * 31 + c.charCodeAt(0)) >>> 0; return h; };

/**
 * @param {{title:string, tags:string[], lyrics?:{t:number,text:string}[]}} track
 * @param {{artist?:string, tags:string[]}[]} allTracks
 * @param {string} [stylePrompt]
 * @returns {{name:string, why:string, kind:string}[]}
 */
export function suggestArtists(track, allTracks, stylePrompt = '') {
  const tags = track.tags || [];
  const bank = BANK[tags.find((t) => BANK[t])] || DEFAULT_BANK;
  const h = hash(track.title);
  const pick = (arr, k) => arr[(h >>> (k * 3)) % arr.length];
  const words = track.title.split(/\s+/).filter((w) => !STOP.has(w.toLowerCase()));
  const titleWord = words[(h >>> 2) % words.length] || 'Echo';
  const lyricLine = track.lyrics && track.lyrics.length ? track.lyrics[(h >>> 4) % track.lyrics.length].text : null;
  const out = [];
  // consistency: existing artist with overlapping tags
  const byArtist = {};
  allTracks.forEach((t) => { if (t.artist && t.tags.some((g) => tags.includes(g))) byArtist[t.artist] = (byArtist[t.artist] || 0) + 1; });
  const existing = Object.entries(byArtist).sort((a, b) => b[1] - a[1])[0];
  if (existing) out.push({ name: existing[0], why: `Existing artist · ${existing[1]} song${existing[1] > 1 ? 's' : ''} share tag “${tags.find((g) => allTracks.some((t) => t.artist === existing[0] && t.tags.includes(g)))}”`, kind: 'existing' });
  const promptWords = stylePrompt.split(/\s+/).map((w) => w.replace(/[^a-zA-Z]/g, '')).filter((w) => w.length > 3 && !STOP.has(w.toLowerCase()));
  if (promptWords.length) { const pw = promptWords[h % promptWords.length]; out.push({ name: `${pw[0].toUpperCase() + pw.slice(1).toLowerCase()} ${pick(bank.noun, 5)}`, why: `From your style prompt · “${pw}”`, kind: 'prompt' }); }
  out.push({ name: `${pick(bank.adj, 0)} ${pick(bank.noun, 1)}`, why: `Mood · ${tags.slice(0, 2).join(', ') || 'untagged'}`, kind: 'mood' });
  out.push({ name: `${titleWord} ${pick(bank.noun, 2)}`, why: `Title word · “${titleWord}”`, kind: 'title' });
  if (lyricLine) { const lw = lyricLine.split(/\s+/).filter((w) => w.length > 4 && !STOP.has(w.toLowerCase())); const w = lw.length ? lw[(h >>> 6) % lw.length] : titleWord; out.push({ name: `The ${w[0].toUpperCase() + w.slice(1).replace(/[^a-zA-Z]/g, '')}s`, why: `Lyric · “${lyricLine}”`, kind: 'lyric' }); }
  out.push({ name: `${pick(bank.first, 3)} ${pick(bank.last, 4)}`, why: `Solo-artist style · ${track.tags.includes('female vocal') ? 'female vocal' : track.tags.includes('male vocal') ? 'male vocal' : 'fits the album grouping'}`, kind: 'person' });
  const seen = new Set();
  return out.filter((s) => (seen.has(s.name) ? false : seen.add(s.name))).slice(0, 5);
}
