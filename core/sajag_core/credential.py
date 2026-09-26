"""
The Sajag safety-competency credential.

Design constraint that drives everything here: a DGMS inspector standing at a
pit mouth with no cellular signal must get a trustworthy verdict from a scan.
That rules out putting a URL or a lookup id in the QR — both need a server. So
the credential itself travels in the QR, signed, and verification is one local
Ed25519 check against a cached trust list.

Wire format
-----------
    QR text  =  "SJG1:" + base45( [deflate] ( CBOR(payload) || sig(64) ) )

`payload` is a positional CBOR array — no map keys, because key strings would
cost more than the values they label. Field order is frozen; adding a field
means bumping VERSION and appending, never inserting.

    idx  field    type      notes
    ---  -------  --------  -----------------------------------------------
     0   ver      uint      format version
     1   iss      uint      index into the signed trust list
     2   sub      bstr[16]  worker id (ULID bytes)
     3   name     tstr      truncated to NAME_MAX chars, for the verdict screen
     4   emp      tstr      employer / contractor code
     5   mod      uint      index into MODULES
     6   tier     uint      1 | 2 | 3 — the mode actually used: AR, camera, guided
     7   score    uint      0..100 aggregate
     8   comps    bstr[6]   one byte per competency, COMPETENCIES order
     9   dig      bstr[16]  BLAKE2s-128 over the canonical attempt event log
    10   nbf      uint      days since EPOCH_DAY
    11   exp      uint      days since EPOCH_DAY
    12   flags    uint      bit0 = provisional (device-signed, not issuer-signed)

`dig` doubles as the credential id: it is what a revocation entry names, and it
is what ties a printed certificate back to a replayable attempt in the event
store.
"""

from __future__ import annotations

import hashlib
import zlib
from dataclasses import dataclass, field
from datetime import date
from typing import Dict, List, Optional

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)

from . import base45, cbor_min

VERSION = 1
PREFIX = "SJG1:"
EPOCH_DAY = date(2020, 1, 1)
SIG_LEN = 64

# Budgets are in BYTES, not characters. A 28-character Devanagari or Ol Chiki
# name is 84 UTF-8 bytes and would push the QR two versions denser than a Latin
# one — so the QR carries a Latin transliteration for the verdict screen, and
# the worker's name in his own script lives in the full credential on the
# server and on the printed certificate. See docs/credential.md.
NAME_MAX_BYTES = 32
EMP_MAX_BYTES = 12

# Frozen orders. Appending is safe; reordering silently changes what every
# already-issued certificate means, so these are append-only forever.
COMPETENCIES = ["HAZ-ID", "EQP-SEL", "SEQ", "EGR", "TECH", "KNW"]
MODULES = ["FIRE-01", "GAS-01", "MACH-01", "HEIGHT-01", "ELEC-01"]

FLAG_PROVISIONAL = 1 << 0


class CredentialError(ValueError):
    """Raised for anything that makes a credential un-trustworthy."""


def truncate_utf8(text: str, max_bytes: int) -> str:
    """Cut to a byte budget without splitting a character in half. A half
    character would make the CBOR undecodable and the certificate unverifiable,
    which is a much worse outcome than a shortened name."""
    raw = text.encode("utf-8")
    if len(raw) <= max_bytes:
        return text
    cut = raw[:max_bytes]
    while cut and (cut[-1] & 0xC0) == 0x80:      # step back off continuation bytes
        cut = cut[:-1]
    if cut and cut[-1] >= 0xC0:                  # drop a dangling lead byte
        cut = cut[:-1]
    return cut.decode("utf-8")


# ---------------------------------------------------------------- model

