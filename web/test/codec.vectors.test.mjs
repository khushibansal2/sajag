/**
 * Third leg of the cross-language proof.
 *
 * Python mints the credential. Kotlin verifies it on the inspector's phone.
 * This checks that the browser implementation agrees with both, against the
 * same 20 golden vectors — including the Devanagari and Ol Chiki names, all
 * three tiers, and provisional credentials.
 *
 *   node --test web/test/codec.vectors.test.mjs
 *
 * Regenerate the fixture from the source of truth first:
 *   cd core && python3 cli.py vectors 20 > ../jvm-vectors/vectors.json
 *   cd ../jvm-vectors && python3 tsv.py
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

import { verify, COMPETENCIES, isSupported } from '../src/codec/codec.js';

const here = dirname(fileURLToPath(import.meta.url));
const tsv = readFileSync(join(here, '..', '..', 'jvm-vectors', 'vectors.tsv'), 'utf8')
  .split('\n')
  .filter((l) => l.trim().length > 0);

const ISSUER_KEY_HEX = tsv[0].trim();
const TRUST = { 7: ISSUER_KEY_HEX };
const B45 = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:';

const VECTORS = tsv.slice(1).map((line) => {
  const f = line.split('\t');
  return {
    qr: f[0],
    issuerIndex: Number(f[1]),
    subjectIdHex: f[2],
    name: f[3],
    employerCode: f[4],
    module: f[5],
    tier: Number(f[6]),
    score: Number(f[7]),
    competencies: f[8].split(',').map(Number),
    attemptDigestHex: f[9],
    notBefore: f[10],
    expires: f[11],
    provisional: f[12] === 'true',
  };
});

/** A date inside the credential's own window, so these tests do not start
 *  failing in 2028 for entirely the wrong reason. */
function insideWindow(v) {
  const d = new Date(v.notBefore + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + 1);
  return d;
}

test('WebCrypto Ed25519 is available in this runtime', () => {
  assert.ok(isSupported(), 'no crypto.subtle — the verifier console cannot work here');
});

test('decodes every Python-minted credential identically', async () => {
  assert.ok(VECTORS.length >= 20, 'fixture is short — regenerate it');

  for (const [i, v] of VECTORS.entries()) {
    const r = await verify(v.qr, TRUST, { today: insideWindow(v) });
    assert.ok(r.ok, `vector ${i} rejected: ${r.reason}`);

    const c = r.credential;
    assert.equal(c.issuerIndex, v.issuerIndex, `vector ${i} issuerIndex`);
    assert.equal(c.subjectIdHex, v.subjectIdHex, `vector ${i} subjectId`);
    assert.equal(c.name, v.name, `vector ${i} name`);
    assert.equal(c.employerCode, v.employerCode, `vector ${i} employerCode`);
    assert.equal(c.module, v.module, `vector ${i} module`);
    assert.equal(c.tier, v.tier, `vector ${i} tier`);
    assert.equal(c.score, v.score, `vector ${i} score`);
    assert.equal(c.attemptDigestHex, v.attemptDigestHex, `vector ${i} digest`);
    assert.equal(c.notBefore, v.notBefore, `vector ${i} notBefore`);
    assert.equal(c.expires, v.expires, `vector ${i} expires`);
    assert.equal(c.provisional, v.provisional, `vector ${i} provisional`);

    COMPETENCIES.forEach((code, k) => {
      assert.equal(c.competencies[code], v.competencies[k], `vector ${i} ${code}`);
    });
  }
});

test('Indic-script names survive the round trip byte-identically', async () => {
  const indic = VECTORS.filter((v) => /[^\x00-\x7F]/.test(v.name));
  assert.ok(indic.length > 0, 'fixture has no Indic-script names — regenerate it');
  for (const v of indic) {
    const r = await verify(v.qr, TRUST, { today: insideWindow(v) });
    assert.ok(r.ok, `Indic vector rejected: ${r.reason}`);
    assert.equal(r.credential.name, v.name);
  }
});

test('no single-character mutation ever verifies', async () => {
  const v = VECTORS[0];
  const today = insideWindow(v);
  let mutated = 0;
  let accepted = 0;

  for (let pos = 5; pos < v.qr.length; pos++) {
    const idx = B45.indexOf(v.qr[pos]);
    if (idx < 0) continue;
    const swapped = B45[(idx + 1) % 45];
    const bad = v.qr.slice(0, pos) + swapped + v.qr.slice(pos + 1);
    mutated++;
    const r = await verify(bad, TRUST, { today });
    if (r.ok) accepted++;
  }

  assert.ok(mutated > 100, 'no mutations generated');
  assert.equal(accepted, 0, `${accepted} of ${mutated} tampered codes verified`);
});

test('a certificate signed by a stranger is rejected', async () => {
  const stranger = '11'.repeat(32);
  const r = await verify(VECTORS[0].qr, { 7: stranger }, { today: insideWindow(VECTORS[0]) });
  assert.equal(r.ok, false);
});

test('an unknown issuer index names the problem', async () => {
  const r = await verify(VECTORS[0].qr, { 99: ISSUER_KEY_HEX });
  assert.equal(r.ok, false);
  assert.match(r.reason, /Unknown issuer/);
});

test('expiry and not-yet-valid are reported separately', async () => {
  const v = VECTORS[0];
  const after = new Date(v.expires + 'T00:00:00Z');
  after.setUTCDate(after.getUTCDate() + 1);
  assert.match((await verify(v.qr, TRUST, { today: after })).reason, /Expired/);

  const before = new Date(v.notBefore + 'T00:00:00Z');
  before.setUTCDate(before.getUTCDate() - 1);
  assert.match((await verify(v.qr, TRUST, { today: before })).reason, /Not valid until/);
});

test('a revoked credential is refused even though the signature is good', async () => {
  const v = VECTORS[0];
  const r = await verify(v.qr, TRUST, {
    today: insideWindow(v),
    revoked: new Set([v.attemptDigestHex]),
  });
  assert.equal(r.ok, false);
  assert.match(r.reason, /revoked/);
});

test('guided-mode (tier 3) and provisional credentials pass but disclose themselves', async () => {
  const t3 = VECTORS.find((v) => v.tier === 3);
  assert.ok(t3, 'fixture has no tier 3 vector');
  const r = await verify(t3.qr, TRUST, { today: insideWindow(t3) });
  assert.ok(r.ok);
  assert.ok(r.warnings.some((w) => w.includes('guided 2D mode')));

  const prov = VECTORS.find((v) => v.provisional);
  assert.ok(prov, 'fixture has no provisional vector');
  const rp = await verify(prov.qr, TRUST, { today: insideWindow(prov) });
  assert.ok(rp.ok);
  assert.ok(rp.warnings.some((w) => w.includes('Provisional')));
});

test('garbage input is rejected, never thrown', async () => {
  const junk = ['', 'hello', 'SJG1:', 'SJG1:ZZZZ', 'SJG1:' + '0'.repeat(400),
                'SJG1:%%%%%%', 'https://example.com/cert/1', 'SJG1:BB8B', null, 42, {}];
  for (const j of junk) {
    const r = await verify(j, TRUST);
    assert.equal(r.ok, false, `accepted junk: ${String(j).slice(0, 20)}`);
  }
});
