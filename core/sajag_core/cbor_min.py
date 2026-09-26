"""
Deterministic CBOR subset — encode/decode for exactly the four major types the
Sajag credential uses: unsigned int, byte string, text string, array.

Why not the `cbor2` package: the credential codec has to exist identically in
Python (issuer), Kotlin (device + verifier) and eventually JS (browser console).
A 120-line deterministic subset ports in an afternoon; a full CBOR library does
not. Everything here is canonical/deterministic by construction — shortest
possible integer encoding, definite-length items only — so the same logical
payload always produces the same bytes, which is what makes the signature
reproducible.

RFC 8949 §4.2 (deterministic encoding) is the reference.
"""

from typing import Any, List, Tuple

MAJOR_UINT = 0
MAJOR_BSTR = 2
MAJOR_TSTR = 3
MAJOR_ARRAY = 4


class CborError(ValueError):
    pass


# ---------------------------------------------------------------- encoding

def _head(major: int, value: int) -> bytes:
    """Type byte + shortest possible length/value encoding."""
    if value < 0:
        raise CborError("negative values are not in this subset")
    mt = major << 5
    if value < 24:
        return bytes([mt | value])
    if value < 0x100:
        return bytes([mt | 24, value])
    if value < 0x10000:
        return bytes([mt | 25]) + value.to_bytes(2, "big")
    if value < 0x100000000:
        return bytes([mt | 26]) + value.to_bytes(4, "big")
    if value < 0x10000000000000000:
        return bytes([mt | 27]) + value.to_bytes(8, "big")
    raise CborError("value too large")


def encode(item: Any) -> bytes:
    if isinstance(item, bool):
        # Guard: bool is an int subclass in Python and would silently encode as
        # 0/1. The credential has no booleans; flags live in a bitfield.
        raise CborError("bool is not in this subset — use a uint bitfield")
    if isinstance(item, int):
        return _head(MAJOR_UINT, item)
    if isinstance(item, (bytes, bytearray)):
        return _head(MAJOR_BSTR, len(item)) + bytes(item)
    if isinstance(item, str):
        raw = item.encode("utf-8")
        return _head(MAJOR_TSTR, len(raw)) + raw
    if isinstance(item, (list, tuple)):
        out = _head(MAJOR_ARRAY, len(item))
        for element in item:
            out += encode(element)
        return out
    raise CborError(f"unsupported type: {type(item).__name__}")


# ---------------------------------------------------------------- decoding

def _read_head(buf: bytes, i: int) -> Tuple[int, int, int]:
    if i >= len(buf):
        raise CborError("truncated: expected a head byte")
    b = buf[i]
    major, extra = b >> 5, b & 0x1F
    i += 1
    if extra < 24:
        return major, extra, i
    width = {24: 1, 25: 2, 26: 4, 27: 8}.get(extra)
    if width is None:
        raise CborError(f"unsupported additional-info {extra} in this subset")
    if i + width > len(buf):
        raise CborError("truncated: incomplete length field")
    return major, int.from_bytes(buf[i:i + width], "big"), i + width


def _decode_at(buf: bytes, i: int) -> Tuple[Any, int]:
    major, value, i = _read_head(buf, i)
    if major == MAJOR_UINT:
        return value, i
    if major in (MAJOR_BSTR, MAJOR_TSTR):
        end = i + value
        if end > len(buf):
            raise CborError("truncated: string runs past end of buffer")
        raw = buf[i:end]
        if major == MAJOR_BSTR:
            return raw, end
        # A tampered QR routinely lands here with bytes that are not valid
        # UTF-8. That must be a rejection, never an uncaught exception — a
        # verifier that crashes on a bad scan is a verifier an inspector stops
        # trusting. Found by the exhaustive single-character tamper test.
        try:
            return raw.decode("utf-8"), end
        except UnicodeDecodeError as exc:
            raise CborError(f"invalid UTF-8 in text string: {exc.reason}") from None
    if major == MAJOR_ARRAY:
        out: List[Any] = []
        for _ in range(value):
            element, i = _decode_at(buf, i)
            out.append(element)
        return out, i
    raise CborError(f"major type {major} is not in this subset")


def decode(buf: bytes) -> Any:
    """Decode one item. Trailing bytes are an error — the credential is exactly
    one array, and silently ignoring a tail is how forgeries get through."""
    item, i = _decode_at(buf, 0)
    if i != len(buf):
        raise CborError(f"{len(buf) - i} trailing byte(s) after the top-level item")
    return item
