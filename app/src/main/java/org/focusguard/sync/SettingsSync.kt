package org.focusguard.sync

import org.focusguard.FocusGuardApp
import org.focusguard.data.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

class SettingsSync(private val app: FocusGuardApp) {
    companion object {
        val booleanKeys=setOf("dark","strict","reels","shorts","notifications")
        val numberKeys=setOf("usageGoal","focusGoal","breakMinutes","sessionMinutes","webStart","webEnd")
        val textKeys=setOf("profile","domains","webDays")
    }
    suspend fun enqueue() {
        val p=app.preferences.snapshot()
        if(!p.flag("sync") || !p.flag("syncSettings") || p.str("userId").isBlank()) return
        val config=JSONObject()
        booleanKeys.forEach { config.put(it,p.flag(it)) }
        numberKeys.forEach { config.put(it,p.num(it,when(it){"usageGoal"->180;"focusGoal"->90;"breakMinutes"->5;"sessionMinutes"->25;else->-1})) }
        textKeys.forEach { config.put(it,p.str(it,if(it=="profile") "Default" else "")) }
        val rules=JSONArray()
        app.database.dao().allRules().forEach { rule -> rules.put(JSONObject().put("packageName",rule.packageName).put("label",rule.label).put("enabled",rule.enabled).put("always",rule.always).put("focusOnly",rule.focusOnly).put("limitMinutes",rule.limitMinutes).put("startMinute",rule.startMinute).put("endMinute",rule.endMinute).put("days",rule.days).put("profile",rule.profile)) }
        val now=System.currentTimeMillis(); val id=UUID.randomUUID().toString()
        val payload=JSONObject().put("operation",id).put("config",config).put("rules",rules).put("client_updated_at",java.time.Instant.ofEpochMilli(now).toString()).put("user_id",p.str("userId"))
        app.preferences.number("settingsModified",now)
        app.database.dao().enqueue(Outbox(id,"settings_bundle",payload.toString(),now))
    }
    suspend fun apply(bundle: JSONObject) {
        val config=bundle.getJSONObject("settings")
        booleanKeys.forEach { if(config.has(it)) app.preferences.bool(it,config.getBoolean(it)) }
        numberKeys.forEach { if(config.has(it)) app.preferences.number(it,config.getLong(it)) }
        textKeys.forEach { if(config.has(it)) app.preferences.text(it,config.getString(it)) }
        val list=bundle.optJSONArray("rules") ?: return
        val rules=(0 until list.length()).map { index ->
            val rule=list.getJSONObject(index)
            AppRule(rule.getString("packageName"),rule.getString("label"),rule.getBoolean("enabled"),rule.getBoolean("always"),rule.getBoolean("focusOnly"),rule.getInt("limitMinutes").coerceAtLeast(0),rule.getInt("startMinute").coerceIn(-1,1439),rule.getInt("endMinute").coerceIn(-1,1439),rule.getString("days"),rule.getString("profile"))
        }
        app.database.dao().replaceRules(rules)
    }
}
