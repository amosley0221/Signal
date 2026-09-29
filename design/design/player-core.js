// Shared data + state machine + fictional-artist suggester for all three player variants.
const art = (h1, h2, l = 0.42) => `linear-gradient(135deg, oklch(${l} 0.14 ${h1}), oklch(${l - 0.18} 0.12 ${h2})), repeating-linear-gradient(90deg, rgba(255,255,255,.05) 0 2px, transparent 2px 9px)`;
const glow = (h1, h2) => `linear-gradient(160deg, oklch(0.38 0.12 ${h1}) 0%, oklch(0.2 0.08 ${h2}) 60%, oklch(0.12 0.03 ${h2}) 100%)`;

const L = (s) => s.map(([t, text]) => ({ t, text }));
const LYR_CHROME = L([[0, 'Headlights spill across the flooded lane'], [9, 'You count the seconds between thunder and my name'], [18, 'Every window in this city is a mirror'], [27, 'And every mirror wants to keep you here'], [37, 'Chrome hearts don\u2019t break, they only lose their shine'], [46, 'Chrome hearts don\u2019t break, they just stop keeping time'], [56, 'I left the engine running in the rain'], [65, 'So the radio could finish your refrain'], [76, 'Neon on the water, violet on the glass'], [85, 'We were built to outlast, not to last'], [96, 'Chrome hearts don\u2019t break, they only lose their shine'], [105, 'Chrome hearts don\u2019t break, they just stop keeping time'], [118, 'Hold the wheel a little longer'], [127, 'Let the static make us stronger'], [140, 'Chrome hearts don\u2019t break'], [152, 'They just stop keeping time']]);
const LYR_HARBOR = L([[0, 'The ferry\u2019s late again tonight'], [8, 'I\u2019m folding paper boats out of your letters'], [17, 'The harbor lights come on in pairs'], [26, 'Like someone up there knows we\u2019re better together'], [38, 'Row me home, row me home'], [46, 'Past the buoys and the breakwater stone'], [55, 'Row me home, row me home'], [63, 'I can\u2019t hear the sea when I\u2019m alone'], [76, 'Salt on the rope and salt on my sleeve'], [85, 'Every knot you tied, I never learned to leave'], [98, 'Row me home, row me home'], [107, 'Past the buoys and the breakwater stone']]);
const LYR_TRANSIT = L([[0, 'Last train out of the amber district'], [9, 'Windows full of people I\u2019ll never meet'], [19, 'Every stop is a name you used to whisper'], [29, 'Every tunnel takes a little of the heat'], [41, 'Midnight transit, carry me sideways'], [50, 'Past the towers dressed in violet rain'], [60, 'Midnight transit, I don\u2019t need a highway'], [70, 'I just need to feel the ground again'], [84, 'Doors close soft like the end of a sentence'], [94, 'The map above me glows a route I know'], [106, 'Midnight transit, carry me sideways'], [116, 'Past the towers dressed in violet rain']]);
const LYR_BLOOM = L([[0, 'Turn the dial until the static blooms'], [10, 'There\u2019s a garden in the noise between the rooms'], [21, 'I could stay here in the hiss and the hum'], [31, 'Where the signal never says where it\u2019s from'], [44, 'Static bloom, static bloom'], [53, 'Something growing in the empty room'], [63, 'Static bloom, static bloom'], [72, 'Petals made of frequencies too soon']]);
const LYR_COMPASS = L([[0, 'I drew you a compass out of a receipt'], [9, 'North was the kitchen, south was the street'], [18, 'Paper compass, folded twice'], [27, 'Points to nowhere, points to nice'], [38, 'We got lost on purpose most of the time'], [47, 'Following the creases like a dotted line'], [57, 'Paper compass in my coat'], [66, 'Only map I ever wrote']]);
const LYR_TIDE = L([[0, 'Every morning the tide brings back a line'], [10, 'Something you wrote in salt, something of mine'], [21, 'I read them off the rocks before they dry'], [31, 'Tide letters, signed with foam, sealed with sky'], [45, 'Come back slow, come back low'], [55, 'Bring the words the water knows'], [66, 'Come back slow, come back low'], [76, 'I\u2019ll be reading on the shore']]);
const LYR_LOWSUN = L([[0, 'Low sun over wires, humming gold'], [12, 'Starlings on the line like notes untold'], [24, 'You said the evening has a frequency'], [36, 'Tuned to whatever we were meant to be'], [50, 'Hold the light, hold the light'], [62, 'Before the poles turn into night'], [74, 'Hold the light, hold the light'], [86, 'Copper singing out of sight']]);
const LYR_COPPER = L([[0, 'Sleep now in the copper glow'], [11, 'The kettle hums the only song I know'], [22, 'Pennies on the windowsill'], [33, 'Counting down the dark until'], [46, 'Copper lullaby, copper lullaby'], [57, 'Warm as wire, soft as sky'], [68, 'Copper lullaby, close your eyes'], [79, 'Morning\u2019s just a little oxidized']]);
const LYR_GHOST = L([[0, 'One two three, the dial is turning'], [8, 'One two three, the tubes are burning'], [16, 'A voice from nineteen-thirty-two'], [24, 'Asks the room to dance with you'], [34, 'Ghost radio, play me a waltz'], [42, 'Every note has a lovely fault'], [50, 'Ghost radio, keep me in time'], [58, 'Three-four forever, yours and mine'], [70, 'One two three, the station\u2019s fading'], [78, 'One two three, but we\u2019re still swaying']]);
const LYR_DUNE = L([[0, 'Heat shimmer on the blacktop, nothing on the band'], [10, 'Just the crackle of the desert talking to my hand'], [20, 'Dune static, dune static'], [28, 'Every mile is automatic'], [38, 'Rearview full of a town that never learned my name'], [48, 'Dashboard saint is nodding, says it\u2019s all the same'], [58, 'Dune static, dune static'], [66, 'Radio\u2019s dead but I\u2019m ecstatic']]);
const LT = (s) => s.map(([t, text, tr]) => ({ t, text, tr }));
const LYR_FARO = LT([[0, 'Baja la marea y el faro se apaga', 'The tide goes out and the lighthouse goes dark'], [10, 'Cuento los barcos que nunca volvieron', 'I count the ships that never came back'], [21, 'Tu nombre en la sal, tu voz en la niebla', 'Your name in the salt, your voice in the fog'], [32, 'Y yo esperando lo que el mar no me dio', 'And me waiting for what the sea never gave me'], [46, 'Faro de sal, enci\u00e9ndete otra vez', 'Lighthouse of salt, light up once more'], [56, 'Que la noche es larga y no s\u00e9 remar', 'For the night is long and I don\u2019t know how to row'], [67, 'Faro de sal, enci\u00e9ndete otra vez', 'Lighthouse of salt, light up once more'], [77, 'Si no vuelves t\u00fa, que vuelva la luz', 'If you won\u2019t return, let the light return'], [92, 'Guardo las cartas en botellas vac\u00edas', 'I keep the letters in empty bottles'], [103, 'Y se las devuelvo a la misma marea', 'And give them back to the same tide']]);
const LYR_GLASS = L([[0, 'Under the glass orchard the light bends slow'], [11, 'Fruit made of morning nobody will grow'], [22, 'I planted your voice where the silence was thin'], [33, 'Now every branch hums with the room you were in'], [46, 'Stay in the pale, stay in the pale'], [56, 'Where the sun can\u2019t find us and the colors fail'], [68, 'Stay in the pale, stay in the pale'], [78, 'Glass doesn\u2019t bruise, it just tells the tale'], [92, 'Under the glass orchard I\u2019m learning to wait'], [104, 'For a season that never arrives on a date']]);

