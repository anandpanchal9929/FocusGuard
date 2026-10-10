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
        val apps = manager.queryAndAggregateUsageStats(start, end).mapValues { it.value.totalTimeInForeground }
            .filterValues { it > 0 }
        val launches = mutableMapOf<String, Int>()
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        var unlocks = 0
        while(events.hasNextEvent()) {
            events.getNextEvent(event)
            if(event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName ?: continue
                launches[pkg] = (launches[pkg] ?: 0) + 1
            }
            if(event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) unlocks++
        }
        return UsageReport(apps, launches, if(android.os.Build.VERSION.SDK_INT >= 28) unlocks else null)
    }
}
