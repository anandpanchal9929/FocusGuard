package org.focusguard.core
import org.focusguard.data.Outbox
import org.focusguard.sync.SyncDrain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class SyncTest {
    @Test fun networkFailureLeavesOperationForRetryAndStableId() = runBlocking {
        val operation=Outbox("stable","focus_sessions","{}",0); val acknowledged=mutableListOf<String>(); val uploads=mutableListOf<String>()
        try { SyncDrain.run(listOf(operation),{true},{uploads.add(it.id); error("HTTP 503")},{acknowledged.add(it)}) } catch(e: IllegalStateException) {}
        assertTrue(acknowledged.isEmpty())
        SyncDrain.run(listOf(operation),{true},{uploads.add(it.id)},{acknowledged.add(it)})
        assertEquals(listOf("stable","stable"),uploads); assertEquals(listOf("stable"),acknowledged)
    }
    @Test fun disablingSyncStopsFutureUploads() = runBlocking {
        var enabled=true; val uploaded=mutableListOf<String>()
        SyncDrain.run(listOf(Outbox("a","focus_sessions","{}",0),Outbox("b","focus_sessions","{}",1)),{enabled},{uploaded.add(it.id); enabled=false},{})
        assertEquals(listOf("a"),uploaded)
    }
}