export const DATA = {
  albums: [
    { id: 'neon', title: 'Neon Cathedral', artist: 'Velvet Static', year: 2026, tags: ['synthwave', 'night drive', 'male vocal'], bg: art(300, 340), glow: glow(300, 340), accent: 'oklch(0.78 0.16 320)' },
    { id: 'salt', title: 'Salt & Signal', artist: 'Marlow Vane', year: 2026, tags: ['indie folk', 'acoustic', 'harbor'], bg: art(50, 25, 0.5), glow: glow(50, 25), accent: 'oklch(0.82 0.13 60)' },
    { id: 'sess09', title: 'Untitled Session 09', artist: null, year: 2026, tags: ['dream pop', 'slow', 'female vocal'], bg: art(200, 260, 0.46), glow: glow(200, 260), accent: 'oklch(0.8 0.1 220)' },
    { id: 'tempest', title: 'Tempest Protocol', artist: 'Ninefold Array', year: 2025, tags: ['industrial', 'drum and bass', 'instrumental'], bg: art(160, 190, 0.36), glow: glow(160, 190), accent: 'oklch(0.8 0.14 170)' },
    { id: 'singles', title: 'Singles', artist: null, year: 2026, tags: ['mixed'], bg: art(20, 350, 0.38), glow: glow(20, 350), accent: 'oklch(0.78 0.15 20)' },
  ],
  tracks: [
    { id: 't1', album: 'neon', n: 1, title: 'Chrome Hearts Don\u2019t Break', artist: 'Velvet Static', dur: 228, q: 'hires', bits: '24-bit / 96 kHz', lyrics: LYR_CHROME, tags: ['synthwave', 'night drive'] },
    { id: 't2', album: 'neon', n: 2, title: 'Midnight Transit', artist: 'Velvet Static', dur: 251, q: 'hires', bits: '24-bit / 96 kHz', lyrics: LYR_TRANSIT, tags: ['synthwave'] },
    { id: 't3', album: 'neon', n: 3, title: 'Static Bloom', artist: 'Velvet Static', dur: 197, q: 'lossless', bits: '16-bit / 44.1 kHz', lyrics: LYR_BLOOM, tags: ['synthwave', 'slow'] },
    { id: 't4', album: 'salt', n: 1, title: 'Harbor Lights', artist: 'Marlow Vane', dur: 214, q: 'lossless', bits: '16-bit / 44.1 kHz', ext: 'FLAC', lyrics: LYR_HARBOR, tags: ['indie folk'], src: 'pc' },
    { id: 't5', album: 'salt', n: 2, title: 'Paper Compass', artist: 'Marlow Vane', dur: 188, q: 'lossless', bits: '16-bit / 44.1 kHz', ext: 'FLAC', lyrics: LYR_COMPASS, tags: ['indie folk', 'acoustic'], src: 'pc' },
    { id: 't6', album: 'salt', n: 3, title: 'Tide Letters', artist: 'Marlow Vane', dur: 262, q: 'hires', bits: '24-bit / 48 kHz', lyrics: LYR_TIDE, tags: ['indie folk'], src: 'pc' },
    { id: 't7', album: 'sess09', n: 1, title: 'Glass Orchard', artist: null, dur: 243, q: 'hires', bits: '24-bit / 96 kHz', lyrics: LYR_GLASS, tags: ['dream pop', 'slow', 'female vocal'] },
    { id: 't8', album: 'sess09', n: 2, title: 'Low Sun Over Wires', artist: null, dur: 275, q: 'hires', bits: '24-bit / 96 kHz', lyrics: LYR_LOWSUN, tags: ['dream pop', 'ambient'] },
    { id: 't9', album: 'sess09', n: 3, title: 'Copper Lullaby', artist: null, dur: 201, q: 'lossless', bits: '16-bit / 44.1 kHz', lyrics: LYR_COPPER, tags: ['dream pop', 'slow'] },
    { id: 't10', album: 'tempest', n: 1, title: 'Failsafe', artist: 'Ninefold Array', dur: 312, q: 'lossy', bits: '256 kbps AAC', ext: 'M4A', instrumental: true, tags: ['industrial', 'drum and bass'], src: 'pc' },
    { id: 't11', album: 'tempest', n: 2, title: 'Ballistic Dawn', artist: 'Ninefold Array', dur: 287, q: 'lossy', bits: '320 kbps MP3', ext: 'MP3', instrumental: true, tags: ['drum and bass'], src: 'pc' },
    { id: 't12', album: 'singles', n: 1, title: 'Ghost Radio Waltz', artist: null, dur: 176, q: 'lossless', bits: '16-bit / 44.1 kHz', lyrics: LYR_GHOST, tags: ['dark cabaret', 'waltz'] },
    { id: 't13', album: 'singles', n: 2, title: 'Dune Static', artist: null, dur: 233, q: 'hires', bits: '24-bit / 48 kHz', lyrics: LYR_DUNE, tags: ['desert rock', 'male vocal'] },
    { id: 't14', album: 'singles', n: 3, title: 'Faro de Sal', artist: null, dur: 212, q: 'hires', bits: '24-bit / 96 kHz', lyrics: LYR_FARO, lang: 'es', langLabel: 'Spanish', translatedOn: 'STUDIO-PC', tags: ['bolero', 'slow', 'female vocal'] },
  ],
  newFiles: [
    { id: 'n1', file: 'suno_2026-09-27_glass_river.wav', title: 'Glass River', tags: ['dream pop', 'slow', 'female vocal'], q: 'hires', bits: '24-bit / 96 kHz', dur: 219 },
    { id: 'n2', file: 'suno_2026-09-28_ash_parade.wav', title: 'Ash Parade', tags: ['dark cabaret', 'brass'], q: 'lossless', bits: '16-bit / 44.1 kHz', dur: 191 },
    { id: 'n3', file: 'suno_2026-09-28_sodium_sky.wav', title: 'Sodium Sky', tags: ['synthwave', 'night drive'], q: 'hires', bits: '24-bit / 96 kHz', dur: 244 },
  ],
  videos: [
    { id: 'v1', title: 'Neon Cathedral \u2014 Visualizer', album: 'neon', sub: '4K \u00b7 HDR \u00b7 3:48', dur: 228, bg: glow(300, 340), chapters: [[0, 'Intro'], [40, 'Verse'], [96, 'Chorus'], [150, 'Bridge'], [200, 'Outro']] },
    { id: 'v2', title: 'Live at the Warehouse (Set 2)', album: 'tempest', sub: '1080p \u00b7 52:10', dur: 3130, bg: glow(160, 190), src: 'pc', chapters: [[0, 'Failsafe'], [312, 'Ballistic Dawn'], [600, 'Interlude'], [780, 'Ninefold'], [1400, 'Encore']] },
    { id: 'v3', title: 'Studio Session \u2014 Harbor Lights', album: 'salt', sub: '1080p \u00b7 6:02', dur: 362, bg: glow(50, 25), src: 'pc', chapters: [[0, 'Setup'], [60, 'Take 1'], [200, 'Take 2']] },
  ],
  outputs: [
    { id: 'phone', name: 'This phone', kind: 'Galaxy Z Fold 8', proto: '', maxQ: '24-bit / 96 kHz' },
    { id: 'kitchen', name: 'Kitchen', kind: 'Sonos Era 300', proto: 'Cast', maxQ: '24-bit / 48 kHz', room: true },
    { id: 'living', name: 'Living Room', kind: 'Sonos Arc + Sub', proto: 'Cast', maxQ: '24-bit / 48 kHz', room: true },
    { id: 'office', name: 'Office', kind: 'Sonos One SL', proto: 'Cast', maxQ: '24-bit / 48 kHz', room: true },
    { id: 'tv', name: 'Living Room TV', kind: 'Chromecast with Google TV', proto: 'Cast', maxQ: '4K HDR', video: true },
  ],
  activity: [
    { id: 'a1', kind: 'download', title: 'Orbital Drift \u00b7 S1E4 Ballast', sub: 'D:\\Video\\TV \u00b7 2.1 GB', pct: 0.62, state: 'active' },
    { id: 'a2', kind: 'download', title: 'Salt & Signal \u00b7 Tide Letters', sub: 'Original 24/48 WAV \u00b7 84 MB', pct: 0, state: 'queued' },
    { id: 'a3', kind: 'download', title: 'The Long Quiet', sub: 'Movies \u00b7 4.6 GB \u00b7 waiting for Wi-Fi', pct: 0, state: 'waiting' },
    { id: 'a4', kind: 'upload', title: 'Ghost Radio Waltz.wav \u2192 D:\\Music\\Suno', sub: 'Phone-only file \u00b7 34 MB', pct: 1, state: 'done' },
    { id: 'a5', kind: 'tag', title: 'Write tags \u00b7 3 files', sub: 'ARTIST=Velvet Static \u00b7 written on STUDIO-PC', pct: 1, state: 'done' },
    { id: 'a6', kind: 'download', title: 'Live at the Warehouse (Set 2)', sub: 'Checksum mismatch after 3 tries', pct: 0.4, state: 'failed' },
    { id: 'a7', kind: 'conflict', title: 'Glass Orchard \u00b7 ARTIST tag', sub: 'Phone: \u201cPale Meridian\u201d \u00b7 PC: \u201cIlse Vane\u201d', pct: 0, state: 'conflict' },
    { id: 'a8', kind: 'watched', title: 'Watched state \u2192 Plex', sub: 'S1E3 Perigee marked watched \u00b7 synced 2 min ago', pct: 1, state: 'done' },
  ],
  server: { name: 'STUDIO-PC', addr: '192.168.1.20 \u00b7 Always on', lastSync: 'Synced 2 min ago', libs: [
    { id: 'l1', name: 'Suno Music', path: 'D:\\Music\\Suno', count: '146 files \u00b7 9.8 GB', type: 'Music' },
    { id: 'l2', name: 'Music Videos', path: 'D:\\Video\\Music Videos', count: '12 files \u00b7 18 GB', type: 'Videos' },
    { id: 'l3', name: 'TV Shows', path: 'D:\\Video\\TV', count: '48 episodes \u00b7 96 GB', type: 'TV' },
    { id: 'l4', name: 'Movies', path: 'D:\\Video\\Movies', count: '31 films \u00b7 210 GB', type: 'Movies' } ] },
  movies: [
    { id: 'm1', title: 'The Long Quiet', meta: '2025 \u00b7 2h 04m \u00b7 Sci-fi', src: 'pc', dur: 7440, bg: art(250, 290, 0.38), glow: glow(250, 290), w: 0.35, rating: '7.9', cert: 'PG-13', synopsis: 'A salvage crew wakes from cold sleep to find their station has drifted a decade off course and the only voice on the radio is their own, recorded years ago.', cast: ['Ines Halloran', 'Teo Marsh', 'Ada Okonkwo'], director: 'Rafael Ostrom' },
    { id: 'm2', title: 'Salt Roads', meta: '2024 \u00b7 1h 48m \u00b7 Drama', src: 'pc', dur: 6480, bg: art(45, 20, 0.46), glow: glow(45, 20), w: 0, rating: '7.2', cert: 'R', synopsis: 'Two estranged sisters drive the old salt-haul route to scatter their father\u2019s ashes and discover he kept a second family at the coast.', cast: ['Marlow Vane', 'Rosa Reyes'], director: 'Junie Beck' },
    { id: 'm3', title: 'Ninefold', meta: '2026 \u00b7 1h 36m \u00b7 Thriller', src: 'pc', dur: 5760, bg: art(170, 200, 0.36), glow: glow(170, 200), w: 1, rating: '6.8', cert: 'R', synopsis: 'A data-center night technician notices the same nine-second gap in every security log and pulls a thread that leads to the building\u2019s owners.', cast: ['Kai Voss', 'Sable Theta'], director: 'Unit Zero' },
    { id: 'm4', title: 'Paper Compass', meta: '2023 \u00b7 1h 52m \u00b7 Romance', src: 'pc', dur: 6720, bg: art(330, 10, 0.44), glow: glow(330, 10), w: 0, rating: '7.5', cert: 'PG', synopsis: 'A cartographer who has never left her city and a courier who has never stayed in one draw each other a map that neither can follow alone.', cast: ['June Harrow', 'Elias Whitlock'], director: 'Tamsin Beck' },
    { id: 'm5', title: 'Glass Orchard', meta: '2026 \u00b7 1h 41m \u00b7 Mystery', dur: 6060, bg: art(205, 240, 0.42), glow: glow(205, 240), w: 0, rating: '8.1', cert: 'PG-13', synopsis: 'After a greenhouse fire, a botanist finds the plants that survived have been arranged to spell something, and the only person who could have done it died three years earlier.', cast: ['Ilse Kessler', 'Wren Ashby'], director: 'Marin Rook' },
    { id: 'm6', title: 'Orbital', meta: '2022 \u00b7 2h 15m \u00b7 Documentary', src: 'pc', dur: 8100, bg: art(120, 150, 0.4), glow: glow(120, 150), w: 0.7, rating: '8.4', cert: 'TV-G', synopsis: 'Eighteen months aboard a low-earth research station, filmed entirely by its crew.', cast: ['Crew of Station Meridian'], director: 'Dana Cross' },
  ],
  shows: [
    { id: 's1', title: 'Orbital Drift', meta: '3 seasons \u00b7 24 episodes \u00b7 Sci-fi', src: 'pc', bg: art(230, 270, 0.4), glow: glow(230, 270), rating: '8.6', cert: 'TV-14', synopsis: 'The crew of a decommissioned relay station refuse the order to come home and become the last people between Earth and whatever is answering their pings.', cast: ['Ines Halloran', 'Teo Marsh', 'Kai Voss', 'Ada Okonkwo'], seasons: [
      { n: 1, eps: [['Low Orbit', 48, 1, 'The station receives a shutdown order and a second message no one sent.'], ['The Long Quiet', 51, 1, 'Forty hours of radio silence force the crew to ration power and trust.'], ['Perigee', 47, 1, 'A close pass reveals debris that is not debris.'], ['Ballast', 52, 0.4, 'Marsh dumps the water reserve to change the orbit; Halloran finds out too late.'], ['Ground Truth', 55, 0, 'Mission control finally answers, and its story does not match the telemetry.'], ['Re-entry', 58, 0, 'One seat on the capsule. Season finale.']] },
      { n: 2, eps: [['Aphelion', 50, 0, 'Six months later, a supply ship arrives with a crew that knows too much.'], ['Signal Loss', 49, 0, 'The pings stop. The crew decide whether that is good news.'], ['Habitat', 53, 0, 'Okonkwo builds a greenhouse from cargo netting and salvaged LEDs.'], ['The Tether', 47, 0, 'A spacewalk to repair the antenna becomes a hostage situation.']] },
      { n: 3, eps: [['Dark Side', 54, 0, 'The station loses line-of-sight with Earth for the first time.'], ['Escape Velocity', 61, 0, 'Series finale.']] } ] },
    { id: 's2', title: 'The Cartographers', meta: '2 seasons \u00b7 16 episodes \u00b7 Drama', src: 'pc', bg: art(40, 80, 0.44), glow: glow(40, 80), rating: '7.8', cert: 'TV-MA', synopsis: 'A nineteenth-century survey team mapping a disputed coastline realise their employer wants the map wrong.', cast: ['June Harrow', 'Elias Whitlock', 'Rosa Reyes'], seasons: [
      { n: 1, eps: [['Terra Incognita', 44, 1, 'The team lands at Blank Coast with orders and no supplies.'], ['Meridian', 43, 1, 'A dispute over the prime meridian splits the camp.'], ['Longitude Zero', 45, 0.7, 'Whitlock\u2019s chronometer is sabotaged.'], ['Sea of Names', 46, 0, 'The team must choose which villages appear on the map.']] },
      { n: 2, eps: [['Blank Coast', 44, 0, 'One year later, the map is published and the coast is already changing.'], ['The Surveyor', 47, 0, 'A rival surveyor arrives with a better instrument and a worse plan.'], ['Compass Rose', 45, 0, 'Season finale.']] } ] },
    { id: 's3', title: 'Slow Kitchen', meta: '1 season \u00b7 8 episodes \u00b7 Documentary', src: 'pc', bg: art(110, 140, 0.44), glow: glow(110, 140), rating: '8.0', cert: 'TV-G', synopsis: 'One dish per episode, made the slow way in kitchens around the world.', cast: [], seasons: [
      { n: 1, eps: [['Bread', 28, 1, 'A 72-hour sourdough in a Lisbon bakery.'], ['Broth', 27, 0, 'Bone broth simmered for two days in Hokkaido.'], ['Ferment', 30, 0, 'Kimchi buried for a winter in Gangwon.']] } ] },
  ],
  playlists: [
    { id: 'p1', title: 'Late Drive', tracks: ['t1', 't2', 't13', 't3'], bg: art(280, 320, 0.4), glow: glow(280, 320) },
    { id: 'p2', title: 'Suno Favourites', tracks: ['t4', 't7', 't1', 't12', 't10'], bg: art(60, 100, 0.48), glow: glow(60, 100) },
    { id: 'p3', title: 'Needs an artist', tracks: ['t7', 't8', 't9', 't12', 't13'], bg: art(10, 40, 0.4), glow: glow(10, 40), smart: true },
  ],
};

