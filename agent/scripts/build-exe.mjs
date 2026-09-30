#!/usr/bin/env node
// Builds Signal Agent as one double-clickable program using Node's Single Executable Applications (SEA):
//   1. esbuild bundles src/index.js and every dependency into dist/signal-agent.cjs (CommonJS, no node_modules)
//   2. node --experimental-sea-config turns that into a SEA blob
//   3. a copy of the running node binary gets the blob injected with postject
// Output: dist/SignalAgent.exe on Windows, dist/signal-agent elsewhere (the SEA runs on the OS/arch it was built on).
// The .exe is then switched to the Windows GUI subsystem so it runs without a console window; it logs
// to <dataDir>/agent.log instead. The Linux/macOS binaries are left as they are.
//
//   npm run build:exe                  build the bundle + executable (each is smoke-tested)
//   npm run build:exe -- --bundle-only only dist/signal-agent.cjs
//   npm run build:exe -- --no-test     skip the smoke test of the executable
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { execFileSync, spawn, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { patchPeFileSubsystem } from './pe-subsystem.mjs';

const AGENT_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DIST = path.join(AGENT_DIR, 'dist');
const BUNDLE = path.join(DIST, 'signal-agent.cjs');
const BLOB = path.join(DIST, 'sea-prep.blob');
const SEA_CONFIG = path.join(DIST, 'sea-config.json');
const isWin = process.platform === 'win32';
const isMac = process.platform === 'darwin';
const EXE = path.join(DIST, isWin ? 'SignalAgent.exe' : 'signal-agent');
const FUSE = 'NODE_SEA_FUSE_fce680ab2cc467b6e072b8b5df1996b2';

/**
 * Start `cmd args… --config <temp> --no-browser` with a throwaway config on a random port, wait for the
 * banner (on stdout, or in data/agent.log for the windowless .exe), check GET /api/info, then stop it.
 * Throws on failure.
 */
async function smokeTest(cmd, args) {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'signal-agent-smoke-'));
  const cfg = path.join(tmp, 'signal-agent.config.json');
  fs.writeFileSync(cfg, JSON.stringify({ name: 'SMOKE-TEST', port: 0, libraries: [] }));
  const child = spawn(cmd, [...args, '--config', cfg, '--no-browser'], { cwd: tmp, stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true });
  let out = '';
  child.stdout.on('data', (d) => { out += d; });
  child.stderr.on('data', (d) => { out += d; });
  const logFile = path.join(tmp, 'data', 'agent.log');
  const logText = () => { try { return fs.readFileSync(logFile, 'utf8'); } catch { return ''; } };
  try {
    const port = await new Promise((resolve, reject) => {
      const t = setTimeout(() => reject(new Error('timed out waiting for the agent to start')), 30000);
      child.on('exit', (code) => { clearTimeout(t); reject(new Error(`exited early with code ${code}`)); });
      const poll = setInterval(() => {
        const m = /Signal Agent is running\. Setup: http:\/\/localhost:(\d+)\//.exec(out + logText());
        if (m) { clearInterval(poll); clearTimeout(t); resolve(Number(m[1])); }
      }, 100);
    });
    const info = await (await fetch(`http://127.0.0.1:${port}/api/info`)).json();
    if (info.name !== 'SMOKE-TEST' || info.version !== pkg.version) throw new Error(`unexpected /api/info: ${JSON.stringify(info)}`);
    console.log(`  OK — GET /api/info on port ${port}: ${JSON.stringify(info)}`);
  } catch (e) {
    console.error(out + logText());
    throw new Error(`smoke test failed for ${cmd}: ${e.message}`);
  } finally {
    child.removeAllListeners('exit');
    child.kill();
    await new Promise((r) => { if (child.exitCode != null) r(); else { child.once('exit', r); setTimeout(r, 3000); } });
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

const pkg = JSON.parse(fs.readFileSync(path.join(AGENT_DIR, 'package.json'), 'utf8'));
const step = (m) => console.log(`\n> ${m}`);

const [major, minor] = process.versions.node.split('.').map(Number);
if (major < 20 || (major === 20 && minor < 12)) {
  console.error(`Node ${process.versions.node} is too old to build a single executable; use Node 20.12+ (22 LTS recommended).`);
  process.exit(1);
}

fs.rmSync(DIST, { recursive: true, force: true });
fs.mkdirSync(DIST, { recursive: true });

// ---- 1. bundle ---------------------------------------------------------------------------------
step(`esbuild: bundling src/index.js → ${path.relative(AGENT_DIR, BUNDLE)}`);
const esbuild = await import('esbuild');
await esbuild.build({
  absWorkingDir: AGENT_DIR,
  // A tiny entry that calls main(); src/index.js only auto-runs when it is the node script itself.
  stdin: {
    contents: "import { main } from './src/index.js';\nmain();\n",
    resolveDir: AGENT_DIR,
    sourcefile: 'sea-entry.js',
    loader: 'js',
  },
  bundle: true,
  platform: 'node',
  format: 'cjs',
  target: 'node20',
  outfile: BUNDLE,
  // ESM-only deps (music-metadata & co.) are converted to CommonJS; their string-literal dynamic
  // imports are bundled too. Prefer the "node" export conditions.
  conditions: ['node', 'import', 'default'],
  mainFields: ['module', 'main'],
  // import.meta.url does not exist in CommonJS: point it at the bundle file via a banner shim.
  define: {
    'import.meta.url': '__signal_import_meta_url',
    __SIGNAL_BUNDLED__: 'true',
    __SIGNAL_AGENT_VERSION__: JSON.stringify(pkg.version),
  },
  banner: {
    js: [
      '/* Signal Agent single-file bundle — generated by scripts/build-exe.mjs, do not edit. */',
      "const __signal_import_meta_url = require('node:url').pathToFileURL(__filename).href;",
    ].join('\n'),
  },
  legalComments: 'none',
  logLevel: 'warning',
});
console.log(`  ${(fs.statSync(BUNDLE).size / 1024).toFixed(0)} KB`);

// Smoke test: the bundle must run on its own (no node_modules next to it) and answer /api/info.
step('smoke test: node dist/signal-agent.cjs');
await smokeTest(process.execPath, [BUNDLE]);

if (process.argv.includes('--bundle-only')) {
  console.log('\nDone (bundle only).');
  process.exit(0);
}

// ---- 2. SEA blob -----------------------------------------------------------------------------------
step('node --experimental-sea-config: generating the SEA blob');
fs.writeFileSync(SEA_CONFIG, JSON.stringify({
  main: BUNDLE,
  output: BLOB,
  disableExperimentalSEAWarning: true,
  useCodeCache: false,
  useSnapshot: false,
}, null, 2));
execFileSync(process.execPath, ['--experimental-sea-config', SEA_CONFIG], { stdio: 'inherit', cwd: DIST });

// ---- 3. copy node + inject -------------------------------------------------------------------------
step(`copying ${process.execPath} → ${path.relative(AGENT_DIR, EXE)}`);
fs.copyFileSync(process.execPath, EXE);
fs.chmodSync(EXE, 0o755);

const has = (cmd, args = []) => {
  try {
    const r = spawnSync(cmd, args, { stdio: 'ignore', shell: false, windowsHide: true });
    return !r.error;
  } catch {
    return false;
  }
};

if (isWin) {
  // The official node.exe is Authenticode-signed; injecting invalidates that signature. Remove it when
  // signtool is available (Windows SDK), otherwise skip — an unsigned or invalid-signature exe still runs.
  if (has('signtool', ['/?'])) {
    step('signtool remove /s (dropping the node.exe signature)');
    const r = spawnSync('signtool', ['remove', '/s', EXE], { stdio: 'inherit' });
    if (r.status !== 0) console.warn('  signtool could not remove the signature; continuing.');
  } else {
    console.log('  signtool not found — leaving the signature as is (the exe will be unsigned/invalid-signed, which is fine).');
  }
} else if (isMac && has('codesign', ['--help'])) {
  step('codesign --remove-signature');
  spawnSync('codesign', ['--remove-signature', EXE], { stdio: 'inherit' });
}

step('postject: injecting the blob');
const { inject } = await import('postject');
await inject(EXE, 'NODE_SEA_BLOB', fs.readFileSync(BLOB), {
  sentinelFuse: FUSE,
  ...(isMac ? { machoSegmentName: 'NODE_SEA' } : {}),
  overwrite: true,
});

if (isMac && has('codesign', ['--help'])) {
  step('codesign --sign - (ad-hoc)');
  spawnSync('codesign', ['--sign', '-', EXE], { stdio: 'inherit' });
}

fs.rmSync(BLOB, { force: true });

if (EXE.toLowerCase().endsWith('.exe')) {
  step('PE header: console → GUI subsystem (no console window)');
  try {
    const off = patchPeFileSubsystem(EXE);
    console.log(`  Subsystem at 0x${off.toString(16)}: 3 (WINDOWS_CUI) → 2 (WINDOWS_GUI)`);
  } catch (e) {
    console.error(`Could not switch ${path.basename(EXE)} to the GUI subsystem: ${e.message}`);
    process.exit(1);
  }
}

if (!process.argv.includes('--no-test')) {
  step(`smoke test: ${path.relative(AGENT_DIR, EXE)}`);
  await smokeTest(EXE, []);
}
console.log(`\nBuilt ${EXE} (${(fs.statSync(EXE).size / 1024 / 1024).toFixed(1)} MB, Node ${process.versions.node}, ${process.platform}-${process.arch}).`);
