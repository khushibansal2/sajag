package `in`.sajag.insight

import `in`.sajag.assess.Scenario
import `in`.sajag.assess.Scoring
import `in`.sajag.assess.ScoringEvent
import `in`.sajag.data.AttemptEntity
import `in`.sajag.data.EventEntity
import `in`.sajag.data.Profile
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** One finished drill, scored again on this phone from its raw events. */
data class AttemptSummary(
    val attemptId: String,
    val workerId: String,
    val workerName: String,
    /** District id of the worker's site, "" if unknown. */
    val district: String,
    val moduleId: String,
    val startedAtMs: Long,
    /** The mode the drill really ran in: 1 AR, 2 camera, 3 guided. The certificate calls this the tier. */
    val mode: Int,
    val score: Int,
    val passed: Boolean,
    val competencies: Map<String, Int>,
    /** STOP events: actions that would kill in a real mine. */
    val stops: List<String>,
    /** Rubric items scored under 70. */
    val weakItems: List<String>,
    val coSigned: Boolean,
    val synced: Boolean,
)

data class ModuleStat(val moduleId: String, val attempts: Int, val passed: Int, val averageScore: Int)

/** A rubric item and how often it was missed (scored under 70). */
data class ItemMiss(val moduleId: String, val itemId: String, val misses: Int, val attempts: Int)

data class TrainerStats(
    val attempts: Int,
    val passed: Int,
    val workers: Int,
    val coSigned: Int,
    val pendingSync: Int,
    val stops: Int,
    val byModule: List<ModuleStat>,
    val competencyAverages: Map<String, Int>,
    val mostMissed: List<ItemMiss>,
    val stopTypes: List<Pair<String, Int>>,
    val recent: List<AttemptSummary>,
) {
    /** Percent of attempts passed, or null with no attempts. */
    val passRate: Int? get() = if (attempts == 0) null else (passed * 100.0 / attempts).roundToInt()
}

/**
 * The training centre's numbers, built from attempts taken on this phone.
 *
 * Every attempt is scored again from its append-only event log with the same
 * engine the drill used, so this screen can never show a score the log does
 * not support.
 */
object Trainer {
    const val MISS_BELOW = 70.0

    fun summarize(attempt: AttemptEntity, events: List<EventEntity>, scenario: Scenario, worker: Profile?): AttemptSummary {
        val ordered = events.sortedBy { it.seq }
        val scoring = ordered.map { ScoringEvent(it.beat, it.itemId, it.type, parsePayload(it.payloadJson)) }
        // The most conservative mode used in the attempt, as the server does.
        val mode = ordered.maxOfOrNull { it.tier } ?: attempt.tierCeiling
        val result = Scoring.score(scoring, scenario, mode)
        return AttemptSummary(
            attemptId = attempt.attemptId,
            workerId = attempt.workerId,
            workerName = worker?.name?.takeIf { it.isNotBlank() } ?: "ID ${attempt.workerId.take(8).uppercase()}",
            district = worker?.district ?: "",
            moduleId = attempt.moduleId,
            startedAtMs = attempt.startedAtMs,
            mode = mode,
            score = result.aggregate,
            passed = result.passed,
            competencies = result.competencies,
            stops = scoring.filter { it.type in scenario.hardFails }.map { it.type },
            weakItems = result.items.filter { it.score < MISS_BELOW }.map { it.itemId },
            coSigned = attempt.supervisorSig != null,
            synced = ordered.isNotEmpty() && ordered.all { it.synced },
        )
    }

    fun stats(summaries: List<AttemptSummary>, modules: List<String>): TrainerStats {
        val byModule = modules.map { id ->
            val rows = summaries.filter { it.moduleId == id }
            ModuleStat(
                moduleId = id,
                attempts = rows.size,
                passed = rows.count { it.passed },
                averageScore = if (rows.isEmpty()) 0 else rows.map { it.score }.average().roundToInt(),
            )
        }
        val competencyAverages = Scoring.COMPETENCIES.associateWith { code ->
            val values = summaries.mapNotNull { it.competencies[code] }
            if (values.isEmpty()) 0 else values.average().roundToInt()
        }
        val attemptsPerModule = summaries.groupingBy { it.moduleId }.eachCount()
        val mostMissed = summaries
            .flatMap { s -> s.weakItems.map { s.moduleId to it } }
            .groupingBy { it }.eachCount()
            .map { (key, misses) -> ItemMiss(key.first, key.second, misses, attemptsPerModule[key.first] ?: 0) }
            .sortedWith(compareByDescending<ItemMiss> { it.misses }.thenBy { it.moduleId }.thenBy { it.itemId })
            .take(5)
        val stopTypes = summaries.flatMap { it.stops }.groupingBy { it }.eachCount()
            .toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        return TrainerStats(
            attempts = summaries.size,
            passed = summaries.count { it.passed },
            workers = summaries.map { it.workerId }.distinct().size,
            coSigned = summaries.count { it.coSigned },
            pendingSync = summaries.count { !it.synced },
            stops = summaries.sumOf { it.stops.size },
            byModule = byModule,
            competencyAverages = competencyAverages,
            mostMissed = mostMissed,
            stopTypes = stopTypes,
            recent = summaries.sortedByDescending { it.startedAtMs }.take(8),
        )
    }

    /** The canonical JSON payload of one event, back as plain Kotlin values. */
    fun parsePayload(json: String): Map<String, Any?> =
        runCatching { toMap(JSONObject(json)) }.getOrDefault(emptyMap())

    private fun toMap(o: JSONObject): Map<String, Any?> =
        o.keys().asSequence().associateWith { k -> unwrap(o.get(k)) }

    private fun unwrap(v: Any?): Any? = when {
        v == null || v == JSONObject.NULL -> null
        v is JSONObject -> toMap(v)
        v is JSONArray -> (0 until v.length()).map { unwrap(v.get(it)) }
        else -> v
    }
}
