package org.focusguard.core
import org.junit.Assert.*
import org.junit.Test
class UsageTest {
    @Test fun clipsSessionAcrossMidnightAndEnd() {
        val events=listOf(ForegroundEvent("a",900,true),ForegroundEvent("a",1100,false),ForegroundEvent("b",1150,true))
        assertEquals(mapOf("a" to 100L,"b" to 50L),UsageReducer.durations(events,1000,1200))
    }
    @Test fun missingResumeDoesNotFabricateUsage() { assertTrue(UsageReducer.durations(listOf(ForegroundEvent("a",1100,false)),1000,1200).isEmpty()) }
    @Test fun duplicateResumeDoesNotResetBeginning() { assertEquals(100L,UsageReducer.durations(listOf(ForegroundEvent("a",1000,true),ForegroundEvent("a",1050,true),ForegroundEvent("a",1100,false)),1000,1200)["a"]) }
}
