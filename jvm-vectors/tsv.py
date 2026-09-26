"""vectors.json -> vectors.tsv, so the JVM check needs no JSON dependency."""
import json
ORDER = ["HAZ-ID", "EQP-SEL", "SEQ", "EGR", "TECH", "KNW"]
d = json.load(open("vectors.json", encoding="utf-8"))
lines = [d["issuerPublicKeyHex"]]
for v in d["vectors"]:
    e = v["expect"]
    lines.append("\t".join([
        v["qr"], str(e["issuerIndex"]), e["subjectIdHex"], e["name"], e["employerCode"],
        e["module"], str(e["tier"]), str(e["score"]),
        ",".join(str(e["competencies"][c]) for c in ORDER),
        e["attemptDigestHex"], e["notBefore"], e["expires"],
        "true" if e["provisional"] else "false",
    ]))
open("vectors.tsv", "w", encoding="utf-8").write("\n".join(lines))
print(f"wrote vectors.tsv: {len(lines) - 1} vectors")
