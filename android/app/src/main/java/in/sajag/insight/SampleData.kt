package `in`.sajag.insight

import `in`.sajag.data.HazardReport
import kotlin.random.Random

/**
 * Made-up workers, drills and reports for demo mode, so the training centre
 * and the risk map have something to show before a pilot has produced real
 * data. Every screen that shows this says SAMPLE DATA in plain words, and none
 * of it is ever written to the phone's database or sent to the server.
 *
 * Deterministic: the same day always produces the same sample. Times count
 * back from the start of that day, so nothing moves while the demo runs and
 * no sample is ever dated in the future.
 */
object SampleData {
    private const val DAY = 86_400_000L

    private val names = listOf(
        "Birsa Oraon", "Sunita Munda", "Ramesh Mahato", "Anita Hembrom", "Suresh Soren",
        "Kavita Kumari", "Manoj Tudu", "Pooja Marandi", "Ajay Singh", "Rekha Devi",
        "Vijay Hansda", "Mamta Kisku", "Rakesh Yadav", "Salomi Toppo",
    )

    /** Coal, steel and mica districts carry most of the sample, as they would in life. */
    private val districts = listOf(
        "DHANBAD" to 6, "BOKARO" to 4, "RAMGARH" to 3, "HAZARIBAGH" to 3, "GIRIDIH" to 3,
        "KODERMA" to 3, "EAST_SINGHBHUM" to 3, "WEST_SINGHBHUM" to 2, "CHATRA" to 1, "LATEHAR" to 1,
    )

    private val moduleStops = mapOf(
        "FIRE-01" to listOf("WATER_ON_ELECTRICAL", "UPRIGHT_IN_SMOKE", "OPENED_HOT_DOOR", "APPROACH_ENERGISED_PANEL"),
        "GAS-01" to listOf("SOLO_ENTRY", "DUST_MASK_AS_RESPIRATOR", "ENTRY_BEFORE_ATMOSPHERE_TEST", "UNPROTECTED_RESCUE_ENTRY"),
    )

    private val moduleItems = mapOf(
        "FIRE-01" to listOf("gauge-check", "head-height", "door-heat-check", "aim-angle", "sweep-coverage", "response-order", "safe-standoff"),
        "GAS-01" to listOf("zone-id", "retest-after-ventilation", "scsr-seal-check", "donning-order", "permit-order", "comms-check"),
    )

    /** Hazard types most often reported in each kind of district. */
    private fun likelyTypes(district: String): List<String> = when (district) {
        "DHANBAD", "RAMGARH", "HAZARIBAGH", "CHATRA", "LATEHAR" -> listOf("GAS", "FIRE", "FALL", "GAS", "MACHINERY")
        "BOKARO", "EAST_SINGHBHUM" -> listOf("MACHINERY", "ELECTRICAL", "FIRE", "SLIP")
        "KODERMA", "GIRIDIH" -> listOf("FALL", "SLIP", "FALL", "OTHER")
        else -> listOf("MACHINERY", "FALL", "SLIP")
    }

    private val places = listOf(
        "Level 2, north gallery", "Conveyor transfer point", "Pump house", "Sump near shaft 3",
        "Blast furnace cast house", "Mica sorting shed", "Haul road bend", "Workshop bay 4",
        "Return airway", "Coke oven battery", "Substation yard", "Loading bay",
    )

    private fun pickDistrict(r: Random): String {
        val total = districts.sumOf { it.second }
        var roll = r.nextInt(total)
        for ((id, weight) in districts) {
            if (roll < weight) return id
            roll -= weight
        }
        return districts.first().first
    }

    private fun seed(nowMs: Long) = 26041L * 1_000_003L + nowMs / DAY

    private fun dayStart(nowMs: Long) = nowMs / DAY * DAY

    fun attempts(nowMs: Long): List<AttemptSummary> {
        val r = Random(seed(nowMs))
        val day = dayStart(nowMs)
        val workerDistricts = names.associateWith { pickDistrict(r) }
        return List(38) { i ->
            val name = names[r.nextInt(names.size)]
            val module = if (r.nextInt(100) < 55) "FIRE-01" else "GAS-01"
            val stops = if (r.nextInt(100) < 18) listOf(moduleStops.getValue(module).random(r)) else emptyList()
            val items = moduleItems.getValue(module)
            val weak = items.shuffled(r).take(r.nextInt(0, 4))
            val score = (62 + r.nextInt(0, 36) - weak.size * 3).coerceIn(35, 98)
            val comps = listOf("HAZ-ID", "EQP-SEL", "SEQ", "EGR", "TECH", "KNW").associateWith {
                (score + r.nextInt(-12, 13)).coerceIn(30, 100)
            }
            val passed = stops.isEmpty() && score >= 70 && comps.getValue("EQP-SEL") >= 70
            val modeRoll = r.nextInt(100)
            AttemptSummary(
                attemptId = "SAMPLE-A" + i.toString().padStart(2, '0'),
                workerId = "sample-" + names.indexOf(name).toString().padStart(2, '0'),
                workerName = name,
                district = workerDistricts.getValue(name),
                moduleId = module,
                startedAtMs = day - r.nextLong(DAY / 4, 60 * DAY),
                mode = when {
                    modeRoll < 30 -> 1
                    modeRoll < 85 -> 2
                    else -> 3
                },
                score = score,
                passed = passed,
                competencies = comps,
                stops = stops,
                weakItems = weak,
                coSigned = r.nextInt(100) < 82,
                synced = r.nextInt(100) < 86,
            )
        }.sortedByDescending { it.startedAtMs }
    }

    fun reports(nowMs: Long): List<HazardReport> {
        val r = Random(seed(nowMs) + 7)
        val day = dayStart(nowMs)
        return List(30) { i ->
            val district = pickDistrict(r)
            val severityRoll = r.nextInt(100)
            HazardReport(
                id = "SAMPLE-R" + i.toString().padStart(2, '0'),
                workerId = "sample",
                type = likelyTypes(district).random(r),
                severity = when {
                    severityRoll < 25 -> "HIGH"
                    severityRoll < 70 -> "MEDIUM"
                    else -> "LOW"
                },
                location = places.random(r),
                note = "",
                createdAtMs = day - r.nextLong(DAY / 8, 75 * DAY),
                sent = true,
                district = district,
            )
        }.sortedByDescending { it.createdAtMs }
    }
}
