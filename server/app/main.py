"""
Sajag issuer service.

Deliberately small. The interesting property of this system is that the phone
and the verifier both work without this service, so it does only this:

  1. accept signed event bundles (verified, idempotent, append-only)
  2. re-score attempts from raw events, using the same engine the device used
  3. mint issuer-signed credentials, only for supervisor co-signed attempts
  4. publish the signed trust list and revocation delta
  5. collect hazard reports from the field

Note (1): every bundle is checked by app/security.py before a single event is
stored: the device signature over the whole bundle, the freshness window, and
replay. Note (2): the server does NOT trust the device's score. It trusts the
device's *events* and recomputes the score itself with the site's current
policy. A rooted phone can lie about what happened in the scene, but it cannot
lie about what that means, and a re-score after a policy change fixes every
affected certificate at once.

Two modes, chosen with environment variables:

  demo (default)       A phone is enrolled the first time it sends a bundle
                       that its own key verifies ("trust on first use"), and a
                       supervisor co-signature is accepted if it verifies, even
                       if the supervisor was never registered. Admin routes are
                       open unless SAJAG_ADMIN_TOKEN is set.
  SAJAG_STRICT=1       Only phones and supervisors registered through the
                       admin routes are accepted, and the admin routes require
                       the X-Admin-Token header to match SAJAG_ADMIN_TOKEN.

Run:  uvicorn app.main:app --reload
"""

from __future__ import annotations

import json
import os
import sys
from datetime import date, datetime, timedelta, timezone
from typing import Dict, List, Optional, Tuple

from fastapi import Body, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field, ValidationError

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "core"))

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

from sajag_core import credential as C
from sajag_core.scoring import canonical_event_log, score_attempt
from sajag_core.trustlist import Issuer, build_revocation_list, build_trust_list

from app.security import (
    BundleRejected, DeviceRegistry, EnrolledDevice, verify_supervisor_cosignature,
)

app = FastAPI(title="Sajag issuer", version="0.2.0")

ISSUER_INDEX = 7
STRICT = os.environ.get("SAJAG_STRICT") == "1"
ADMIN_TOKEN = os.environ.get("SAJAG_ADMIN_TOKEN") or None

# Development only. In production the issuer key is generated in a documented
# ceremony and lives in an HSM or a sealed keystore; it never appears in code,
# in an environment variable, or in a repository. See docs/key-ceremony.md.
_ISSUER_KEY = Ed25519PrivateKey.from_private_bytes(
    bytes.fromhex(os.environ["SAJAG_ISSUER_KEY_HEX"])
    if "SAJAG_ISSUER_KEY_HEX" in os.environ
    else bytes(range(32))
)
_ROOT_KEY = Ed25519PrivateKey.from_private_bytes(bytes(range(32, 64)))

# Stand-ins for PostgreSQL tables. Swap for SQLAlchemy before the pilot.
EVENTS: Dict[str, List[dict]] = {}
ATTEMPTS: Dict[str, dict] = {}
REVOKED: List[str] = []
SCENARIOS: Dict[str, dict] = {}
HAZARDS: Dict[str, dict] = {}
SUPERVISORS: Dict[str, dict] = {}   # public key hex -> {"name": ..., "registeredAt": ...}
DEVICES = DeviceRegistry()


def _load_scenarios() -> None:
    here = os.path.join(os.path.dirname(__file__), "..", "..", "core", "scenarios")
    for name in os.listdir(here):
        if name.endswith(".json"):
            with open(os.path.join(here, name), encoding="utf-8") as fh:
                sc = json.load(fh)
                SCENARIOS[sc["id"]] = sc


_load_scenarios()


# ---------------------------------------------------------------- models

class EventIn(BaseModel):
    eventId: str
    attemptId: str
    seq: int
    atMs: int
    beat: str
    item: Optional[str] = None
    type: str
    tier: int
    payload: dict = Field(default_factory=dict)


