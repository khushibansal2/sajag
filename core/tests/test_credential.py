"""Credential codec tests. These are the ones that matter: if any of these
break, a forged certificate passes or a real one fails at a pit mouth."""

import sys, os, unittest
from datetime import date, timedelta

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from sajag_core import base45, cbor_min, credential as C
from sajag_core.trustlist import (
    Issuer, build_trust_list, build_revocation_list,
    load_trust_list, load_revocation_list, TrustError,
)

ISSUER_KEY = Ed25519PrivateKey.generate()
ROOT_KEY = Ed25519PrivateKey.generate()
TRUST = {7: ISSUER_KEY.public_key()}


def make_cred(**over) -> C.Credential:
    base = dict(
        issuer_index=7,
        subject_id=bytes(range(16)),
        name="Ramesh Kumar Mahato",
        employer_code="CTR-2291",
        module="FIRE-01",
        tier=2,
        score=88,
        competencies={"HAZ-ID": 91, "EQP-SEL": 100, "SEQ": 84,
                      "EGR": 79, "TECH": 86, "KNW": 83},
        attempt_digest=bytes.fromhex("9f2c4a1b8e7d6c5b4a39281706f5e4d3"),
        not_before=date(2026, 12, 14),
        expires=date(2027, 12, 13),
    )
    base.update(over)
    return C.Credential(**base)


class TestCborSubset(unittest.TestCase):
    def test_round_trip(self):
        for item in [0, 23, 24, 255, 256, 65535, 65536, b"", b"\x00\xff",
                     "santali", ["a", 1, b"\x02", ["nested", 9]]]:
            self.assertEqual(cbor_min.decode(cbor_min.encode(item)), item)

    def test_shortest_form(self):
        self.assertEqual(cbor_min.encode(23), b"\x17")
        self.assertEqual(cbor_min.encode(24), b"\x18\x18")
        self.assertEqual(len(cbor_min.encode(65535)), 3)

    def test_deterministic(self):
        cred = make_cred()
        self.assertEqual(cbor_min.encode(cred.to_payload()),
                         cbor_min.encode(cred.to_payload()))

    def test_trailing_bytes_rejected(self):
        with self.assertRaises(cbor_min.CborError):
            cbor_min.decode(cbor_min.encode(1) + b"\x01")

    def test_bool_rejected(self):
        with self.assertRaises(cbor_min.CborError):
            cbor_min.encode(True)


class TestBase45(unittest.TestCase):
    def test_round_trip_all_lengths(self):
        for n in range(0, 260):
            data = bytes((i * 37 + 11) % 256 for i in range(n))
            self.assertEqual(base45.decode(base45.encode(data)), data)

    def test_rfc_vectors(self):
        self.assertEqual(base45.encode(b"AB"), "BB8")
        self.assertEqual(base45.encode(b"Hello!!"), "%69 VD92EX0")
        self.assertEqual(base45.decode("%69 VD92EX0"), b"Hello!!")

    def test_bad_char(self):
        with self.assertRaises(base45.Base45Error):
            base45.decode("abc")

    def test_impossible_length(self):
        with self.assertRaises(base45.Base45Error):
            base45.decode("BB8B")


class TestSignVerify(unittest.TestCase):
    def test_valid_round_trip(self):
        qr = C.sign(make_cred(), ISSUER_KEY)
        res = C.verify(qr, TRUST, today=date(2027, 1, 5))
        self.assertTrue(res.ok, res.reason)
        self.assertEqual(res.credential.name, "Ramesh Kumar Mahato")
        self.assertEqual(res.credential.competencies["EQP-SEL"], 100)
        self.assertEqual(res.credential.tier, 2)

    def test_every_single_byte_flip_is_caught(self):
        """The tamper test from the demo script, exhaustively. Flipping any one
        character of the QR must never produce a valid certificate."""
        qr = C.sign(make_cred(), ISSUER_KEY)
        accepted = 0
        for i in range(len(qr) - len(C.PREFIX)):
            pos = len(C.PREFIX) + i
            original = qr[pos]
            swap = base45.ALPHABET[(base45.ALPHABET.index(original) + 1) % 45] \
                if original in base45.ALPHABET else "0"
            tampered = qr[:pos] + swap + qr[pos + 1:]
            if C.verify(tampered, TRUST, today=date(2027, 1, 5)).ok:
                accepted += 1
        self.assertEqual(accepted, 0, f"{accepted} tampered codes were accepted")

    def test_wrong_issuer_key_rejected(self):
        qr = C.sign(make_cred(), Ed25519PrivateKey.generate())
        res = C.verify(qr, TRUST, today=date(2027, 1, 5))
        self.assertFalse(res.ok)
        self.assertIn("altered", res.reason)

    def test_unknown_issuer_index(self):
        qr = C.sign(make_cred(issuer_index=99), ISSUER_KEY)
        self.assertIn("Unknown issuer", C.verify(qr, TRUST, today=date(2027, 1, 5)).reason)

    def test_expiry_and_not_yet_valid(self):
        qr = C.sign(make_cred(), ISSUER_KEY)
        self.assertIn("Expired", C.verify(qr, TRUST, today=date(2028, 1, 1)).reason)
        self.assertIn("Not valid until", C.verify(qr, TRUST, today=date(2026, 1, 1)).reason)

    def test_revoked(self):
        cred = make_cred()
        qr = C.sign(cred, ISSUER_KEY)
        res = C.verify(qr, TRUST, revoked={cred.credential_id}, today=date(2027, 1, 5))
        self.assertFalse(res.ok)
        self.assertIn("revoked", res.reason)

    def test_provisional_warns_but_passes(self):
        qr = C.sign(make_cred(provisional=True), ISSUER_KEY)
        res = C.verify(qr, TRUST, today=date(2027, 1, 5))
        self.assertTrue(res.ok)
        self.assertTrue(any("Provisional" in w for w in res.warnings))

    def test_guided_mode_is_disclosed(self):
        qr = C.sign(make_cred(tier=3), ISSUER_KEY)
        res = C.verify(qr, TRUST, today=date(2027, 1, 5))
        self.assertTrue(res.ok)
        self.assertTrue(any("guided 2D mode" in w for w in res.warnings))

    def test_not_a_sajag_code(self):
        self.assertIn("Not a Sajag", C.verify("https://example.com/cert/123", TRUST).reason)

    def test_garbage_never_crashes(self):
        for junk in ["SJG1:", "SJG1:ZZZZ", "SJG1:" + "0" * 300, "SJG1:%%%%%%"]:
            res = C.verify(junk, TRUST)
            self.assertFalse(res.ok)


