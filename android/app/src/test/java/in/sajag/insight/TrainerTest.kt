package `in`.sajag.insight

import `in`.sajag.assess.Scenario
import `in`.sajag.assess.Scoring
import `in`.sajag.assess.ScoringEvent
import `in`.sajag.credential.CanonicalJson
import `in`.sajag.data.AttemptEntity
import `in`.sajag.data.EventEntity
import `in`.sajag.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The training centre screen scores every attempt again from its stored
 * events. That must give exactly what the drill gave, from the same log.
 */
class TrainerTest {
    private val fire = Scenario.parse(File("../../core/scenarios/fire-01.json").readText())

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

    private fun stored(events: List<ScoringEvent>, modes: List<Int>, synced: Boolean) = events.mapIndexed { i, e ->
        EventEntity(
            eventId = "E$i", attemptId = "A1", seq = i, atMs = 1_700_000_000_000L + i, beat = e.beat,
            itemId = e.item, type = e.type, tier = modes[i], payloadJson = CanonicalJson.encode(e.payload), synced = synced,
        )
    }

    private val attempt = AttemptEntity(
        attemptId = "A1", workerId = "00112233445566778899aabbccddeeff", moduleId = "FIRE-01", scenarioVersion = "1",
        tierCeiling = 1, startedAtMs = 1_700_000_000_000L, finishedAtMs = 1_700_000_600_000L, supervisorSig = "pub.sig",
    )

    @Test
    fun payloadsComeBackAsPlainValues() {
        val p = Trainer.parsePayload(CanonicalJson.encode(mapOf("chosen" to listOf("co2"), "value" to 0.82, "ms" to 3000L, "done" to true)))
        assertEquals(listOf("co2"), p["chosen"])
        assertEquals(0.82, (p["value"] as Number).toDouble(), 1e-9)
        assertEquals(3000L, (p["ms"] as Number).toLong())
        assertEquals(true, p["done"])
        assertTrue(Trainer.parsePayload("not json").isEmpty())
    }

    @Test
    fun aStoredAttemptScoresExactlyAsTheDrillDid() {
        // Mostly AR, one step in camera mode: the attempt counts as camera mode, as on the server.
        val modes = perfectFire.indices.map { if (it == 5) 2 else 1 }
        val worker = Profile("Birsa Munda", "CTR-2291", attempt.workerId, district = "DHANBAD")
        val s = Trainer.summarize(attempt, stored(perfectFire, modes, synced = false), fire, worker)
        val expected = Scoring.score(perfectFire, fire, 2)
        assertEquals(2, s.mode)
        assertEquals(expected.aggregate, s.score)
        assertEquals(expected.passed, s.passed)
        assertEquals(expected.competencies, s.competencies)
        assertEquals("Birsa Munda", s.workerName)
        assertEquals("DHANBAD", s.district)
        assertTrue(s.coSigned)
        assertFalse(s.synced)
        assertTrue(s.stops.isEmpty())
    }

    @Test
    fun stopsAndWeakStepsAreFound() {
        val events = perfectFire.map { if (it.item == "ext-choice") item("1.2", "ext-choice", "chosen" to listOf("water")) else it } +
            ScoringEvent("1.2", null, "WATER_ON_ELECTRICAL")
        val s = Trainer.summarize(attempt, stored(events, events.map { 2 }, synced = true), fire, null)
        assertEquals(listOf("WATER_ON_ELECTRICAL"), s.stops)
        assertTrue("ext-choice" in s.weakItems)
        assertFalse(s.passed)
        assertTrue(s.synced)
        assertEquals("ID 00112233", s.workerName)
    }

    @Test
    fun statsAddUp() {
        fun summary(id: String, worker: String, module: String, passed: Boolean, weak: List<String>, signed: Boolean, synced: Boolean) = AttemptSummary(
            attemptId = id, workerId = worker, workerName = worker, district = "RANCHI", moduleId = module,
            startedAtMs = id.hashCode().toLong(), mode = 2, score = if (passed) 85 else 55, passed = passed,
            competencies = Scoring.COMPETENCIES.associateWith { if (passed) 85 else 55 },
            stops = if (passed) emptyList() else listOf("SOLO_ENTRY"), weakItems = weak, coSigned = signed, synced = synced,
        )
        val rows = listOf(
            summary("a", "w1", "FIRE-01", true, emptyList(), signed = true, synced = true),
            summary("b", "w1", "FIRE-01", false, listOf("gauge-check", "head-height"), signed = true, synced = false),
            summary("c", "w2", "GAS-01", false, listOf("zone-id", "gauge-check"), signed = false, synced = true),
            summary("d", "w3", "FIRE-01", true, listOf("head-height"), signed = true, synced = true),
        )
        val stats = Trainer.stats(rows, listOf("FIRE-01", "GAS-01"))
        assertEquals(4, stats.attempts)
        assertEquals(2, stats.passed)
        assertEquals(50, stats.passRate)
        assertEquals(3, stats.workers)
        assertEquals(3, stats.coSigned)
        assertEquals(1, stats.pendingSync)
        assertEquals(2, stats.stops)
        assertEquals(listOf("SOLO_ENTRY" to 2), stats.stopTypes)
        // FIRE-01 head-height missed twice leads; module-scoped, so FIRE gauge-check is counted apart from GAS.
        assertEquals(ItemMiss("FIRE-01", "head-height", 2, 3), stats.mostMissed.first())
        assertEquals(3, stats.byModule.first { it.moduleId == "FIRE-01" }.attempts)
        assertEquals(2, stats.byModule.first { it.moduleId == "FIRE-01" }.passed)
        assertEquals(70, stats.competencyAverages.getValue("SEQ"))
        assertEquals(null, Trainer.stats(emptyList(), listOf("FIRE-01")).passRate)
    }
}
