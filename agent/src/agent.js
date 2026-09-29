// The Agent: wires config, state, catalogue, Plex, HTTP server, mDNS and periodic rescans together.
import fs from 'node:fs';
import path from 'node:path';
import { State } from './state.js';
import { Activity } from './activity.js';
import { Catalog } from './catalog.js';
import { Plex } from './plex.js';
import { createServer } from './server.js';
import { AGENT_DIR } from './config.js';
import { lanAddresses, readJsonSync } from './util.js';

export const VERSION = readJsonSync(path.join(AGENT_DIR, 'package.json'), {}).version || '1.0.0';
const RESCAN_INTERVAL_MS = 10 * 60 * 1000;

export class Agent {
  /**
   * @param {any} config normalised config (see config.js)
   * @param {{mdns?:boolean, watch?:boolean, periodic?:boolean, logFile?:boolean, quiet?:boolean, initialScan?:boolean}} [opts]
   */
  constructor(config, opts = {}) {
    this.config = config;
    this.opts = { mdns: true, watch: true, periodic: true, logFile: true, quiet: false, initialScan: true, ...opts };
    fs.mkdirSync(config.dataDir, { recursive: true });
    this.logPath = path.join(config.dataDir, 'agent.log');
    this.log = this.log.bind(this);
    this.state = new State(config.dataDir, this.log);
    this.activity = new Activity();
    this.plex = config.plex?.url && config.plex?.token ? new Plex(config.plex, { clientId: this.state.agentId, log: this.log }) : null;
    this.catalog = new Catalog({ config, state: this.state, activity: this.activity, plex: this.plex, log: this.log });
    this.server = null;
    this.port = null;
    this.bonjour = null;
    this.timer = null;
    this.state.onChange((ev) => {
      if (ev.type === 'pair') {
        this.log(`[pair] "${ev.req.deviceName}" (${ev.req.ip || '?'}) wants to pair — code ${ev.req.code}. Approve at http://localhost:${this.port}/ or type: approve ${ev.req.code}`);
      } else if (ev.type === 'approved') {
        this.log(`[pair] approved "${ev.req.deviceName}"`);
      } else if (ev.type === 'denied') {
        this.log(`[pair] denied "${ev.req.deviceName}"`);
      }
    });
  }

  log(msg) {
    const line = `${new Date().toISOString()} ${msg}`;
    if (!this.opts.quiet) console.log(line);
    if (this.opts.logFile) {
      try {
        fs.appendFileSync(this.logPath, `${line}\n`);
      } catch { /* ignore */ }
    }
  }

  info() {
    return { name: this.config.name, version: VERSION, id: this.state.agentId, plex: !!this.plex, addresses: lanAddresses() };
  }

  adminState() {
    return {
      info: this.info(),
      port: this.port,
      configPath: this.config.configPath || null,
      pending: this.state.pendingPairs(),
      devices: this.state.listDevices(),
      libraries: this.catalog.getLibraries(),
      lastScanAt: this.catalog.lastScanAt,
      scanning: !!this.catalog.scanning,
      activity: this.activity.list(),
    };
  }

  /** Approve a pending pair request by request id or 6-digit code. Returns the request or null. */
  approvePairing(idOrCode) { return this.state.approvePair(idOrCode); }

  denyPairing(idOrCode) { return this.state.denyPair(idOrCode); }

  async start({ port = this.config.port, host = this.config.host || '0.0.0.0' } = {}) {
    await this.catalog.init();
    this.server = createServer(this);
    await new Promise((resolve, reject) => {
      this.server.once('error', reject);
      this.server.listen(port, host, () => { this.server.off('error', reject); resolve(); });
    });
    this.port = this.server.address().port;
    this.log(`[http] Signal Agent ${VERSION} "${this.config.name}" listening on port ${this.port} — admin page: http://localhost:${this.port}/`);
    const addrs = lanAddresses();
    if (addrs.length) this.log(`[http] LAN address${addrs.length > 1 ? 'es' : ''}: ${addrs.map((a) => `${a}:${this.port}`).join(', ')}`);

    if (this.opts.mdns) this.#advertise();
    if (this.opts.initialScan) {
      const first = this.catalog.scan('startup').catch((e) => this.log(`[scan] ${e.message}`));
      if (this.opts.watch) first.then(() => this.catalog.startWatching());
      this.initialScan = first;
    }
    if (this.opts.periodic) {
      this.timer = setInterval(() => this.catalog.scan('periodic').catch(() => {}), RESCAN_INTERVAL_MS);
      this.timer.unref?.();
    }
    return this.port;
  }

  async #advertise() {
    try {
      const { Bonjour } = await import('bonjour-service');
      this.bonjour = new Bonjour(undefined, (err) => this.log(`[mdns] ${err?.message || err}`));
      this.bonjour.publish({
        name: this.config.name,
        type: 'signal',
        protocol: 'tcp',
        port: this.port,
        txt: { name: this.config.name, version: VERSION, id: this.state.agentId, plex: this.plex ? '1' : '0' },
      }).on('error', (e) => this.log(`[mdns] ${e.message || e}`));
      this.log('[mdns] advertising _signal._tcp');
    } catch (e) {
      this.log(`[mdns] disabled: ${e.message}`);
    }
  }

  async stop() {
    clearInterval(this.timer);
    this.catalog.stopWatching();
    this.state.saveSoon.cancel();
    if (this.bonjour) {
      await new Promise((r) => { try { this.bonjour.unpublishAll(() => r()); } catch { r(); } setTimeout(r, 1000).unref?.(); });
      try { this.bonjour.destroy(); } catch { /* ignore */ }
      this.bonjour = null;
    }
    if (this.server) {
      await new Promise((r) => { this.server.close(() => r()); this.server.closeAllConnections?.(); });
      this.server = null;
    }
    if (this.catalog.scanning) await this.catalog.scanning.catch(() => {});
    await this.state.save().catch(() => {});
  }
}
