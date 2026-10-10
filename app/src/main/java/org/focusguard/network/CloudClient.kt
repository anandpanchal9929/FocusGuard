package org.focusguard.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import org.focusguard.BuildConfig
import org.focusguard.FocusGuardApp
import org.focusguard.data.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class CloudClient(private val app: FocusGuardApp) {
    companion object { private val authLock=Mutex() }
    private val client=OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(20)).build()
    private suspend fun request(path: String, method: String = "GET", body: String? = null, authenticated: Boolean = true): String = withContext(Dispatchers.IO) {
        require(BuildConfig.SUPABASE_URL.startsWith("https://")) { "Configure a Supabase project before using cloud features" }
        if(authenticated) refresh()
        val token=if(authenticated) app.tokens.get("token") else BuildConfig.SUPABASE_ANON_KEY
        require(token.isNotBlank()) { "Sign in first" }
        val builder=Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/')+path)
            .header("apikey",BuildConfig.SUPABASE_ANON_KEY)
        if(authenticated) builder.header("Authorization","Bearer $token")
        val req=builder
            .header("Prefer","resolution=ignore-duplicates,return=representation")
            .method(method,body?.toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { response ->
            val text=response.body?.string().orEmpty()
            if(!response.isSuccessful) error("Cloud request failed (${response.code}). Check your connection and sign-in.")
            text
        }
    }
    suspend fun authenticate(email: String,password: String,signup: Boolean) {
        val result=JSONObject(request(if(signup) "/auth/v1/signup" else "/auth/v1/token?grant_type=password","POST",JSONObject().put("email",email).put("password",password).toString(),false))
        if(!result.has("access_token")) error("Check your email to confirm your account, then sign in")
        app.preferences.bool("sync",false)
        app.database.dao().clearQueue()
        app.tokens.put("token",result.getString("access_token"))
        app.tokens.put("refresh",result.optString("refresh_token"))
        app.preferences.number("tokenExpiry",System.currentTimeMillis()+result.optLong("expires_in",3600)*1000)
        app.preferences.text("userId",result.getJSONObject("user").getString("id"))
    }
    suspend fun refresh() = authLock.withLock {
        val p=app.preferences.snapshot()
        if(p.num("tokenExpiry")>System.currentTimeMillis()+60_000 && app.tokens.get("token").isNotBlank()) return@withLock
        val result=JSONObject(request("/auth/v1/token?grant_type=refresh_token","POST",JSONObject().put("refresh_token",app.tokens.get("refresh")).toString(),false))
        app.tokens.put("token",result.getString("access_token")); app.tokens.put("refresh",result.getString("refresh_token"))
        app.preferences.number("tokenExpiry",System.currentTimeMillis()+result.optLong("expires_in",3600)*1000)
    }
    suspend fun upload(op: Outbox) {
        refresh()
        val user=app.preferences.snapshot().str("userId")
        val payload=JSONObject(op.payload)
        check(payload.getString("user_id")==user) { "Queued operation belongs to a different account" }
        if(op.tableName=="settings_bundle") {
            val p=app.preferences.snapshot()
            if(!p.flag("syncSettings")) return
            payload.remove("user_id")
            val response=JSONObject(request("/rest/v1/rpc/apply_settings","POST",payload.toString()))
            if(app.preferences.snapshot().num("settingsModified")<=op.createdAt) org.focusguard.sync.SettingsSync(app).apply(response)
        } else request("/rest/v1/${op.tableName}?on_conflict=id","POST",payload.toString())
    }
    suspend fun restore(): Int {
        refresh()
        val array=JSONArray(request("/rest/v1/focus_sessions?select=*&order=started_at.desc"))
        for(i in 0 until array.length()) {
            val row=array.getJSONObject(i)
            app.database.dao().session(FocusSession(row.getString("id"),java.time.Instant.parse(row.getString("started_at")).toEpochMilli(),java.time.Instant.parse(row.getString("ended_at")).toEpochMilli(),row.getLong("focused_ms"),row.getBoolean("completed")))
        }
        return array.length()
    }
    suspend fun rooms()=request("/rest/v1/focus_rooms?select=*")
    suspend fun createRoom(name: String): String {
        val p=app.preferences.snapshot()
        return request("/rest/v1/focus_rooms","POST",JSONObject().put("owner_id",p.str("userId")).put("name",name).toString())
    }
    suspend fun joinRoom(code: String)=request("/rest/v1/rpc/join_room","POST",JSONObject().put("invite",code.trim()).toString())
    suspend fun members(room: String)=request("/rest/v1/room_members?room_id=eq.$room&select=user_id,status,updated_at")
    suspend fun startRoom(room: String,minutes: Int)=request("/rest/v1/rpc/start_room","POST",JSONObject().put("room",room).put("minutes",minutes).toString())
    suspend fun setStatus(room: String,status: String)=request("/rest/v1/room_members?room_id=eq.$room&user_id=eq.${app.preferences.snapshot().str("userId")}","PATCH",JSONObject().put("status",status).put("updated_at",java.time.Instant.now().toString()).toString())
    suspend fun leaderboard(room: String)=request("/rest/v1/leaderboard_records?room_id=eq.$room&select=user_id,focused_ms&order=focused_ms.desc")
    fun roomChanges()=callbackFlow {
        refresh()
        val token=app.tokens.get("token")
        val url=BuildConfig.SUPABASE_URL.replace("https://","wss://").trimEnd('/')+"/realtime/v1/websocket?apikey="+BuildConfig.SUPABASE_ANON_KEY+"&vsn=1.0.0"
        val socket=client.newWebSocket(Request.Builder().url(url).build(),object: WebSocketListener() {
            override fun onOpen(webSocket: WebSocket,response: Response) {
                val changes=JSONArray().put(JSONObject().put("event","*").put("schema","public").put("table","room_members")).put(JSONObject().put("event","*").put("schema","public").put("table","focus_rooms"))
                val payload=JSONObject().put("access_token",token).put("config",JSONObject().put("postgres_changes",changes))
                webSocket.send(JSONObject().put("topic","realtime:focusguard").put("event","phx_join").put("payload",payload).put("ref","1").toString())
            }
            override fun onMessage(webSocket: WebSocket,text: String) { try { if(JSONObject(text).optString("event")=="postgres_changes") trySend(Unit) } catch(e: Exception) { close(e) } }
            override fun onFailure(webSocket: WebSocket,t: Throwable,response: Response?) { close(t) }
            override fun onClosed(webSocket: WebSocket,code: Int,reason: String) { close() }
        })
        val heartbeat=launch { var ref=2; while(true) { kotlinx.coroutines.delay(25_000); socket.send(JSONObject().put("topic","phoenix").put("event","heartbeat").put("payload",JSONObject()).put("ref",(ref++).toString()).toString()) } }
        awaitClose { heartbeat.cancel(); socket.close(1000,"Leaving rooms screen") }
    }
    suspend fun friends()=request("/rest/v1/friendships?select=*")
    suspend fun inviteFriend(id: String)=request("/rest/v1/friendships","POST",JSONObject().put("user_id",app.preferences.snapshot().str("userId")).put("friend_id",id).toString())
    suspend fun acceptFriend(id: String)=request("/rest/v1/friendships?user_id=eq.$id&friend_id=eq.${app.preferences.snapshot().str("userId")}","PATCH",JSONObject().put("accepted",true).toString())
    suspend fun restoreSettings() {
        val p=app.preferences.snapshot(); check(!(p.flag("strict") && p.timer().active(System.currentTimeMillis()))) { "Settings are locked during this session" }
        val rows=JSONArray(request("/rest/v1/user_preferences?select=settings"))
        if(rows.length()==0) error("No settings backup yet")
        val config=rows.getJSONObject(0).getJSONObject("settings")
        org.focusguard.sync.SettingsSync(app).apply(config)
    }
    suspend fun deleteAccount() { request("/rest/v1/rpc/delete_my_account","POST","{}"); signOut() }
    suspend fun signOut() { app.preferences.bool("sync",false); app.database.dao().clearQueue(); app.tokens.clear(); app.preferences.text("userId","") }
}