class BundleIn(BaseModel):
    """A sealed bundle from a device, possibly carried to the surface on
    someone else's phone (the courier relay). The carrier cannot read or alter
    it: `deviceSig` covers every other field. `supervisorSig` is
    "publicKeyHex.signatureHex" over "attemptId|workerId", or null."""
    workerId: str
    moduleId: str
    tierCeiling: int
    deviceKeyId: str
    devicePublicKey: Optional[str] = None
    issuedAt: str
    deviceSig: str
    supervisorSig: Optional[str] = None
    events: List[EventIn]


class DeviceIn(BaseModel):
    publicKeyHex: str
    site: str = ""


class SupervisorIn(BaseModel):
    publicKeyHex: str
    name: str


class HazardIn(BaseModel):
    id: str
    workerId: str = ""
    type: str
    severity: str
    location: str = ""
    note: str = ""
    createdAtMs: int
    # District id as the app's map uses it (e.g. "DHANBAD"); "" if not given.
    district: str = ""


class HazardBatchIn(BaseModel):
    deviceKeyId: str = ""
    reports: List[HazardIn]


# ---------------------------------------------------------------- helpers

def _require_admin(token: Optional[str]) -> None:
    if STRICT and not ADMIN_TOKEN:
        raise HTTPException(403, "strict mode needs SAJAG_ADMIN_TOKEN to be set")
    if ADMIN_TOKEN and token != ADMIN_TOKEN:
        raise HTTPException(403, "admin token required (X-Admin-Token header)")


def _public_key(hex_key: str) -> Ed25519PublicKey:
    """Raises ValueError for anything that is not a 32-byte Ed25519 key in hex."""
    raw = bytes.fromhex(hex_key)
    if len(raw) != 32:
        raise ValueError("an Ed25519 public key is 32 bytes")
    return Ed25519PublicKey.from_public_bytes(raw)


def _enrol_on_first_use(bundle: dict) -> None:
    """Demo mode only: take the public key a phone sends in its first bundle,
    but only after that key has verified the bundle itself. That proves the
    sender holds the private key, so nobody can register a key for someone
    else's device id."""
    device_id = bundle.get("deviceKeyId")
    pub = bundle.get("devicePublicKey")
    if not isinstance(device_id, str) or not isinstance(pub, str) or not pub.startswith(device_id):
        raise HTTPException(401, f"unknown device {device_id!r}: not enrolled")
    try:
        _public_key(pub)
    except ValueError:
        raise HTTPException(401, "devicePublicKey is not an Ed25519 public key") from None
    candidate = EnrolledDevice(device_id=device_id, public_key_hex=pub, site="first use",
                               enrolled_at=datetime.now(timezone.utc))
    try:
        DeviceRegistry([candidate]).verify(bundle)
    except BundleRejected as e:
        raise HTTPException(401, str(e)) from None
    DEVICES.enrol(candidate)


def _check_cosignature(attempt_id: str, worker_id: str, cosig: Optional[str]) -> Tuple[bool, Optional[dict]]:
    """(signature verifies, registered supervisor record or None)."""
    if not cosig or "." not in cosig:
        return False, None
    key_hex, _, sig_hex = cosig.partition(".")
    try:
        key = _public_key(key_hex)
    except ValueError:
        return False, None
    return verify_supervisor_cosignature(attempt_id, worker_id, sig_hex, key), SUPERVISORS.get(key_hex.lower())


# ---------------------------------------------------------------- routes

@app.post("/v1/sync/bundle")
def ingest(bundle: dict = Body(...)):
    """Verified, then append-only and idempotent. The signature is checked on
    the raw JSON exactly as the phone signed it, before any parsing.
    Re-pushing after a failed upload is safe: the phone signs each push with a
    fresh issuedAt, and event ids are ULIDs minted on the phone, so a duplicate
    event is simply dropped. There is no merge path because there is never a
    conflict."""
    device_id = bundle.get("deviceKeyId")
    if isinstance(device_id, str) and DEVICES.get(device_id) is None:
        if STRICT:
            raise HTTPException(401, f"unknown device {device_id!r}: not enrolled")
        _enrol_on_first_use(bundle)
    try:
        DEVICES.verify(bundle)
    except BundleRejected as e:
        raise HTTPException(401, str(e)) from None

    try:
        parsed = BundleIn.model_validate(bundle)
    except ValidationError as e:
        raise HTTPException(422, f"bundle is signed but malformed: {e.error_count()} field error(s)") from None
    accepted = 0
    for ev in parsed.events:
        log = EVENTS.setdefault(ev.attemptId, [])
        if any(existing["eventId"] == ev.eventId for existing in log):
            continue
        log.append(ev.model_dump())
        accepted += 1

    for attempt_id in {e.attemptId for e in parsed.events}:
        ATTEMPTS.setdefault(attempt_id, {
            "attemptId": attempt_id,
            "workerId": parsed.workerId,
            "moduleId": parsed.moduleId,
            "tierCeiling": parsed.tierCeiling,
            "supervisorSig": parsed.supervisorSig,
            "deviceKeyId": parsed.deviceKeyId,
        })

    return {"accepted": accepted, "duplicates": len(parsed.events) - accepted}


