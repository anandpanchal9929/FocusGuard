package org.focusguard.core

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class RulesTest {
    private val monday=ZonedDateTime.parse("2026-10-12T23:00:00+05:30[Asia/Kolkata]")
    @Test fun overnightUsesPreviousWeekday() {
        val schedule=Schedule(22*60,6*60,setOf(1))
        assertTrue(schedule.active(monday)); assertTrue(schedule.active(monday.plusHours(6)))
        assertFalse(schedule.active(monday.plusHours(7))); assertFalse(schedule.active(monday.plusDays(1)))
    }
    @Test fun exactBoundaries() { val s=Schedule(9*60,10*60,setOf(1)); assertTrue(s.active(monday.withHour(9))); assertFalse(s.active(monday.withHour(10))) }
    @Test fun equalTimesMeansWholeSelectedDay() { assertTrue(Schedule(0,0,setOf(1)).active(monday)); assertFalse(Schedule(0,0,setOf(2)).active(monday)) }
    @Test fun localZoneDeterminesSchedule() { val s=Schedule(22*60,23*60,setOf(1)); assertFalse(s.active(monday)); assertTrue(s.active(monday.minusMinutes(1))); assertFalse(s.active(monday.withZoneSameInstant(java.time.ZoneId.of("UTC")))) }
    @Test fun missingUsageDoesNotInventLimit() { assertNull(RuleEngine.reason(Rule(true,false,false,10,null),monday,null,false,0)) }
    @Test fun limitThreshold() { val rule=Rule(true,false,false,10,null); assertNull(RuleEngine.reason(rule,monday,599999,false,0)); assertNotNull(RuleEngine.reason(rule,monday,600000,false,0)) }
    @Test fun bypassExpires() { val rule=Rule(true,true,false,0,null); val now=monday.toInstant().toEpochMilli(); assertNull(RuleEngine.reason(rule,monday,0,false,now+1)); assertNotNull(RuleEngine.reason(rule,monday,0,false,now)) }
    @Test fun strictFocusOverridesAnEarlierBreakAllowance() {
        val rule=Rule(true,false,true,0,null)
        val allowance=monday.toInstant().toEpochMilli()+300000
        assertNull(RuleEngine.reason(rule,monday,0,true,allowance,false))
        assertNotNull(RuleEngine.reason(rule,monday,0,true,allowance,true))
    }
    @Test fun focusAndDisabledRules() { assertNotNull(RuleEngine.reason(Rule(true,false,true,0,null),monday,0,true,0)); assertNull(RuleEngine.reason(Rule(false,true,true,1,null),monday,999999,true,0)) }
    @Test fun timerRestoresFromPersistedTimestamps() { val timer=TimerState(1000,60000); assertEquals(30000,timer.remaining(31000)); assertFalse(timer.active(61000)) }
    @Test fun pauseFreezesAndResumeSubtractsPause() { val timer=TimerState(1000,60000,11000); assertEquals(50000,timer.remaining(999999)); assertEquals(40000,timer.copy(pausedAtMs=0,pausedMs=10000).remaining(31000)) }
    @Test fun stopwatchDoesNotExpire() { assertTrue(TimerState(1000,0).active(999999)); assertEquals(10000,TimerState(1000,0).elapsed(11000)) }
}
