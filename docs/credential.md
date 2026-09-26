# Credential format

A Sajag certificate is a QR code that carries the whole credential, signed. An
inspector's phone checks it with one local Ed25519 verification against a
cached trust list. There is no URL and no lookup id in the code, so checking
needs no network.

The reference implementation is `core/sajag_core/credential.py`. The Kotlin
(`android/.../credential/CredentialCodec.kt`, `CredentialMinter.kt`) and
JavaScript (`web/src/codec/codec.js`) versions are checked against it with the
same golden vectors (`make jvm`, `make web`, and the Android unit tests).

## Wire format

```
QR text  =  "SJG1:" + base45( marker || body )

body     =  CBOR(payload) || signature
marker   =  0x00 if body is stored as is, 0x01 if body is deflated
signature = Ed25519 over CBOR(payload), 64 bytes
```

The minter deflates only when that makes the result smaller. It usually does
not, because 64 of the roughly 150 bytes are the signature, which does not
compress.

## Payload

`payload` is a CBOR array. Positions are fixed; a new field is appended with a
new version number, never inserted.

| Index | Field | Type | Notes |
|---|---|---|---|
| 0 | ver | uint | Format version, currently 1 |
| 1 | iss | uint | Issuer index in the signed trust list (7 = training centre, 200 = the worker's own phone) |
| 2 | sub | bytes[16] | Worker id |
| 3 | name | text | Worker name, cut to 32 UTF-8 bytes on a character boundary |
| 4 | emp | text | Employer or contractor code, cut to 12 UTF-8 bytes |
| 5 | mod | uint | Module index: FIRE-01, GAS-01, MACH-01, HEIGHT-01, ELEC-01 |
| 6 | tier | uint | The mode the drill actually ran in: 1 AR (world-anchored), 2 camera, 3 guided (no camera). The wire name stays `tier`; screens say "mode" |
| 7 | score | uint | Aggregate score, 0 to 100 |
| 8 | comps | bytes[6] | One byte per competency: HAZ-ID, EQP-SEL, SEQ, EGR, TECH, KNW |
| 9 | dig | bytes[16] | BLAKE2s-128 of the canonical attempt event log; also the credential id |
| 10 | nbf | uint | Valid from, in days since 2020-01-01 |
| 11 | exp | uint | Valid until, in days since 2020-01-01 |
| 12 | flags | uint | Bit 0 set = provisional (signed by the phone, not yet by the training centre) |

The module and competency orders are frozen. Changing them would silently
change the meaning of every certificate already issued.

The name budget is in bytes, not characters. A Devanagari or Ol Chiki letter
takes 3 bytes in UTF-8, so a name in the worker's own script would push the QR
code one or two versions denser. The app therefore asks for the name in
English letters for the QR, and the name in the worker's own script belongs
on the printed certificate and in the server record.

## Measured size

A real credential is 148 signed bytes, 229 characters of QR text, and fits a
version 9 QR code (53 x 53 modules) at error correction level M. Base45 is used
instead of base64 because every base45 character is in the QR alphanumeric
set (5.5 bits per character); base64 needs lower case and falls back to byte
mode at 8 bits per character.

Run `cd core && python3 cli.py size` to reproduce the numbers.

## Verification

A verifier needs only the trust list (issuer index to public key, signed by
the root key) and the revocation list. It returns VALID or a reason, in this
order:

1. Not a Sajag certificate (wrong prefix)
2. Damaged code (base45, inflate or CBOR fails, or the payload is too short)
3. Unknown issuer (index not in the trust list)
4. Signature does not match (any change to any byte)
5. Revoked (credential id in the revocation list)
6. Not valid yet, or expired

A valid certificate still carries warnings, and every verifier shows them: a
provisional certificate, and a guided-mode certificate (`tier` = 3, trained
with no camera).

## The attempt digest

`dig` is BLAKE2s-128 over the canonical JSON of the attempt's events: sorted
keys, no whitespace, UTF-8 without escaping, floats rounded to 4 decimal
places and written the way Python writes them. The phone and the server both
compute it, so the provisional certificate the phone mints and the final one
the training centre issues for the same attempt carry the same credential id.

## Sync bundles use the same canonical JSON

When the phone uploads an attempt, it signs the canonical JSON of the whole
bundle with its device key (`android/.../sync/SyncBundle.kt`), and the server
rebuilds the same bytes to check it (`server/app/security.py`). A golden
bundle pinned in `SyncBundleTest.kt` and `server/tests/test_sync_contract.py`
keeps the two in step.
