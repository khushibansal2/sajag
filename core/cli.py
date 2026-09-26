#!/usr/bin/env python3
"""
Sajag reference CLI — score an attempt, mint a credential, verify a QR, and
measure the wire size. This is the tool to run in front of a jury when they ask
"is the certificate really verifiable offline?"

    python3 cli.py demo                 end-to-end: score -> mint -> verify -> tamper
    python3 cli.py size                 measured wire sizes and QR versions
    python3 cli.py vectors [n]          golden vectors for the JVM port
    python3 cli.py score <events.json> <scenario.json>
    python3 cli.py verify "<QR text>"   verify against the demo trust list
"""

import json
import os
import sys
from datetime import date, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from sajag_core import base45, cbor_min, credential as C
from sajag_core.scoring import score_attempt, canonical_event_log

HERE = os.path.dirname(os.path.abspath(__file__))
SCEN = os.path.join(HERE, "scenarios")

# Demo keys only. Real issuer keys are generated in a documented ceremony and
# never leave the training centre's keystore. See docs/key-ceremony.md.
DEMO_ISSUER = Ed25519PrivateKey.from_private_bytes(bytes(range(32)))
DEMO_TRUST = {7: DEMO_ISSUER.public_key()}

# The credential's "tier" field is the mode the drill ran in.
MODES = {1: "AR", 2: "camera", 3: "guided"}


def load_scenario(name):
    with open(os.path.join(SCEN, name), encoding="utf-8") as fh:
        return json.load(fh)


def sample_run():
    return [
        {"beat": "1.1", "item": "fire-class", "chosen": ["electrical"]},
        {"beat": "1.1", "item": "classify-latency", "ms": 4200},
        {"beat": "1.1", "item": "safe-standoff", "value": 3.1},
        {"beat": "1.2", "item": "ext-choice", "chosen": ["co2"]},
        {"beat": "1.2", "item": "gauge-check", "value": True},
        {"beat": "1.2", "item": "select-latency", "ms": 3800},
        {"beat": "1.3", "item": "pass-sequence", "order": ["pull", "aim", "squeeze", "sweep"]},
        {"beat": "1.3", "item": "aim-angle", "value": 9.0},
        {"beat": "1.3", "item": "sweep-coverage", "value": 0.82},
        {"beat": "1.3", "item": "standoff", "value": 2.4},
        {"beat": "1.4", "item": "exit-choice", "chosen": ["exit_north"]},
        {"beat": "1.4", "item": "wall-contact", "value": 0.88},
        {"beat": "1.4", "item": "head-height", "value": 0.9},
        {"beat": "1.4", "item": "door-heat-check", "value": True},
        {"beat": "1.4", "item": "egress-latency", "ms": 21000},
        {"beat": "1.5", "item": "response-order",
         "order": ["alarm", "trip_conveyor", "alert_buddy", "assembly_point", "report_headcount"]},
        {"beat": "1.5", "item": "alarm-latency", "ms": 7400},
        {"beat": "1.5", "item": "written-check",
         "chosen": ["q1_b", "q2_a", "q3_c", "q4_a", "q5_b", "q6_c"]},
    ]


def mint(events, scenario, tier=2, provisional=False):
    scenario = dict(scenario, _tier=tier)
    result = score_attempt(events, scenario)
    today = date.today()
    cred = C.Credential(
        issuer_index=7,
        subject_id=bytes(range(16)),
        name="Ramesh Kumar Mahato",
        employer_code="CTR-2291",
        module=scenario["id"],
        tier=tier,
        score=result.aggregate,
        competencies=result.competencies,
        attempt_digest=C.attempt_digest(canonical_event_log(events)),
        not_before=today,
        expires=today + timedelta(days=365),
        provisional=provisional,
    )
    return result, cred, C.sign(cred, DEMO_ISSUER)


