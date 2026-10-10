package org.focusguard.core

data class ForegroundEvent(val packageName: String,val time: Long,val resumed: Boolean)
object UsageReducer {
    fun durations(events: List<ForegroundEvent>,start: Long,end: Long): Map<String,Long> {
        val active=mutableMapOf<String,Long>(); val result=mutableMapOf<String,Long>()
        for(event in events.sortedBy { it.time }) {
            if(event.time>end) break
            if(event.resumed) active.putIfAbsent(event.packageName,event.time)
            else active.remove(event.packageName)?.let { began ->
                val duration=(minOf(end,event.time)-maxOf(start,began)).coerceAtLeast(0)
                result[event.packageName]=(result[event.packageName] ?: 0)+duration
            }
        }
        for((pkg,began) in active) result[pkg]=(result[pkg] ?: 0)+(end-maxOf(start,began)).coerceAtLeast(0)
        return result.filterValues { it>0 }
    }
}
