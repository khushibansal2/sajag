"""
The assessment engine.

The problem statement's sharpest complaint is that existing certificates have
"no mechanism to verify comprehension". So nothing here scores an answer. Every
score is derived from what the worker physically did in the AR scene: which
cylinder he reached for, the angle he held it at, whether he tested the
atmosphere before entering a confined space.

Three properties make the result defensible rather than merely computed:

1. Per-competency floors are absolute. A high aggregate cannot rescue a failed
   critical competency, because in this domain it shouldn't — picking the water
   extinguisher for an electrical fire is not 80% right.

2. Hard fails force a taught replay rather than a deduction. The attempt is not
   marked down; it is stopped, and the worker repeats the beat until clean.

3. Timing rubrics are calibrated per mode, not discounted. A worker tapping a
   2D plate in guided mode cannot physically match one turning his body in AR,
   so the thresholds differ — but the pass bar does not. Calibration factors
   come from the October pilot and live in the scenario file, in the open.

The engine is pure: (events, scenario) -> result. No I/O, no clock, no
randomness. That is what makes an attempt replayable years later in a dispute.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional, Sequence

COMPETENCIES = ["HAZ-ID", "EQP-SEL", "SEQ", "EGR", "TECH", "KNW"]

# Weight toward the aggregate, and the absolute floor each must clear.
# EQP-SEL is 100 because there is no partial credit for the wrong cylinder.
DEFAULT_MODEL: Dict[str, Dict[str, int]] = {
    "HAZ-ID":  {"weight": 20, "floor": 70},
    "EQP-SEL": {"weight": 20, "floor": 100},
    "SEQ":     {"weight": 15, "floor": 75},
    "EGR":     {"weight": 15, "floor": 70},
    "TECH":    {"weight": 15, "floor": 65},
    "KNW":     {"weight": 15, "floor": 60},
}


class ScoringError(ValueError):
    pass


# ---------------------------------------------------------------- results

@dataclass
class ItemScore:
    item_id: str
    competency: str
    weight: float
    score: float                 # 0..100
    detail: str = ""


@dataclass
class AttemptResult:
    passed: bool
    aggregate: int
    competencies: Dict[str, int]
    items: List[ItemScore] = field(default_factory=list)
    hard_fails: List[str] = field(default_factory=list)
    replay_beats: List[str] = field(default_factory=list)
    reasons: List[str] = field(default_factory=list)

    def summary(self) -> str:
        head = "PASS" if self.passed else "FAIL"
        comps = "  ".join(f"{c}:{self.competencies[c]:3d}" for c in COMPETENCIES)
        return f"{head}  aggregate {self.aggregate:3d}   {comps}"


# ---------------------------------------------------------------- rubrics

def _clamp(x: float) -> float:
    return max(0.0, min(100.0, x))


def _score_choice(params: dict, ev: dict) -> tuple:
    """Exact set match. Anything short of the required set scores the fraction
    correct minus a penalty for each wrong item picked."""
    required = set(params["required"])
    forbidden = set(params.get("forbidden", []))
    chosen = set(ev.get("chosen", []))
    if chosen & forbidden:
        return 0.0, f"selected forbidden item(s): {sorted(chosen & forbidden)}"
    if not required:
        return 100.0, ""
    hit = len(chosen & required) / len(required)
    extra = len(chosen - required)
    value = _clamp(100.0 * hit - 15.0 * extra)
    return value, f"{len(chosen & required)}/{len(required)} correct, {extra} extra"


def _kendall_tau(order: Sequence[str], reference: Sequence[str]) -> float:
    """Normalised rank correlation in 0..1. Used for procedure ordering, where
    'nearly right order' is genuinely partial credit — unlike equipment choice."""
    rank = {step: i for i, step in enumerate(reference)}
    seq = [rank[s] for s in order if s in rank]
    n = len(seq)
    if n < 2:
        return 1.0 if n == len(reference) else 0.0
    concordant = discordant = 0
    for i in range(n):
        for j in range(i + 1, n):
            if seq[i] < seq[j]:
                concordant += 1
            else:
                discordant += 1
    tau = (concordant - discordant) / (n * (n - 1) / 2)
    completeness = n / len(reference)
    return max(0.0, (tau + 1) / 2) * completeness


def _score_sequence(params: dict, ev: dict) -> tuple:
    reference = params["order"]
    given = ev.get("order", [])
    tau = _kendall_tau(given, reference)
    return _clamp(100.0 * tau), f"{len(given)}/{len(reference)} steps, tau={tau:.2f}"


def _score_range(params: dict, ev: dict) -> tuple:
    """Full marks inside the band, linear decay to zero at `zero_at` beyond it.
    Used for aim angle, standoff distance, sampling height."""
    value = float(ev.get("value", 0.0))
    lo, hi = float(params["min"]), float(params["max"])
    zero_at = float(params.get("zero_at", (hi - lo) or 1.0))
    if lo <= value <= hi:
        return 100.0, f"{value:g} within [{lo:g}, {hi:g}]"
    excess = (lo - value) if value < lo else (value - hi)
    return _clamp(100.0 * (1 - excess / zero_at)), f"{value:g} outside [{lo:g}, {hi:g}]"


def _score_fraction(params: dict, ev: dict) -> tuple:
    """A measured coverage fraction against a target, e.g. extinguisher sweep."""
    target = float(params.get("target", 0.7))
    value = float(ev.get("value", 0.0))
    return _clamp(100.0 * min(1.0, value / target)), f"{value:.0%} of {target:.0%} target"


def _score_latency(params: dict, ev: dict, tier_factor: float) -> tuple:
    """Full marks at or under `good_ms`, zero at `bad_ms`, linear between.
    Both thresholds are scaled by the tier calibration factor."""
    good = float(params["good_ms"]) * tier_factor
    bad = float(params["bad_ms"]) * tier_factor
    if bad <= good:
        raise ScoringError("bad_ms must exceed good_ms")
    ms = float(ev.get("ms", bad))
    if ms <= good:
        return 100.0, f"{ms:.0f} ms"
    if ms >= bad:
        return 0.0, f"{ms:.0f} ms (over {bad:.0f} ms)"
    return _clamp(100.0 * (bad - ms) / (bad - good)), f"{ms:.0f} ms"


def _score_flag(params: dict, ev: dict) -> tuple:
    """Did he do the thing at all — atmosphere test, gauge check, comms check."""
    want = bool(params.get("expect", True))
    got = bool(ev.get("value", False))
    return (100.0 if got == want else 0.0), ("performed" if got else "not performed")


# ---------------------------------------------------------------- engine

def score_attempt(
    events: Iterable[dict],
    scenario: dict,
    model: Optional[Dict[str, Dict[str, int]]] = None,
) -> AttemptResult:
    """Score one attempt.

    `events` is the attempt's append-only log, oldest first. Each event is
    ``{"beat": "1.2", "item": "ext-choice", ...}`` plus whatever the rubric
    needs. Unknown events are ignored on purpose — the log is also an audit
    trail and will carry more than the engine reads.
    """
    model = model or DEFAULT_MODEL
    events = list(events)

    tier = int(scenario.get("_tier", 1))
    tier_factor = float(scenario.get("tier_calibration", {}).get(str(tier), 1.0))
    hard_fail_types = set(scenario.get("hard_fails", []))

    by_item: Dict[str, dict] = {}
    hard_fails: List[str] = []
    replay_beats: List[str] = []

    for ev in events:
        if ev.get("type") in hard_fail_types:
            label = f'{ev.get("beat", "?")}: {ev["type"]}'
            hard_fails.append(label)
            beat = ev.get("beat")
            if beat and beat not in replay_beats:
                replay_beats.append(beat)
        if "item" in ev:
            by_item[ev["item"]] = ev          # last attempt of an item wins

    items: List[ItemScore] = []
    for beat in scenario["beats"]:
        for rubric in beat.get("rubric", []):
            item_id = rubric["id"]
            competency = rubric["competency"]
            if competency not in model:
                raise ScoringError(f"rubric {item_id} names unknown competency {competency}")
            ev = by_item.get(item_id)
            if ev is None:
                items.append(ItemScore(item_id, competency, rubric.get("weight", 1.0), 0.0,
                                       "not attempted"))
                continue
            kind = rubric["kind"]
            params = rubric.get("params", {})
            if kind == "choice":
                value, detail = _score_choice(params, ev)
            elif kind == "sequence":
                value, detail = _score_sequence(params, ev)
            elif kind == "range":
                value, detail = _score_range(params, ev)
            elif kind == "fraction":
                value, detail = _score_fraction(params, ev)
            elif kind == "latency":
                value, detail = _score_latency(params, ev, tier_factor)
            elif kind == "flag":
                value, detail = _score_flag(params, ev)
            else:
                raise ScoringError(f"unknown rubric kind {kind!r} in {item_id}")
            items.append(ItemScore(item_id, competency, rubric.get("weight", 1.0), value, detail))

    # Roll up to competencies, then to the aggregate.
    comp_scores: Dict[str, int] = {}
    for code in COMPETENCIES:
        rows = [i for i in items if i.competency == code]
        if not rows:
            comp_scores[code] = 0
            continue
        total_weight = sum(r.weight for r in rows) or 1.0
        comp_scores[code] = round(sum(r.score * r.weight for r in rows) / total_weight)

    aggregate = round(
        sum(comp_scores[c] * model[c]["weight"] for c in COMPETENCIES)
        / sum(model[c]["weight"] for c in COMPETENCIES)
    )

    reasons: List[str] = []
    passed = True
    if hard_fails:
        passed = False
        reasons.append(
            f"{len(hard_fails)} hard fail(s) — replay required before certification"
        )
    for code in COMPETENCIES:
        floor = model[code]["floor"]
        if comp_scores[code] < floor:
            passed = False
            reasons.append(f"{code} scored {comp_scores[code]}, floor is {floor}")

    # The written check can never carry a pass on its own.
    non_written = [c for c in COMPETENCIES if c != "KNW"]
    if passed and all(comp_scores[c] == 0 for c in non_written):
        passed = False
        reasons.append("no practical evidence — written check alone cannot certify")

    return AttemptResult(
        passed=passed,
        aggregate=aggregate,
        competencies=comp_scores,
        items=items,
        hard_fails=hard_fails,
        replay_beats=replay_beats,
        reasons=reasons,
    )


def canonical_event_log(events: Iterable[dict]) -> bytes:
    """Byte-exact serialisation of an attempt, for the credential's digest.

    Deterministic: sorted keys, no whitespace, no floats that could round
    differently on another machine. If this function ever changes, every
    already-issued credential stops matching its attempt — so it does not
    change without a format version bump.
    """
    import json

    def normalise(ev: dict) -> dict:
        out = {}
        for k in sorted(ev):
            v = ev[k]
            out[k] = round(v, 4) if isinstance(v, float) else v
        return out

    body = [normalise(e) for e in events]
    return json.dumps(body, separators=(",", ":"), sort_keys=True,
                      ensure_ascii=False).encode("utf-8")