@dataclass
class Credential:
    issuer_index: int
    subject_id: bytes                 # 16 bytes
    name: str
    employer_code: str
    module: str                       # e.g. "FIRE-01"
    tier: int                         # 1 | 2 | 3
    score: int                        # 0..100
    competencies: Dict[str, int]      # code -> 0..100
    attempt_digest: bytes             # 16 bytes
    not_before: date
    expires: date
    provisional: bool = False

    # -------------------------------------------------------- validation
    def validate(self) -> None:
        if len(self.subject_id) != 16:
            raise CredentialError("subject_id must be exactly 16 bytes")
        if len(self.attempt_digest) != 16:
            raise CredentialError("attempt_digest must be exactly 16 bytes")
        if self.module not in MODULES:
            raise CredentialError(f"unknown module {self.module!r}")
        if self.tier not in (1, 2, 3):
            raise CredentialError(f"tier must be 1, 2 or 3, got {self.tier}")
        if not 0 <= self.score <= 100:
            raise CredentialError("score must be 0..100")
        for code in COMPETENCIES:
            if code not in self.competencies:
                raise CredentialError(f"missing competency {code}")
            if not 0 <= self.competencies[code] <= 100:
                raise CredentialError(f"competency {code} must be 0..100")
        if self.expires <= self.not_before:
            raise CredentialError("expires must be after not_before")

    # -------------------------------------------------------- payload
    def to_payload(self) -> list:
        self.validate()
        return [
            VERSION,
            self.issuer_index,
            self.subject_id,
            truncate_utf8(self.name, NAME_MAX_BYTES),
            truncate_utf8(self.employer_code, EMP_MAX_BYTES),
            MODULES.index(self.module),
            self.tier,
            self.score,
            bytes(self.competencies[c] for c in COMPETENCIES),
            self.attempt_digest,
            (self.not_before - EPOCH_DAY).days,
            (self.expires - EPOCH_DAY).days,
            FLAG_PROVISIONAL if self.provisional else 0,
        ]

    @classmethod
    def from_payload(cls, p: list) -> "Credential":
        if not isinstance(p, list) or len(p) < 13:
            raise CredentialError("payload is not a 13-element array")
        if p[0] != VERSION:
            raise CredentialError(f"unsupported credential version {p[0]}")
        comps = p[8]
        if not isinstance(comps, (bytes, bytearray)) or len(comps) != len(COMPETENCIES):
            raise CredentialError("competency block has the wrong length")
        cred = cls(
            issuer_index=p[1],
            subject_id=p[2],
            name=p[3],
            employer_code=p[4],
            module=MODULES[p[5]],
            tier=p[6],
            score=p[7],
            competencies={c: comps[i] for i, c in enumerate(COMPETENCIES)},
            attempt_digest=p[9],
            not_before=EPOCH_DAY.fromordinal(EPOCH_DAY.toordinal() + p[10]),
            expires=EPOCH_DAY.fromordinal(EPOCH_DAY.toordinal() + p[11]),
            provisional=bool(p[12] & FLAG_PROVISIONAL),
        )
        cred.validate()
        return cred

    @property
    def credential_id(self) -> str:
        return self.attempt_digest.hex()


# ---------------------------------------------------------------- codec

def _maybe_deflate(raw: bytes) -> bytes:
    """Deflate only when it actually helps.

    64 of these bytes are an Ed25519 signature, which is incompressible by
    construction, so on a short credential deflate usually *adds* bytes. We
    measure per credential and set a one-byte marker rather than assuming.
    """
    packed = zlib.compress(raw, 9)
    if len(packed) < len(raw):
        return b"\x01" + packed
    return b"\x00" + raw


def _maybe_inflate(blob: bytes) -> bytes:
    if not blob:
        raise CredentialError("empty payload")
    marker, body = blob[0], blob[1:]
    if marker == 0:
        return body
    if marker == 1:
        try:
            return zlib.decompress(body)
        except zlib.error as exc:
            raise CredentialError(f"corrupt deflate stream: {exc}") from None
    raise CredentialError(f"unknown compression marker {marker}")


def sign(cred: Credential, private_key: Ed25519PrivateKey) -> str:
    """Produce the QR text. The signature covers the CBOR payload bytes only —
    never the compressed or base45 form, so the wire encoding can change
    without invalidating already-issued certificates."""
    payload = cbor_min.encode(cred.to_payload())
    signature = private_key.sign(payload)
    return PREFIX + base45.encode(_maybe_deflate(payload + signature))


