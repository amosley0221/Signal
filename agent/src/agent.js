// The Agent: wires config, state, catalogue, Plex, HTTP server, mDNS and periodic rescans together.
import fs from 'node:fs';
import path from 'node:path';
import { State } from './state.js';
import { Activity } from './activity.js';
import { Catalog } from './catalog.js';
import { Plex } from './plex.js';
import { createServer } from './server.js';
import { AGENT_DIR, isPackaged, isTailscaleAddress, configOverride } from './config.js';
import { StartupEntry, launchCommand } from './desktop.js';
import { lanAddresses, readJsonSync } from './util.js';
import { logFileFor } from './logfile.js';
import { Updater, cleanupOldExe } from './updater.js';

/* global __SIGNAL_AGENT_VERSION__ -- replaced at build time in the single-executable bundle */
export const VERSION = (typeof __SIGNAL_AGENT_VERSION__ === 'string' ? __SIGNAL_AGENT_VERSION__ : null)
  || readJsonSync(path.join(AGENT_DIR, 'package.json'), {}).version || '1.0.0';
// Folder watching picks up new files right away; this periodic check is only a safety net
// (e.g. network drives where watching doesn't work).
const RESCAN_INTERVAL_MS = 6 * 60 * 60 * 1000;

export class Agent {
  /**
   * @param {any} config normalised config (see config.js)
   * @param {{mdns?:boolean, watch?:boolean, periodic?:boolean, logFile?:boolean, quiet?:boolean, initialScan?:boolean, startup?:StartupEntry, onQuit?:()=>any}} [opts]
   *   `onQuit` is called by POST /admin/quit (the tray's Quit). Default: stop the agent and exit the process.
   */
  constructor(config, opts = {}) {
    this.config = config;
    this.opts = { mdns: true, watch: true, periodic: true, logFile: true, quiet: false, initialScan: true, ...opts };
    this.startup = this.opts.startup || new StartupEntry({ command: launchCommand({ packaged: isPackaged(), configOverride: configOverride() }) });
    this.reconfiguring = Promise.resolve();
    fs.mkdirSync(config.dataDir, { recursive: true });
    this.logPath = path.join(config.dataDir, 'agent.log');
    this.logFile = this.opts.logFile ? logFileFor(this.logPath) : null;
    this.log = this.log.bind(this);
    this.state = new State(config.dataDir, this.log);
    this.activity = new Activity();
    this.plex = this.#makePlex(config);
    this.catalog = new Catalog({ config, state: this.state, activity: this.activity, plex: this.plex, log: this.log });
    this.server = null;
    this.port = null;
    this.bonjour = null;
    const cfgArg = configOverride();
    this.updater = this.opts.updater || new Updater({
      current: VERSION,
      packaged: isPackaged(),
      dataDir: config.dataDir,
      args: cfgArg ? ['--config', cfgArg] : [],
      log: this.log,
      // The new copy is already starting; free the port and leave.
      onRestart: async () => { await this.stop(); process.exit(0); },
    });
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

  #makePlex(config) {
    return config.plex?.url && config.plex?.token ? new Plex(config.plex, { clientId: this.state.agentId, log: this.log }) : null;
  }

  log(msg) {
    const line = `${new Date().toISOString()} ${msg}`;
    if (!this.opts.quiet) console.log(line);
    this.logFile?.write(line); // rotates at ~5 MB, keeping agent.log.1
  }

  info() {
    return { name: this.config.name, version: VERSION, id: this.state.agentId, plex: !!this.plex, addresses: lanAddresses() };
  }

  adminState() {
    return {
      info: this.info(),
      port: this.port,
      configPath: this.config.configPath || null,
      needsSetup: !this.config.libraries.length,
      network: this.network(),
      pending: this.state.pendingPairs(),
      devices: this.state.listDevices(),
      libraries: this.catalog.getLibraries(),
      lastScanAt: this.catalog.lastScanAt,
      scanning: !!this.catalog.scanning,
      activity: this.activity.list(),
    };
  }

