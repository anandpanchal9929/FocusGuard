package org.focusguard.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

data class UsageReport(val apps: Map<String, Long>, val launches: Map<String, Int>, val unlocks: Int?) {
    val totalMs get() = apps.values.sum()
}
class UsageRepository(private val context: Context) {
    fun allowed(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    fun report(date: LocalDate = LocalDate.now()): UsageReport? {
        if (!allowed()) return null
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = minOf(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), System.currentTimeMillis())
        if(end <= start) return UsageReport(emptyMap(),emptyMap(),null)
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val timeline=mutableListOf<org.focusguard.core.ForegroundEvent>()
        val launches = mutableMapOf<String, Int>()
        val events = manager.queryEvents(start-2*24*60*60_000L, end)
        val event = UsageEvents.Event()
        var unlocks = 0
        val active=mutableSetOf<String>()
        while(events.hasNextEvent()) {
            events.getNextEvent(event)
            if(event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName ?: continue
                timeline.add(org.focusguard.core.ForegroundEvent(pkg,event.timeStamp,true)); active.add(pkg)
                if(event.timeStamp>=start) launches[pkg] = (launches[pkg] ?: 0) + 1
            }
            if(event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                val pkg=event.packageName ?: continue
                timeline.add(org.focusguard.core.ForegroundEvent(pkg,event.timeStamp,false)); active.remove(pkg)
            }
            if(event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                active.forEach { timeline.add(org.focusguard.core.ForegroundEvent(it,event.timeStamp,false)) }; active.clear()
            }
            if(event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN && event.timeStamp>=start) unlocks++
        }
        val apps=org.focusguard.core.UsageReducer.durations(timeline,start,end)
        return UsageReport(apps, launches, if(android.os.Build.VERSION.SDK_INT >= 28) unlocks else null)
    }
}
