import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.*;

/**
 * Cross-language proof for the credential codec.
 *
 * The server mints certificates with the Python implementation; the
 * inspector's phone verifies them with the Kotlin one. If those two ever
 * disagree by a single byte, real certificates start failing at a pit mouth
 * and nobody finds out until someone is turned away from a shift.
 *
 * This file is the JVM half of that proof, written against nothing but the
 * JDK — no Android, no Gradle, no BouncyCastle. JDK 15+ ships Ed25519, so the
 * whole verification path runs with `javac` and `java`. The Kotlin code in
 * android/.../CredentialCodec.kt is a line-for-line transliteration of what is
 * below; if this passes, the Kotlin decoder is correct up to Kotlin/Java
 * syntax rather than up to hope.
 *
 * Run:
 *   cd core && python3 cli.py vectors 20 > ../jvm-vectors/vectors.json
 *   cd ../jvm-vectors && javac CodecVectorTest.java && java CodecVectorTest
 */
public class CodecVectorTest {

    static final String PREFIX = "SJG1:";
    static final int SIG_LEN = 64;
    static final LocalDate EPOCH = LocalDate.of(2020, 1, 1);
    static final String[] COMPETENCIES = {"HAZ-ID", "EQP-SEL", "SEQ", "EGR", "TECH", "KNW"};
    static final String[] MODULES = {"FIRE-01", "GAS-01", "MACH-01", "HEIGHT-01", "ELEC-01"};
    static final String B45 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

    static int checks = 0, failures = 0;

    public static void main(String[] args) throws Exception {
        Path tsv = Paths.get("vectors.tsv");
        if (!Files.exists(tsv)) {
            System.err.println("vectors.tsv not found — generate it first (see the header).");
            System.exit(2);
        }
        List<String> lines = Files.readAllLines(tsv, StandardCharsets.UTF_8);
        String issuerHex = lines.get(0).trim();
        PublicKey issuerKey = ed25519PublicKey(hex(issuerHex));

        System.out.println("Cross-checking " + (lines.size() - 1) +
                           " Python-minted credentials against a pure-JDK decoder\n");

        for (int i = 1; i < lines.size(); i++) {
            String[] f = lines.get(i).split("\t", -1);
            String qr = f[0];
            try {
                Decoded d = verify(qr, issuerKey);
                int n = 1;
                eq(i, "issuerIndex", f[n++], String.valueOf(d.issuerIndex));
                eq(i, "subjectId", f[n++], hex(d.subjectId));
                eq(i, "name", f[n++], d.name);
                eq(i, "employerCode", f[n++], d.employerCode);
                eq(i, "module", f[n++], d.module);
                eq(i, "tier", f[n++], String.valueOf(d.tier));
                eq(i, "score", f[n++], String.valueOf(d.score));
                StringBuilder comps = new StringBuilder();
                for (int c = 0; c < COMPETENCIES.length; c++) {
                    if (c > 0) comps.append(',');
                    comps.append(d.competencies[c]);
                }
                eq(i, "competencies", f[n++], comps.toString());
                eq(i, "attemptDigest", f[n++], hex(d.attemptDigest));
                eq(i, "notBefore", f[n++], d.notBefore.toString());
                eq(i, "expires", f[n++], d.expires.toString());
                eq(i, "provisional", f[n++], String.valueOf(d.provisional));
            } catch (Exception e) {
                failures++;
                System.out.println("  vector " + i + " THREW: " + e);
            }
        }

        // The security property, not just the round trip: a single altered
        // character must never verify. Same exhaustive sweep as the Python
        // suite, re-run on the JVM side.
        String qr = lines.get(1).split("\t", -1)[0];
        int accepted = 0, tampered = 0;
        for (int p = PREFIX.length(); p < qr.length(); p++) {
            char orig = qr.charAt(p);
            int idx = B45.indexOf(orig);
            char swap = B45.charAt((idx + 1) % 45);
            String bad = qr.substring(0, p) + swap + qr.substring(p + 1);
            tampered++;
            try {
                verify(bad, issuerKey);
                accepted++;
                System.out.println("  !! tampered code ACCEPTED at position " + p);
            } catch (Exception ignored) {
                // rejected, as it must be — and it must throw, never crash the
                // process with something the caller cannot catch
            }
        }
        checks++;
        if (accepted != 0) failures++;
        System.out.println("  " + tampered + " single-character mutations, " +
                           accepted + " accepted\n");

        System.out.println(failures == 0
            ? "PASS — " + checks + " assertions, JVM decoder agrees with Python on every field"
            : "FAIL — " + failures + " mismatch(es)");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------ verify

    record Decoded(int issuerIndex, byte[] subjectId, String name, String employerCode,
                   String module, int tier, int score, int[] competencies,
                   byte[] attemptDigest, LocalDate notBefore, LocalDate expires,
                   boolean provisional) {}

    static Decoded verify(String qrText, PublicKey issuerKey) throws Exception {
        if (!qrText.startsWith(PREFIX)) throw new IllegalArgumentException("not a Sajag code");
        byte[] blob = base45Decode(qrText.substring(PREFIX.length()));
        byte[] raw = inflateIfNeeded(blob);
        if (raw.length <= SIG_LEN) throw new IllegalArgumentException("payload too short");

        byte[] payload = Arrays.copyOfRange(raw, 0, raw.length - SIG_LEN);
        byte[] sig = Arrays.copyOfRange(raw, raw.length - SIG_LEN, raw.length);

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(issuerKey);
        verifier.update(payload);
        if (!verifier.verify(sig)) throw new SecurityException("signature does not match");

        List<Object> p = cborDecodeArray(payload);
        if (p.size() < 13) throw new IllegalArgumentException("short payload array");
        if (((Long) p.get(0)).intValue() != 1) throw new IllegalArgumentException("bad version");

        byte[] comps = (byte[]) p.get(8);
        if (comps.length != COMPETENCIES.length)
            throw new IllegalArgumentException("bad competency block");
        int[] scores = new int[comps.length];
        for (int i = 0; i < comps.length; i++) scores[i] = comps[i] & 0xFF;

        return new Decoded(
            ((Long) p.get(1)).intValue(), (byte[]) p.get(2), (String) p.get(3),
            (String) p.get(4), MODULES[((Long) p.get(5)).intValue()],
            ((Long) p.get(6)).intValue(), ((Long) p.get(7)).intValue(), scores,
            (byte[]) p.get(9), EPOCH.plusDays((Long) p.get(10)),
            EPOCH.plusDays((Long) p.get(11)),
            (((Long) p.get(12)).intValue() & 1) != 0);
    }

    // ------------------------------------------------------------ codec

    static byte[] base45Decode(String text) {
        int[] v = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            v[i] = B45.indexOf(text.charAt(i));
            if (v[i] < 0) throw new IllegalArgumentException("bad base45 character");
        }
        if (v.length % 3 == 1) throw new IllegalArgumentException("impossible base45 length");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        for (; i + 2 < v.length; i += 3) {
            int n = v[i] + v[i + 1] * 45 + v[i + 2] * 45 * 45;
            if (n > 0xFFFF) throw new IllegalArgumentException("triplet overflow");
            out.write(n >> 8);
            out.write(n & 0xFF);
        }
        if (v.length % 3 == 2) {
            int n = v[v.length - 2] + v[v.length - 1] * 45;
            if (n > 0xFF) throw new IllegalArgumentException("pair overflow");
            out.write(n);
        }
        return out.toByteArray();
    }

