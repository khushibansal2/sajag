"""
Tests for device authentication.

Every one of these is a way a forged certificate could otherwise be produced.
They run with no database, no web framework and no network — which is the whole
reason security.py was written dependency-free.
"""

import os
import sys
import unittest
from datetime import datetime, timedelta, timezone

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from app.security import (
    CLOCK_SKEW, MAX_BUNDLE_AGE, BundleRejected, DeviceRegistry, EnrolledDevice,
    canonical_bundle_bytes, sign_bundle, verify_supervisor_cosignature,
)

NOW = datetime(2026, 12, 14, 9, 0, tzinfo=timezone.utc)


def make_registry():
    key = Ed25519PrivateKey.generate()
    reg = DeviceRegistry()
    reg.enrol(EnrolledDevice(
        device_id="dev-jharia-0447",
        public_key_hex=key.public_key().public_bytes_raw().hex(),
        site="Jharia VTC",
        enrolled_at=NOW - timedelta(days=30),
    ))
    return reg, key


def make_bundle(issued=None, worker="wkr-001", events=None):
    return {
        "deviceKeyId": "dev-jharia-0447",
        "issuedAt": (issued or NOW).isoformat(),
        "workerId": worker,
        "moduleId": "FIRE-01",
        "tierCeiling": 2,
        "events": events or [
            {"eventId": "01JD8Q0000", "attemptId": "att-1", "seq": 0, "atMs": 1,
             "beat": "1.2", "item": "ext-choice", "type": "ITEM", "tier": 2,
             "payload": {"chosen": ["co2"]}},
        ],
    }


def signed(key, **kw):
    b = make_bundle(**kw)
    b["deviceSig"] = sign_bundle(b, key)
    return b


class TestHappyPath(unittest.TestCase):
    def test_a_properly_signed_bundle_is_accepted(self):
        reg, key = make_registry()
        device = reg.verify(signed(key), now=NOW)
        self.assertEqual(device.device_id, "dev-jharia-0447")


class TestForgery(unittest.TestCase):
    def test_unsigned_bundle_rejected(self):
        reg, _ = make_registry()
        with self.assertRaises(BundleRejected) as cm:
            reg.verify(make_bundle(), now=NOW)
        self.assertIn("deviceSig", str(cm.exception))

    def test_bundle_signed_by_an_unenrolled_device_rejected(self):
        reg, _ = make_registry()
        attacker = Ed25519PrivateKey.generate()
        with self.assertRaises(BundleRejected) as cm:
            reg.verify(signed(attacker), now=NOW)
        self.assertIn("does not match", str(cm.exception))

    def test_unknown_device_id_rejected(self):
        reg, key = make_registry()
        b = signed(key)
        b["deviceKeyId"] = "dev-someone-elses"
        with self.assertRaises(BundleRejected) as cm:
            reg.verify(b, now=NOW)
        self.assertIn("not enrolled", str(cm.exception))

    def test_every_mutated_field_is_caught(self):
        """The attack that matters: take a real bundle for a worker who failed,
        change one thing, resubmit. Each of these must be refused."""
        reg, key = make_registry()
        mutations = [
            ("workerId", "wkr-someone-else"),
            ("moduleId", "GAS-01"),
            ("tierCeiling", 1),
            ("issuedAt", (NOW - timedelta(days=1)).isoformat()),
        ]
        for field, value in mutations:
            with self.subTest(field=field):
                b = signed(key)
                b[field] = value
                with self.assertRaises(BundleRejected):
                    reg.verify(b, now=NOW)

    def test_mutating_a_nested_event_is_caught(self):
        reg, key = make_registry()
        b = signed(key)
        b["events"][0]["payload"]["chosen"] = ["water"]
        with self.assertRaises(BundleRejected) as cm:
            reg.verify(b, now=NOW)
        self.assertIn("does not match", str(cm.exception))

    def test_appending_an_event_is_caught(self):
        reg, key = make_registry()
        b = signed(key)
        b["events"].append({"eventId": "01JD8Q0001", "attemptId": "att-1", "seq": 1,
                            "atMs": 2, "beat": "1.2", "item": "gauge-check",
                            "type": "ITEM", "tier": 2, "payload": {"value": True}})
        with self.assertRaises(BundleRejected):
            reg.verify(b, now=NOW)

    def test_truncated_signature_rejected_not_crashed(self):
        reg, key = make_registry()
        b = signed(key)
        b["deviceSig"] = b["deviceSig"][:20]
        with self.assertRaises(BundleRejected):
            reg.verify(b, now=NOW)

    def test_non_hex_signature_rejected_not_crashed(self):
        reg, key = make_registry()
        b = signed(key)
        b["deviceSig"] = "not-hex-at-all"
        with self.assertRaises(BundleRejected) as cm:
            reg.verify(b, now=NOW)
        self.assertIn("hex", str(cm.exception))


