// HTTP Range header parsing (single byte range; multi-range requests are served as their first range).

/**
 * @param {string|undefined} header value of the Range header
 * @param {number} size total size of the representation
 * @returns {null | 'unsatisfiable' | {start:number, end:number}}
 *   null → ignore the header and send the full body (absent or syntactically invalid),
 *   'unsatisfiable' → respond 416, otherwise an inclusive byte range.
 */
export function parseRange(header, size) {
  if (!header || typeof header !== 'string') return null;
  const m = /^\s*bytes\s*=\s*(.+)$/i.exec(header);
  if (!m) return null;
  const first = m[1].split(',')[0].trim();
  const r = /^(\d*)\s*-\s*(\d*)$/.exec(first);
  if (!r) return null;
  const [, a, b] = r;
  if (a === '' && b === '') return null;
  let start;
  let end;
  if (a === '') {
    // suffix range: last N bytes
    const n = Number(b);
    if (n === 0) return 'unsatisfiable';
    start = Math.max(0, size - n);
    end = size - 1;
  } else {
    start = Number(a);
    end = b === '' ? size - 1 : Math.min(Number(b), size - 1);
    if (b !== '' && Number(b) < start) return null; // invalid → ignore per RFC 9110
  }
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end)) return null;
  if (start >= size || size === 0) return 'unsatisfiable';
  return { start, end };
}
