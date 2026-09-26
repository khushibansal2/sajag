"""
End-to-end tests of the issuer API: a phone's signed bundle in, a verifiable
certificate out, and every way that path must refuse.

Needs fastapi and httpx (pip install -r requirements.txt httpx). Skipped,
not failed, when they are missing, so the security tests still run on a
laptop with only `cryptography` installed.
"""

import os
import sys
import unittest
from datetime import datetime, timedelta, timezone

HERE = os.path.dirname(__file__)
sys.path.insert(0, os.path.join(HERE, ".."))
sys.path.insert(0, os.path.join(HERE, "..", "..", "core"))

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

try:
    from fastapi.testclient import TestClient
    from app import main
    HAVE_FASTAPI = True
except ImportError:  # pragma: no cover
    HAVE_FASTAPI = False

from app.security import DeviceRegistry, sign_bundle

WORKER = "00112233445566778899aabbccddeeff"


def sample_events(attempt_id):
    """A passing FIRE-01 attempt (the same one `cli.py demo` scores), shaped
    the way the phone logs it."""
    import cli
    events = []
    for seq, ev in enumerate(cli.sample_run()):
        payload = {k: v for k, v in ev.items() if k not in ("beat", "item", "type")}
        events.append({
            "eventId": f"{attempt_id}-E{seq:02d}", "attemptId": attempt_id, "seq": seq,
            "atMs": 1727190000000 + seq * 1000, "beat": ev["beat"], "item": ev.get("item"),
            "type": ev.get("type", "ITEM"), "tier": 2, "payload": payload,
        })
    return events


def phone_bundle(key, attempt_id, worker=WORKER, supervisor_sig=None, issued=None, events=None):
    pub = key.public_key().public_bytes_raw().hex()
    bundle = {
        "workerId": worker, "moduleId": "FIRE-01", "tierCeiling": 2,
        "deviceKeyId": pub[:16], "devicePublicKey": pub,
        "issuedAt": (issued or datetime.now(timezone.utc)).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "supervisorSig": supervisor_sig,
        "events": events if events is not None else sample_events(attempt_id),
    }
    bundle["deviceSig"] = sign_bundle(bundle, key)
    return bundle


def cosign(supervisor_key, attempt_id, worker=WORKER):
    pub = supervisor_key.public_key().public_bytes_raw().hex()
    return pub + "." + supervisor_key.sign(f"{attempt_id}|{worker}".encode()).hex()


