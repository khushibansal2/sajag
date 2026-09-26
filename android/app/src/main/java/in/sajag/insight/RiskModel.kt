package `in`.sajag.insight

import `in`.sajag.data.HazardReport
import kotlin.math.pow

enum class RiskLevel { NONE, LOW, MEDIUM, HIGH }

/** What the risk map knows about one district. */
data class DistrictRisk(
    val district: String,
    /** Risk points after ageing (see [RiskModel]). */
    val points: Double,
    val level: RiskLevel,
    val reports: Int,
    val highReports: Int,
    val drills: Int,
    val failedDrills: Int,
    val stops: Int,
    /** Hazard type to number of reports, most reported first. */
    val topHazards: List<Pair<String, Int>>,
    val lastSignalMs: Long?,
)

/**
 * A simple, explainable risk score per district, from what this phone knows:
 * hazard reports from the field and how workers from that district did in
 * their drills.
 *
 *   high-severity report    4 points
 *   medium report           2 points
 *   low report              1 point
 *   STOP in a drill         2 points
 *   failed drill            1 point
 *
 * Points halve every [HALF_LIFE_DAYS] days, and anything older than
 * [WINDOW_DAYS] is left out. Under [MEDIUM_AT] points a district is low risk,
 * from [HIGH_AT] it is high. The rule is written on the map screen too: a
 * supervisor should be able to check any colour by hand.
 */
object RiskModel {
    const val HALF_LIFE_DAYS = 30.0
    const val WINDOW_DAYS = 180
    const val MEDIUM_AT = 3.0
    const val HIGH_AT = 8.0
    private const val DAY_MS = 86_400_000.0

    fun reportPoints(severity: String): Double = when (severity) {
        "HIGH" -> 4.0
        "MEDIUM" -> 2.0
        else -> 1.0
    }

    const val STOP_POINTS = 2.0
    const val FAILED_DRILL_POINTS = 1.0

    fun decay(points: Double, ageMs: Long): Double =
        points * 0.5.pow(ageMs.coerceAtLeast(0L) / DAY_MS / HALF_LIFE_DAYS)

    fun level(points: Double, hasData: Boolean): RiskLevel = when {
        !hasData -> RiskLevel.NONE
        points >= HIGH_AT -> RiskLevel.HIGH
        points >= MEDIUM_AT -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }

    /** Risk for every district that has any report or drill. Districts with neither are absent. */
    fun compute(reports: List<HazardReport>, attempts: List<AttemptSummary>, nowMs: Long): Map<String, DistrictRisk> {
        val windowStart = nowMs - (WINDOW_DAYS * DAY_MS).toLong()
        val recentReports = reports.filter { it.district.isNotBlank() && it.createdAtMs >= windowStart }
        val recentDrills = attempts.filter { it.district.isNotBlank() && it.startedAtMs >= windowStart }
        val districts = (recentReports.map { it.district } + recentDrills.map { it.district }).toSortedSet()

        return districts.associateWith { id ->
            val rs = recentReports.filter { it.district == id }
            val ds = recentDrills.filter { it.district == id }
            var points = 0.0
            rs.forEach { points += decay(reportPoints(it.severity), nowMs - it.createdAtMs) }
            ds.forEach { d ->
                points += decay(STOP_POINTS * d.stops.size, nowMs - d.startedAtMs)
                if (!d.passed) points += decay(FAILED_DRILL_POINTS, nowMs - d.startedAtMs)
            }
            DistrictRisk(
                district = id,
                points = points,
                level = level(points, rs.isNotEmpty() || ds.isNotEmpty()),
                reports = rs.size,
                highReports = rs.count { it.severity == "HIGH" },
                drills = ds.size,
                failedDrills = ds.count { !it.passed },
                stops = ds.sumOf { it.stops.size },
                topHazards = rs.groupingBy { it.type }.eachCount().toList()
                    .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }),
                lastSignalMs = (rs.map { it.createdAtMs } + ds.map { it.startedAtMs }).maxOrNull(),
            )
        }
    }
}
