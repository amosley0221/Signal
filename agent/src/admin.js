// Localhost-only admin page (served at "/"). Polls /admin/state; actions send X-Signal-Admin: 1.
export function adminPage() {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Signal Agent</title>
<style>
  :root { --bg:#0e0f12; --panel:#17191e; --line:#2a2d35; --text:#e8e9ec; --dim:#9097a3; --accent:#7cf2c4; --warn:#ffb86b; --bad:#ff6b7a; }
  @media (prefers-color-scheme: light) { :root { --bg:#f5f6f8; --panel:#fff; --line:#dde0e6; --text:#15171c; --dim:#5d6470; --accent:#0c8a60; --warn:#b15c00; --bad:#c62838; } }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--text); font:15px/1.45 system-ui, -apple-system, "Segoe UI", sans-serif; }
  main { max-width: 880px; margin: 0 auto; padding: 24px 16px 64px; }
  h1 { font-size: 22px; margin: 0 0 4px; letter-spacing: .01em; }
  h2 { font-size: 12px; text-transform: uppercase; letter-spacing: .12em; color: var(--dim); margin: 28px 0 10px; font-family: ui-monospace, "JetBrains Mono", Consolas, monospace; }
  .sub { color: var(--dim); font-family: ui-monospace, Consolas, monospace; font-size: 13px; }
  .card { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 14px 16px; margin-bottom: 10px; display:flex; align-items:center; gap: 14px; flex-wrap: wrap; }
  .grow { flex: 1 1 220px; min-width: 0; }
  .code { font-family: ui-monospace, "JetBrains Mono", Consolas, monospace; font-size: 30px; letter-spacing: .18em; color: var(--accent); }
  .muted { color: var(--dim); font-size: 13px; overflow-wrap: anywhere; }
  button { font: inherit; border-radius: 8px; border: 1px solid var(--line); background: transparent; color: var(--text); padding: 7px 14px; cursor: pointer; }
  button.primary { background: var(--accent); color: #07140f; border-color: var(--accent); font-weight: 600; }
  button.danger { color: var(--bad); border-color: var(--bad); }
  .empty { color: var(--dim); font-size: 14px; padding: 6px 0; }
  .state-done { color: var(--accent); } .state-failed { color: var(--bad); } .state-running { color: var(--warn); }
  .row { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
  .bar { height: 4px; background: var(--line); border-radius: 2px; overflow: hidden; width: 100%; margin-top: 6px; }
  .bar > i { display:block; height:100%; background: var(--accent); }
</style>
</head>
<body>
<main>
  <div class="row"><div class="grow"><h1 id="name">Signal Agent</h1><div class="sub" id="meta">…</div></div>
  <button id="rescan">Rescan libraries</button></div>

  <h2>Pair requests</h2>
  <div id="pending"><div class="empty">No phones waiting. In Signal Player, open Sync → Pair with a PC.</div></div>

  <h2>Paired devices</h2>
  <div id="devices"></div>

  <h2>Libraries</h2>
  <div id="libs"></div>

  <h2>Activity</h2>
  <div id="activity"></div>
</main>
<script>
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]));
const ago = (t) => { if (!t) return 'never'; const s = Math.round((Date.now() - t) / 1000); if (s < 60) return s + ' s ago'; if (s < 3600) return Math.round(s/60) + ' min ago'; if (s < 86400) return Math.round(s/3600) + ' h ago'; return new Date(t).toLocaleString(); };
const bytes = (n) => { const u = ['B','KB','MB','GB','TB']; let i = 0; while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; } return n.toFixed(i ? 1 : 0) + ' ' + u[i]; };
async function post(url) { await fetch(url, { method: 'POST', headers: { 'X-Signal-Admin': '1' } }); refresh(); }
window.approve = (id) => post('/admin/pair/' + id + '/approve');
window.deny = (id) => post('/admin/pair/' + id + '/deny');
let last = null;
window.revoke = (id) => { const d = (last?.devices || []).find((x) => x.id === id); if (confirm('Revoke ' + (d ? d.name : 'this device') + '? The phone will need to pair again.')) post('/admin/devices/' + id + '/revoke'); };
$('rescan').onclick = () => post('/admin/rescan');
async function refresh() {
  let s;
  try { s = await (await fetch('/admin/state')).json(); } catch { $('meta').textContent = 'Agent not reachable'; return; }
  last = s;
  $('name').textContent = s.info.name;
  document.title = 'Signal Agent · ' + s.info.name;
  $('meta').textContent = 'v' + s.info.version + ' · port ' + s.port + ' · ' + (s.info.addresses.join(', ') || 'no LAN address') + ' · Plex ' + (s.info.plex ? 'on' : 'off') + ' · last scan ' + ago(s.lastScanAt) + (s.scanning ? ' (scanning…)' : '');
  $('pending').innerHTML = s.pending.length ? s.pending.map((p) => '<div class="card"><div class="code">' + esc(p.code) + '</div><div class="grow"><b>' + esc(p.deviceName) + '</b><div class="muted">' + esc(p.ip || '') + ' · expires in ' + Math.max(0, Math.round((p.expiresAt - Date.now())/1000)) + ' s</div></div><button class="primary" onclick="approve(\\'' + p.id + '\\')">Approve</button><button onclick="deny(\\'' + p.id + '\\')">Deny</button></div>').join('') : '<div class="empty">No phones waiting. In Signal Player, open Sync → Pair with a PC.</div>';
  $('devices').innerHTML = s.devices.length ? s.devices.map((d) => '<div class="card"><div class="grow"><b>' + esc(d.name) + '</b><div class="muted">paired ' + new Date(d.createdAt).toLocaleString() + ' · last seen ' + ago(d.lastSeen) + '</div></div><button class="danger" onclick="revoke(\\'' + d.id + '\\')">Revoke</button></div>').join('') : '<div class="empty">No devices paired yet.</div>';
  $('libs').innerHTML = s.libraries.length ? s.libraries.map((l) => '<div class="card"><div class="grow"><b>' + esc(l.name) + '</b> <span class="muted">' + esc(l.type) + '</span><div class="muted">' + esc(l.path) + '</div></div><div class="muted">' + l.count + ' items · ' + bytes(l.bytes) + '</div></div>').join('') : '<div class="empty">No libraries configured. Edit ' + esc(s.configPath) + ' and restart the agent.</div>';
  $('activity').innerHTML = s.activity.length ? s.activity.slice(0, 15).map((j) => '<div class="card"><div class="grow"><b>' + esc(j.title) + '</b> <span class="muted">' + esc(j.kind) + '</span><div class="muted">' + esc(j.detail) + '</div>' + (j.state === 'running' ? '<div class="bar"><i style="width:' + Math.round(j.progress * 100) + '%"></i></div>' : '') + '</div><div class="state-' + j.state + '">' + j.state + '</div></div>').join('') : '<div class="empty">Nothing yet.</div>';
}
refresh();
setInterval(refresh, 2000);
</script>
</body>
</html>`;
}
