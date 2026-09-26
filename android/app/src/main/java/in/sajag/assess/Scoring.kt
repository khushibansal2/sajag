package `in`.sajag.assess

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Kotlin mirror of core/sajag_core/scoring.py — the phone scores offline so the
 * worker gets an immediate verdict underground. The server re-scores from the
 * raw events anyway and never trusts this number; still, the two must agree,
 * so change the Python first and keep ScoringTest green.
 */
data class ScoringEvent(
    val beat: String,
    val item: String?,
    val type: String,
    val payload: Map<String, Any?> = emptyMap(),
)

data class ItemScore(
    val itemId: String,
    val competency: String,
    val weight: Double,
    val score: Double,
    val detail: String,
)

data class AttemptResult(
    val passed: Boolean,
    val aggregate: Int,
    val competencies: Map<String, Int>,
    val items: List<ItemScore>,
    val hardFails: List<String>,
    val replayBeats: List<String>,
    val reasons: List<String>,
)

object Scoring {
    val COMPETENCIES = listOf("HAZ-ID", "EQP-SEL", "SEQ", "EGR", "TECH", "KNW")

    /** competency -> (weight, floor). EQP-SEL floor is 100: no partial credit for the wrong cylinder. */
    val MODEL = mapOf(
        "HAZ-ID" to (20 to 70),
        "EQP-SEL" to (20 to 100),
        "SEQ" to (15 to 75),
        "EGR" to (15 to 70),
        "TECH" to (15 to 65),
        "KNW" to (15 to 60),
    )

    fun score(events: List<ScoringEvent>, scenario: Scenario, tier: Int): AttemptResult {
        val factor = scenario.tierCalibration[tier] ?: 1.0
        val byItem = HashMap<String, ScoringEvent>()
        val hardFails = mutableListOf<String>()
        val replayBeats = mutableListOf<String>()

        for (ev in events) {
            if (ev.type in scenario.hardFails) {
                hardFails += "${ev.beat}: ${ev.type}"
                if (ev.beat !in replayBeats) replayBeats += ev.beat
            }
            if (ev.item != null) byItem[ev.item] = ev   // last attempt of an item wins
        }

        val items = scenario.beats.flatMap { it.rubric }.map { r ->
            val ev = byItem[r.id]
                ?: return@map ItemScore(r.id, r.competency, r.weight, 0.0, "not attempted")
            val (value, detail) = when (r.kind) {
                "choice" -> choice(r.params, ev.payload)
                "sequence" -> sequence(r.params, ev.payload)
                "range" -> range(r.params, ev.payload)
                "fraction" -> fraction(r.params, ev.payload)
                "latency" -> latency(r.params, ev.payload, factor)
                "flag" -> flag(r.params, ev.payload)
                else -> throw IllegalArgumentException("unknown rubric kind ${r.kind} in ${r.id}")
            }
            ItemScore(r.id, r.competency, r.weight, value, detail)
        }

        val comps = COMPETENCIES.associateWith { code ->
            val rows = items.filter { it.competency == code }
            if (rows.isEmpty()) 0 else {
                val tw = rows.sumOf { it.weight }.takeIf { it != 0.0 } ?: 1.0
                pyRound(rows.sumOf { it.score * it.weight } / tw)
            }
        }

        val aggregate = pyRound(
            COMPETENCIES.sumOf { comps.getValue(it) * MODEL.getValue(it).first }.toDouble() /
                COMPETENCIES.sumOf { MODEL.getValue(it).first }
        )

        val reasons = mutableListOf<String>()
        var passed = true
        if (hardFails.isNotEmpty()) {
            passed = false
            reasons += "${hardFails.size} hard fail(s) — replay required before certification"
        }
        for (code in COMPETENCIES) {
            val floor = MODEL.getValue(code).second
            if (comps.getValue(code) < floor) {
                passed = false
                reasons += "$code scored ${comps.getValue(code)}, floor is $floor"
            }
        }
        if (passed && COMPETENCIES.filter { it != "KNW" }.all { comps.getValue(it) == 0 }) {
            passed = false
            reasons += "no practical evidence — written check alone cannot certify"
        }

        return AttemptResult(passed, aggregate, comps, items, hardFails, replayBeats, reasons)
    }

