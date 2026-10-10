package org.focusguard.core

import java.time.ZonedDateTime

data class Schedule(val start: Int, val end: Int, val days: Set<Int>) {
    fun active(now: ZonedDateTime): Boolean {
        val minute = now.hour * 60 + now.minute
        val day = now.dayOfWeek.value
        if (start == end) return day in days
        return if (start < end) day in days && minute >= start && minute < end
        else (day in days && minute >= start) || (now.minusDays(1).dayOfWeek.value in days && minute < end)
    }
}
data class Rule(val enabled: Boolean, val always: Boolean, val focusOnly: Boolean, val limitMinutes: Int, val schedule: Schedule?)
object RuleEngine {
    fun reason(rule: Rule, now: ZonedDateTime, usageMs: Long?, focus: Boolean, bypassUntil: Long, strict: Boolean = false): String? {
        if (!rule.enabled || (!(strict && focus) && now.toInstant().toEpochMilli() < bypassUntil)) return null
        if (rule.always) return "This app is blocked by your profile"
        if (rule.focusOnly && focus) return "Your focus session is active"
        if (rule.schedule?.active(now) == true) return "Scheduled focus time"
        if (rule.limitMinutes > 0 && usageMs != null && usageMs >= rule.limitMinutes * 60_000L) return "Daily app limit reached"
        return null
    }
}
data class TimerState(val startMs: Long, val durationMs: Long, val pausedAtMs: Long = 0, val pausedMs: Long = 0) {
    fun elapsed(now: Long) = ((if (pausedAtMs > 0) pausedAtMs else now) - startMs - pausedMs).coerceAtLeast(0)
    fun remaining(now: Long) = (durationMs - elapsed(now)).coerceAtLeast(0)
    fun active(now: Long) = startMs > 0 && (durationMs == 0L || remaining(now) > 0)
}