@app.post("/v1/attempts/{attempt_id}/issue")
def issue(attempt_id: str, name: str, employerCode: str, workerUlidHex: str,
          x_admin_token: Optional[str] = Header(default=None)):
    """Re-score from raw events, then mint. The device's own score is never an
    input here. Issuing is a training-centre action, so it needs the admin
    token when one is configured."""
    _require_admin(x_admin_token)
    attempt = ATTEMPTS.get(attempt_id)
    log = EVENTS.get(attempt_id)
    if not attempt or not log:
        raise HTTPException(404, "unknown attempt")
    if workerUlidHex.lower() != attempt["workerId"].lower():
        raise HTTPException(409, "this attempt belongs to a different worker")

    scenario = SCENARIOS.get(attempt["moduleId"])
    if not scenario:
        raise HTTPException(422, f"no scenario for module {attempt['moduleId']}")

    tier = max((e["tier"] for e in log), default=attempt["tierCeiling"])
    scoring_events = [
        {"beat": e["beat"], "item": e["item"], "type": e["type"], **e["payload"]}
        for e in sorted(log, key=lambda e: e["seq"])
    ]
    result = score_attempt(scoring_events, dict(scenario, _tier=tier))

    if not result.passed:
        return {
            "issued": False,
            "aggregate": result.aggregate,
            "competencies": result.competencies,
            "reasons": result.reasons,
            "replayBeats": result.replay_beats,
        }

    # Anti-proxy without biometrics (decision D-03). A missing or false
    # co-signature is not a scoring failure, it is an identity failure, and it
    # blocks issuance rather than reducing the score.
    cosig = attempt.get("supervisorSig")
    if not cosig:
        raise HTTPException(409, "no supervisor co-signature on this attempt")
    ok, supervisor = _check_cosignature(attempt_id, attempt["workerId"], cosig)
    if not ok:
        raise HTTPException(409, "supervisor co-signature does not verify for this attempt and worker")
    if STRICT and supervisor is None:
        raise HTTPException(409, "the supervisor who signed this attempt is not registered")

    today = date.today()
    cred = C.Credential(
        issuer_index=ISSUER_INDEX,
        subject_id=bytes.fromhex(workerUlidHex),
        name=name,
        employer_code=employerCode,
        module=attempt["moduleId"],
        tier=tier,
        score=result.aggregate,
        competencies=result.competencies,
        attempt_digest=C.attempt_digest(canonical_event_log(scoring_events)),
        not_before=today,
        expires=today + timedelta(days=365),
        provisional=False,
    )
    qr = C.sign(cred, _ISSUER_KEY)
    return {
        "issued": True,
        "credentialId": cred.credential_id,
        "qr": qr,
        "qrChars": len(qr),
        "qrVersion": C.qr_version_for(qr),
        "aggregate": result.aggregate,
        "competencies": result.competencies,
        "tier": tier,
        "supervisor": supervisor["name"] if supervisor else None,
        "supervisorRegistered": supervisor is not None,
    }


@app.get("/v1/trust-list")
def trust_list():
    """Cached by every verifier. Signed by the root key, so a verifier that
    cannot reach this endpoint keeps using its last copy safely, and shows the
    inspector how old that copy is."""
    raw = build_trust_list(
        [Issuer(ISSUER_INDEX, "VTC Dhanbad",
                _ISSUER_KEY.public_key().public_bytes_raw().hex())],
        date.today(), _ROOT_KEY)
    return json.loads(raw)