class TestTrustList(unittest.TestCase):
    def test_round_trip(self):
        raw = build_trust_list(
            [Issuer(7, "VTC Dhanbad", ISSUER_KEY.public_key().public_bytes_raw().hex())],
            date(2026, 12, 1), ROOT_KEY)
        keys, as_of = load_trust_list(raw, ROOT_KEY.public_key())
        self.assertIn(7, keys)
        self.assertEqual(as_of, date(2026, 12, 1))

    def test_tampered_trust_list_refused(self):
        raw = build_trust_list(
            [Issuer(7, "VTC Dhanbad", ISSUER_KEY.public_key().public_bytes_raw().hex())],
            date(2026, 12, 1), ROOT_KEY)
        with self.assertRaises(TrustError):
            load_trust_list(raw.replace("VTC Dhanbad", "VTC Dhanbadx"), ROOT_KEY.public_key())

    def test_revocation_round_trip(self):
        raw = build_revocation_list(["aa" * 16], date(2026, 12, 2), ISSUER_KEY)
        ids, as_of = load_revocation_list(raw, ISSUER_KEY.public_key())
        self.assertEqual(ids, {"aa" * 16})
        self.assertEqual(as_of, date(2026, 12, 2))


class TestSize(unittest.TestCase):
    """The size budget is a real constraint: it decides the QR version, which
    decides whether the code scans off a scuffed laminated card."""

    def test_fits_in_a_printable_qr(self):
        qr = C.sign(make_cred(), ISSUER_KEY)
        version = C.qr_version_for(qr)
        self.assertIsNotNone(version)
        self.assertLessEqual(version, 12, f"QR version {version} is too dense for a helmet card")

    def test_long_name_still_fits(self):
        qr = C.sign(make_cred(name="Shatrughan Prasad Bhattach", employer_code="CTR-99284731"), ISSUER_KEY)
        self.assertLessEqual(C.qr_version_for(qr), 12)


if __name__ == "__main__":
    unittest.main(verbosity=2)


class TestScriptBudgets(unittest.TestCase):
    """Names in Devanagari and Ol Chiki are 3 UTF-8 bytes per character. The
    budget is in bytes so a long Hindi name cannot silently push the QR two
    versions denser than an equivalent Latin one."""

    def test_devanagari_name_truncates_on_a_character_boundary(self):
        hindi = "रमेश कुमार महतो सिंह यादव चौधरी"
        cred = make_cred(name=hindi)
        stored = cred.to_payload()[3]
        self.assertLessEqual(len(stored.encode("utf-8")), C.NAME_MAX_BYTES)
        stored.encode("utf-8").decode("utf-8")          # must not be half a character
        self.assertTrue(hindi.startswith(stored))

    def test_ol_chiki_name_truncates_cleanly(self):
        olck = "ᱥᱟᱱᱛᱟᱲᱤ ᱠᱚᱲᱟ ᱢᱟᱬᱡᱷᱤ ᱦᱮᱢᱵᱨᱚᱢ"
        cred = make_cred(name=olck)
        stored = cred.to_payload()[3]
        self.assertLessEqual(len(stored.encode("utf-8")), C.NAME_MAX_BYTES)
        stored.encode("utf-8").decode("utf-8")

    def test_devanagari_credential_still_fits_a_printable_qr(self):
        qr = C.sign(make_cred(name="रमेश कुमार महतो सिंह यादव"), ISSUER_KEY)
        self.assertLessEqual(C.qr_version_for(qr), 12)

    def test_truncation_never_splits_a_codepoint(self):
        for n in range(1, 40):
            out = C.truncate_utf8("रमेश कुमार महतो", n)
            out.encode("utf-8").decode("utf-8")
            self.assertLessEqual(len(out.encode("utf-8")), n)
