// Persistent agent state (dataDir/state.json): agent id, paired devices, tag overrides, playback progress.
// Pairing requests live in memory only.
import crypto from 'node:crypto';
import path from 'node:path';
import { readJsonSync, sha256, writeJsonAtomic, debounce } from './util.js';

export const PAIR_TTL_MS = 5 * 60 * 1000;

export class State {
  constructor(dataDir, log = () => {}) {
    this.file = path.join(dataDir, 'state.json');
    this.log = log;
    const s = readJsonSync(this.file, {});
    this.data = {
      agentId: s.agentId || crypto.randomUUID(),
      devices: Array.isArray(s.devices) ? s.devices : [],
      overrides: s.overrides || {},
      progress: s.progress || {},
    };
    this.pairRequests = new Map();
    this.listeners = new Set();
    this.saveSoon = debounce(() => this.save().catch((e) => this.log(`[state] save failed: ${e.message}`)), 500);
    this.saveSync = () => writeJsonAtomic(this.file, this.data);
  }

  get agentId() { return this.data.agentId; }

  async save() { await writeJsonAtomic(this.file, this.data); }

  onChange(fn) { this.listeners.add(fn); return () => this.listeners.delete(fn); }

  emit(ev) { for (const fn of this.listeners) { try { fn(ev); } catch { /* ignore */ } } }

  // ---- devices / tokens ----
  /** Returns the device record for a token, or null. Tokens are stored as sha256 hashes. */
  deviceForToken(token) {
    if (!token || typeof token !== 'string') return null;
    const h = sha256(token);
    const dev = this.data.devices.find((d) => d.tokenHash === h);
    if (dev) {
      const now = Date.now();
      if (!dev.lastSeen || now - dev.lastSeen > 60000) { dev.lastSeen = now; this.saveSoon(); }
    }
    return dev || null;
  }

  addDevice(name, ip) {
    const token = crypto.randomBytes(32).toString('hex');
    const dev = { id: crypto.randomBytes(6).toString('hex'), name: name || 'Unknown device', tokenHash: sha256(token), createdAt: Date.now(), lastSeen: null, ip: ip || null };
    this.data.devices.push(dev);
    this.saveSoon();
    return { dev, token };
  }

  revokeDevice(id) {
    const before = this.data.devices.length;
    this.data.devices = this.data.devices.filter((d) => d.id !== id);
    if (this.data.devices.length !== before) { this.saveSoon(); this.emit({ type: 'revoked', id }); return true; }
    return false;
  }

  listDevices() {
    return this.data.devices.map(({ id, name, createdAt, lastSeen, ip }) => ({ id, name, createdAt, lastSeen, ip }));
  }

  // ---- pairing ----
  expirePairs() {
    const now = Date.now();
    for (const r of this.pairRequests.values()) {
      if (r.status === 'pending' && now - r.createdAt > PAIR_TTL_MS) r.status = 'expired';
      // forget finished requests after twice the TTL
      if (now - r.createdAt > PAIR_TTL_MS * 2) this.pairRequests.delete(r.id);
    }
  }

  startPair(deviceName, ip) {
    this.expirePairs();
    const pendingCodes = new Set([...this.pairRequests.values()].filter((r) => r.status === 'pending').map((r) => r.code));
    let code;
    do code = String(crypto.randomInt(0, 1000000)).padStart(6, '0'); while (pendingCodes.has(code));
    const req = { id: crypto.randomBytes(12).toString('hex'), code, deviceName: String(deviceName || 'Unknown device').slice(0, 100), ip: ip || null, createdAt: Date.now(), status: 'pending', token: null };
    this.pairRequests.set(req.id, req);
    this.emit({ type: 'pair', req });
    return req;
  }

  pairStatus(id) {
    this.expirePairs();
    const r = this.pairRequests.get(id);
    if (!r) return { status: 'expired' };
    if (r.status === 'approved') return { status: 'approved', token: r.token };
    return { status: r.status };
  }

  /** Find a pending request by request id or 6-digit code. */
  findPending(idOrCode) {
    this.expirePairs();
    const key = String(idOrCode || '').trim();
    for (const r of this.pairRequests.values()) {
      if (r.status === 'pending' && (r.id === key || r.code === key)) return r;
    }
    return null;
  }

  approvePair(idOrCode) {
    const r = this.findPending(idOrCode);
    if (!r) return null;
    const { dev, token } = this.addDevice(r.deviceName, r.ip);
    r.status = 'approved';
    r.token = token;
    r.deviceId = dev.id;
    this.emit({ type: 'approved', req: r });
    return r;
  }

  denyPair(idOrCode) {
    const r = this.findPending(idOrCode);
    if (!r) return null;
    r.status = 'denied';
    this.emit({ type: 'denied', req: r });
    return r;
  }

  pendingPairs() {
    this.expirePairs();
    return [...this.pairRequests.values()].filter((r) => r.status === 'pending')
      .map(({ id, code, deviceName, ip, createdAt }) => ({ id, code, deviceName, ip, createdAt, expiresAt: createdAt + PAIR_TTL_MS }));
  }

  // ---- overrides / progress ----
  setOverride(id, fields, fileMtime) {
    this.data.overrides[id] = { ...(this.data.overrides[id]?.fileMtime === fileMtime ? this.data.overrides[id] : {}), ...fields, fileMtime, updatedAt: Date.now() };
    this.saveSoon();
  }

  clearOverride(id) {
    if (this.data.overrides[id]) { delete this.data.overrides[id]; this.saveSoon(); }
  }

  setProgress(id, positionMs, watched) {
    this.data.progress[id] = { positionMs, watched: !!watched, updatedAt: Date.now() };
    this.saveSoon();
  }
}
