# Keys and the key ceremony

## Keys in this repository are development keys

Every key that appears in the code is public and exists so the demo works on
any laptop and phone. None of them may be used in a pilot.

| Key | Where | Used for |
|---|---|---|
| Development issuer key, bytes 0..31, issuer index 7 | `server/app/main.py`, `core/cli.py`, `DeviceIdentity.kt` (debug builds only) | Signing demo certificates |
| Development root key, bytes 32..63 | `server/app/main.py` | Signing the demo trust list |

Keys that are generated, not published:

| Key | Where it is made | Used for |
|---|---|---|
| Device key | On each phone, at first use | Signing sync bundles, and provisional certificates in release builds (issuer index 200) |
| Supervisor key | On a training-centre phone, on the Supervisor screen | Co-signing that a worker is the one taking an attempt |

Debug builds sign and verify with the development issuer key, so a certificate
made on one demo phone verifies on another and in the dashboard. Release
builds never trust the development key.

## Pilot procedure

1. **Root key.** Generate on an offline machine. Store two encrypted copies in
   separate places, split so no single person can use it. It signs only the
   trust list, a few times a year.
2. **Issuer key per training centre.** Generate in the centre's HSM or sealed
   keystore; it never leaves it. Publish its public half in the trust list,
   signed by the root key.
3. **Server.** Run the issuer with `SAJAG_STRICT=1` and a long random
   `SAJAG_ADMIN_TOKEN`, behind TLS (and remove `usesCleartextTraffic` from the
   Android manifest). Replace `SAJAG_ISSUER_KEY_HEX` with the HSM.
4. **Release app.** Build with the centre's issuer public key:
   `./gradlew assembleWorkerRelease -PsajagIssuerPublicKey=<64 hex characters>`.
   The verifier then trusts that key and the phone's own key, nothing else.
5. **Phones.** Enrol each phone at the training centre:
   `POST /v1/admin/devices` with its public key. A lost phone is revoked with
   `POST /v1/admin/devices/{id}/revoke`; bundles it already sent stay valid.
6. **Supervisors.** Each supervisor sets up their PIN on the Supervisor screen,
   which shows a key id. Register the key with `POST /v1/admin/supervisors`.
   In strict mode an attempt co-signed by an unregistered supervisor is not
   issued.
7. **Rotation.** Add the new issuer key to the trust list before retiring the
   old one, so certificates signed with either verify during the change.
   Revoke individual certificates with `POST /v1/revocations/{id}`.

## Before the pilot

- Move the device key and the supervisor key into Android Keystore with user
  authentication. Today both live in app storage; the supervisor key is
  encrypted under a key derived from the PIN, which a determined attacker with
  a copy of the phone's storage can brute-force.
- Replace the in-memory stores in `main.py` with a database.