    /** Python's round() is round-half-to-even; so is kotlin.math.round. */
    private fun pyRound(x: Double): Int = round(x).toInt()

    private fun clamp(x: Double) = max(0.0, min(100.0, x))

    private fun strings(v: Any?): List<String> = (v as? List<*>)?.map { it.toString() } ?: emptyList()

    private fun num(v: Any?): Double? = (v as? Number)?.toDouble()

    private fun choice(p: Map<String, Any?>, ev: Map<String, Any?>): Pair<Double, String> {
        val required = strings(p["required"]).toSet()
        val forbidden = strings(p["forbidden"]).toSet()
        val chosen = strings(ev["chosen"]).toSet()
        val bad = chosen intersect forbidden
        if (bad.isNotEmpty()) return 0.0 to "selected forbidden item(s): ${bad.sorted()}"
        if (required.isEmpty()) return 100.0 to ""
        val hit = (chosen intersect required).size
        val extra = (chosen - required).size
        return clamp(100.0 * hit / required.size - 15.0 * extra) to
            "$hit/${required.size} correct, $extra extra"
    }

    private fun kendallTau(order: List<String>, reference: List<String>): Double {
        val rank = reference.withIndex().associate { it.value to it.index }
        val seq = order.mapNotNull { rank[it] }
        val n = seq.size
        if (n < 2) return if (n == reference.size) 1.0 else 0.0
        var concordant = 0
        var discordant = 0
        for (i in 0 until n) for (j in i + 1 until n) {
            if (seq[i] < seq[j]) concordant++ else discordant++
        }
        val tau = (concordant - discordant) / (n * (n - 1) / 2.0)
        return max(0.0, (tau + 1) / 2) * (n.toDouble() / reference.size)
    }

    private fun sequence(p: Map<String, Any?>, ev: Map<String, Any?>): Pair<Double, String> {
        val reference = strings(p["order"])
        val given = strings(ev["order"])
        val tau = kendallTau(given, reference)
        return clamp(100.0 * tau) to "${given.size}/${reference.size} steps, tau=${"%.2f".format(tau)}"
    }

    private fun range(p: Map<String, Any?>, ev: Map<String, Any?>): Pair<Double, String> {
        val value = num(ev["value"]) ?: 0.0
        val lo = num(p["min"])!!
        val hi = num(p["max"])!!
        val zeroAt = num(p["zero_at"]) ?: ((hi - lo).takeIf { it != 0.0 } ?: 1.0)
        if (value in lo..hi) return 100.0 to "$value within [$lo, $hi]"
        val excess = if (value < lo) lo - value else value - hi
        return clamp(100.0 * (1 - excess / zeroAt)) to "$value outside [$lo, $hi]"
    }

    private fun fraction(p: Map<String, Any?>, ev: Map<String, Any?>): Pair<Double, String> {
        val target = num(p["target"]) ?: 0.7
        val value = num(ev["value"]) ?: 0.0
        return clamp(100.0 * min(1.0, value / target)) to
            "${(value * 100).toInt()}% of ${(target * 100).toInt()}% target"
    }

    private fun latency(p: Map<String, Any?>, ev: Map<String, Any?>, factor: Double): Pair<Double, String> {
        val good = num(p["good_ms"])!! * factor
        val bad = num(p["bad_ms"])!! * factor
        require(bad > good) { "bad_ms must exceed good_ms" }
        val ms = num(ev["ms"]) ?: bad
        return when {
            ms <= good -> 100.0 to "${ms.toLong()} ms"
            ms >= bad -> 0.0 to "${ms.toLong()} ms (over ${bad.toLong()} ms)"
            else -> clamp(100.0 * (bad - ms) / (bad - good)) to "${ms.toLong()} ms"
        }
    }

    private fun flag(p: Map<String, Any?>, ev: Map<String, Any?>): Pair<Double, String> {
        val want = p["expect"] as? Boolean ?: true
        val got = ev["value"] as? Boolean ?: false
        return (if (got == want) 100.0 else 0.0) to (if (got) "performed" else "not performed")
    }
}
