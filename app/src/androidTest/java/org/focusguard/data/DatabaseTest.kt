package org.focusguard.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    @Test fun outboxAndSessionAreAtomicAndIdempotent() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,GuardDatabase::class.java).build()
        try {
            val dao=db.dao(); val s=FocusSession("session",100,200,100,true); val op=Outbox("session","focus_sessions","{}",200)
            dao.finish(s,op); dao.finish(s,op)
            assertEquals(1,dao.sessions().size); assertEquals(1,dao.pending().size)
            dao.acknowledge("session"); assertTrue(dao.pending().isEmpty()); assertEquals(1,dao.sessions().size)
            dao.save(AppRule("test.app","Test",limitMinutes=5)); assertEquals(5,dao.allRules().single().limitMinutes)
        } finally { db.close() }
    }
}
