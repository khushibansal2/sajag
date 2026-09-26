package `in`.sajag.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The simplified district map must still put every town in the right district. */
class JharkhandMapTest {
    @Test
    fun hasAllTwentyFourDistrictsOnce() {
        assertEquals(24, Jharkhand.districts.size)
        assertEquals(24, Jharkhand.districts.map { it.id }.toSet().size)
    }

    @Test
    fun everyLabelSitsInsideItsOwnDistrict() {
        for (d in Jharkhand.districts) {
            assertEquals(d.id, Jharkhand.districtAt(d.labelX, d.labelY)?.id)
        }
    }

    @Test
    fun ringsAreClosedPolygonsInsideTheMap() {
        for (d in Jharkhand.districts) {
            assertTrue(d.rings.isNotEmpty())
            for (ring in d.rings) {
                assertEquals("${d.id} ring has an odd number of coordinates", 0, ring.size % 2)
                assertTrue("${d.id} ring too small", ring.size >= 6)
                for (i in ring.indices step 2) {
                    assertTrue(ring[i] in 0f..Jharkhand.WIDTH)
                    assertTrue(ring[i + 1] in 0f..Jharkhand.HEIGHT)
                }
            }
        }
    }

    @Test
    fun townsFallInTheirDistricts() {
        val towns = mapOf(
            "DHANBAD" to (86.4304 to 23.7957),
            "RANCHI" to (85.3096 to 23.3441),
            "EAST_SINGHBHUM" to (86.2029 to 22.8046), // Jamshedpur
            "BOKARO" to (86.1511 to 23.6693),         // Bokaro Steel City
            "KODERMA" to (85.5946 to 24.4677),
            "HAZARIBAGH" to (85.3647 to 23.9925),
            "RAMGARH" to (85.5160 to 23.6363),
            "WEST_SINGHBHUM" to (85.8045 to 22.5528), // Chaibasa
            "GIRIDIH" to (86.3083 to 24.1854),
            "DUMKA" to (87.2497 to 24.2676),
        )
        for ((district, lonLat) in towns) {
            val (x, y) = Jharkhand.project(lonLat.first, lonLat.second)
            assertEquals(district, Jharkhand.districtAt(x, y)?.id)
        }
    }

    @Test
    fun outsideTheStateIsNoDistrict() {
        assertNull(Jharkhand.districtAt(0.02f, 0.02f))
        assertNull(Jharkhand.districtAt(-0.5f, 0.3f))
        val (x, y) = Jharkhand.project(88.3639, 22.5726) // Kolkata
        assertNull(Jharkhand.districtAt(x, y))
    }
}
