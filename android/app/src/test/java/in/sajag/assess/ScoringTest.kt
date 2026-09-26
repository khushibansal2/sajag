package `in`.sajag.assess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Drives the real scenario files with the events the drill UI emits. The
 * expected aggregates were produced by core/sajag_core/scoring.py on the same
 * event lists — if Kotlin and Python disagree, this test is where you find out.
 */
class ScoringTest {
    private fun scenario(name: String) = Scenario.parse(File("../../core/scenarios/$name").readText())

    private fun item(beat: String, id: String, vararg kv: Pair<String, Any?>) = ScoringEvent(beat, id, "ITEM", mapOf(*kv))

    private val perfectFire = listOf(
        item("1.1", "classify-latency", "ms" to 3000L),
        item("1.1", "fire-class", "chosen" to listOf("electrical")),
        item("1.1", "safe-standoff", "value" to 3.0),
        item("1.2", "gauge-check", "value" to true),
        item("1.2", "select-latency", "ms" to 2500L),
        item("1.2", "ext-choice", "chosen" to listOf("co2")),
        item("1.3", "pass-sequence", "order" to listOf("pull", "aim", "squeeze", "sweep")),
        item("1.3", "aim-angle", "value" to 5.0),
        item("1.3", "sweep-coverage", "value" to 0.9),
        item("1.3", "standoff", "value" to 2.5),
        item("1.4", "head-height", "value" to 0.8),
        item("1.4", "wall-contact", "value" to 1.0),
        item("1.4", "door-heat-check", "value" to true),
        item("1.4", "exit-choice", "chosen" to listOf("exit_north")),
        item("1.4", "egress-latency", "ms" to 20000L),
        item("1.5", "alarm-latency", "ms" to 2000L),
        item("1.5", "response-order", "order" to listOf("alarm", "trip_conveyor", "alert_buddy", "assembly_point", "report_headcount")),
        item("1.5", "written-check", "chosen" to listOf("q1_b", "q2_a", "q3_c", "q4_a", "q5_b", "q6_c")),
    )

    @Test
    fun perfectFireRunPassesAtTier2() {
        val r = Scoring.score(perfectFire, scenario("fire-01.json"), tier = 2)
        assertTrue(r.reasons.toString(), r.passed)
        assertEquals(100, r.aggregate)
    }

    @Test
    fun waterOnElectricalIsAHardFailEvenWithAHighScore() {
        val events = perfectFire + ScoringEvent("1.2", null, "WATER_ON_ELECTRICAL")
        val r = Scoring.score(events, scenario("fire-01.json"), tier = 2)
        assertFalse(r.passed)
        assertEquals(listOf("1.2"), r.replayBeats)
    }

    @Test
    fun anExtraExtinguisherBreaksTheEquipmentFloor() {
        val events = perfectFire.map {
            if (it.item == "ext-choice") item("1.2", "ext-choice", "chosen" to listOf("co2", "sand")) else it
        }
        val r = Scoring.score(events, scenario("fire-01.json"), tier = 2)
        assertFalse(r.passed)
        assertTrue(r.competencies.getValue("EQP-SEL") < 100)
    }

    @Test
    fun gasScenarioParsesAndEmptyRunFails() {
        val sc = scenario("gas-01.json")
        assertEquals(5, sc.beats.size)
        val r = Scoring.score(emptyList(), sc, tier = 3)
        assertFalse(r.passed)
        assertEquals(0, r.aggregate)
    }

    @Test
    fun everyTaskInTheUiMapsToARubricItem() {
        for (id in listOf("FIRE-01", "GAS-01")) {
            val content = Modules.content(id)
            val sc = scenario(content.asset)
            val rubricIds = sc.beats.flatMap { b -> b.rubric.map { "${b.id}/${it.id}" } }.toSet()
            val uiIds = content.beats.flatMap { b ->
                b.tasks.flatMap { t ->
                    listOfNotNull(
                        t.itemId,
                        (t as? ChoiceTask)?.latencyItem,
                        (t as? SequenceTask)?.latencyItem,
                    ).map { "${b.beatId}/$it" }
                } + listOfNotNull(b.beatLatencyItem?.let { "${b.beatId}/$it" })
            }.toSet()
            assertEquals("$id: rubric items with no UI task", emptySet<String>(), rubricIds - uiIds)
            assertEquals("$id: UI tasks with no rubric item", emptySet<String>(), uiIds - rubricIds)
            val hardFails = content.beats.flatMap { b ->
                b.tasks.flatMap { t ->
                    when (t) {
                        is ChoiceTask -> t.hardFailOn.values.toList()
                        is SequenceTask -> listOfNotNull(t.mustStartWith?.second)
                        is FlagTask -> listOfNotNull(t.hardFailIfNo)
                        is RangeTask -> listOfNotNull(t.hardFailAbove?.second, t.hardFailBelow?.second)
                        else -> emptyList()
                    }
                }
            }.toSet()
            assertEquals("$id: hard fails the UI never raises", emptySet<String>(), sc.hardFails - hardFails)
        }
    }
}
