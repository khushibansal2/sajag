# Sajag: AR safety training and offline certification

Sajag is our solution for **SIH26041**: AR-based vocational training and safety
certification for workers in Jharkhand's mining, steel and mica sectors. A
worker practises fire, gas and confined-space emergencies on an ordinary
Android phone, is scored on what they actually do, and gets a signed QR
certificate that an inspector can check with no network.

## What it does

**For the worker (Android app)**

- **Train.** Drills for fire and explosion, and for gas leak and confined
  space, in Hindi or English, with every instruction spoken aloud. Each step
  is taught first, then performed; while the worker answers, the app gives no
  hints and no right-or-wrong signals. An action that would kill in a real
  mine stops the drill with a STOP card and is taught again.
- **Three ways to see a drill.**
  - **AR:** the worker taps a real floor or table and the fire, smoke or gas
    stands there, staying in place while they walk around it (ARCore through
    SceneView).
  - **Camera:** hazards drawn over the live camera, on phones without ARCore.
  - **Guided:** a drawn mine gallery, when there is no camera.

  The drill falls back on its own, and the certificate records the mode that
  was actually used.
- **Before every drill.** The worker confirms they are standing in a safe,
  clear area, and the supervisor signs off with a PIN that this worker is the
  one taking the attempt.
- **Passport.** A worker ID card with a QR code (from day one, before any
  certificate), every module's status (certified, renew soon, expired, not
  trained), skill scores, what to practise next and all certificates. Built
  only from certificates whose signature checks out on the phone.
- **Emergency.** The steps for a fire, bad air or a collapsed worker, read
  aloud. It also offers:
  - one tap to call the site control room (the number the supervisor set) or 112;
  - the torch;
  - a quick hazard report.

  It says plainly that it is not an alarm.
- **Report.** Report a hazard with its type, district, place (optional),
  severity and a note, and confirm before sending. The report is saved on the
  phone and delivered when there is network.
- **Verify.** Scan a certificate QR and get a large VALID or NOT VALID result
  with a sound and a vibration, offline.
- **One phone, a whole batch.** Several workers can train on one phone, each
  with their own ID and certificates.

**For the supervisor (same app)**

- **Training centre.** Every drill taken on the phone:
  - pass rate and supervisor sign-offs;
  - scores by module, and skills across all drills;
  - the steps most often missed, the STOP actions, and the recent drills.
- **Risk map.** Jharkhand's 24 districts, coloured by risk from the phone's
  hazard reports and drill results. The rule is printed under the map:
  - a high report adds 4 points, a medium one 2 and a low one 1;
  - each STOP adds 2 points and each failed drill 1;
  - points halve every 30 days.
- **Demo mode.** Sample workers, drills and reports for the training centre
  and the risk map, marked SAMPLE DATA on every screen, never saved and never
  sent. It also sets up a demo supervisor with PIN 1234. The five-minute demo
  is in [docs/demo-script.md](docs/demo-script.md).

**For the training centre**

- **Issuer service** (`server/`): verifies every uploaded bundle, re-scores the
  attempt from raw events, and issues the final certificate only for an
  attempt a supervisor co-signed.
- **Compliance dashboard** (`web/dist/index.html`): a single offline file with
  the statutory training register, due and overdue workers, contractors,
  near-miss risk, attempt replay and an in-browser certificate verifier.
  It currently shows sample data.

## Status

