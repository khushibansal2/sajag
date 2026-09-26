"""
Base45 (RFC 9285).

Why base45 and not base64: every character in the base45 alphabet is also in the
QR "alphanumeric" mode charset, which packs 2 characters into 11 bits (5.5
bits/char). Base45 expands 2 bytes into 3 characters, so 16 bits of payload
costs 16.5 bits of QR — about 3% overhead.

Base64 in QR alphanumeric mode is impossible (lowercase letters aren't in the
charset), so it falls back to byte mode at 8 bits/char, where base64's own 33%
expansion is paid in full. Base45 is roughly 30% smaller on the wire for the
same payload, which is one or two QR versions — the difference between a code
that scans off a dirty laminated card and one that doesn't.
"""

ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
_INDEX = {c: i for i, c in enumerate(ALPHABET)}
assert len(ALPHABET) == 45


class Base45Error(ValueError):
    pass


def encode(data: bytes) -> str:
    out = []
    for i in range(0, len(data) - 1, 2):
        n = (data[i] << 8) + data[i + 1]          # big-endian pair
        n, c = divmod(n, 45)          # c = least significant digit
        e, d = divmod(n, 45)
        out.append(ALPHABET[c] + ALPHABET[d] + ALPHABET[e])  # least significant first
    if len(data) % 2:
        d, c = divmod(data[-1], 45)
        out.append(ALPHABET[c] + ALPHABET[d])
    return "".join(out)


def decode(text: str) -> bytes:
    try:
        values = [_INDEX[c] for c in text]
    except KeyError as exc:
        raise Base45Error(f"character {exc.args[0]!r} is not in the base45 alphabet") from None

    if len(values) % 3 == 1:
        raise Base45Error("length % 3 == 1 is never a valid base45 string")

    out = bytearray()
    for i in range(0, len(values) - 2, 3):
        n = values[i] + values[i + 1] * 45 + values[i + 2] * 45 * 45
        if n > 0xFFFF:
            raise Base45Error("triplet overflows two bytes")
        out += n.to_bytes(2, "big")
    if len(values) % 3 == 2:
        n = values[-2] + values[-1] * 45
        if n > 0xFF:
            raise Base45Error("final pair overflows one byte")
        out.append(n)
    return bytes(out)
