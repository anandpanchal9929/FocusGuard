package org.focusguard.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.focusguard.core.TimerState

val Context.guardStore by preferencesDataStore("focusguard")
class Preferences(private val context: Context) {
    val flow = context.guardStore.data
    suspend fun snapshot() = flow.first()
    suspend fun bool(name: String, value: Boolean) { context.guardStore.edit { it[booleanPreferencesKey(name)] = value } }
    suspend fun number(name: String, value: Long) { context.guardStore.edit { it[longPreferencesKey(name)] = value } }
    suspend fun text(name: String, value: String) { context.guardStore.edit { it[stringPreferencesKey(name)] = value } }
    suspend fun beginTimer(value: TimerState,kind: String) { context.guardStore.edit { p ->
        p[stringPreferencesKey("sessionId")]=java.util.UUID.randomUUID().toString()
        p[stringPreferencesKey("timerKind")]=kind
        p[stringPreferencesKey("sessionOwner")]=p[stringPreferencesKey("userId")] ?: ""
        p[longPreferencesKey("start")]=value.startMs; p[longPreferencesKey("duration")]=value.durationMs
        p[longPreferencesKey("pausedAt")]=0; p[longPreferencesKey("paused")]=0
    } }
    suspend fun timer(value: TimerState) { context.guardStore.edit { p ->
        p[longPreferencesKey("start")] = value.startMs; p[longPreferencesKey("duration")] = value.durationMs
        p[longPreferencesKey("pausedAt")] = value.pausedAtMs; p[longPreferencesKey("paused")] = value.pausedMs
    } }
}
fun androidx.datastore.preferences.core.Preferences.flag(name: String) = this[booleanPreferencesKey(name)] ?: false
fun androidx.datastore.preferences.core.Preferences.num(name: String, default: Long = 0) = this[longPreferencesKey(name)] ?: default
fun androidx.datastore.preferences.core.Preferences.str(name: String, default: String = "") = this[stringPreferencesKey(name)] ?: default
fun androidx.datastore.preferences.core.Preferences.timer() = TimerState(num("start"),num("duration"),num("pausedAt"),num("paused"))