def cmd_demo():
    scenario = load_scenario("fire-01.json")
    events = sample_run()

    print("\n─── 1. score the attempt ──────────────────────────────────────")
    result, cred, qr = mint(events, scenario, tier=2)
    print(result.summary())
    print(f"   attempt digest  {cred.credential_id}")

    print("\n─── 2. mint the credential ────────────────────────────────────")
    print(f"   QR text ({len(qr)} chars, QR version {C.qr_version_for(qr)} at ECC-M)")
    print(f"   {qr}")

    print("\n─── 3. verify it, offline ─────────────────────────────────────")
    res = C.verify(qr, DEMO_TRUST)
    print(f"   {'VALID' if res.ok else 'REJECTED'} — {res.reason}")
    print(f"   {res.credential.name} · {res.credential.module} · {MODES[res.credential.tier]} mode"
          f" · {res.credential.score}")
    print("   " + "  ".join(f"{k}:{v}" for k, v in res.credential.competencies.items()))
    for w in res.warnings:
        print(f"   note: {w}")

    print("\n─── 4. tamper with one character ──────────────────────────────")
    i = len(C.PREFIX) + 20
    swapped = base45.ALPHABET[(base45.ALPHABET.index(qr[i]) + 1) % 45]
    bad = qr[:i] + swapped + qr[i + 1:]
    res = C.verify(bad, DEMO_TRUST)
    print(f"   {'VALID' if res.ok else 'REJECTED'} — {res.reason}")

    print("\n─── 5. the same worker fails the cylinder choice ──────────────")
    broken = list(events)
    broken[3] = {"beat": "1.2", "item": "ext-choice", "chosen": ["water"]}
    broken.append({"beat": "1.2", "type": "WATER_ON_ELECTRICAL"})
    bad_result = score_attempt(dict_tier(scenario, 2), broken) if False else \
        score_attempt(broken, dict(scenario, _tier=2))
    print(bad_result.summary())
    for r in bad_result.reasons:
        print(f"   ! {r}")
    print(f"   replay required at beat(s): {', '.join(bad_result.replay_beats)}\n")


def dict_tier(scenario, tier):
    return dict(scenario, _tier=tier)


def cmd_size():
    scenario = load_scenario("fire-01.json")
    events = sample_run()
    _, cred, qr = mint(events, scenario, tier=2)

    payload = cbor_min.encode(cred.to_payload())
    # Use the REAL signature, not a placeholder. Zeroed bytes compress to
    # nothing and would make deflate look far better than it is.
    signed = payload + DEMO_ISSUER.sign(payload)
    import zlib
    deflated = zlib.compress(signed, 9)

    rows = [
        ("CBOR payload (no signature)", len(payload)),
        ("Ed25519 signature", C.SIG_LEN),
        ("signed blob", len(signed)),
        ("deflate(signed blob)", len(deflated)),
        ("on the wire (marker + best of the two)", len(base45.decode(qr[len(C.PREFIX):]))),
    ]
    print("\nbytes")
    for label, n in rows:
        print(f"  {label:<42} {n:>5}")
    print(f"\n  base45 text                                {len(qr) - len(C.PREFIX):>5} chars")
    print(f"  + \"{C.PREFIX}\" prefix                            {len(qr):>5} chars total")
    print(f"  smallest QR at ECC-M                       v{C.qr_version_for(qr)}"
          f"  ({21 + 4 * (C.qr_version_for(qr) - 1)}x{21 + 4 * (C.qr_version_for(qr) - 1)} modules)")
    print(f"\n  deflate {'helps' if len(deflated) < len(signed) else 'does NOT help'}: "
          f"{len(deflated)} vs {len(signed)} bytes — "
          f"{'compressed' if len(deflated) < len(signed) else 'stored uncompressed'}")
    print("  (64 of those bytes are an incompressible signature, which is why.)\n")

    # Worst case must use high-entropy ids too — all-zero bytes would compress
    # and quietly produce a smaller "worst case" than the typical one.
    import os as _os
    worst = C.sign(
        C.Credential(
            issuer_index=7, subject_id=_os.urandom(16), name="Shatrughan Prasad Bhattach",
            employer_code="CTR-99284731", module="FIRE-01", tier=1, score=100,
            competencies={c: 100 for c in C.COMPETENCIES},
            attempt_digest=_os.urandom(16), not_before=date(2026, 1, 1),
            expires=date(2030, 1, 1),
        ), DEMO_ISSUER)
    print(f"  worst case (max-length name and employer code): "
          f"{len(worst)} chars, QR v{C.qr_version_for(worst)}\n")


