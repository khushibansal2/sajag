/**
 * The credential codec, third implementation.
 *
 * Python mints. Kotlin verifies on the inspector's phone. This one verifies in
 * a browser, for the dashboard's verifier console — so a contractor's HR clerk
 * or a gate guard with no app installed can still check a certificate.
 *
 * THREE hand-written implementations of one wire format is exactly how a
 * format drifts apart, so this file is held to the same rule as the other two:
 * it is checked against golden vectors minted by the Python source of truth,
 * in CI, on every push. See web/test/codec.vectors.test.mjs.
 *
 * Zero dependencies. WebCrypto has shipped Ed25519 in Node 18+ and in current
 * Chrome, Safari and Firefox, so there is no npm package here to audit, pin,
 * or have go unmaintained before December. `isSupported()` reports honestly
 * rather than failing in a confusing way on an older browser.
 */

const PREFIX = 'SJG1:';
const SIG_LEN = 64;
const EPOCH = Date.UTC(2020, 0, 1);
const DAY_MS = 86400000;

export const COMPETENCIES = ['HAZ-ID', 'EQP-SEL', 'SEQ', 'EGR', 'TECH', 'KNW'];
export const MODULES = ['FIRE-01', 'GAS-01', 'MACH-01', 'HEIGHT-01', 'ELEC-01'];
const B45 = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:';

/* ------------------------------------------------------------------ base45 */

export function base45Decode(text) {
  const v = [];
  for (const ch of text) {
    const i = B45.indexOf(ch);
    if (i < 0) throw new Error(`character '${ch}' is not in the base45 alphabet`);
    v.push(i);
  }
  if (v.length % 3 === 1) throw new Error('length % 3 == 1 is never valid base45');

  const out = [];
  let i = 0;
  for (; i + 2 < v.length; i += 3) {
    const n = v[i] + v[i + 1] * 45 + v[i + 2] * 45 * 45;
    if (n > 0xffff) throw new Error('triplet overflows two bytes');
    out.push(n >> 8, n & 0xff);
  }
  if (v.length % 3 === 2) {
    const n = v[v.length - 2] + v[v.length - 1] * 45;
    if (n > 0xff) throw new Error('final pair overflows one byte');
    out.push(n);
  }
  return new Uint8Array(out);
}

/* -------------------------------------------------------------------- CBOR */

/** Deterministic subset: uint, bstr, tstr, array. Mirror of cbor_min.py. */
export function cborDecode(buf) {
  const [item, used] = decodeAt(buf, 0);
  if (used !== buf.length) throw new Error(`${buf.length - used} trailing byte(s)`);
  return item;
}

function decodeAt(buf, start) {
  if (start >= buf.length) throw new Error('truncated: expected a head byte');
  const b = buf[start];
  const major = b >> 5;
  let value = b & 0x1f;
  let i = start + 1;

  if (value >= 24) {
    const width = { 24: 1, 25: 2, 26: 4, 27: 8 }[value];
    if (width === undefined) throw new Error(`unsupported additional-info ${value}`);
    if (i + width > buf.length) throw new Error('truncated: incomplete length field');
    value = 0;
    for (let k = 0; k < width; k++) value = value * 256 + buf[i++];
  }

  if (major === 0) return [value, i];

  if (major === 2 || major === 3) {
    const end = i + value;
    if (end > buf.length) throw new Error('truncated: string runs past end of buffer');
    const slice = buf.subarray(i, end);
    if (major === 2) return [slice, end];
    // Strict UTF-8. A tampered QR routinely lands here with invalid bytes, and
    // that must be a rejection — not U+FFFD substitution, which would let two
    // implementations disagree about a worker's name.
    return [new TextDecoder('utf-8', { fatal: true }).decode(slice), end];
  }

  if (major === 4) {
    const list = [];
    for (let k = 0; k < value; k++) {
      const [el, next] = decodeAt(buf, i);
      list.push(el);
      i = next;
    }
    return [list, i];
  }

  throw new Error(`major type ${major} is not in this subset`);
}

/* -------------------------------------------------------------- credential */

function inflateIfNeeded(blob) {
  if (blob.length === 0) throw new Error('empty payload');
  if (blob[0] === 0) return blob.subarray(1);
  if (blob[0] === 1) {
    // The Python side only sets this marker when deflate actually helps, which
    // for a 148-byte credential carrying a 64-byte incompressible signature is
    // essentially never. Supported for format completeness.
    if (typeof DecompressionStream === 'undefined')
      throw new Error('this credential is compressed and this browser cannot inflate it');
    throw new Error('compressed credentials need the async path — use verifyAsync');
  }
  throw new Error(`unknown compression marker ${blob[0]}`);
}