@dataclass
class VerifyResult:
    ok: bool
    reason: str = ""
    credential: Optional[Credential] = None
    warnings: List[str] = field(default_factory=list)


def verify(
    qr_text: str,
    trust_list: Dict[int, Ed25519PublicKey],
    revoked: Optional[set] = None,
    today: Optional[date] = None,
) -> VerifyResult:
    """Verify a scanned QR with no network and no database.

    `trust_list` and `revoked` are both cached on the verifying device and are
    themselves signed; see trustlist.py. Every failure path returns a reason
    the verdict screen can show, because "invalid" with no explanation is how
    an inspector ends up waving someone through.
    """
    today = today or date.today()
    revoked = revoked or set()

    if not qr_text.startswith(PREFIX):
        return VerifyResult(False, "Not a Sajag certificate")

    try:
        blob = base45.decode(qr_text[len(PREFIX):])
        raw = _maybe_inflate(blob)
    except (base45.Base45Error, CredentialError) as exc:
        return VerifyResult(False, f"Damaged code — {exc}")

    if len(raw) <= SIG_LEN:
        return VerifyResult(False, "Damaged code — payload too short")

    payload_bytes, signature = raw[:-SIG_LEN], raw[-SIG_LEN:]

    try:
        cred = Credential.from_payload(cbor_min.decode(payload_bytes))
    except (cbor_min.CborError, CredentialError, IndexError) as exc:
        return VerifyResult(False, f"Damaged code — {exc}")

    public_key = trust_list.get(cred.issuer_index)
    if public_key is None:
        return VerifyResult(False, "Unknown issuer — update the trust list", cred)

    try:
        public_key.verify(signature, payload_bytes)
    except InvalidSignature:
        return VerifyResult(False, "Signature does not match — certificate altered", cred)

    if cred.credential_id in revoked:
        return VerifyResult(False, "Certificate has been revoked", cred)
    if today < cred.not_before:
        return VerifyResult(False, f"Not valid until {cred.not_before}", cred)
    if today > cred.expires:
        return VerifyResult(False, f"Expired on {cred.expires}", cred)

    warnings = []
    if cred.provisional:
        warnings.append(
            "Provisional — issued offline by the worker's device and not yet "
            "confirmed by the training centre."
        )
    if cred.tier == 3:
        warnings.append("Trained in guided 2D mode (no camera).")
    return VerifyResult(True, "Valid", cred, warnings)


# ---------------------------------------------------------------- digests

def attempt_digest(canonical_event_log: bytes) -> bytes:
    """BLAKE2s truncated to 128 bits. Long enough that forging a second event
    log with the same digest is out of reach, short enough to fit the QR."""
    return hashlib.blake2s(canonical_event_log, digest_size=16).digest()


# ---------------------------------------------------------------- QR sizing

# Alphanumeric-mode capacity, error-correction level M, per ISO/IEC 18004.
_QR_ALNUM_M = {
    1: 20, 2: 38, 3: 61, 4: 90, 5: 122, 6: 154, 7: 178, 8: 221, 9: 262,
    10: 311, 11: 366, 12: 419, 13: 483, 14: 528, 15: 600, 16: 656,
    17: 734, 18: 816, 19: 909, 20: 970,
}


def qr_version_for(qr_text: str, ecc: str = "M") -> Optional[int]:
    """Smallest QR version that holds this text in alphanumeric mode at ECC-M.

    Level M (~15% recovery) is the right trade for a card in a helmet band:
    level L smudges out, level H costs a version and a half for recovery this
    use case does not need.
    """
    if ecc != "M":
        raise NotImplementedError("only the ECC-M table is bundled")
    for version, capacity in sorted(_QR_ALNUM_M.items()):
        if len(qr_text) <= capacity:
            return version
    return None
