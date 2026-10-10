package org.focusguard.focus

import org.focusguard.FocusGuardApp
import org.focusguard.core.TimerState
import org.focusguard.data.*
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FocusRepository(private val app: FocusGuardApp) {
    companion object { private val finishLock=Mutex() }
    suspend fun start(minutes: Long, kind: String = "focus") {
        val p=app.preferences.snapshot()
        if(p.timer().active(System.currentTimeMillis())) return
        finish(false)
        app.preferences.beginTimer(TimerState(System.currentTimeMillis(),minutes.coerceIn(0,240)*60_000),kind)
        app.startForegroundService(android.content.Intent(app,TimerService::class.java))
    }
    suspend fun pauseOrResume() {
        val p=app.preferences.snapshot(); val timer=p.timer(); val now=System.currentTimeMillis()
        if(p.flag("strict") || !timer.active(now)) return
        app.preferences.timer(if(timer.pausedAtMs == 0L) timer.copy(pausedAtMs=now) else timer.copy(pausedMs=timer.pausedMs+now-timer.pausedAtMs,pausedAtMs=0))
    }
    suspend fun finish(completed: Boolean) = finishLock.withLock {
        val p=app.preferences.snapshot(); val timer=p.timer(); val now=System.currentTimeMillis()
        if(timer.startMs == 0L) return@withLock
        val elapsed = if(timer.durationMs > 0) minOf(timer.durationMs,timer.elapsed(now)) else timer.elapsed(now)
        val id=p.str("sessionId").ifBlank { UUID.nameUUIDFromBytes("focusguard:${timer.startMs}".toByteArray()).toString() }
        val session=FocusSession(id,timer.startMs,now,elapsed,completed)
        val payload=JSONObject().put("id",id).put("user_id",p.str("userId")).put("started_at",java.time.Instant.ofEpochMilli(timer.startMs).toString()).put("ended_at",java.time.Instant.ofEpochMilli(now).toString()).put("focused_ms",elapsed).put("completed",completed)
        if(p.str("timerKind","focus")=="focus") app.database.dao().finish(session, if(p.flag("sync") && p.str("userId").isNotBlank()) Outbox(id,"focus_sessions",payload.toString(),now) else null)
        app.preferences.timer(TimerState(0,0))
        app.preferences.number("endRequested",0)
    }
}