    static byte[] inflateIfNeeded(byte[] blob) throws Exception {
        if (blob.length == 0) throw new IllegalArgumentException("empty");
        if (blob[0] == 0) return Arrays.copyOfRange(blob, 1, blob.length);
        if (blob[0] == 1) {
            java.util.zip.Inflater inf = new java.util.zip.Inflater();
            inf.setInput(blob, 1, blob.length - 1);
            byte[] buf = new byte[4096];
            int n = inf.inflate(buf);
            inf.end();
            return Arrays.copyOfRange(buf, 0, n);
        }
        throw new IllegalArgumentException("unknown compression marker");
    }

    @SuppressWarnings("unchecked")
    static List<Object> cborDecodeArray(byte[] buf) throws Exception {
        int[] pos = {0};
        Object item = cborAt(buf, pos);
        if (pos[0] != buf.length) throw new IllegalArgumentException("trailing bytes");
        return (List<Object>) item;
    }

    static Object cborAt(byte[] buf, int[] pos) throws Exception {
        if (pos[0] >= buf.length) throw new IllegalArgumentException("truncated");
        int b = buf[pos[0]++] & 0xFF;
        int major = b >> 5;
        long value = b & 0x1F;
        if (value >= 24) {
            int width = switch ((int) value) {
                case 24 -> 1; case 25 -> 2; case 26 -> 4; case 27 -> 8;
                default -> throw new IllegalArgumentException("bad additional-info");
            };
            if (pos[0] + width > buf.length) throw new IllegalArgumentException("truncated len");
            value = 0;
            for (int k = 0; k < width; k++) value = (value << 8) | (buf[pos[0]++] & 0xFF);
        }
        switch (major) {
            case 0: return value;
            case 2: case 3: {
                int end = pos[0] + (int) value;
                if (end > buf.length || end < pos[0])
                    throw new IllegalArgumentException("string past end");
                byte[] slice = Arrays.copyOfRange(buf, pos[0], end);
                pos[0] = end;
                if (major == 2) return slice;
                // Strict UTF-8. A tampered code routinely lands here with
                // invalid bytes; this must throw, never silently substitute
                // U+FFFD, or two implementations could disagree about a name.
                CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
                return dec.decode(ByteBuffer.wrap(slice)).toString();
            }
            case 4: {
                List<Object> list = new ArrayList<>((int) value);
                for (long k = 0; k < value; k++) list.add(cborAt(buf, pos));
                return list;
            }
            default: throw new IllegalArgumentException("major type " + major + " unsupported");
        }
    }

    /** Raw 32-byte Ed25519 public key wrapped in the fixed X.509 SPKI prefix,
     *  which is the least fiddly way to get one into the JDK KeyFactory. */
    static PublicKey ed25519PublicKey(byte[] raw) throws Exception {
        byte[] spkiPrefix = hex("302a300506032b6570032100");
        byte[] der = new byte[spkiPrefix.length + raw.length];
        System.arraycopy(spkiPrefix, 0, der, 0, spkiPrefix.length);
        System.arraycopy(raw, 0, der, spkiPrefix.length, raw.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
    }

    // ------------------------------------------------------------ helpers

    static void eq(int vector, String field, String expected, String actual) {
        checks++;
        if (!Objects.equals(expected, actual)) {
            failures++;
            System.out.println("  vector " + vector + " field " + field +
                               ": expected <" + expected + "> got <" + actual + ">");
        }
    }

    static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
