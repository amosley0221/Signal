// Switch a Windows .exe from the console subsystem to the GUI subsystem, so double-clicking it does not
// open a console window. Only the 2-byte Subsystem field of the PE optional header changes.
//   DOS header: e_lfanew (uint32) at 0x3C → "PE\0\0" signature → COFF header (20 bytes) → optional header.
//   Optional header: Magic 0x10b (PE32) / 0x20b (PE32+); Subsystem is a uint16 at offset 68 in both.
import fs from 'node:fs';

export const SUBSYSTEM_WINDOWS_GUI = 2;
export const SUBSYSTEM_WINDOWS_CUI = 3;
const SUBSYSTEM_OFFSET = 68;
const HEADER_READ = 4096;

/** Byte offset of the Subsystem field in `buf` (the start of a PE file). Throws when it is not a PE image. */
export function peSubsystemOffset(buf) {
  if (buf.length < 0x40 || buf.readUInt16LE(0) !== 0x5a4d) throw new Error('not a Windows executable (no MZ header)');
  const peOff = buf.readUInt32LE(0x3c);
  if (peOff + 24 > buf.length) throw new Error(`PE header offset 0x${peOff.toString(16)} is outside the file header`);
  if (buf.readUInt32LE(peOff) !== 0x00004550) throw new Error('missing "PE\\0\\0" signature');
  const optSize = buf.readUInt16LE(peOff + 4 + 16);
  const opt = peOff + 24;
  if (optSize < SUBSYSTEM_OFFSET + 2 || opt + SUBSYSTEM_OFFSET + 2 > buf.length) throw new Error(`optional header too small (${optSize} bytes)`);
  const magic = buf.readUInt16LE(opt);
  if (magic !== 0x10b && magic !== 0x20b) throw new Error(`unknown optional header magic 0x${magic.toString(16)}`);
  return opt + SUBSYSTEM_OFFSET;
}

export function readPeSubsystem(buf) {
  return buf.readUInt16LE(peSubsystemOffset(buf));
}

/** Patch in memory. Throws unless the current value is `from`. Returns the field's offset. */
export function setPeSubsystem(buf, { from = SUBSYSTEM_WINDOWS_CUI, to = SUBSYSTEM_WINDOWS_GUI } = {}) {
  const off = peSubsystemOffset(buf);
  const cur = buf.readUInt16LE(off);
  if (cur !== from) throw new Error(`expected PE subsystem ${from}, found ${cur}`);
  buf.writeUInt16LE(to, off);
  return off;
}

/** Patch a file on disk in place (reads only the header, writes 2 bytes). */
export function patchPeFileSubsystem(file, opts) {
  const fd = fs.openSync(file, 'r+');
  try {
    const head = Buffer.alloc(HEADER_READ);
    const n = fs.readSync(fd, head, 0, HEADER_READ, 0);
    const buf = head.subarray(0, n);
    const off = setPeSubsystem(buf, opts);
    fs.writeSync(fd, buf, off, 2, off);
    return off;
  } finally {
    fs.closeSync(fd);
  }
}
