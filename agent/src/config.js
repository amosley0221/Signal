// Config file handling: signal-agent.config.json (created with defaults on first run).
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const AGENT_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const DEFAULT_CONFIG_PATH = path.join(AGENT_DIR, 'signal-agent.config.json');
export const LIBRARY_TYPES = ['music', 'musicvideos', 'movies', 'tv'];

export function defaultConfig() {
  return {
    name: os.hostname(),
    port: 8765,
    dataDir: './data',
    libraries: [],
    plex: { url: '', token: '' },
  };
}

/** Resolve the config path from `--config <file>`, SIGNAL_AGENT_CONFIG, or the default next to ./data. */
export function resolveConfigPath(argv = process.argv.slice(2), env = process.env) {
  const i = argv.indexOf('--config');
  if (i >= 0 && argv[i + 1]) return path.resolve(argv[i + 1]);
  const eq = argv.find((a) => a.startsWith('--config='));
  if (eq) return path.resolve(eq.slice('--config='.length));
  if (env.SIGNAL_AGENT_CONFIG) return path.resolve(env.SIGNAL_AGENT_CONFIG);
  return DEFAULT_CONFIG_PATH;
}

/**
 * Normalise a raw config object. Relative `dataDir` / library paths resolve against `baseDir`
 * (the folder containing the config file).
 */
export function normalizeConfig(raw, baseDir = AGENT_DIR) {
  const d = defaultConfig();
  const c = { ...d, ...(raw || {}) };
  c.name = String(c.name || d.name);
  c.port = Number.isInteger(Number(c.port)) ? Number(c.port) : d.port;
  c.host = c.host || '0.0.0.0';
  c.dataDir = path.resolve(baseDir, c.dataDir || d.dataDir);
  c.plex = { url: String(c.plex?.url || '').replace(/\/+$/, ''), token: String(c.plex?.token || '') };
  const seen = new Set();
  c.libraries = (Array.isArray(c.libraries) ? c.libraries : []).map((l, i) => {
    let id = String(l.id || `l${i + 1}`);
    while (seen.has(id)) id = `${id}_`;
    seen.add(id);
    const type = LIBRARY_TYPES.includes(l.type) ? l.type : 'music';
    return { id, name: String(l.name || path.basename(l.path || '') || id), type, path: path.resolve(baseDir, String(l.path || '.')) };
  });
  return c;
}

/** Load (or create with defaults) the config file. */
export function loadConfig(configPath = resolveConfigPath()) {
  let raw;
  if (fs.existsSync(configPath)) {
    raw = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  } else {
    raw = defaultConfig();
    fs.mkdirSync(path.dirname(configPath), { recursive: true });
    fs.writeFileSync(configPath, `${JSON.stringify(raw, null, 2)}\n`);
    console.log(`[config] created ${configPath} — edit it to add your libraries`);
  }
  const cfg = normalizeConfig(raw, path.dirname(configPath));
  cfg.configPath = configPath;
  return cfg;
}
