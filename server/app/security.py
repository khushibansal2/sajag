"""
Device authentication for incoming event bundles.

main.py runs every incoming bundle through DeviceRegistry.verify() before a
single event is stored, and checks the supervisor co-signature before it mints
a certificate. Without these checks, anyone who could reach the API could post
a bundle claiming a worker passed a confined-space module, and the certificate
that came out would be indistinguishable from a real one.

THE TRUST CHAIN, IN ONE PARAGRAPH
At enrolment the training centre provisions a keypair into the phone's Android
Keystore; the public half is registered here against a device id. Every bundle
the device pushes is signed with the private half, which cannot be exported
from the Keystore even on a rooted phone. The server verifies that signature
before a single event is stored. So a rooted phone can still lie about what
happened in the scene — nothing can prevent that — but it cannot lie about
WHOSE phone it happened on, it cannot replay another device's bundle, and it
cannot backdate one. (Today the app still keeps its key in app storage, not in
the Keystore; moving it is on the before-pilot list in DeviceIdentity.kt.)

Deliberately dependency-free: stdlib plus `cryptography`. That means this
module is testable with no database, no web framework and no network, which is
how it comes to have tests at all.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Dict, Iterable, List, Optional, Set

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)

# A bundle older than this is refused. Long enough that a device carried out of
# a district by a courier after a full shift still syncs; short enough that a
# captured bundle is not replayable next month.
MAX_BUNDLE_AGE = timedelta(days=7)

# Tolerance for a device whose clock is ahead. Cheap phones drift, and a worker
# cannot be blocked from certifying because his handset thinks it is Thursday.
CLOCK_SKEW = timedelta(hours=6)


class BundleRejected(Exception):
    """Raised with a reason an operator can act on. Never swallow this — a
    rejected bundle is either a bug in the device build or an attack, and both
    need to reach a human."""


# ---------------------------------------------------------------- canonical form

def canonical_bundle_bytes(bundle: dict) -> bytes:
    """
    The exact bytes that get signed.

    MUST match the device implementation byte for byte. `sajag_core.scoring.
    canonical_event_log` is the sibling of this function on the attempt side,
    and the same rules apply:

      * keys sorted, no whitespace
      * floats rounded to 4 dp, because float-to-string differs across ABIs and
        a bundle signed on arm64 must verify on the server's x86
      * the signature field itself excluded, obviously
      * UTF-8, not escaped ASCII — a Devanagari worker name must produce the
        same bytes on both sides

    There is a golden-vector test for this in core/tests and a cross-language
    check in jvm-vectors. Change this function and both must be regenerated.
    """
    body = {k: v for k, v in bundle.items() if k != "deviceSig"}

    def normalise(x):
        if isinstance(x, float):
            return round(x, 4)
        if isinstance(x, dict):
            return {k: normalise(x[k]) for k in sorted(x)}
        if isinstance(x, list):
            return [normalise(i) for i in x]
        return x

    return json.dumps(normalise(body), separators=(",", ":"), sort_keys=True,
                      ensure_ascii=False).encode("utf-8")


def sign_bundle(bundle: dict, device_key: Ed25519PrivateKey) -> str:
    """Device side. Lives here so the test can produce a real signed bundle
    without an Android phone; the Kotlin equivalent is in SyncWorker.kt."""
    return device_key.sign(canonical_bundle_bytes(bundle)).hex()


# ---------------------------------------------------------------- registry

@dataclass
class EnrolledDevice:
    device_id: str
    public_key_hex: str
    site: str
    enrolled_at: datetime
    revoked: bool = False

    @property
    def public_key(self) -> Ed25519PublicKey:
        return Ed25519PublicKey.from_public_bytes(bytes.fromhex(self.public_key_hex))


class DeviceRegistry:
    """Which phones are allowed to submit, and what their keys are.

    In production this is a table. The interface is deliberately tiny so the
    storage swap does not touch the verification logic — which is the part that
    has tests.
    """

    def __init__(self, devices: Optional[Iterable[EnrolledDevice]] = None):
        self._devices: Dict[str, EnrolledDevice] = {}
        for d in devices or []:
            self._devices[d.device_id] = d
        # Every (device, bundle-digest) pair already accepted. See verify().
        self._seen: Set[str] = set()

    def enrol(self, device: EnrolledDevice) -> None:
        if device.device_id in self._devices:
            raise BundleRejected(f"device {device.device_id} is already enrolled")
        self._devices[device.device_id] = device

    def revoke(self, device_id: str) -> None:
        """A lost or stolen handset. Bundles it has already submitted stay
        valid — they were legitimate at the time — but nothing new is taken."""
        if device_id in self._devices:
            self._devices[device_id].revoked = True

    def get(self, device_id: str) -> Optional[EnrolledDevice]:
        return self._devices.get(device_id)

    # ------------------------------------------------------------ verify

    def verify(self, bundle: dict, now: Optional[datetime] = None) -> EnrolledDevice:
        """
        Accept or reject a bundle. Order matters: cheapest checks first, so a
        flood of malformed posts costs the server almost nothing.

        Returns the device on success; raises BundleRejected with a reason
        otherwise. There is no boolean return, on purpose — a caller cannot
        accidentally ignore the result.
        """
        now = now or datetime.now(timezone.utc)

        # 1. shape
        for field in ("deviceKeyId", "deviceSig", "issuedAt", "events"):
            if field not in bundle:
                raise BundleRejected(f"bundle is missing '{field}'")
        if not isinstance(bundle["events"], list) or not bundle["events"]:
            raise BundleRejected("bundle carries no events")

        # 2. is this device allowed to talk to us at all
        device = self._devices.get(bundle["deviceKeyId"])
        if device is None:
            raise BundleRejected(f"unknown device {bundle['deviceKeyId']!r} — not enrolled")
        if device.revoked:
            raise BundleRejected(f"device {device.device_id} has been revoked")

        # 3. freshness. Checked BEFORE the signature so a captured bundle
        #    cannot be used to make us do crypto work forever.
        try:
            issued = datetime.fromisoformat(bundle["issuedAt"].replace("Z", "+00:00"))
        except (ValueError, AttributeError):
            raise BundleRejected("issuedAt is not an ISO-8601 timestamp") from None
        if issued.tzinfo is None:
            issued = issued.replace(tzinfo=timezone.utc)
        if issued > now + CLOCK_SKEW:
            raise BundleRejected("bundle is dated in the future beyond clock skew")
        if now - issued > MAX_BUNDLE_AGE:
            raise BundleRejected(
                f"bundle is older than {MAX_BUNDLE_AGE.days} days — refusing as a replay")

        # 4. the signature
        payload = canonical_bundle_bytes(bundle)
        try:
            sig = bytes.fromhex(bundle["deviceSig"])
        except ValueError:
            raise BundleRejected("deviceSig is not hex") from None
        try:
            device.public_key.verify(sig, payload)
        except InvalidSignature:
            raise BundleRejected(
                "signature does not match — the bundle was altered in transit, "
                "or was not produced by this device") from None

        # 5. replay. A valid bundle resubmitted is normally a retried upload and
        #    is harmless because events are idempotent by ULID — but we refuse
        #    it anyway so the operator sees duplicate traffic rather than having
        #    it silently absorbed. The digest is over the signed bytes, so an
        #    attacker cannot dodge this by reordering JSON keys.
        digest = hashlib.blake2s(payload, digest_size=16).hexdigest()
        key = f"{device.device_id}:{digest}"
        if key in self._seen:
            raise BundleRejected("bundle has already been accepted (replay)")
        self._seen.add(key)

        return device


# ---------------------------------------------------------------- co-signature

def verify_supervisor_cosignature(
    attempt_id: str,
    worker_id: str,
    cosig_hex: str,
    supervisor_key: Ed25519PublicKey,
) -> bool:
    """
    Anti-proxy without biometrics (decision D-03).

    A supervisor's device signs `attempt_id|worker_id` at session start. That
    is stronger evidence than a face scan — it is a cryptographic assertion by
    a named, accountable person that this worker sat down at this attempt — and
    it starts no privacy argument, stores no biometric, and works in a dark
    gallery where face recognition would not.
    """
    message = f"{attempt_id}|{worker_id}".encode("utf-8")
    try:
        supervisor_key.verify(bytes.fromhex(cosig_hex), message)
        return True
    except (InvalidSignature, ValueError):
        return False
