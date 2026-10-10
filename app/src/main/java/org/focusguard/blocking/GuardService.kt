package org.focusguard.blocking

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import android.view.Gravity
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import org.focusguard.FocusGuardApp
import org.focusguard.core.*
import org.focusguard.data.*
import org.focusguard.usage.UsageRepository
import java.time.ZonedDateTime

class GuardService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var rules = emptyList<AppRule>()
    private var prefs: androidx.datastore.preferences.core.Preferences = androidx.datastore.preferences.core.emptyPreferences()
    private var foreground = ""
    private var overlayMessage: TextView? = null
    private var overlayKey: String? = null
    private var shortScreen = false
    private var overlay: LinearLayout? = null
    private var usage: Map<String, Long>? = null
    private var usageAt = 0L
    private val app get() = application as FocusGuardApp
    override fun onServiceConnected() {
        scope.launch { combine(app.database.dao().rules(),app.preferences.flow) { r,p -> r to p }.collect { (r,p) -> rules=r; prefs=p; evaluate() } }
        scope.launch { while(isActive) { evaluate(); delay(1000) } }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if(pkg == packageName) return
        if(event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) { foreground=pkg; shortScreen=false }
        if(pkg == "com.google.android.youtube" || pkg == "com.instagram.android") {
            val root = rootInActiveWindow
            shortScreen = root != null && detect(root,pkg)
        }
        evaluate()
    }
    private fun detect(node: AccessibilityNodeInfo, pkg: String, depth: Int = 0): Boolean {
        if(depth > 20) return false
        // Only resource identifiers are inspected; text and descriptions are never read or retained.
        val id = node.viewIdResourceName?.substringAfter(":id/") ?: ""
        val match = if(pkg == "com.google.android.youtube") id in setOf("reel_player_page_container", "reel_watch_fragment_root")
        else id in setOf("clips_viewer_view_pager", "clips_viewer_container")
        if(match && node.isVisibleToUser) return true
        for(index in 0 until minOf(node.childCount,100)) {
            val child = node.getChild(index) ?: continue
            if(detect(child,pkg,depth+1)) return true
        }
        return false
    }
    private fun evaluate() {
        if(foreground.isBlank()) return
        val now = System.currentTimeMillis()
        if(now - usageAt > 15_000) {
            usageAt = now
            scope.launch { usage = withContext(Dispatchers.IO) { UsageRepository(this@GuardService).report()?.apps } }
        }
        // Never block system settings, dialer, emergency interfaces, launchers or our own app.
        val home = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        val launcher = packageManager.resolveActivity(home,0)?.activityInfo?.packageName
        val dialer = getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage
        val protected=setOf("com.android.settings","com.android.systemui","com.android.phone","com.android.server.telecom","com.android.emergency","com.android.permissioncontroller","com.google.android.permissioncontroller","com.google.android.apps.safetyhub")
        if(foreground == packageName || foreground == launcher || foreground == dialer || foreground in protected) { dismiss(); return }
        val strict = prefs.flag("strict") && prefs.timer().active(now)
        val focus = prefs.str("timerKind","focus")=="focus" && prefs.timer().active(now) && (strict || prefs.num("pausedAt") == 0L)
        val selected = rules.firstOrNull { it.packageName == foreground && it.profile == prefs.str("profile","Default") }
        var reason = selected?.let {
            val schedule = if(it.startMinute >= 0 && it.endMinute >= 0) Schedule(it.startMinute,it.endMinute,it.days.split(',').mapNotNull(String::toIntOrNull).toSet()) else null
            RuleEngine.reason(Rule(it.enabled,it.always,it.focusOnly,it.limitMinutes,schedule),ZonedDateTime.now(),usage?.let { values -> values[foreground] ?: 0L },focus,it.bypassUntil,strict)
        }
        if(reason == null && shortScreen && ((foreground == "com.google.android.youtube" && prefs.flag("shorts")) || (foreground == "com.instagram.android" && prefs.flag("reels")))) reason = "Short-video screen restriction"
        if(reason == null) dismiss() else show(reason,selected,strict)
    }
    private fun show(reason: String, rule: AppRule?, strict: Boolean) {
        val now=System.currentTimeMillis()
        val timer=prefs.timer()
        val detail=when(reason) {
            "Your focus session is active" -> if(timer.durationMs==0L) "Until this stopwatch session ends" else {
                val seconds=timer.remaining(now)/1000
                "Focus time remaining: ${seconds/60}m ${seconds%60}s${if(timer.pausedAtMs>0) " · paused" else ""}"
            }
            "Daily app limit reached" -> {
                val local=ZonedDateTime.now()
                val minutes=java.time.Duration.between(local,local.toLocalDate().plusDays(1).atStartOfDay(local.zone)).toMinutes()
                "Daily limit resets in ${minutes/60}h ${minutes%60}m"
            }
            "Scheduled focus time" -> if(rule!=null && rule.startMinute!=rule.endMinute) {
                val local=ZonedDateTime.now()
                var end=local.withHour(rule.endMinute/60).withMinute(rule.endMinute%60).withSecond(0).withNano(0)
                if(!end.isAfter(local)) end=end.plusDays(1)
                val minutes=java.time.Duration.between(local,end).toMinutes()
                "Schedule ends in ${minutes/60}h ${minutes%60}m"
            } else "Blocked on the selected weekday"
            else -> "Take a breath. Return to what matters."
        }
        val message="FocusGuard\n\n$reason\n\n$detail"
        val key="$foreground|$reason|$strict|${prefs.num("breakMinutes",5)}"
        if(overlay!=null && overlayKey==key) { overlayMessage?.text=message; return }
        dismiss()
        val layout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(48,48,48,48); setBackgroundColor(Color.rgb(15,44,39)) }
        overlayMessage=TextView(this).apply { text=message; textSize=24f; setTextColor(Color.WHITE); gravity=Gravity.CENTER }
        layout.addView(overlayMessage)
        layout.addView(Button(this).apply { text="Return home"; setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME); dismiss() } })
        if(rule != null && !strict) layout.addView(Button(this).apply { text="Allow ${prefs.num("breakMinutes",5)} minutes"; setOnClickListener { scope.launch { val current=app.preferences.snapshot(); if(current.flag("strict") && current.timer().active(System.currentTimeMillis())) return@launch; app.database.dao().save(rule.copy(bypassUntil=System.currentTimeMillis()+current.num("breakMinutes",5).coerceIn(1,60)*60_000)); dismiss() } } })
        layout.addView(Button(this).apply { text="Emergency exit"; setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME); dismiss() } })
        val params=WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,android.graphics.PixelFormat.OPAQUE)
        getSystemService(WindowManager::class.java).addView(layout,params); overlay=layout; overlayKey=key
    }
    private fun dismiss() { overlay?.let { getSystemService(WindowManager::class.java).removeView(it) }; overlay=null; overlayMessage=null; overlayKey=null }
    override fun onInterrupt() { dismiss() }
    override fun onDestroy() { scope.cancel(); dismiss(); super.onDestroy() }
}