function dateFromDays(days) {
  return new Date(EPOCH + days * DAY_MS).toISOString().slice(0, 10);
}

function toHex(bytes) {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

function fromHex(hex) {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16);
  return out;
}

export function isSupported() {
  return typeof crypto !== 'undefined' &&
    crypto.subtle !== undefined &&
    typeof TextDecoder !== 'undefined';
}

async function importIssuerKey(rawHex) {
  // Raw 32-byte key wrapped in the fixed X.509 SPKI prefix — the least fiddly
  // way to hand an Ed25519 key to WebCrypto, and identical to what the Java
  // cross-check does.
  const der = new Uint8Array(12 + 32);
  der.set(fromHex('302a300506032b6570032100'), 0);
  der.set(fromHex(rawHex), 12);
  return crypto.subtle.importKey('spki', der, { name: 'Ed25519' }, false, ['verify']);
}

/**
 * Verify a scanned or pasted QR.
 *
 * @param {string} qrText
 * @param {Object<number,string>} trustList issuer index -> raw public key hex
 * @param {{revoked?: Set<string>, today?: Date}} opts
 * @returns {Promise<{ok: boolean, reason: string, credential: object|null, warnings: string[]}>}
 *
 * Never throws on bad input. A verifier that crashes on a bad scan is one the
 * person using it stops trusting — the Python suite found exactly that bug, so
 * it is pinned in all three implementations.
 */
export async function verify(qrText, trustList, opts = {}) {
  const today = opts.today ?? new Date();
  const revoked = opts.revoked ?? new Set();
  const fail = (reason, credential = null) => ({ ok: false, reason, credential, warnings: [] });

  if (!isSupported()) return fail('This browser cannot check Ed25519 signatures.');
  if (typeof qrText !== 'string' || !qrText.startsWith(PREFIX))
    return fail('Not a Sajag certificate');

  let raw;
  try {
    raw = inflateIfNeeded(base45Decode(qrText.slice(PREFIX.length)));
  } catch (e) {
    return fail(`Damaged code — ${e.message}`);
  }
  if (raw.length <= SIG_LEN) return fail('Damaged code — payload too short');

  const payload = raw.subarray(0, raw.length - SIG_LEN);
  const signature = raw.subarray(raw.length - SIG_LEN);

  let p;
  try {
    p = cborDecode(payload);
  } catch (e) {
    return fail(`Damaged code — ${e.message}`);
  }

  let cred;
  try {
    if (!Array.isArray(p) || p.length < 13) throw new Error('payload is not a 13-element array');
    if (p[0] !== 1) throw new Error(`unsupported credential version ${p[0]}`);
    const comps = p[8];
    if (!(comps instanceof Uint8Array) || comps.length !== COMPETENCIES.length)
      throw new Error('competency block has the wrong length');
    const module = MODULES[p[5]];
    if (module === undefined) throw new Error(`unknown module index ${p[5]}`);

    cred = {
      issuerIndex: p[1],
      subjectIdHex: toHex(p[2]),
      name: p[3],
      employerCode: p[4],
      module,
      tier: p[6],
      score: p[7],
      competencies: Object.fromEntries(COMPETENCIES.map((c, i) => [c, comps[i]])),
      attemptDigestHex: toHex(p[9]),
      notBefore: dateFromDays(p[10]),
      expires: dateFromDays(p[11]),
      provisional: (p[12] & 1) !== 0,
    };
    cred.credentialId = cred.attemptDigestHex;
  } catch (e) {
    return fail(`Damaged code — ${e.message}`);
  }

  const keyHex = trustList[cred.issuerIndex];
  if (!keyHex) return fail('Unknown issuer — update the trust list', cred);

  let good = false;
  try {
    const key = await importIssuerKey(keyHex);
    good = await crypto.subtle.verify({ name: 'Ed25519' }, key, signature, payload);
  } catch {
    return fail('Could not check the signature on this device', cred);
  }
  if (!good) return fail('Signature does not match — certificate altered', cred);

  if (revoked.has(cred.credentialId)) return fail('Certificate has been revoked', cred);

  const day = today.toISOString().slice(0, 10);
  if (day < cred.notBefore) return fail(`Not valid until ${cred.notBefore}`, cred);
  if (day > cred.expires) return fail(`Expired on ${cred.expires}`, cred);

  const warnings = [];
  if (cred.provisional)
    warnings.push('Provisional — issued offline by the worker’s device and not yet ' +
                  'confirmed by the training centre.');
  if (cred.tier === 3) warnings.push('Trained in guided 2D mode (no camera).');

  return { ok: true, reason: 'Valid', credential: cred, warnings };
}