| Part | State | Notes |
|---|---|---|
| Credential codec (Python) | Working, tested | CBOR + base45 + Ed25519, offline verify; every single-character change is rejected |
| Scoring engine (Python) | Working, tested | Competency floors, hard fails, timing calibrated per mode, deterministic |
| Codec cross-checks | 241 JVM assertions, 10 JavaScript tests | Same golden vectors as Python |
| Scenarios | FIRE-01 and GAS-01 authored | `core/scenarios/*.json`, full rubrics for all 10 beats |
| Device authentication | Working, wired into the issuer | Bundle signature, freshness window, replay refusal, revocation, supervisor co-signature |
| Issuer service (FastAPI) | Runs, tested end to end | In-memory store; demo mode and strict mode (see Security) |
| Android app | Worker and verifier flavours | `./gradlew assembleWorkerDebug`; 44 JVM unit tests |
| Drill UI | FIRE-01 and GAS-01, all 10 beats | AR, camera or guided mode; each event records the mode really on screen |
| World-anchored AR | Built, needs a phone test | SceneView 2.2.1 and ARCore: tap a surface, hazards stay anchored; falls back to camera mode if ARCore is missing or fails |
| Passport, hazard report, verifier | Working | Real data from the phone; reports sync to `/v1/hazards` |
| Emergency, training centre, risk map | Working | From the phone's own data; sample data only in demo mode, labelled |
| Localisation | English and Hindi | Santali falls back to Hindi until a native speaker records the lines (see `i18n/T.kt`) |
| Unity content (C#) | Written, not compiled | Richer 3D content for later; AR mode does not need it |
| Browser AR demo | Working offline | `web/ar-demo/index.html`, three.js bundled locally |

## Run it

```bash
make test      # 43 tests: codec, scoring, scenarios
make server    # 47 tests: device authentication, sync contract, issuer API
make jvm       # 241 assertions: Python and JVM codecs agree
make web       # 10 tests: Python and JavaScript codecs agree
make dash      # build the dashboard into web/dist/index.html
make all       # everything above

cd core && python3 cli.py demo   # score, mint, verify, tamper, fail
cd core && python3 cli.py size   # measured wire sizes and QR versions

cd android && ./gradlew testWorkerDebugUnitTest assembleWorkerDebug   # 44 unit tests, then the APK
cd android && ./gradlew installWorkerDebug                            # onto a phone over USB
cd server && uvicorn app.main:app --host 0.0.0.0   # issuer on the LAN
```

The Python tests need only `cryptography`. The issuer API tests also need
`pip install -r server/requirements.txt httpx`; without them those 17 tests
are skipped, not failed.

## Measured, not estimated

`python3 cli.py size`, on a real credential with a real signature:

```
CBOR payload (no signature)                84 bytes
Ed25519 signature                          64 bytes
signed blob                               148 bytes
deflate(signed blob)                      156 bytes   (larger)
on the wire                               149 bytes   (1 marker + 148 raw)

base45 text                               224 chars
+ "SJG1:" prefix                          229 chars
smallest QR at ECC-M                      v9  (53x53 modules)
realistic worst case                      247 chars, still v9
```

**Deflate makes it bigger.** 64 of those bytes are an Ed25519 signature, which
does not compress, and zlib's header costs more than it saves on the other 84.
The codec writes a one-byte marker and stores whichever form is smaller.

**Base45, not base64.** Every base45 character is in the QR alphanumeric set,
which packs 5.5 bits per character. Base64 needs lower case, so the QR falls
back to byte mode at 8 bits per character: roughly 30% more QR, one or two
versions denser on a card that lives in a helmet band.

The full format is in [docs/credential.md](docs/credential.md).

## Repository map

```
core/                      Python reference implementation, the source of truth
  sajag_core/
    cbor_min.py            deterministic CBOR subset
    base45.py              RFC 9285
    credential.py          the credential: model, sign, verify, QR sizing
    scoring.py             the assessment engine
    trustlist.py           signed trust list and revocation list
  scenarios/               fire-01.json, gas-01.json
  tests/                   43 tests
  cli.py                   demo / size / vectors / score / verify

jvm-vectors/               cross-language check, pure JDK (make jvm)

android/                   Kotlin app, the system of record
  capability/TierProbe.kt  what the phone can do (AR, camera, guided) and the tracking watchdog
  credential/              codec mirror, device identity, supervisor co-signature
  data/                    append-only event log (Room), hazard reports, workers and settings
  geo/JharkhandMap.kt      the 24 district outlines (generated, see tools/jharkhand-map)
  insight/                 risk model, training centre statistics, demo sample data
  sync/                    signed bundles and the background sync worker
  ui/                      every screen: onboarding, home, drill, passport, report, verify,
                           emergency, training centre, risk map, settings
  ui/ar/                   world-anchored AR: SceneView scene, hazard models, sprites
  ui/Icons.kt              every icon the app draws (Material Icons, no emoji)

unity/                     Unity-as-a-Library content player (C#)

server/app/security.py     device authentication and co-signature checks
server/app/main.py         issuer: verify, re-score, mint, publish lists, hazards

web/                       compliance dashboard, no bundler
  src/codec/codec.js       third codec implementation, for the browser verifier
  ar-demo/                 browser AR drill, runs offline
docs/                      credential format, keys, motion budget, five-minute demo script
tools/jharkhand-map/       regenerates the district map from DataMeet's Census 2011 shapefile
```

## Design decisions

**Three modes, one rubric (`TierProbe.kt`, `DrillScreen.kt`).** ARCore runs
only on certified phones, a list that is thin below Rs 15,000, and a dark,
dusty gallery defeats markerless tracking on any phone. So the phone's ceiling
is probed once and each drill uses the best mode that works:

- AR, if the phone supports it;
- the camera view, if AR is missing or fails;
- a drawn gallery, with no camera.

All three feed one rubric and one certificate format. Every event records the
mode that was really on screen: AR counts only while the scene stands on a
tracked surface. An attempt is scored at the most conservative mode it used,
exactly as the server does. The certificate carries that mode in its `tier`
field (1 AR, 2 camera, 3 guided). The app and the dashboard show it wherever a
certificate appears.

**The log is append-only (`EventLog.kt`).** There is no UPDATE and no DELETE
for attempt data. Two phones can never disagree about an attempt because
neither edits one, which is what makes carrying bundles phone to phone safe
and why there is no merge logic anywhere.

**The server does not trust the phone's score.** It trusts the phone's signed
events and recomputes the score with the site's current policy. A rooted phone
can lie about what happened in the scene; it cannot lie about what that means.

**Identity without biometrics.** Before a drill, the supervisor's key signs
"attempt id | worker id". The server issues a final certificate only if that
signature verifies. No face scan, no stored biometric, and it works in a dark
gallery.

**Risk you can argue with.** The risk map's colours come from a rule printed
under the map, not a model nobody can explain. A supervisor can tap a district
and see which reports and drills put it there.

**Sample data never pretends.** Demo mode exists so the training centre and
the risk map have something to show before a pilot. Every screen that shows it
says SAMPLE DATA. It is generated on the phone, never written to the database
and never sent.

**No emoji anywhere.** Emoji look different on every phone brand and show as
empty boxes on older Android. Every picture is a vector icon shipped inside
the app and the web pages.

## Security

The issuer runs in one of two modes:

- **Demo (default).** A phone is enrolled the first time it sends a bundle that
  its own key verifies. A supervisor co-signature is accepted if it verifies,
  even if the supervisor was never registered. Admin routes are open unless
  `SAJAG_ADMIN_TOKEN` is set.
- **Strict (`SAJAG_STRICT=1`).** Only phones and supervisors registered through
  `/v1/admin/devices` and `/v1/admin/supervisors` are accepted, and admin
  routes need the `X-Admin-Token` header.

Every bundle is checked for its signature, freshness (7 days, 6 hours of clock
skew) and replay before a single event is stored. Revoking a certificate and
issuing one are admin actions.

All keys in this repository are development keys. Debug builds sign with the
published development key so demo certificates verify across phones; release
builds never trust it. See [docs/key-ceremony.md](docs/key-ceremony.md) for the
pilot procedure.

## Cross-language checks

Three hand-written implementations share one certificate format: Python mints,
Kotlin verifies on the inspector's phone, JavaScript verifies in the dashboard.
If any two disagree by one byte, real certificates fail at a pit mouth. So all
three are checked against the same golden vectors in CI, and the vectors
include Devanagari and Ol Chiki names, all three modes and provisional
certificates. The sync bundle has its own golden test on both sides
(`SyncBundleTest.kt`, `server/tests/test_sync_contract.py`).

## Known gaps

- AR mode is type-checked against SceneView 2.2.1 and ARCore 1.47, but it has
  not yet been run on a phone from this repository. Build it and run one drill
  on the demo phone before the demo. If ARCore fails there, the drill falls
  back to camera mode by itself.
- The device key and supervisor key are kept in app storage; they move to
  Android Keystore before a pilot.
- The issuer keeps everything in memory; it needs a database before a pilot.
- The demo issuer runs over plain HTTP on the site LAN; a pilot needs TLS.
- Santali lines are waiting for a native speaker. Machine translation into
  Santali scores 4.7 to 7.3 BLEU, which is unsafe for a safety instruction.
- Gas thresholds in `gas-01.json` are configuration, not constants, and are to
  be confirmed with a mining-safety reviewer. All drill content has the same
  review pending (see `ModuleContent.kt`).
- The motion layer has not been profiled on real hardware yet
  (`docs/motion.md` has the per-element budget).
- The timing factors per mode (1.0 AR, 1.25 camera, 1.6 guided) are
  placeholders until measured in a pilot.
- The risk map and the training centre use only the phone's own data. The
  issuer does not serve district totals yet.
- The dashboard shows sample data; it does not read from the issuer yet.
- The working name "Sajag" still needs checking against existing DGMS and Coal
  India systems.

## Credits

- District boundaries: the DataMeet India community (Census 2011),
  [CC BY 2.5 India](https://creativecommons.org/licenses/by/2.5/in/),
  simplified (see `tools/jharkhand-map/`).
- Icons: Material Icons by Google, Apache License 2.0.
- 3D rendering in the app: SceneView and Filament, Apache License 2.0.
- Browser AR demo: three.js r128, MIT.

## Licence

MIT.