@unittest.skipUnless(HAVE_FASTAPI, "fastapi/httpx not installed")
class TestIssuerApi(unittest.TestCase):
    def setUp(self):
        for store in (main.EVENTS, main.ATTEMPTS, main.HAZARDS, main.SUPERVISORS):
            store.clear()
        main.REVOKED.clear()
        main.DEVICES = DeviceRegistry()
        main.STRICT = False
        main.ADMIN_TOKEN = None
        self.client = TestClient(main.app)
        self.phone = Ed25519PrivateKey.generate()
        self.supervisor = Ed25519PrivateKey.generate()

    def post_bundle(self, bundle):
        return self.client.post("/v1/sync/bundle", json=bundle)

    def issue(self, attempt_id, worker=WORKER, headers=None):
        return self.client.post(
            f"/v1/attempts/{attempt_id}/issue",
            params={"name": "Birsa Munda", "employerCode": "CTR-2291", "workerUlidHex": worker},
            headers=headers or {})

    # ------------------------------------------------------------ ingest

    def test_a_new_phone_is_enrolled_on_its_first_signed_bundle(self):
        r = self.post_bundle(phone_bundle(self.phone, "ATT1"))
        self.assertEqual(r.status_code, 200, r.text)
        self.assertEqual(r.json()["accepted"], 18)
        self.assertIsNotNone(main.DEVICES.get(self.phone.public_key().public_bytes_raw().hex()[:16]))

    def test_an_altered_bundle_is_refused(self):
        bundle = phone_bundle(self.phone, "ATT1")
        bundle["events"][3]["payload"]["chosen"] = ["water"]
        r = self.post_bundle(bundle)
        self.assertEqual(r.status_code, 401)
        self.assertIn("signature", r.json()["detail"])
        self.assertEqual(main.EVENTS, {})

    def test_a_bundle_signed_with_someone_elses_key_is_refused(self):
        bundle = phone_bundle(self.phone, "ATT1")
        bundle["deviceSig"] = sign_bundle(bundle, Ed25519PrivateKey.generate())
        self.assertEqual(self.post_bundle(bundle).status_code, 401)
        self.assertIsNone(main.DEVICES.get(bundle["deviceKeyId"]))

    def test_a_replayed_bundle_is_refused(self):
        bundle = phone_bundle(self.phone, "ATT1")
        self.assertEqual(self.post_bundle(bundle).status_code, 200)
        r = self.post_bundle(bundle)
        self.assertEqual(r.status_code, 401)
        self.assertIn("replay", r.json()["detail"])

    def test_a_retried_push_with_a_fresh_timestamp_is_idempotent(self):
        self.post_bundle(phone_bundle(self.phone, "ATT1"))
        later = datetime.now(timezone.utc) + timedelta(seconds=5)
        r = self.post_bundle(phone_bundle(self.phone, "ATT1", issued=later))
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json(), {"accepted": 0, "duplicates": 18})

    def test_a_stale_bundle_is_refused(self):
        old = datetime.now(timezone.utc) - timedelta(days=8)
        r = self.post_bundle(phone_bundle(self.phone, "ATT1", issued=old))
        self.assertEqual(r.status_code, 401)

    def test_strict_mode_refuses_an_unregistered_phone_until_it_is_enrolled(self):
        main.STRICT = True
        main.ADMIN_TOKEN = "t0ken"
        self.assertEqual(self.post_bundle(phone_bundle(self.phone, "ATT1")).status_code, 401)
        pub = self.phone.public_key().public_bytes_raw().hex()
        self.assertEqual(self.client.post("/v1/admin/devices", json={"publicKeyHex": pub}).status_code, 403)
        r = self.client.post("/v1/admin/devices", json={"publicKeyHex": pub, "site": "VTC Dhanbad"},
                             headers={"X-Admin-Token": "t0ken"})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(self.post_bundle(phone_bundle(self.phone, "ATT1")).status_code, 200)

    def test_a_revoked_phone_is_refused(self):
        self.post_bundle(phone_bundle(self.phone, "ATT1"))
        device_id = self.phone.public_key().public_bytes_raw().hex()[:16]
        self.assertEqual(self.client.post(f"/v1/admin/devices/{device_id}/revoke").status_code, 200)
        self.assertEqual(self.post_bundle(phone_bundle(self.phone, "ATT2")).status_code, 401)

    # ------------------------------------------------------------ issue

    def test_no_certificate_without_a_supervisor_cosignature(self):
        self.post_bundle(phone_bundle(self.phone, "ATT1"))
        r = self.issue("ATT1")
        self.assertEqual(r.status_code, 409)
        self.assertIn("co-signature", r.json()["detail"])

    def test_a_cosigned_attempt_gets_a_certificate_that_verifies(self):
        from sajag_core import credential as C
        self.post_bundle(phone_bundle(self.phone, "ATT1", supervisor_sig=cosign(self.supervisor, "ATT1")))
        r = self.issue("ATT1")
        self.assertEqual(r.status_code, 200, r.text)
        body = r.json()
        self.assertTrue(body["issued"])
        self.assertFalse(body["supervisorRegistered"])
        trust = {7: main._ISSUER_KEY.public_key()}
        result = C.verify(body["qr"], trust)
        self.assertTrue(result.ok, result.reason)
        self.assertEqual(result.credential.name, "Birsa Munda")
        self.assertFalse(result.credential.provisional)

    def test_a_cosignature_made_for_another_worker_is_refused(self):
        forged = cosign(self.supervisor, "ATT1", worker="ffffffffffffffffffffffffffffffff")
        self.post_bundle(phone_bundle(self.phone, "ATT1", supervisor_sig=forged))
        r = self.issue("ATT1")
        self.assertEqual(r.status_code, 409)
        self.assertIn("does not verify", r.json()["detail"])

    def test_strict_mode_needs_a_registered_supervisor(self):
        main.ADMIN_TOKEN = "t0ken"
        admin = {"X-Admin-Token": "t0ken"}
        self.post_bundle(phone_bundle(self.phone, "ATT1", supervisor_sig=cosign(self.supervisor, "ATT1")))
        main.STRICT = True
        self.assertEqual(self.issue("ATT1", headers=admin).status_code, 409)
        pub = self.supervisor.public_key().public_bytes_raw().hex()
        r = self.client.post("/v1/admin/supervisors", json={"publicKeyHex": pub, "name": "Asha Devi"}, headers=admin)
        self.assertEqual(r.status_code, 200)
        r = self.issue("ATT1", headers=admin)
        self.assertEqual(r.status_code, 200, r.text)
        self.assertEqual(r.json()["supervisor"], "Asha Devi")

    def test_an_attempt_cannot_be_issued_to_a_different_worker(self):
        self.post_bundle(phone_bundle(self.phone, "ATT1", supervisor_sig=cosign(self.supervisor, "ATT1")))
        r = self.issue("ATT1", worker="ffffffffffffffffffffffffffffffff")
        self.assertEqual(r.status_code, 409)

    def test_the_register_shows_verified_cosignatures_only(self):
        self.post_bundle(phone_bundle(self.phone, "ATT1", supervisor_sig=cosign(self.supervisor, "ATT1")))
        self.post_bundle(phone_bundle(self.phone, "ATT2", supervisor_sig="aa.bb"))
        flags = sorted(r["supervisorCoSigned"] for r in self.client.get("/v1/registers/training").json()["rows"])
        self.assertEqual(flags, [False, True])

    # ------------------------------------------------------------ admin + hazards

    def test_revocation_needs_the_admin_token_when_one_is_set(self):
        main.ADMIN_TOKEN = "t0ken"
        self.assertEqual(self.client.post("/v1/revocations/abc").status_code, 403)
        r = self.client.post("/v1/revocations/abc", headers={"X-Admin-Token": "t0ken"})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(main.REVOKED, ["abc"])

    def test_hazard_reports_are_stored_once(self):
        report = {"id": "01J9HAZARD", "workerId": WORKER, "type": "GAS", "severity": "HIGH",
                  "location": "Level 3, north gallery", "note": "smell near the pump", "createdAtMs": 1727190000000,
                  "district": "DHANBAD", "sent": False}
        batch = {"deviceKeyId": "abcd", "reports": [report]}
        self.assertEqual(self.client.post("/v1/hazards", json=batch).json(), {"accepted": 1, "duplicates": 0})
        self.assertEqual(self.client.post("/v1/hazards", json=batch).json(), {"accepted": 0, "duplicates": 1})
        listed = self.client.get("/v1/hazards").json()
        self.assertEqual(listed["count"], 1)
        self.assertEqual(listed["reports"][0]["location"], "Level 3, north gallery")
        self.assertEqual(listed["reports"][0]["district"], "DHANBAD")

    def test_a_hazard_report_needs_only_a_type(self):
        # The app lets a worker in a hurry send without a place or a note.
        report = {"id": "01J9HAZARD2", "type": "FIRE", "severity": "HIGH", "createdAtMs": 1727190000000}
        r = self.client.post("/v1/hazards", json={"reports": [report]})
        self.assertEqual(r.json(), {"accepted": 1, "duplicates": 0})
        self.assertEqual(self.client.get("/v1/hazards").json()["reports"][0]["location"], "")


if __name__ == "__main__":
    unittest.main()
