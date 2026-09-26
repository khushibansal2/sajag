"""
The two lists a verifier caches, and nothing else.

A verifier holds no worker records, no attempt history and no database — only
these two signed files. That is the whole reason an inspector can check a
certificate 60 metres underground: there is nothing to look up.

  trust list       issuer index -> public key, signed by the root key
  revocation list  credential ids withdrawn, signed by the issuing authority

Both carry their own freshness date, and the verdict screen shows it, because
an inspector needs to know how old the list he is trusting actually is. A stale
list is a known state, not a silent one.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import date
from typing import Dict, List, Set

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)


class TrustError(ValueError):
    pass


def _canonical(obj) -> bytes:
    return json.dumps(obj, separators=(",", ":"), sort_keys=True).encode("utf-8")


@dataclass
class Issuer:
    index: int
    name: str
    public_key_hex: str

    @property
    def public_key(self) -> Ed25519PublicKey:
        return Ed25519PublicKey.from_public_bytes(bytes.fromhex(self.public_key_hex))


# ---------------------------------------------------------------- build

def build_trust_list(issuers: List[Issuer], as_of: date, root: Ed25519PrivateKey) -> str:
    body = {
        "v": 1,
        "asOf": as_of.isoformat(),
        "issuers": [
            {"i": i.index, "n": i.name, "k": i.public_key_hex}
            for i in sorted(issuers, key=lambda x: x.index)
        ],
    }
    sig = root.sign(_canonical(body))
    return json.dumps({"body": body, "sig": sig.hex()}, separators=(",", ":"))


def build_revocation_list(
    credential_ids: List[str], as_of: date, signer: Ed25519PrivateKey
) -> str:
    body = {"v": 1, "asOf": as_of.isoformat(), "ids": sorted(set(credential_ids))}
    sig = signer.sign(_canonical(body))
    return json.dumps({"body": body, "sig": sig.hex()}, separators=(",", ":"))


# ---------------------------------------------------------------- load

def _open_signed(raw: str, verify_key: Ed25519PublicKey, what: str) -> dict:
    try:
        doc = json.loads(raw)
        body, sig = doc["body"], bytes.fromhex(doc["sig"])
    except (json.JSONDecodeError, KeyError, ValueError) as exc:
        raise TrustError(f"{what} is malformed: {exc}") from None
    try:
        verify_key.verify(sig, _canonical(body))
    except InvalidSignature:
        raise TrustError(f"{what} signature is invalid — refusing to load it") from None
    return body


def load_trust_list(raw: str, root_public: Ed25519PublicKey):
    """Returns (index -> public key, asOf date). An unsigned or badly-signed
    trust list is never partially loaded; a verifier with no trust list refuses
    everything, which is the correct failure direction."""
    body = _open_signed(raw, root_public, "trust list")
    keys: Dict[int, Ed25519PublicKey] = {}
    for entry in body["issuers"]:
        keys[entry["i"]] = Ed25519PublicKey.from_public_bytes(bytes.fromhex(entry["k"]))
    return keys, date.fromisoformat(body["asOf"])


def load_revocation_list(raw: str, authority_public: Ed25519PublicKey):
    body = _open_signed(raw, authority_public, "revocation list")
    ids: Set[str] = set(body["ids"])
    return ids, date.fromisoformat(body["asOf"])