class TestReplayAndFreshness(unittest.TestCase):
    def test_replaying_the_same_bundle_is_refused(self):
        reg, key = make_registry()
        b = signed(key)
        reg.verify(b, now=NOW)
        with self.assertRaises(BundleRejected) as cm:
            reg.verify(b, now=NOW)
        self.assertIn("replay", str(cm.exception))

    def test_reordering_keys_does_not_dodge_replay_detection(self):
        """The digest is over the signed canonical bytes, so shuffling JSON key
        order produces the same digest and is still caught."""
        reg, key = make_registry()
        b = signed(key)
        reg.verify(b, now=NOW)
        shuffled = {k: b[k] for k in reversed(list(b.keys()))}
        with self.assertRaises(BundleRejected) as cm:
            reg.verify(shuffled, now=NOW)
        self.assertIn("replay", str(cm.exception))

    def test_a_stale_bundle_is_refused(self):
        reg, key = make_registry()
        old = NOW - MAX_BUNDLE_AGE - timedelta(days=1)
        with self.assertRaises(BundleRejected) as cm:
            reg.verify(signed(key, issued=old), now=NOW)
        self.assertIn("replay", str(cm.exception))

    def test_a_courier_carrying_a_bundle_for_days_still_syncs(self):
        """The relay case: a phone underground all week, carried out on
        someone else's handset. This must work or the offline story is a lie."""
        reg, key = make_registry()
        six_days_ago = NOW - timedelta(days=6)
        device = reg.verify(signed(key, issued=six_days_ago), now=NOW)
        self.assertEqual(device.device_id, "dev-jharia-0447")

    def test_a_phone_with_a_fast_clock_is_tolerated(self):
        reg, key = make_registry()
        ahead = NOW + CLOCK_SKEW - timedelta(minutes=5)
        self.assertIsNotNone(reg.verify(signed(key, issued=ahead), now=NOW))

    def test_a_wildly_future_bundle_is_refused(self):
        reg, key = make_registry()
        ahead = NOW + CLOCK_SKEW + timedelta(hours=2)
        with self.assertRaises(BundleRejected) as cm:
            reg.verify(signed(key, issued=ahead), now=NOW)
        self.assertIn("future", str(cm.exception))


class TestRevocation(unittest.TestCase):
    def test_a_revoked_handset_cannot_submit(self):
        reg, key = make_registry()
        reg.revoke("dev-jharia-0447")
        with self.assertRaises(BundleRejected) as cm:
            reg.verify(signed(key), now=NOW)
        self.assertIn("revoked", str(cm.exception))

    def test_double_enrolment_refused(self):
        reg, key = make_registry()
        with self.assertRaises(BundleRejected):
            reg.enrol(EnrolledDevice("dev-jharia-0447", key.public_key()
                                     .public_bytes_raw().hex(), "x", NOW))


class TestCanonicalForm(unittest.TestCase):
    def test_key_order_does_not_change_the_bytes(self):
        a = {"b": 1, "a": 2, "events": [], "deviceSig": "xx"}
        b = {"a": 2, "events": [], "b": 1, "deviceSig": "yy"}
        self.assertEqual(canonical_bundle_bytes(a), canonical_bundle_bytes(b))

    def test_signature_field_is_excluded(self):
        self.assertNotIn(b"deviceSig", canonical_bundle_bytes({"deviceSig": "ab", "x": 1}))

    def test_floats_are_rounded_so_arm64_and_x86_agree(self):
        a = canonical_bundle_bytes({"v": 1.23456789012})
        b = canonical_bundle_bytes({"v": 1.23456789099})
        self.assertEqual(a, b)

    def test_devanagari_is_not_escaped(self):
        raw = canonical_bundle_bytes({"name": "रमेश"})
        self.assertIn("रमेश".encode("utf-8"), raw)
        self.assertNotIn(b"\\u", raw)

    def test_nested_dict_keys_are_sorted_too(self):
        a = canonical_bundle_bytes({"e": [{"z": 1, "a": 2}]})
        b = canonical_bundle_bytes({"e": [{"a": 2, "z": 1}]})
        self.assertEqual(a, b)


class TestSupervisorCosignature(unittest.TestCase):
    def test_valid_cosignature_accepted(self):
        sup = Ed25519PrivateKey.generate()
        sig = sup.sign(b"att-1|wkr-001").hex()
        self.assertTrue(verify_supervisor_cosignature(
            "att-1", "wkr-001", sig, sup.public_key()))

    def test_cosignature_for_a_different_worker_rejected(self):
        """The proxy attack: a supervisor co-signs for worker A, someone tries
        to reuse it for worker B."""
        sup = Ed25519PrivateKey.generate()
        sig = sup.sign(b"att-1|wkr-001").hex()
        self.assertFalse(verify_supervisor_cosignature(
            "att-1", "wkr-002", sig, sup.public_key()))

    def test_cosignature_from_a_stranger_rejected(self):
        sup, stranger = Ed25519PrivateKey.generate(), Ed25519PrivateKey.generate()
        sig = stranger.sign(b"att-1|wkr-001").hex()
        self.assertFalse(verify_supervisor_cosignature(
            "att-1", "wkr-001", sig, sup.public_key()))

    def test_garbage_cosignature_does_not_crash(self):
        sup = Ed25519PrivateKey.generate()
        for junk in ["", "zz", "00" * 64, "nothex"]:
            self.assertFalse(verify_supervisor_cosignature(
                "att-1", "wkr-001", junk, sup.public_key()))


if __name__ == "__main__":
    unittest.main(verbosity=2)