  /** Small status snapshot polled by the Windows tray icon every few seconds. */
  trayState() {
    const pending = this.state.pendingPairs();
    const st = this.startup.status();
    return {
      name: this.config.name,
      startup: !!st.enabled,
      startupSupported: !!st.supported,
      pending: pending.length,
      requests: pending.map(({ id, deviceName }) => ({ id, deviceName })),
      devices: this.state.listDevices().length,
      needsSetup: !this.config.libraries.length,
      update: (({ available, latest, downloading, error }) => ({ available, latest, downloading, error }))(this.updater.state()),
    };
  }

  /** Stop everything and leave (tray → Quit). */
  async quit() {
    if (this.quitting) return this.quitting;
    this.log('[agent] quit requested');
    this.quitting = (async () => {
      if (this.opts.onQuit) return this.opts.onQuit();
      await this.stop();
      process.exit(0);
    })();
    return this.quitting;
  }

  /** LAN and Tailscale (100.64.0.0/10) IPv4 addresses, for typing into the phone. */
  network() {
    const all = lanAddresses();
    return { lan: all.filter((a) => !isTailscaleAddress(a)), tailscale: all.filter(isTailscaleAddress), port: this.port };
  }

  /** What the admin page's setup form edits. */
  setupState() {
    const c = this.config;
    return {
      name: c.name,
      libraries: c.libraries.map(({ id, name, type, path: p }) => ({ id, name, type, path: p })),
      plex: { url: c.plex?.url || '', token: c.plex?.token || '' },
      configPath: c.configPath || null,
      dataDir: c.dataDir,
      platform: process.platform,
      packaged: isPackaged(),
      startup: this.startup.status(),
    };
  }

  /**
   * Apply a new config live: rebuild Plex + catalogue from the new libraries and rescan. The HTTP
   * server keeps running (it always reads agent.catalog). Port/host/dataDir changes need a restart.
   */
  reconfigure(config) {
    const run = this.reconfiguring.then(() => this.#reconfigure(config));
    this.reconfiguring = run.catch((e) => this.log(`[config] could not apply settings: ${e.stack || e.message}`));
    return run;
  }

  async #reconfigure(config) {
    const old = this.config;
    const next = { ...config, port: old.port, host: old.host, dataDir: old.dataDir };
    this.catalog.stopWatching();
    await this.catalog.cancelScan();
    this.config = next;
    this.plex = this.#makePlex(next);
    this.catalog = new Catalog({ config: next, state: this.state, activity: this.activity, plex: this.plex, log: this.log });
    await this.catalog.init();
    this.log(`[config] settings saved — ${next.libraries.length} librar${next.libraries.length === 1 ? 'y' : 'ies'}${this.plex ? ', Plex on' : ''}`);
    if (this.opts.mdns && this.server && (old.name !== next.name || !!old.plex?.token !== !!next.plex?.token)) {
      await this.#unadvertise();
      this.#advertise();
    }
    if (this.server) {
      const scan = this.catalog.scan('settings changed').catch((e) => this.log(`[scan] ${e.message}`));
      if (this.opts.watch) scan.then(() => this.catalog.startWatching());
      this.lastApplyScan = scan;
    }
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
    // Self-update (packaged Windows exe only): check now and every 6 h; tidy up after an update.
    if (this.opts.periodic) this.updater.start();
    if (isPackaged() && process.platform === 'win32') cleanupOldExe(process.execPath).catch(() => {});
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

  async #unadvertise() {
    if (!this.bonjour) return;
    await new Promise((r) => { try { this.bonjour.unpublishAll(() => r()); } catch { r(); } setTimeout(r, 1000).unref?.(); });
    try { this.bonjour.destroy(); } catch { /* ignore */ }
    this.bonjour = null;
  }

  async stop() {
    clearInterval(this.timer);
    this.updater.stop();
    await this.reconfiguring;
    this.catalog.stopWatching();
    this.state.saveSoon.cancel();
    await this.#unadvertise();
    if (this.server) {
      await new Promise((r) => { this.server.close(() => r()); this.server.closeAllConnections?.(); });
      this.server = null;
    }
    if (this.catalog.scanning) await this.catalog.scanning.catch(() => {});
    await this.state.save().catch(() => {});
  }
}