export const fmt = (s) => { s = Math.max(0, Math.round(s)); const m = Math.floor(s / 60), r = s % 60; return `${m}:${r < 10 ? '0' : ''}${r}`; };
export const fmtLong = (s) => s >= 3600 ? `${Math.floor(s / 3600)}:${fmt(s % 3600).padStart(5, '0')}` : fmt(s);
export const QLABEL = { hires: 'Hi-Res Lossless', lossless: 'Lossless', lossy: 'Lossy' };

// ---- Fictional artist name suggester ---------------------------------------------------
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
const STOP = new Set(['the', 'a', 'an', 'of', 'over', 'don\u2019t', 'and', 'in', 'on', 'my', 'your', 'at', 'to', 'for']);
const hash = (s) => { let h = 7; for (const c of s) h = (h * 31 + c.charCodeAt(0)) >>> 0; return h; };

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
  if (existing) out.push({ name: existing[0], why: `Existing artist \u00b7 ${existing[1]} song${existing[1] > 1 ? 's' : ''} share tag \u201c${tags.find((g) => allTracks.some((t) => t.artist === existing[0] && t.tags.includes(g)))}\u201d`, kind: 'existing' });
  const promptWords = stylePrompt.split(/\s+/).map((w) => w.replace(/[^a-zA-Z]/g, '')).filter((w) => w.length > 3 && !STOP.has(w.toLowerCase()));
  if (promptWords.length) { const pw = promptWords[h % promptWords.length]; out.push({ name: `${pw[0].toUpperCase() + pw.slice(1).toLowerCase()} ${pick(bank.noun, 5)}`, why: `From your style prompt \u00b7 \u201c${pw}\u201d`, kind: 'prompt' }); }
  out.push({ name: `${pick(bank.adj, 0)} ${pick(bank.noun, 1)}`, why: `Mood \u00b7 ${tags.slice(0, 2).join(', ') || 'untagged'}`, kind: 'mood' });
  out.push({ name: `${titleWord} ${pick(bank.noun, 2)}`, why: `Title word \u00b7 \u201c${titleWord}\u201d`, kind: 'title' });
  if (lyricLine) { const lw = lyricLine.split(/\s+/).filter((w) => w.length > 4 && !STOP.has(w.toLowerCase())); const w = lw.length ? lw[(h >>> 6) % lw.length] : titleWord; out.push({ name: `The ${w[0].toUpperCase() + w.slice(1).replace(/[^a-zA-Z]/g, '')}s`, why: `Lyric \u00b7 \u201c${lyricLine}\u201d`, kind: 'lyric' }); }
  out.push({ name: `${pick(bank.first, 3)} ${pick(bank.last, 4)}`, why: `Solo-artist style \u00b7 ${track.tags.includes('female vocal') ? 'female vocal' : track.tags.includes('male vocal') ? 'male vocal' : 'fits the album grouping'}`, kind: 'person' });
  const seen = new Set();
  return out.filter((s) => (seen.has(s.name) ? false : seen.add(s.name))).slice(0, 5);
}

