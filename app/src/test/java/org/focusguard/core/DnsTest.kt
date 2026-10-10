package org.focusguard.core
import org.focusguard.web.DnsCodec
import org.junit.Assert.*
import org.junit.Test
class DnsTest {
    @Test fun suffixBoundaryPreventsOverblocking() { assertTrue(DnsCodec.matches("www.example.com",setOf("example.com"))); assertFalse(DnsCodec.matches("notexample.com",setOf("example.com"))) }
    @Test fun normalizesAndRejectsUrls() { assertEquals("example.com",DnsCodec.normalize(" EXAMPLE.com. ")); try { DnsCodec.normalize("https://example.com/path"); fail() } catch(e: IllegalArgumentException) {} }
    @Test fun queryParsingAndDeniedResponse() {
        val header=byteArrayOf(1,2,1,0,0,1,0,0,0,0,0,0)
        val query=header+byteArrayOf(7)+"example".toByteArray()+byteArrayOf(3)+"com".toByteArray()+byteArrayOf(0,0,1,0,1)
        assertEquals("example.com",DnsCodec.question(query)); val denied=DnsCodec.denied(query)
        assertEquals(0x83,denied[3].toInt() and 255); assertEquals(query.size,denied.size)
        assertNull(DnsCodec.question(byteArrayOf(0)))
    }
}
