#!/usr/bin/env node
// Entry point: `node src/index.js [--config path/to/signal-agent.config.json]`
import readline from 'node:readline';
import { pathToFileURL } from 'node:url';
import { loadConfig, resolveConfigPath, normalizeConfig } from './config.js';
import { Agent, VERSION } from './agent.js';

export { Agent, VERSION, loadConfig, normalizeConfig };

const HELP = `Commands:
  approve <code>   approve a pending pair request (6-digit code shown on the phone)
  deny <code>      deny a pending pair request
  pending          list pending pair requests
  devices          list paired devices
  revoke <id>      revoke a paired device
  rescan           rescan all libraries
  status           show libraries and last scan
  quit             stop the agent`;

function startConsole(agent) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout, terminal: !!process.stdin.isTTY });
  rl.on('line', async (line) => {
    const [cmd, arg] = line.trim().split(/\s+/);
    switch ((cmd || '').toLowerCase()) {
      case '':
        break;
      case 'approve':
      case 'a': {
        const r = agent.approvePairing(arg);
        if (!r) console.log(`No pending request with code ${arg || '(none)'}`);
        break;
      }
      case 'deny':
      case 'd': {
        const r = agent.denyPairing(arg);
        if (!r) console.log(`No pending request with code ${arg || '(none)'}`);
        break;
      }
      case 'pending':
      case 'list': {
        const p = agent.state.pendingPairs();
        console.log(p.length ? p.map((r) => `  ${r.code}  ${r.deviceName}  (${r.ip || '?'})`).join('\n') : '  no pending requests');
        break;
      }
      case 'devices': {
        const d = agent.state.listDevices();
        console.log(d.length ? d.map((x) => `  ${x.id}  ${x.name}  paired ${new Date(x.createdAt).toLocaleString()}`).join('\n') : '  no paired devices');
        break;
      }
      case 'revoke':
        console.log(agent.state.revokeDevice(arg) ? `Revoked ${arg}` : `No device ${arg}`);
        break;
      case 'rescan':
        agent.catalog.scan('console').then(() => console.log('Rescan finished'));
        break;
      case 'status':
        for (const l of agent.catalog.getLibraries()) console.log(`  ${l.id}  ${l.name} (${l.type})  ${l.count} items  ${l.path}`);
        console.log(`  last scan: ${agent.catalog.lastScanAt ? new Date(agent.catalog.lastScanAt).toLocaleString() : 'never'}`);
        break;
      case 'quit':
      case 'exit':
        process.kill(process.pid, 'SIGINT');
        break;
      default:
        console.log(HELP);
    }
  });
  return rl;
}

export async function main() {
  const configPath = resolveConfigPath();
  let config;
  try {
    config = loadConfig(configPath);
  } catch (e) {
    console.error(`Could not read config ${configPath}: ${e.message}`);
    process.exit(1);
  }
  const agent = new Agent(config);
  try {
    await agent.start();
  } catch (e) {
    console.error(`Could not start: ${e.message}`);
    process.exit(1);
  }
  let rl = null;
  if (process.stdin.isTTY) {
    rl = startConsole(agent);
    console.log('Type "help" for console commands.');
  }
  let stopping = false;
  const shutdown = async () => {
    if (stopping) return;
    stopping = true;
    agent.log('[agent] stopping');
    rl?.close();
    await agent.stop();
    process.exit(0);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
  process.on('uncaughtException', (e) => { agent.log(`[agent] uncaught: ${e.stack || e}`); process.exit(1); });
  process.on('unhandledRejection', (e) => agent.log(`[agent] unhandled rejection: ${e?.stack || e}`));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
