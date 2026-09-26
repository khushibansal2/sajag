package `in`.sajag.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Clean-up rules for what workers and supervisors type in. */
class ProfileTest {
    @Test
    fun employerCodesAreUpperCaseAndFitTheCertificate() {
        assertEquals("CTR-2291", Workers.cleanEmployer("  ctr-2291 "))
        assertEquals(12, Workers.cleanEmployer("abcdefghijklmnop").length)
    }

    @Test
    fun namesLoseStraySpaces() {
        assertEquals("Birsa Munda", Workers.cleanName("  Birsa    Munda "))
    }

    @Test
    fun phoneNumbersKeepOnlyDialableCharacters() {
        assertEquals("+91 326-222 1234", AppSettings.cleanPhone(" +91 326-222 1234 ext"))
        assertEquals("112", AppSettings.cleanPhone("112"))
        assertEquals("9123", AppSettings.cleanPhone("9+1(2)3"))
    }

    @Test
    fun theShortIdIsTheFirstEightHexDigits() {
        assertEquals("00112233", Profile("A", "B", "00112233445566778899aabbccddeeff").shortId)
    }
}
