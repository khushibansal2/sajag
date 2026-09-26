"""
The sync bundle contract, from the server's side.

GOLDEN is a bundle signed with the development key (bytes 0..31). The Android
unit test SyncBundleTest asserts the phone produces exactly this string for the
same attempt; this test asserts the server accepts exactly these bytes. If
either side's canonical JSON drifts, one of the two fails.
"""

import json
import os
import sys
import unittest
from datetime import datetime, timedelta, timezone

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from app.security import (
    BundleRejected, DeviceRegistry, EnrolledDevice, canonical_bundle_bytes, sign_bundle,
)

GOLDEN = '{"deviceKeyId":"03a107bff3ce10be","devicePublicKey":"03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8","deviceSig":"269a091270297f7229944b9b0e3337366446a46af4188869ca28ff1397e613c2b4e9a814525f2224f69a6244d3348de375c5966d1396843912337c4fb168a707","events":[{"atMs":1727190000000,"attemptId":"01J8Z3Q4R5S6T7V8W9X0Y1Z2A3","beat":"1.2","eventId":"01J8Z3Q4R5S6T7V8W9X0Y1Z2A4","item":"ext-choice","payload":{"chosen":["co2"]},"seq":0,"tier":2,"type":"ITEM"},{"atMs":1727190004200,"attemptId":"01J8Z3Q4R5S6T7V8W9X0Y1Z2A3","beat":"1.3","eventId":"01J8Z3Q4R5S6T7V8W9X0Y1Z2A5","item":"sweep-coverage","payload":{"note":"बिरसा","value":0.82},"seq":1,"tier":2,"type":"ITEM"},{"atMs":1727190009000,"attemptId":"01J8Z3Q4R5S6T7V8W9X0Y1Z2A3","beat":"1.2","eventId":"01J8Z3Q4R5S6T7V8W9X0Y1Z2A6","item":null,"payload":{},"seq":2,"tier":2,"type":"WATER_ON_ELECTRICAL"}],"issuedAt":"2026-09-24T15:00:00Z","moduleId":"FIRE-01","supervisorSig":null,"tierCeiling":2,"workerId":"00112233445566778899aabbccddeeff"}'

DEV_KEY = Ed25519PrivateKey.from_private_bytes(bytes(range(32)))


class TestSyncContract(unittest.TestCase):
    def setUp(self):
        self.bundle = json.loads(GOLDEN)
        self.pub = DEV_KEY.public_key().public_bytes_raw().hex()

    def registry(self):
        return DeviceRegistry([EnrolledDevice(
            device_id=self.pub[:16], public_key_hex=self.pub, site="test",
            enrolled_at=datetime(2026, 1, 1, tzinfo=timezone.utc))])

    def test_golden_is_already_canonical(self):
        unsigned = dict(self.bundle)
        del unsigned["deviceSig"]
        wire = json.dumps(self.bundle, separators=(",", ":"), sort_keys=True, ensure_ascii=False)
        self.assertEqual(wire, GOLDEN)
        self.assertEqual(canonical_bundle_bytes(self.bundle), canonical_bundle_bytes(unsigned))

    def test_signature_is_what_the_server_would_make(self):
        self.assertEqual(sign_bundle(self.bundle, DEV_KEY), self.bundle["deviceSig"])

    def test_server_accepts_the_phone_bundle(self):
        issued = datetime(2026, 9, 24, 15, 0, tzinfo=timezone.utc)
        device = self.registry().verify(self.bundle, now=issued + timedelta(hours=1))
        self.assertEqual(device.device_id, self.pub[:16])

    def test_one_changed_character_is_refused(self):
        tampered = json.loads(GOLDEN.replace('"sweep-coverage"', '"sweep-coveragf"'))
        with self.assertRaises(BundleRejected):
            self.registry().verify(tampered, now=datetime(2026, 9, 24, 16, 0, tzinfo=timezone.utc))


if __name__ == "__main__":
    unittest.main()
