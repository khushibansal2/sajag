package `in`.sajag.capability

import org.junit.Assert.assertEquals
import org.junit.Test

/** The tier number is written onto every certificate, so its wire values must never drift. */
class TierProbeTest {
    @Test
    fun wireValuesRoundTrip() {
        for (tier in Tier.entries) assertEquals(tier, Tier.fromWire(tier.wire))
    }

    @Test
    fun tierNumbersMatchTheCertificateFormat() {
        assertEquals(1, Tier.WORLD_ANCHORED.wire)
        assertEquals(2, Tier.MARKER_IMU.wire)
        assertEquals(3, Tier.GUIDED_2D.wire)
    }
}
