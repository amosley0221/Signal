// Localhost-only admin page (served at "/"). Polls /admin/state; actions send X-Signal-Admin: 1.
// Holds the browser-based setup (PC name, library folders with a folder picker, Plex, Start with Windows)
// so nobody has to edit signal-agent.config.json by hand.
// Note: the client script avoids backslashes and ${} so it can live inside this template literal as-is.
export function adminPage() {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Signal Agent</title>
<style>
  :root { --bg:#0e0f12; --panel:#17191e; --line:#2a2d35; --text:#e8e9ec; --dim:#9097a3; --accent:#7cf2c4; --warn:#ffb86b; --bad:#ff6b7a; --field:#0f1115; }
  @media (prefers-color-scheme: light) { :root { --bg:#f5f6f8; --panel:#fff; --line:#dde0e6; --text:#15171c; --dim:#5d6470; --accent:#0c8a60; --warn:#b15c00; --bad:#c62838; --field:#fbfbfc; } }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--text); font:15px/1.45 system-ui, -apple-system, "Segoe UI", sans-serif; }
  main { max-width: 880px; margin: 0 auto; padding: 24px 16px 64px; }
  h1 { font-size: 22px; margin: 0 0 4px; letter-spacing: .01em; }
  h2 { font-size: 12px; text-transform: uppercase; letter-spacing: .12em; color: var(--dim); margin: 28px 0 10px; font-family: ui-monospace, "JetBrains Mono", Consolas, monospace; }
  h3 { font-size: 15px; margin: 18px 0 8px; }
  p { margin: 6px 0; }
  .sub { color: var(--dim); font-family: ui-monospace, Consolas, monospace; font-size: 13px; overflow-wrap:anywhere; }
  .card { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 14px 16px; margin-bottom: 10px; display:flex; align-items:center; gap: 14px; flex-wrap: wrap; }
  .card.block { display:block; }
  .card.hot { border-color: var(--accent); box-shadow: 0 0 0 1px var(--accent) inset; }
  .welcome { border-color: var(--accent); }
  [hidden] { display: none !important; }
  .grow { flex: 1 1 220px; min-width: 0; }
  .code { font-family: ui-monospace, "JetBrains Mono", Consolas, monospace; font-size: 30px; letter-spacing: .18em; color: var(--accent); }
  .addr { font-family: ui-monospace, "JetBrains Mono", Consolas, monospace; font-size: 18px; color: var(--accent); user-select: all; }
  .muted { color: var(--dim); font-size: 13px; overflow-wrap: anywhere; }
  button { font: inherit; border-radius: 8px; border: 1px solid var(--line); background: transparent; color: var(--text); padding: 7px 14px; cursor: pointer; }
  button:disabled { opacity: .5; cursor: default; }
  button.primary { background: var(--accent); color: #07140f; border-color: var(--accent); font-weight: 600; }
  button.danger { color: var(--bad); border-color: var(--bad); }
  button.small { padding: 4px 10px; font-size: 13px; }
  input[type=text], input[type=password], select { font: inherit; color: var(--text); background: var(--field); border: 1px solid var(--line); border-radius: 8px; padding: 7px 10px; min-width: 0; }
  input:focus, select:focus, button:focus-visible { outline: 2px solid var(--accent); outline-offset: 1px; }
  label.field { display:block; font-size: 13px; color: var(--dim); margin: 10px 0 4px; }
  .empty { color: var(--dim); font-size: 14px; padding: 6px 0; }
  .state-done { color: var(--accent); } .state-failed { color: var(--bad); } .state-running { color: var(--warn); }
  .row { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
  .bar { height: 4px; background: var(--line); border-radius: 2px; overflow: hidden; width: 100%; margin-top: 6px; }
  .bar > i { display:block; height:100%; background: var(--accent); }
  .lib { display:grid; grid-template-columns: minmax(110px, 1fr) 150px minmax(180px, 2.4fr) auto auto; gap: 8px; align-items:center; margin-bottom: 8px; }
  .lib .err { grid-column: 1 / -1; color: var(--bad); font-size: 13px; margin-top: -4px; }
  @media (max-width: 680px) { .lib { grid-template-columns: 1fr 1fr; } .lib .path { grid-column: 1 / -1; } }
  .msg { font-size: 14px; margin-top: 10px; }
  .msg.ok { color: var(--accent); } .msg.bad { color: var(--bad); }
  details > summary { cursor: pointer; color: var(--dim); font-size: 14px; margin-top: 14px; }
  .switch { display:flex; gap: 10px; align-items:center; cursor:pointer; }
  .switch input { width: 20px; height: 20px; accent-color: var(--accent); }
  dialog { background: var(--panel); color: var(--text); border: 1px solid var(--line); border-radius: 12px; width: min(640px, calc(100vw - 32px)); padding: 16px; }
  dialog::backdrop { background: rgba(0,0,0,.55); }
  .dirs { max-height: 50vh; overflow:auto; border: 1px solid var(--line); border-radius: 8px; margin: 10px 0; }
  .dirs button { display:block; width:100%; text-align:left; border:0; border-bottom: 1px solid var(--line); border-radius:0; padding: 9px 12px; }
  .dirs button:hover { background: var(--line); }
  .dirs .empty { padding: 10px 12px; }
</style>
</head>
<body>
<main>
  <div class="row"><div class="grow"><h1 id="name">Signal Agent</h1><div class="sub" id="meta">…</div></div>
  <button id="rescan">Rescan libraries</button></div>

  <div id="updateBox" class="card block welcome" hidden>
    <h3 style="margin-top:0" id="updateTitle">Update available</h3>
    <p class="muted" id="updateNotes" style="white-space:pre-wrap;max-height:200px;overflow:auto"></p>
    <button id="updateBtn">Update now</button> <span id="updateMsg" class="msg"></span>
  </div>

  <div id="welcome" class="card block welcome" hidden>
    <h3 style="margin-top:0">Welcome! Let's set up Signal Agent.</h3>
    <p>This program lets the Signal app on your phone play the music and videos stored on this PC.</p>
    <p>Just tell it which folders to share: click <b>Add a folder</b> below, pick the folder, choose what's in it, then click <b>Save</b>. You can change this any time.</p>
  </div>

  <h2>Pair requests</h2>
  <div id="pending"><div class="empty">No phones waiting. In Signal Player, open Sync → Pair with a PC.</div></div>

  <h2>Connect your phone</h2>
  <div class="card block" id="network"><div class="empty">…</div></div>

  <h2 id="setupTitle">Settings</h2>
  <div class="card block">
    <label class="field" for="pcName">PC name (shown on your phone)</label>
    <input type="text" id="pcName" style="width:100%;max-width:360px" maxlength="64">

    <h3>Folders to share</h3>
    <div class="muted" style="margin-bottom:8px">Name · what's in it · folder on this PC</div>
    <div id="libRows"></div>
    <button id="addLib" class="small">+ Add a folder</button>

    <details id="plexBox">
      <summary>Optional: Plex</summary>
      <p class="muted">If you use Plex Media Server, Signal can show Plex's movie and TV details and keep watched status in sync. Leave empty if you don't use Plex.</p>
      <label class="field" for="plexUrl">Plex address</label>
      <input type="text" id="plexUrl" placeholder="http://127.0.0.1:32400" style="width:100%;max-width:360px">
      <p class="muted">Plex on this PC: <code>http://127.0.0.1:32400</code> (used automatically if you leave this empty) · Plex on another PC: <code>http://&lt;its IP&gt;:32400</code></p>
      <label class="field" for="plexToken">Plex token</label>
      <input type="password" id="plexToken" autocomplete="off" style="width:100%;max-width:360px">
      <p class="muted">To find your token: open Plex in a web browser, open any movie, click ⋯ → <b>Get Info</b> → <b>View XML</b>. The address of the page that opens ends with <code>X-Plex-Token=…</code> — copy the part after the <code>=</code>.</p>
    </details>

    <div class="row" style="margin-top:16px"><button id="save" class="primary">Save</button><span class="muted" id="cfgPath"></span></div>
    <div class="msg" id="saveMsg"></div>
  </div>

  <h2>Start with Windows</h2>
  <div class="card block">
    <label class="switch"><input type="checkbox" id="startup"> <span>Start Signal Agent automatically when I sign in to this PC</span></label>
    <div class="muted" id="startupMsg" style="margin-top:6px"></div>
  </div>

  <h2>Paired devices</h2>
  <div id="devices"></div>

  <h2>Libraries</h2>
  <div id="libs"></div>

  <h2>Activity</h2>
  <div id="activity"></div>
</main>

<dialog id="picker">
  <div class="row"><b class="grow">Choose a folder</b><button class="small" id="pickUp">Up</button></div>
  <div class="sub" id="pickPath" style="margin-top:6px"></div>
  <div class="row" id="pickShortcuts" style="margin-top:8px"></div>
  <div class="dirs" id="pickDirs"></div>
  <div class="row"><span class="muted grow" id="pickErr"></span><button id="pickCancel">Cancel</button><button class="primary" id="pickUse">Use this folder</button></div>
</dialog>

<script>
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]));
const ago = (t) => { if (!t) return 'never'; const s = Math.round((Date.now() - t) / 1000); if (s < 60) return s + ' s ago'; if (s < 3600) return Math.round(s/60) + ' min ago'; if (s < 86400) return Math.round(s/3600) + ' h ago'; return new Date(t).toLocaleString(); };
const bytes = (n) => { const u = ['B','KB','MB','GB','TB']; let i = 0; while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; } return n.toFixed(i ? 1 : 0) + ' ' + u[i]; };
const H = { 'X-Signal-Admin': '1' };
const BS = String.fromCharCode(92); // backslash
const baseName = (p) => p.split('/').join(BS).split(BS).filter(Boolean).pop() || p;
async function post(url, body) {
  const r = await fetch(url, { method: 'POST', headers: body ? { ...H, 'Content-Type': 'application/json' } : H, body: body ? JSON.stringify(body) : undefined });
  let j = {}; try { j = await r.json(); } catch {}
  refresh();
  return { status: r.status, ...j };
}
window.approve = (id) => post('/admin/pair/' + id + '/approve');
window.deny = (id) => post('/admin/pair/' + id + '/deny');
let last = null;
window.revoke = (id) => { const d = (last?.devices || []).find((x) => x.id === id); if (confirm('Revoke ' + (d ? d.name : 'this device') + '? The phone will need to pair again.')) post('/admin/devices/' + id + '/revoke'); };
$('rescan').onclick = () => post('/admin/rescan');

// ---- setup form -------------------------------------------------------------------------------
const TYPES = [['music', 'Music'], ['musicvideos', 'Music videos'], ['movies', 'Movies'], ['tv', 'TV shows']];
let setup = null;
let rows = [];
function renderRows(errors) {
  const byRow = {};
  for (const e of errors || []) if (e.row != null) (byRow[e.row] = byRow[e.row] || []).push(e.message);
  $('libRows').innerHTML = rows.length ? rows.map((r, i) =>
    '<div class="lib" data-i="' + i + '">' +
      '<input type="text" data-k="name" placeholder="Name, e.g. My Music" value="' + esc(r.name) + '" aria-label="Name">' +
      '<select data-k="type" aria-label="What is in this folder">' + TYPES.map((t) => '<option value="' + t[0] + '"' + (r.type === t[0] ? ' selected' : '') + '>' + t[1] + '</option>').join('') + '</select>' +
      '<input type="text" class="path" data-k="path" placeholder="Folder, e.g. D:&#92;Music" value="' + esc(r.path) + '" aria-label="Folder">' +
      '<button class="small" data-act="browse">Browse…</button>' +
      '<button class="small danger" data-act="remove" title="Stop sharing this folder">Remove</button>' +
      (byRow[i] ? '<div class="err">' + byRow[i].map(esc).join(' ') + '</div>' : '') +
    '</div>').join('') : '<div class="empty">No folders yet. Click <b>+ Add a folder</b>.</div>';
}
$('libRows').addEventListener('input', (e) => {
  const row = e.target.closest('.lib'); const k = e.target.dataset.k;
  if (row && k) rows[Number(row.dataset.i)][k] = e.target.value;
});
$('libRows').addEventListener('click', (e) => {
  const b = e.target.closest('button'); const row = e.target.closest('.lib');
  if (!b || !row) return;
  const i = Number(row.dataset.i);
  if (b.dataset.act === 'remove') { rows.splice(i, 1); renderRows(); }
  if (b.dataset.act === 'browse') openPicker(rows[i].path, (p) => {
    rows[i].path = p;
    if (!rows[i].name) rows[i].name = baseName(p);
    renderRows();
  });
});
$('addLib').onclick = () => {
  rows.push({ name: '', type: 'music', path: '' });
  renderRows();
  const i = rows.length - 1;
  openPicker('', (p) => { rows[i].path = p; rows[i].name = baseName(p); renderRows(); });
};
async function loadSetup() {
  try { setup = await (await fetch('/admin/setup')).json(); } catch { return; }
  $('pcName').value = setup.name || '';
  rows = (setup.libraries || []).map((l) => ({ ...l }));
  $('plexUrl').value = setup.plex?.url || '';
  $('plexToken').value = setup.plex?.token || '';
  if (setup.plex?.url) $('plexBox').open = true;
  $('cfgPath').textContent = setup.configPath ? 'Saved in ' + setup.configPath : '';
  renderRows();
  renderStartup(setup.startup);
}
$('save').onclick = async () => {
  const btn = $('save'); btn.disabled = true;
  $('saveMsg').className = 'msg'; $('saveMsg').textContent = 'Saving…';
  const r = await post('/admin/setup', { name: $('pcName').value, libraries: rows, plex: { url: $('plexUrl').value, token: $('plexToken').value } });
  btn.disabled = false;
  if (r.ok) {
    rows = r.setup.libraries.map((l) => ({ ...l }));
    renderRows();
    $('saveMsg').className = 'msg ok';
    $('saveMsg').textContent = 'Saved. Scanning your folders now — watch the Activity list below. Next: pair your phone (Signal Player → Settings → PC sync → Pair).'
      + (r.plex ? (r.plex.ok ? ' Connected to Plex · ' + r.plex.libraries + ' libraries.' : ' Plex: ' + r.plex.message) : '');
    if (r.plex && !r.plex.ok) { $('saveMsg').className = 'msg bad'; $('plexBox').open = true; }
    if (r.setup?.plex) { $('plexUrl').value = r.setup.plex.url || ''; }
  } else {
    const errs = r.errors || [];
    renderRows(errs);
    $('saveMsg').className = 'msg bad';
    $('saveMsg').textContent = errs.filter((e) => e.row == null).map((e) => e.message).join(' ') || (errs.length ? 'Please fix the highlighted folders.' : 'Could not save (' + (r.error || r.status) + ').');
    if (errs.some((e) => String(e.field).startsWith('plex'))) $('plexBox').open = true;
  }
};

// ---- folder picker ------------------------------------------------------------------------------
let pick = { path: '', parent: null, done: null };
async function browse(p) {
  $('pickErr').textContent = '';
  $('pickDirs').innerHTML = '<div class="empty">Loading…</div>';
  let j;
  try { j = await (await fetch('/admin/browse?path=' + encodeURIComponent(p || ''), { headers: H })).json(); } catch { $('pickErr').textContent = 'Could not list folders.'; return; }
  pick.path = j.path; pick.parent = j.parent;
  $('pickPath').textContent = j.path || (setup && setup.platform === 'win32' ? 'This PC — pick a drive' : 'Top level');
  $('pickUp').disabled = j.parent == null;
  $('pickUse').disabled = !j.path;
  $('pickErr').textContent = j.error || '';
  $('pickShortcuts').innerHTML = (j.shortcuts || []).map((s) => '<button class="small" data-path="' + esc(s.path) + '">' + esc(s.name) + '</button>').join('') + (setup && setup.platform === 'win32' ? '<button class="small" data-path="">Drives</button>' : '');
  $('pickDirs').innerHTML = j.dirs.length ? j.dirs.map((d) => '<button data-path="' + esc(d.path) + '">📁 ' + esc(d.name) + '</button>').join('') : '<div class="empty">No folders inside. Click <b>Use this folder</b> to pick this one.</div>';
}
function openPicker(start, done) {
  pick.done = done;
  if (typeof $('picker').showModal === 'function') $('picker').showModal(); else $('picker').setAttribute('open', '');
  browse(start || '');
}
const closePicker = () => { if ($('picker').close) $('picker').close(); else $('picker').removeAttribute('open'); };
$('pickDirs').addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) browse(b.dataset.path); });
$('pickShortcuts').addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) browse(b.dataset.path); });
$('pickUp').onclick = () => { if (pick.parent != null) browse(pick.parent); };
$('pickCancel').onclick = closePicker;
$('pickUse').onclick = () => { if (pick.path && pick.done) pick.done(pick.path); closePicker(); };