// ---- State machine -----------------------------------------------------------------------
export function initState() {
  return { section: 'music', tab: 'recent', screen: 'library', detail: null, season: null, np: 't1', playing: false, pos: 34, sheet: null, lyrics: false, editId: null, chosen: {}, stylePrompt: '', videoId: null, landscape: false, ui: true, speed: 1, subs: true, vpos: 62, batchIdx: {}, imported: false, importIdx: {}, toast: null,
    downloads: { t4: 1, 's1-1-1': 1, 's1-1-2': 1, 's1-1-3': 1 }, offline: false, wifiOnly: true, libMode: { l1: 'Download new', l2: 'Stream', l3: 'Stream', l4: 'Stream' }, dlQuality: 'Original', lyricMode: 'both', translateTo: 'English', output: 'phone', group: [],
    query: '', queue: ['t2', 't3', 't4'], history: [], shuffle: false, userPlaylists: [], newPlaylistName: '', paired: true, pairStep: 0, pairLibs: { l1: true, l2: true, l3: true, l4: false }, remote: 'Tailscale', pcReachable: true, activity: {}, conflictPick: {}, autoRemove: true, keepEps: 3 };
}
const LIB_MODES = ['Stream', 'Download new', 'Download all'];

export function vals(state, set, opts = {}) {
  const S = state;
  // outputs (Cast / Sonos)
  const out = DATA.outputs.find((o) => o.id === S.output) || DATA.outputs[0];
  const castLabel = S.output === 'phone' ? '' : S.group.length ? `${out.name} + ${S.group.length} more` : out.name;
  const casting = S.output !== 'phone';
  const outputs = DATA.outputs.map((o) => ({ ...o, active: o.id === S.output, inactive: o.id !== S.output, grouped: S.group.includes(o.id), ungrouped: !S.group.includes(o.id), canGroup: !!o.room && o.id !== S.output && casting && out.room, sub: o.proto ? `${o.kind} \u00b7 ${o.proto} \u00b7 up to ${o.maxQ}` : `${o.kind} \u00b7 ${o.maxQ}`, pick: () => set({ output: o.id, group: [], toast: o.id === 'phone' ? 'Playing on this phone' : `Playing on ${o.name}` }), toggleGroup: () => set({ group: S.group.includes(o.id) ? S.group.filter((g) => g !== o.id) : [...S.group, o.id], toast: S.group.includes(o.id) ? `${o.name} left the group` : `${o.name} grouped` }) }));
  const castNote = casting ? (out.video ? 'Video and audio on the TV \u00b7 phone is the remote' : `Sonos streams directly from ${DATA.server.name} \u00b7 96 kHz files play at 48 kHz`) : 'Cast to Sonos or a TV \u00b7 audio streams from STUDIO-PC, not the phone';
  const names = { ...S.chosen };
  const tracks = DATA.tracks.map((t) => ({ ...t, artist: names[t.id] || t.artist }));
  const byId = Object.fromEntries(tracks.map((t) => [t.id, t]));
  const albumOf = (t) => DATA.albums.find((a) => a.id === t.album);
  const dlState = (key, src) => { const d = S.downloads[key]; return { onPc: src === 'pc', downloaded: src !== 'pc' || d === 1, isDownloaded: src === 'pc' && d === 1, needsDownload: src === 'pc' && d !== 1 && typeof d !== 'number', downloading: typeof d === 'number' && d < 1, dlPct: typeof d === 'number' ? Math.round(d * 100) : 0, dlStyle: { width: `${typeof d === 'number' ? d * 100 : 0}%` }, unavailable: S.offline && src === 'pc' && d !== 1, srcLabel: casting ? `Playing on ${castLabel}` : src !== 'pc' ? 'On device' : d === 1 ? 'Downloaded' : typeof d === 'number' ? `Downloading ${Math.round(d * 100)}%` : `Stream from ${DATA.server.name}`, download: () => set({ sheet: S.sheet === 'actions' ? null : S.sheet, downloads: { ...S.downloads, [key]: S.downloads[key] === 1 ? undefined : 0.02 }, toast: S.downloads[key] === 1 ? 'Removed download' : 'Downloading\u2026' }) }; };
  const shuffleArr = (arr) => { const a = [...arr]; for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
  const playFrom = (list, startId, shuffle) => { let ids = list.map((t) => t.id); if (shuffle) { ids = shuffleArr(ids); if (startId) ids = [startId, ...ids.filter((x) => x !== startId)]; } else if (startId) { const k = ids.indexOf(startId); ids = ids.slice(k); } if (!ids.length) return; set({ np: ids[0], queue: ids.slice(1), pos: 0, playing: true, shuffle: !!shuffle, sheet: S.sheet === 'np' ? 'np' : null }); };
  const decorate = (t, i, list) => ({
    ...t, ...dlState(t.id, t.src), ext: t.ext || 'WAV', artistLabel: t.artist || 'Unknown artist', untagged: !t.artist, tagged: !!t.artist, qLabel: QLABEL[t.q], hires: t.q === 'hires', durLabel: fmt(t.dur), bg: albumOf(t).bg, albumTitle: albumOf(t).title,
    isNow: S.np === t.id, notNow: S.np !== t.id, play: () => { if (S.offline && t.src === 'pc' && S.downloads[t.id] !== 1) { set({ toast: 'Not downloaded \u2014 you\u2019re offline' }); return; } playFrom(list || tracks, t.id, false); }, playNext: () => set({ sheet: S.sheet === 'actions' ? null : S.sheet, queue: [t.id, ...S.queue.filter((x) => x !== t.id)], toast: `Playing next \u00b7 ${t.title}` }), playLater: () => set({ sheet: S.sheet === 'actions' ? null : S.sheet, queue: [...S.queue.filter((x) => x !== t.id), t.id], toast: `Added to queue \u00b7 ${t.title}` }), openArtist: (e) => { if (e && e.stopPropagation) e.stopPropagation(); if (t.artist) set({ screen: 'artist', detail: t.artist, section: 'music', sheet: null }); }, removeFromQueue: () => set({ queue: S.queue.filter((x) => x !== t.id) }), addToPlaylist: () => set({ sheet: 'addto', editId: t.id }), actions: () => set({ sheet: 'actions', editId: t.id }), edit: () => set({ sheet: 'edit', editId: t.id, stylePrompt: '' }), idx: i + 1,
  });
  const songs = tracks.map(decorate);
  const np = S.np ? decorate(byId[S.np], 0) : null;
  const npAlbum = np ? albumOf(np) : null;
  const artistsMap = {};
  tracks.forEach((t) => { const k = t.artist || 'Unknown artist'; (artistsMap[k] = artistsMap[k] || { name: k, count: 0, bg: albumOf(t).bg, untagged: !t.artist }).count++; });
  const artists = Object.values(artistsMap).sort((a, b) => (a.untagged ? 1 : 0) - (b.untagged ? 1 : 0)).map((a) => ({ ...a, sub: `${a.count} song${a.count > 1 ? 's' : ''}`, open: () => set({ screen: 'artist', detail: a.name }) }));
  const albums = DATA.albums.map((a) => { const ts = tracks.filter((t) => t.album === a.id); const artist = ts.find((t) => t.artist)?.artist; return { ...a, artistLabel: artist || 'Unknown artist', untagged: !artist, count: ts.length, hires: ts.some((t) => t.q === 'hires'), open: () => set({ screen: 'album', detail: a.id }) }; });
  const videos = DATA.videos.map((v) => ({ ...v, ...dlState(v.id, v.src), open: () => set({ screen: 'video', videoId: v.id, ui: true }) }));
  const shows = DATA.shows.map((s) => ({ ...s, ...dlState(s.id, s.src), open: () => set({ screen: 'show', detail: s.id, season: 1 }) }));
  const movies = DATA.movies.map((m) => ({ ...m, ...dlState(m.id, m.src), pct: Math.round(m.w * 100), pctStyle: { width: `${m.w * 100}%` }, inProgress: m.w > 0 && m.w < 1, watched: m.w === 1, castLabel: (m.cast || []).join(', '), open: () => set({ screen: 'movie', detail: m.id }) }));
  let movie = null;
  if (S.screen === 'movie') { const m = movies.find((x) => x.id === S.detail); movie = { ...m, play: () => set({ screen: 'video', videoId: m.id, ui: true, vpos: Math.round(m.w * m.dur) }), playLabel: m.w > 0 && m.w < 1 ? `Resume \u00b7 ${fmtLong(m.dur - m.w * m.dur)} left` : m.w === 1 ? 'Watch again' : 'Play', metaSource: m.src === 'pc' ? 'Matched by Plex \u00b7 TMDB' : 'Matched on device \u00b7 TMDB', fixMatch: () => set({ toast: 'Fix match: search TMDB for a different title' }) }; }
  const recent = [...songs].reverse().slice(0, 6);
  const playlists = [...S.userPlaylists.map((p) => ({ ...p, bg: DATA.playlists[1].bg, glow: DATA.playlists[1].glow })), ...DATA.playlists].map((p) => ({ ...p, count: p.tracks.length, sub: `${p.tracks.length} songs${p.smart ? ' \u00b7 Smart' : ''}`, open: () => set({ screen: 'playlist', detail: p.id }) }));
  const untagged = songs.filter((t) => t.untagged);
  const q = (S.query || '').trim().toLowerCase();
  const hit = (...xs) => xs.some((x) => (x || '').toLowerCase().includes(q));
  const eps = DATA.shows.flatMap((s) => s.seasons.flatMap((se) => se.eps.map(([title, min, w, summary], i) => ({ id: `${s.id}-${se.n}-${i + 1}`, title, sub: `${s.title} \u00b7 S${se.n}E${i + 1} \u00b7 ${min} min`, bg: s.glow, show: s, summary, open: () => set({ screen: 'video', videoId: 'tv', detail: s.id, season: se.n, ui: true, tvTitle: `${s.title} \u00b7 S${se.n}E${i + 1} \u00b7 ${title}`, sheet: null }) }))));
  const search = q ? {
    songs: songs.filter((t) => hit(t.title, t.artist, albumOf(t).title, ...(t.lyrics || []).map((l) => l.text))).slice(0, 6),
    albums: albums.filter((a) => hit(a.title, a.artistLabel)).slice(0, 4),
    artists: artists.filter((a) => hit(a.name)).slice(0, 4),
    movies: movies.filter((m) => hit(m.title, m.synopsis, ...(m.cast || []), m.director)).slice(0, 4),
    shows: shows.filter((s) => hit(s.title, s.synopsis, ...(s.cast || []))).slice(0, 4),
    episodes: eps.filter((e) => hit(e.title, e.summary)).slice(0, 4),
    videos: videos.filter((v) => hit(v.title)).slice(0, 3),
    playlists: playlists.filter((p) => hit(p.title)).slice(0, 3),
  } : null;
  const lyricHits = q ? songs.filter((t) => !hit(t.title, t.artist) && (t.lyrics || []).some((l) => l.text.toLowerCase().includes(q))).map((t) => ({ ...t, line: (t.lyrics.find((l) => l.text.toLowerCase().includes(q)) || {}).text })).slice(0, 3) : [];
  const searchCount = search ? Object.values(search).reduce((n, a) => n + a.length, 0) : 0;
  const recentSearches = ['velvet static', 'orbital drift', 'lossless', 'harbor'].map((s) => ({ s, pick: () => set({ query: s }) }));

  // detail screens
  let detail = null;
  const byTrackNo = (a, b) => (a.disc || 1) - (b.disc || 1) || a.n - b.n || a.title.localeCompare(b.title);
  const albumVideos = (albumId) => videos.filter((v) => v.album === albumId).map((v) => ({ ...v, durLabel: fmtLong(v.dur) }));
  if (S.screen === 'album') { const a = albums.find((x) => x.id === S.detail); const list = songs.filter((t) => t.album === a.id).sort(byTrackNo); const vids = albumVideos(a.id); detail = { ...a, kind: 'Album', sub: `${a.artistLabel} \u00b7 ${a.year}`, list, videos: vids, hasVideos: vids.length > 0, videoCount: `${vids.length} music video${vids.length > 1 ? 's' : ''}`, total: fmt(list.reduce((n, t) => n + t.dur, 0)), countLabel: `${list.length} songs${vids.length ? ` \u00b7 ${vids.length} video${vids.length > 1 ? 's' : ''}` : ''}` }; }
  if (S.screen === 'playlist') { const p = playlists.find((x) => x.id === S.detail); const list = p.tracks.map((id) => songs.find((t) => t.id === id)).filter(Boolean); detail = { ...p, kind: 'Playlist', sub: p.sub, list, total: fmt(list.reduce((n, t) => n + t.dur, 0)), artistLabel: p.smart ? 'Auto-updated' : 'You' }; }
  if (S.screen === 'artist') { const list = songs.filter((t) => t.artistLabel === S.detail); detail = { title: S.detail, kind: 'Artist', sub: `${list.length} songs`, bg: list[0].bg, glow: albumOf(list[0]).glow, list, total: fmt(list.reduce((n, t) => n + t.dur, 0)), artistLabel: '' , untagged: S.detail === 'Unknown artist' }; }
  if (detail) { detail.videos = detail.videos || []; detail.hasVideos = !!detail.hasVideos; detail.countLabel = detail.countLabel || `${detail.list.length} songs`; detail.playAll = () => playFrom(detail.list, null, false); detail.shuffleAll = () => playFrom(detail.list, null, true); detail.hasArtistLink = detail.kind !== 'Artist' && !!detail.artistLabel && detail.artistLabel !== 'Unknown artist' && detail.artistLabel !== 'You' && detail.artistLabel !== 'Auto-updated'; detail.noArtistLink = !detail.hasArtistLink; detail.openArtist = () => set({ screen: 'artist', detail: detail.artistLabel }); detail.downloadedAll = detail.list.every((t) => t.downloaded); detail.downloadAll = () => set({ downloads: { ...S.downloads, ...Object.fromEntries(detail.list.filter((t) => !t.downloaded).map((t) => [t.id, 0.02])) }, toast: `Downloading ${detail.title}` }); }
  let show = null;
  if (S.screen === 'show') { const s = DATA.shows.find((x) => x.id === S.detail); const sea = s.seasons.find((x) => x.n === S.season) || s.seasons[0]; show = { ...s, seasons: s.seasons.map((x) => ({ n: x.n, label: `Season ${x.n}`, count: `${x.eps.length} ep`, active: x.n === sea.n, inactive: x.n !== sea.n, open: () => set({ season: x.n }) })), season: sea.n, eps: sea.eps.map(([title, min, w, summary], i) => ({ title, summary: summary || '', n: i + 1, label: `E${i + 1}`, min: `${min} min`, pct: Math.round(w * 100), pctStyle: { width: `${w * 100}%` }, watched: w === 1, notWatched: w !== 1, inProgress: w > 0 && w < 1, ...dlState(`${s.id}-${sea.n}-${i + 1}`, s.src), open: () => set({ screen: 'video', videoId: 'tv', ui: true, tvTitle: `${s.title} \u00b7 S${sea.n}E${i + 1} \u00b7 ${title}` }) })) }; show.srcLabel = s.src === 'pc' ? `${DATA.server.name} \u00b7 D:\\Video\\TV` : 'On device'; show.castLabel = (s.cast || []).join(', '); show.hasCast = !!(s.cast && s.cast.length); show.metaSource = 'Matched by Plex \u00b7 TVDB'; show.fixMatch = () => set({ toast: 'Fix match: search TVDB for a different title' }); const next = show.eps.find((e) => !e.watched); show.nextUp = next ? `Continue \u00b7 ${next.label} ${next.title}` : 'Rewatch season'; show.nextOpen = (next || show.eps[0]).open; show.downloadSeason = () => set({ downloads: { ...S.downloads, ...Object.fromEntries(show.eps.filter((e) => !e.downloaded).map((e) => [`${s.id}-${sea.n}-${e.n}`, 0.02])) }, toast: `Downloading Season ${sea.n}` }); }

  // video
  let video = null;
  if (S.screen === 'video') {
    const v = DATA.videos.find((x) => x.id === S.videoId) || (DATA.movies.find((x) => x.id === S.videoId) ? { ...DATA.movies.find((x) => x.id === S.videoId), sub: DATA.movies.find((x) => x.id === S.videoId).meta, bg: DATA.movies.find((x) => x.id === S.videoId).glow, chapters: [[0, 'Opening'], [900, 'Act 1'], [3000, 'Act 2'], [5400, 'Act 3'], [6900, 'Credits']] } : { title: S.tvTitle, sub: '1080p \u00b7 Subtitles', dur: 2880, bg: DATA.shows.find((x) => x.id === S.detail)?.glow, chapters: [[0, 'Cold open'], [300, 'Act 1'], [1200, 'Act 2'], [2400, 'Act 3']] });
    const pct = S.vpos / v.dur;
    const chI = v.chapters.reduce((k, c, i) => (c[0] <= S.vpos ? i : k), 0);
    video = { ...v, pct: pct * 100, pctStyle: { width: `${pct * 100}%` }, posLabel: fmtLong(S.vpos), durLabel: fmtLong(v.dur), remaining: `-${fmtLong(v.dur - S.vpos)}`, chapter: v.chapters[chI][1], chapters: v.chapters.map((c, i) => ({ name: c[1], t: fmtLong(c[0]), active: i === chI, inactive: i !== chI, seek: () => set({ vpos: c[0] }), pos: `${(c[0] / v.dur) * 100}%` })), speedLabel: `${S.speed}\u00d7`, subLabel: S.subs ? 'CC on' : 'CC off', srcLabel: v.src === 'pc' && S.downloads[v.id] !== 1 ? `Direct play \u00b7 ${DATA.server.name}` : 'Playing from device' };
  }

  // lyrics
  const lines = np?.lyrics || null;
  const isForeign = !!np?.lang;
  const lyricMode = isForeign ? S.lyricMode : 'orig';
  const curIdx = lines ? lines.reduce((k, l, i) => (l.t <= S.pos ? i : k), -1) : -1;
  const lyricLines = lines ? lines.map((l, i) => ({ text: lyricMode === 'trans' ? (l.tr || l.text) : l.text, tr: l.tr || '', showTr: lyricMode === 'both' && !!l.tr, active: i === curIdx, past: i < curIdx, style: { opacity: i === curIdx ? 1 : i < curIdx ? 0.28 : 0.42, transform: i === curIdx ? 'scale(1)' : 'scale(0.97)', transformOrigin: 'left center' }, seek: () => set({ pos: l.t, playing: true }) })) : [];
  const LMODES = [['orig', (np?.lang || 'es').toUpperCase()], ['both', `${(np?.lang || 'es').toUpperCase()} + EN`], ['trans', 'EN']];
  const lyricModes = LMODES.map(([id, label]) => ({ id, label, active: lyricMode === id, inactive: lyricMode !== id, pick: () => set({ lyricMode: id }) }));
  const translationNote = isForeign ? `${np.langLabel} \u00b7 translated on ${np.translatedOn}` : '';
  const lineH = opts.lineH || 64;
  const lyricsScroll = { transform: `translateY(${-(Math.max(curIdx, 0)) * lineH}px)`, transition: 'transform .6s cubic-bezier(.2,.8,.2,1)' };
  const currentLine = curIdx >= 0 ? lines[curIdx].text : (lines ? '\u2026' : 'No synced lyrics for this song');
  const noLyricsMsg = np?.instrumental ? 'Instrumental \u00b7 no lyrics' : `No .lrc found on ${DATA.server.name} \u00b7 Suno lyrics will sync on next scan`;

  const seekFrom = (e, dur, key) => { const r = e.currentTarget.getBoundingClientRect(); const p = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)); set({ [key]: p * dur }); };
  const queue = S.queue.map((id) => songs.find((t) => t.id === id)).filter(Boolean).map((t, i) => ({ ...t, qn: String(i + 1).padStart(2, '0'), playFromQueue: () => set({ np: t.id, queue: S.queue.slice(S.queue.indexOf(t.id) + 1), pos: 0, playing: true }) }));
  const queueEmpty = queue.length === 0;
  const userPlaylists = S.userPlaylists;
  const editTrack = S.editId ? songs.find((t) => t.id === S.editId) : null;
  const suggestions = editTrack ? suggestArtists(editTrack, tracks, S.stylePrompt).map((s) => ({ ...s, pick: () => set({ chosen: { ...S.chosen, [editTrack.id]: s.name }, toast: `Artist set to ${s.name}` }), chosen: S.chosen[editTrack.id] === s.name, notChosen: S.chosen[editTrack.id] !== s.name, isExisting: s.kind === 'existing' })) : [];
  const batch = untagged.map((t) => { const sg = suggestArtists(t, tracks, ''); const i = (S.batchIdx[t.id] || 0) % sg.length; return { ...t, pickName: sg[i].name, why: sg[i].why, cycle: () => set({ batchIdx: { ...S.batchIdx, [t.id]: i + 1 } }), _name: sg[i].name }; });
  const importRows = DATA.newFiles.map((f) => { const sg = suggestArtists({ ...f, tags: f.tags, lyrics: null }, tracks, ''); const i = (S.importIdx[f.id] || 0) % sg.length; return { ...f, qLabel: QLABEL[f.q], hires: f.q === 'hires', durLabel: fmt(f.dur), pickName: sg[i].name, why: sg[i].why, alts: sg.filter((_, k) => k !== i).slice(0, 3).map((s, k) => ({ name: s.name, pick: () => set({ importIdx: { ...S.importIdx, [f.id]: sg.indexOf(s) } }) })), tagLabel: f.tags.join(' \u00b7 ') }; });

  const downloadedCount = songs.filter((t) => t.onPc && t.downloaded).length;
  const sync = { ...DATA.server, offline: S.offline, online: !S.offline, reachable: S.pcReachable, unreachable: !S.pcReachable, remote: S.remote, statusLabel: !S.pcReachable ? 'STUDIO-PC unreachable \u00b7 last synced 41 min ago' : S.offline ? 'Offline \u00b7 showing downloads only' : DATA.server.lastSync, libs: DATA.server.libs.map((l) => ({ ...l, mode: S.libMode[l.id], cycle: () => set({ libMode: { ...S.libMode, [l.id]: LIB_MODES[(LIB_MODES.indexOf(S.libMode[l.id]) + 1) % 3] } }) })), storage: `${(3.1 + downloadedCount * 0.06).toFixed(1)} GB downloaded \u00b7 51 GB free`, storageStyle: { width: `${20 + downloadedCount * 2}%` }, wifiOnly: S.wifiOnly, wifiAny: !S.wifiOnly, toggleWifi: () => set({ wifiOnly: !S.wifiOnly }), toggleOffline: () => set({ offline: !S.offline, toast: S.offline ? 'Back online \u00b7 streaming from STUDIO-PC' : 'Offline mode \u00b7 downloads only' }), dlQuality: S.dlQuality, cycleQuality: () => set({ dlQuality: S.dlQuality === 'Original' ? 'Lossless 16/44' : 'Original' }), translateTo: S.translateTo, cycleTranslate: () => set({ translateTo: S.translateTo === 'English' ? 'Off' : 'English' }) };

  const isMusic = S.section === 'music', isMovies = S.section === 'movies', isTv = S.section === 'tv';
  // pairing + sync activity
  const PAIR_STEPS = ['Find your PC', 'Confirm code', 'Choose libraries', 'Away from home', 'Done'];
  const pairing = { step: S.pairStep, steps: PAIR_STEPS.map((label, i) => ({ label, n: i + 1, active: i === S.pairStep, done: i < S.pairStep, upcoming: i > S.pairStep })), canBack: S.pairStep > 0 && S.pairStep < 4, canNext: S.pairStep > 0 && S.pairStep < 4, isFind: S.pairStep === 0, isCode: S.pairStep === 1, isLibs: S.pairStep === 2, isRemote: S.pairStep === 3, isDone: S.pairStep === 4,
    found: [{ name: 'STUDIO-PC', sub: '192.168.1.20 \u00b7 Signal Agent 1.0 \u00b7 Plex detected' }], code: '482 · 913', libs: DATA.server.libs.map((l) => ({ ...l, on: !!S.pairLibs[l.id], off: !S.pairLibs[l.id], toggle: () => set({ pairLibs: { ...S.pairLibs, [l.id]: !S.pairLibs[l.id] } }) })),
    remotes: [['Tailscale', 'Recommended \u00b7 free \u00b7 works on mobile data'], ['Home only', 'Stream on home Wi-Fi, downloads elsewhere'], ['Port forward', 'Advanced \u00b7 needs router setup + HTTPS']].map(([id, sub]) => ({ id, sub, active: S.remote === id, inactive: S.remote !== id, pick: () => set({ remote: id }) })),
    next: () => set({ pairStep: Math.min(4, S.pairStep + 1) }), backStep: () => set({ pairStep: Math.max(0, S.pairStep - 1) }), finish: () => set({ paired: true, screen: 'sync', toast: 'Paired with STUDIO-PC \u00b7 first scan running' }), restart: () => set({ paired: false, pairStep: 0, screen: 'pair' }) };
  const actRows = DATA.activity.map((a) => { const st = S.activity[a.id] || a.state; const pick = S.conflictPick[a.id]; return { ...a, state: st, pctStyle: { width: `${Math.round(a.pct * 100)}%` }, pctLabel: `${Math.round(a.pct * 100)}%`, isActive: st === 'active', isQueued: st === 'queued', isWaiting: st === 'waiting', isDone: st === 'done', isFailed: st === 'failed', isConflict: st === 'conflict' && !pick, isResolved: st === 'conflict' && !!pick, resolvedLabel: pick ? `Kept ${pick}` : '', isUpload: a.kind === 'upload', isTag: a.kind === 'tag', isWatched: a.kind === 'watched', isDl: a.kind === 'download', stateLabel: { active: 'Downloading', queued: 'Queued', waiting: 'Waiting for Wi-Fi', done: 'Done', failed: 'Failed', conflict: 'Needs a decision' }[st], retry: () => set({ activity: { ...S.activity, [a.id]: 'active' }, toast: 'Retrying download' }), skip: () => set({ activity: { ...S.activity, [a.id]: 'done' } }), keepPhone: () => set({ conflictPick: { ...S.conflictPick, [a.id]: 'phone version' }, toast: 'Kept phone tag \u00b7 writing to PC' }), keepPc: () => set({ conflictPick: { ...S.conflictPick, [a.id]: 'PC version' }, toast: 'Kept PC tag' }) }; });
  const activity = { rows: actRows, pending: actRows.filter((r) => ['active', 'queued', 'waiting'].includes(r.state)).length, failed: actRows.filter((r) => r.state === 'failed' || r.isConflict).length, summary: `${actRows.filter((r) => ['active', 'queued', 'waiting'].includes(r.state)).length} pending \u00b7 ${actRows.filter((r) => r.state === 'failed' || r.isConflict).length} need attention`, reachable: S.pcReachable, unreachable: !S.pcReachable, toggleReach: () => set({ pcReachable: !S.pcReachable, toast: S.pcReachable ? 'STUDIO-PC unreachable \u00b7 downloads still play' : 'STUDIO-PC back online' }), autoRemove: S.autoRemove, autoRemoveOff: !S.autoRemove, toggleAutoRemove: () => set({ autoRemove: !S.autoRemove }), keepEps: `Keep next ${S.keepEps} episodes`, cycleKeep: () => set({ keepEps: S.keepEps === 3 ? 5 : S.keepEps === 5 ? 1 : 3 }), storageWarn: 'Downloads will pause at 5 GB free' };
  const SECTIONS = [['music', 'Music'], ['movies', 'Movies'], ['tv', 'TV Shows']];
  return {
    isMusic, isMovies, isTv, sectionTitle: SECTIONS.find((s) => s[0] === S.section)[1], movies, recent, movie, isMovie: !!movie,
    search, isSearch: S.screen === 'search', openSearch: () => set({ screen: 'search', sheet: null }), query: S.query, setQuery: (e) => set({ query: e.target.value }), clearQuery: () => set({ query: '' }), hasQuery: !!q, noQuery: !q, searchCount, searchEmpty: !!q && searchCount === 0, searchSummary: `${searchCount} result${searchCount === 1 ? '' : 's'} for \u201c${S.query}\u201d`, lyricHits, hasLyricHits: lyricHits.length > 0, recentSearches,
    hasSongHits: !!search && search.songs.length > 0, hasAlbumHits: !!search && search.albums.length > 0, hasArtistHits: !!search && search.artists.length > 0, hasMovieHits: !!search && search.movies.length > 0, hasShowHits: !!search && search.shows.length > 0, hasEpisodeHits: !!search && search.episodes.length > 0, hasVideoHits: !!search && search.videos.length > 0, hasPlaylistHits: !!search && search.playlists.length > 0,
    pairing, activity, isPair: S.screen === 'pair', isActivity: S.screen === 'activity', openActivity: () => set({ screen: 'activity' }), openPair: () => set({ screen: 'pair', pairStep: 0 }), backToSync: () => set({ screen: 'sync' }),
    casting, notCasting: !casting, castLabel, castNote, outputs, outputName: out.name, hasGroup: S.group.length > 0, canGroupAll: !!out.room && S.group.length < DATA.outputs.filter((o) => o.room).length - 1, stopCasting: () => set({ output: 'phone', group: [], toast: 'Playing on this phone' }), groupCount: S.group.length, ungroupAll: () => set({ group: [], toast: 'Group dissolved' }), groupAll: () => set({ group: DATA.outputs.filter((o) => o.room && o.id !== S.output).map((o) => o.id), toast: 'Playing everywhere' }), groupLabel: S.group.length ? `${out.name} + ${S.group.map((g) => DATA.outputs.find((o) => o.id === g).name).join(', ')}` : out.name, sheetCast: S.sheet === 'cast', openCast: () => set({ sheet: 'cast', castFrom: S.sheet }), closeCast: () => set({ sheet: S.castFrom === 'np' ? 'np' : null }), volume: S.volume ?? 62, volStyle: { width: `${S.volume ?? 62}%` }, setVolume: (e) => { const r = e.currentTarget.getBoundingClientRect(); set({ volume: Math.round(Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)) * 100) }); },
    sections: SECTIONS.map(([id, label]) => ({ id, label, active: S.section === id, inactive: S.section !== id, open: () => set({ section: id, screen: 'library' }) })),
    sync, isSync: S.screen === 'sync', openSync: () => set({ screen: 'sync' }), openLibrary: () => set({ screen: 'library' }), offline: S.offline, serverName: DATA.server.name,
    tab: S.tab, screen: S.screen, isLibrary: S.screen === 'library', isDetail: !!detail, isShow: !!show, isVideo: !!video,
    tabs: ['recent', 'songs', 'albums', 'artists', 'playlists', 'videos'].map((t) => ({ id: t, label: t === 'recent' ? 'Recently Added' : t === 'videos' ? 'Music Videos' : t[0].toUpperCase() + t.slice(1), active: S.tab === t, inactive: S.tab !== t, open: () => set({ tab: t, screen: 'library', section: 'music' }) })),
    tabRecent: isMusic && S.tab === 'recent', tabSongs: isMusic && S.tab === 'songs', tabAlbums: isMusic && S.tab === 'albums', tabArtists: isMusic && S.tab === 'artists', tabVideos: isMusic && S.tab === 'videos', tabTv: isTv, tabPlaylists: isMusic && S.tab === 'playlists',
    songs, albums, artists, videos, shows, playlists, detail, show, video, untaggedCount: untagged.length, hasUntagged: untagged.length > 0,
    songCount: `${songs.length} songs`, newCount: S.imported ? 0 : DATA.newFiles.length, hasNew: !S.imported,
    np, npAlbum, hasNp: !!np, playing: S.playing, posLabel: np ? fmt(S.pos) : '', remLabel: np ? `-${fmt(np.dur - S.pos)}` : '', pct: np ? (S.pos / np.dur) * 100 : 0,
    pctStyle: { width: np ? `${(S.pos / np.dur) * 100}%` : '0%' }, knobStyle: { left: np ? `${(S.pos / np.dur) * 100}%` : '0%' }, miniPct: { width: np ? `${(S.pos / np.dur) * 100}%` : '0%' },
    npBg: { background: npAlbum?.glow }, npArt: { background: npAlbum?.bg }, npAccent: npAlbum?.accent,
    sheetNp: S.sheet === 'np', sheetQueue: S.sheet === 'queue', sheetEdit: S.sheet === 'edit', sheetBatch: S.sheet === 'batch', sheetImport: S.sheet === 'import', anySheet: !!S.sheet, noSheet: !S.sheet,
    showLyrics: S.lyrics, showArt: !S.lyrics, lyricLines, hasLyrics: !!lines, noLyrics: !lines, noLyricsMsg, lyricsScroll, isForeign, lyricModes, translationNote, translateTo: S.translateTo, currentLine, queue, subs: S.subs,
    editTrack, suggestions, stylePrompt: S.stylePrompt, batch, importRows, importCount: `${DATA.newFiles.length} new files`, toast: S.toast, hasToast: !!S.toast,
    vplaying: S.vplaying !== false, toggleVplay: () => set({ vplaying: !(S.vplaying !== false) }),
    landscape: S.landscape, portrait: !S.landscape, devW: S.landscape ? opts.h || 915 : opts.w || 412, devH: S.landscape ? opts.w || 412 : opts.h || 915, uiVisible: S.ui,
    // handlers
    back: () => set({ screen: S.screen === 'video' && S.videoId === 'tv' ? 'show' : S.screen === 'video' && DATA.movies.some((m) => m.id === S.videoId) ? 'movie' : 'library', landscape: false, vplaying: true }),
    openLibrary: () => set({ screen: 'library', section: 'music' }),
    openNp: () => set({ sheet: 'np' }), closeSheet: () => set({ sheet: null, lyrics: false }), openQueue: () => set({ sheet: 'queue' }), backToNp: () => set({ sheet: 'np' }),
    togglePlay: () => set({ playing: !S.playing }), toggleLyrics: () => set({ lyrics: !S.lyrics }),
    next: () => { const q = queue[0]; if (q) set({ np: q.id, queue: S.queue.slice(1), history: [...S.history, S.np], pos: 0, playing: true }); else set({ pos: 0, playing: false }); }, prev: () => { if (S.pos > 3 || !S.history.length) set({ pos: 0 }); else set({ np: S.history[S.history.length - 1], history: S.history.slice(0, -1), queue: [S.np, ...S.queue], pos: 0 }); },
    queueEmpty, shuffleOn: S.shuffle, shuffleOff: !S.shuffle, playAllSongs: () => playFrom(songs, null, false), toggleShuffle: () => set({ shuffle: !S.shuffle, queue: S.shuffle ? S.queue : shuffleArr(S.queue), toast: S.shuffle ? 'Shuffle off' : 'Shuffle on' }), clearQueue: () => set({ queue: [], toast: 'Queue cleared' }),
    shuffleSongs: () => playFrom(songs, null, true), shuffleAlbums: () => playFrom(songs, null, true),
    sheetAddTo: S.sheet === 'addto', sheetActions: S.sheet === 'actions', openEditFromActions: () => set({ sheet: 'edit', stylePrompt: '' }), sheetNewPlaylist: S.sheet === 'newpl', openNewPlaylist: () => set({ sheet: 'newpl', newPlaylistName: '' }), setPlaylistName: (e) => set({ newPlaylistName: e.target.value }), createPlaylist: () => { const name = S.newPlaylistName.trim() || 'New playlist'; const id = 'u' + Date.now(); set({ userPlaylists: [...S.userPlaylists, { id, title: name, tracks: S.editId && S.sheet === 'newpl' ? [S.editId] : [] }], sheet: null, toast: `Created \u201c${name}\u201d` }); }, playlistTargets: S.userPlaylists.map((p) => ({ ...p, sub: `${p.tracks.length} songs`, add: () => set({ userPlaylists: S.userPlaylists.map((x) => x.id === p.id ? { ...x, tracks: x.tracks.includes(S.editId) ? x.tracks : [...x.tracks, S.editId] } : x), sheet: null, toast: `Added to ${p.title}` }) })), hasPlaylistTargets: S.userPlaylists.length > 0, newPlaylistName: S.newPlaylistName,
    seek: (e) => np && seekFrom(e, np.dur, 'pos'), vseek: (e) => video && seekFrom(e, video.dur, 'vpos'),
    toggleLandscape: () => set({ landscape: !S.landscape }), toggleUi: () => set({ ui: !S.ui }), cycleSpeed: () => set({ speed: [1, 1.25, 1.5, 2, 0.75][([1, 1.25, 1.5, 2, 0.75].indexOf(S.speed) + 1) % 5] }), toggleSubs: () => set({ subs: !S.subs }),
    skipBack: () => set({ vpos: Math.max(0, S.vpos - 10) }), skipFwd: () => set({ vpos: Math.min(video ? video.dur : 0, S.vpos + 10) }),
    openEditForNp: () => np && set({ sheet: 'edit', editId: np.id, stylePrompt: '' }), openBatch: () => set({ sheet: 'batch' }), openImport: () => set({ sheet: 'import' }),
    setPrompt: (e) => set({ stylePrompt: e.target.value }),
    batchApply: () => set({ chosen: { ...S.chosen, ...Object.fromEntries(batch.map((b) => [b.id, b._name])) }, sheet: null, toast: `${batch.length} artists assigned` }),
    importApply: () => set({ imported: true, sheet: null, toast: `${DATA.newFiles.length} songs added to library` }),
    dismissToast: () => set({ toast: null }),
  };
}