def cmd_score(events_path, scenario_path):
    with open(events_path, encoding="utf-8") as fh:
        events = json.load(fh)
    with open(scenario_path, encoding="utf-8") as fh:
        scenario = json.load(fh)
    result = score_attempt(events, scenario)
    print(result.summary())
    for item in result.items:
        print(f"  {item.item_id:<24} {item.competency:<8} {item.score:6.1f}  {item.detail}")
    for r in result.reasons:
        print(f"  ! {r}")


def cmd_verify(qr):
    res = C.verify(qr, DEMO_TRUST)
    print(f"{'VALID' if res.ok else 'REJECTED'} — {res.reason}")
    if res.credential:
        c = res.credential
        print(f"  {c.name} · {c.employer_code} · {c.module} · {MODES[c.tier]} mode · {c.score}")
        print(f"  valid {c.not_before} to {c.expires}")
    for w in res.warnings:
        print(f"  note: {w}")


def cmd_vectors(n=20):
    """Emit golden vectors for the JVM port. Any Kotlin/Java implementation must
    decode these to exactly the same values, or the server mints certificates
    the inspector's phone cannot read."""
    import random
    from datetime import date as _d
    rng = random.Random(20260912)
    names = ["Ramesh Kumar Mahato", "Shatrughan Prasad Bhattach", "Sita Devi",
             "रमेश कुमार महतो", "ᱥᱟᱱᱛᱟᱲᱤ ᱠᱚᱲᱟ", "B. Murugan", "Anil"]
    out = {"issuerPublicKeyHex": DEMO_ISSUER.public_key().public_bytes_raw().hex(),
           "issuerIndex": 7, "vectors": []}
    for i in range(n):
        cred = C.Credential(
            issuer_index=7,
            subject_id=bytes(rng.randrange(256) for _ in range(16)),
            name=names[i % len(names)],
            employer_code=f"CTR-{rng.randrange(1000, 99999)}",
            module=C.MODULES[i % 2],
            tier=(i % 3) + 1,
            score=rng.randrange(0, 101),
            competencies={c: rng.randrange(0, 101) for c in C.COMPETENCIES},
            attempt_digest=bytes(rng.randrange(256) for _ in range(16)),
            not_before=_d(2026, 1 + i % 12, 1 + i % 28),
            expires=_d(2027, 1 + i % 12, 1 + i % 28),
            provisional=(i % 4 == 0),
        )
        p = cred.to_payload()
        out["vectors"].append({
            "qr": C.sign(cred, DEMO_ISSUER),
            "expect": {
                "issuerIndex": p[1], "subjectIdHex": p[2].hex(), "name": p[3],
                "employerCode": p[4], "module": cred.module, "tier": p[6],
                "score": p[7], "competencies": cred.competencies,
                "attemptDigestHex": p[9].hex(),
                "notBefore": cred.not_before.isoformat(),
                "expires": cred.expires.isoformat(),
                "provisional": cred.provisional,
            },
        })
    print(json.dumps(out, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "demo"
    if cmd == "demo":
        cmd_demo()
    elif cmd == "vectors":
        cmd_vectors(int(sys.argv[2]) if len(sys.argv) > 2 else 20)
    elif cmd == "size":
        cmd_size()
    elif cmd == "score":
        cmd_score(sys.argv[2], sys.argv[3])
    elif cmd == "verify":
        cmd_verify(sys.argv[2])
    else:
        print(__doc__)
        sys.exit(2)