// ---- start with Windows -------------------------------------------------------------------------
function renderStartup(st) {
  if (!st) return;
  $('startup').checked = !!st.enabled;
  $('startup').disabled = !st.supported;
  $('startupMsg').textContent = !st.supported ? (st.message || 'Only available on Windows.')
    : st.enabled ? ('On — Signal Agent starts in the background when you sign in.' + (st.upToDate === false ? ' (It points to an older copy of the program — turn it off and on again to update it.)' : ''))
    : 'Off — you need to start Signal Agent yourself after restarting the PC.';
}
$('startup').onchange = async () => {
  const r = await post('/admin/startup', { enabled: $('startup').checked });
  renderStartup(r);
};

// ---- live state ---------------------------------------------------------------------------------
function renderNetwork(s) {
  const n = s.network || { lan: s.info.addresses, tailscale: [] };
  const port = s.port;
  let h = '<p>In Signal Player, open <b>Sync → Pair with a PC</b>. This PC is usually found automatically. If not, type this address on your phone:</p>';
  h += n.lan.length ? n.lan.map((a) => '<div class="addr">' + esc(a) + ':' + port + '</div>').join('') + '<div class="muted">At home, on the same Wi-Fi.</div>' : '<div class="muted">No network address found — is this PC connected to Wi-Fi or a network cable?</div>';
  if (n.tailscale.length) h += '<div style="margin-top:10px">' + n.tailscale.map((a) => '<div class="addr">' + esc(a) + ':' + port + '</div>').join('') + '<div class="muted">Tailscale address — works away from home when Tailscale is on, on the phone too.</div></div>';
  else h += '<div class="muted" style="margin-top:8px">Away from home: install Tailscale on this PC and your phone to get an address that works anywhere.</div>';
  $('network').innerHTML = h;
}
async function refresh() {
  let s;
  try { s = await (await fetch('/admin/state')).json(); } catch { $('meta').textContent = 'Signal Agent is not running. Start it again, then reload this page.'; return; }
  last = s;
  $('name').textContent = s.info.name;
  document.title = 'Signal Agent · ' + s.info.name;
  $('welcome').hidden = !s.needsSetup;
  $('setupTitle').textContent = s.needsSetup ? 'Setup' : 'Settings';
  $('meta').textContent = 'v' + s.info.version + ' · port ' + s.port + ' · Plex ' + (s.info.plex ? 'on' : 'off') + ' · last scan ' + ago(s.lastScanAt) + (s.scanning ? ' (scanning…)' : '');
  renderNetwork(s);
  $('pending').innerHTML = s.pending.length ? s.pending.map((p) => '<div class="card hot"><div class="code">' + esc(p.code) + '</div><div class="grow"><b>' + esc(p.deviceName) + '</b> wants to connect<div class="muted">Check the code matches your phone · ' + esc(p.ip || '') + ' · expires in ' + Math.max(0, Math.round((p.expiresAt - Date.now())/1000)) + ' s</div></div><button class="primary" onclick="approve(&quot;' + esc(p.id) + '&quot;)">Approve</button><button onclick="deny(&quot;' + esc(p.id) + '&quot;)">Deny</button></div>').join('') : '<div class="empty">No phones waiting. In Signal Player, open Sync → Pair with a PC.</div>';
  $('devices').innerHTML = s.devices.length ? s.devices.map((d) => '<div class="card"><div class="grow"><b>' + esc(d.name) + '</b><div class="muted">paired ' + new Date(d.createdAt).toLocaleString() + ' · last seen ' + ago(d.lastSeen) + '</div></div><button class="danger" onclick="revoke(&quot;' + esc(d.id) + '&quot;)">Revoke</button></div>').join('') : '<div class="empty">No devices paired yet.</div>';
  $('libs').innerHTML = s.libraries.length ? s.libraries.map((l) => '<div class="card"><div class="grow"><b>' + esc(l.name) + '</b> <span class="muted">' + esc(l.type) + '</span><div class="muted">' + esc(l.path) + '</div></div><div class="muted">' + l.count + ' items · ' + bytes(l.bytes) + '</div></div>').join('') : '<div class="empty">No folders shared yet — add them under ' + (s.needsSetup ? 'Setup' : 'Settings') + ' above.</div>';
  $('activity').innerHTML = s.activity.length ? s.activity.slice(0, 15).map((j) => '<div class="card"><div class="grow"><b>' + esc(j.title) + '</b> <span class="muted">' + esc(j.kind) + '</span><div class="muted">' + esc(j.detail) + '</div>' + (j.state === 'running' ? '<div class="bar"><i style="width:' + Math.round(j.progress * 100) + '%"></i></div>' : '') + '</div><div class="state-' + j.state + '">' + j.state + '</div></div>').join('') : '<div class="empty">Nothing yet.</div>';
}
loadSetup().then(() => { if (!rows.length) { $('setupTitle').scrollIntoView(); $('addLib').focus(); } });
refresh();
async function refreshUpdate() {
  try {
    const u = await (await fetch('/admin/update', { headers: { 'X-Signal-Admin': '1' } })).json();
    $('updateBox').hidden = !u.available && !u.downloading;
    if (!$('updateBox').hidden) {
      $('updateTitle').textContent = 'Signal Agent ' + u.latest + ' is available (you have ' + u.current + ')';
      $('updateNotes').textContent = u.notes || '';
      $('updateBtn').disabled = !!u.downloading;
      $('updateMsg').textContent = u.downloading ? 'Downloading… ' + Math.round((u.progress || 0) * 100) + '% — Signal Agent restarts by itself when done.' : (u.error ? 'Update failed: ' + u.error : '');
    }
  } catch { /* agent restarting */ }
}
$('updateBtn').onclick = async () => {
  $('updateBtn').disabled = true;
  const r = await post('/admin/update', {});
  $('updateMsg').textContent = r.ok ? 'Downloading… Signal Agent restarts by itself when done.' : 'Could not start the update: ' + (r.reason || r.error);
};
refreshUpdate();
setInterval(refreshUpdate, 5000);
setInterval(refresh, 2000);
</script>
</body>
</html>`;
}