@app.get("/v1/revocations")
def revocations():
    """A few kilobytes, so it syncs over a 2G tail at a pit mouth."""
    return json.loads(build_revocation_list(REVOKED, date.today(), _ISSUER_KEY))


@app.post("/v1/revocations/{credential_id}")
def revoke(credential_id: str, x_admin_token: Optional[str] = Header(default=None)):
    _require_admin(x_admin_token)
    if credential_id not in REVOKED:
        REVOKED.append(credential_id)
    return {"revoked": credential_id, "count": len(REVOKED)}


@app.post("/v1/admin/devices")
def enrol_device(body: DeviceIn, x_admin_token: Optional[str] = Header(default=None)):
    """Register a phone at the training centre. Required in strict mode."""
    _require_admin(x_admin_token)
    try:
        _public_key(body.publicKeyHex)
    except ValueError as e:
        raise HTTPException(422, str(e)) from None
    device_id = body.publicKeyHex[:16]
    if DEVICES.get(device_id) is None:
        DEVICES.enrol(EnrolledDevice(device_id=device_id, public_key_hex=body.publicKeyHex,
                                     site=body.site, enrolled_at=datetime.now(timezone.utc)))
    return {"deviceKeyId": device_id, "enrolled": True}


@app.post("/v1/admin/devices/{device_id}/revoke")
def revoke_device(device_id: str, x_admin_token: Optional[str] = Header(default=None)):
    """A lost or stolen phone: nothing new is accepted from it."""
    _require_admin(x_admin_token)
    if DEVICES.get(device_id) is None:
        raise HTTPException(404, "unknown device")
    DEVICES.revoke(device_id)
    return {"deviceKeyId": device_id, "revoked": True}


@app.post("/v1/admin/supervisors")
def register_supervisor(body: SupervisorIn, x_admin_token: Optional[str] = Header(default=None)):
    """Register the key a supervisor set up on a phone (shown on the phone's
    Supervisor screen). Required in strict mode."""
    _require_admin(x_admin_token)
    try:
        _public_key(body.publicKeyHex)
    except ValueError as e:
        raise HTTPException(422, str(e)) from None
    SUPERVISORS[body.publicKeyHex.lower()] = {
        "name": body.name, "registeredAt": datetime.now(timezone.utc).isoformat()}
    return {"keyId": body.publicKeyHex[:16], "registered": True}


@app.post("/v1/hazards")
def report_hazards(batch: HazardBatchIn):
    """Hazard reports from the field. Idempotent by report id."""
    accepted = 0
    for r in batch.reports:
        if r.id in HAZARDS:
            continue
        HAZARDS[r.id] = dict(r.model_dump(), deviceKeyId=batch.deviceKeyId)
        accepted += 1
    return {"accepted": accepted, "duplicates": len(batch.reports) - accepted}


@app.get("/v1/hazards")
def list_hazards():
    rows = sorted(HAZARDS.values(), key=lambda r: r["createdAtMs"], reverse=True)
    return {"count": len(rows), "reports": rows}


@app.get("/v1/registers/training")
def training_register(site: Optional[str] = None):
    """The statutory register (decision D-08): the artefact a mine is
    obliged to hold under the Mines Vocational Training Rules, 1966. The
    dashboard renders this; the PDF export is the primary deliverable, and the
    charts are secondary.

    TODO(domain reviewer): confirm the column set and form reference against
    the current DGMS circulars before printing a form number on anything.
    """
    rows = []
    for attempt_id, attempt in ATTEMPTS.items():
        log = EVENTS.get(attempt_id, [])
        ok, supervisor = _check_cosignature(attempt_id, attempt["workerId"], attempt.get("supervisorSig"))
        rows.append({
            "workerId": attempt["workerId"],
            "module": attempt["moduleId"],
            "tier": max((e["tier"] for e in log), default=attempt["tierCeiling"]),
            "events": len(log),
            "supervisorCoSigned": ok,
            "supervisor": supervisor["name"] if supervisor else None,
        })
    return {"site": site, "rows": rows, "generated": date.today().isoformat()}
