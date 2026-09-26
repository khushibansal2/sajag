package `in`.sajag.insight

import `in`.sajag.data.HazardReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule printed on the risk map screen, checked by hand. */
class RiskModelTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun report(district: String, severity: String, ageDays: Double, type: String = "GAS") = HazardReport(
        id = "R$district$severity$ageDays", workerId = "w", type = type, severity = severity,
        location = "", note = "", createdAtMs = now - (ageDays * day).toLong(), district = district,
    )

    private fun drill(district: String, passed: Boolean, stops: Int, ageDays: Double = 0.0) = AttemptSummary(
        attemptId = "A$district$passed$stops$ageDays", workerId = "w1", workerName = "Worker", district = district,
        moduleId = "FIRE-01", startedAtMs = now - (ageDays * day).toLong(), mode = 2, score = if (passed) 90 else 50,
        passed = passed, competencies = emptyMap(), stops = List(stops) { "WATER_ON_ELECTRICAL" },
        weakItems = emptyList(), coSigned = true, synced = true,
    )

    @Test
    fun aHighReportTodayIsFourPointsAndMediumRisk() {
        val r = RiskModel.compute(listOf(report("DHANBAD", "HIGH", 0.0)), emptyList(), now).getValue("DHANBAD")
        assertEquals(4.0, r.points, 1e-9)
        assertEquals(RiskLevel.MEDIUM, r.level)
        assertEquals(1, r.highReports)
    }

    @Test
    fun pointsHalveEveryThirtyDays() {
        val r = RiskModel.compute(listOf(report("BOKARO", "HIGH", 30.0)), emptyList(), now).getValue("BOKARO")
        assertEquals(2.0, r.points, 1e-9)
        assertEquals(RiskLevel.LOW, r.level)
    }

    @Test
    fun twoHighReportsTodayAreHighRisk() {
        val reports = listOf(report("DHANBAD", "HIGH", 0.0), report("DHANBAD", "HIGH", 0.0, type = "FIRE"))
        assertEquals(RiskLevel.HIGH, RiskModel.compute(reports, emptyList(), now).getValue("DHANBAD").level)
    }

    @Test
    fun signalsOlderThanTheWindowAreLeftOut() {
        val old = report("RANCHI", "HIGH", RiskModel.WINDOW_DAYS + 1.0)
        assertFalse(RiskModel.compute(listOf(old), emptyList(), now).containsKey("RANCHI"))
    }

    @Test
    fun drillsAddStopsAndFailures() {
        val r = RiskModel.compute(emptyList(), listOf(drill("KODERMA", passed = false, stops = 1)), now).getValue("KODERMA")
        assertEquals(RiskModel.STOP_POINTS + RiskModel.FAILED_DRILL_POINTS, r.points, 1e-9)
        assertEquals(RiskLevel.MEDIUM, r.level)
        assertEquals(1, r.stops)
        assertEquals(1, r.failedDrills)
    }

    @Test
    fun aDistrictWithOnlyPassedDrillsIsLowNotUnknown() {
        val r = RiskModel.compute(emptyList(), listOf(drill("GIRIDIH", passed = true, stops = 0)), now).getValue("GIRIDIH")
        assertEquals(0.0, r.points, 1e-9)
        assertEquals(RiskLevel.LOW, r.level)
    }

    @Test
    fun reportsWithoutADistrictAreNotMapped() {
        assertTrue(RiskModel.compute(listOf(report("", "HIGH", 0.0)), emptyList(), now).isEmpty())
    }

    @Test
    fun topHazardsAreMostReportedFirst() {
        val reports = listOf(
            report("DHANBAD", "LOW", 1.0, "FIRE"), report("DHANBAD", "LOW", 2.0, "GAS"),
            report("DHANBAD", "LOW", 3.0, "GAS"), report("DHANBAD", "LOW", 4.0, "FALL"),
        )
        val top = RiskModel.compute(reports, emptyList(), now).getValue("DHANBAD").topHazards
        assertEquals("GAS" to 2, top.first())
        assertEquals(3, top.size)
    }

    @Test
    fun levelThresholds() {
        assertEquals(RiskLevel.NONE, RiskModel.level(0.0, hasData = false))
        assertEquals(RiskLevel.LOW, RiskModel.level(2.99, hasData = true))
        assertEquals(RiskLevel.MEDIUM, RiskModel.level(3.0, hasData = true))
        assertEquals(RiskLevel.HIGH, RiskModel.level(8.0, hasData = true))
    }
}
