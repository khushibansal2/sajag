package `in`.sajag.insight

import `in`.sajag.geo.Jharkhand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Demo data must look plausible, stay the same all day, and be unmistakably sample. */
class SampleDataTest {
    private val now = 1_800_000_000_000L

    @Test
    fun theSameDayGivesTheSameSample() {
        assertEquals(SampleData.attempts(now), SampleData.attempts(now + 60_000))
        assertEquals(SampleData.reports(now), SampleData.reports(now + 60_000))
    }

    @Test
    fun everySampleIsLabelledAndOnTheMap() {
        val attempts = SampleData.attempts(now)
        val reports = SampleData.reports(now)
        assertTrue(attempts.isNotEmpty() && reports.isNotEmpty())
        attempts.forEach {
            assertTrue(it.attemptId.startsWith("SAMPLE-"))
            assertTrue(it.mode in 1..3)
            assertTrue(it.score in 0..100)
            assertTrue(Jharkhand.byId(it.district) != null)
            assertTrue(it.startedAtMs <= now)
            // A drill with a STOP never passes.
            if (it.stops.isNotEmpty()) assertTrue(!it.passed)
        }
        reports.forEach {
            assertTrue(it.id.startsWith("SAMPLE-"))
            assertTrue(Jharkhand.byId(it.district) != null)
            assertTrue(it.severity in listOf("LOW", "MEDIUM", "HIGH"))
            assertTrue(it.createdAtMs <= now)
        }
    }

    @Test
    fun theSampleColoursTheMap() {
        val risks = RiskModel.compute(SampleData.reports(now), SampleData.attempts(now), now)
        assertTrue(risks.size >= 5)
        assertTrue(risks.values.any { it.level == RiskLevel.HIGH || it.level == RiskLevel.MEDIUM })
    }
}
