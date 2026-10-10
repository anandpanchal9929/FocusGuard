package org.focusguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.focusguard.data.*
import org.focusguard.focus.FocusRepository
import org.focusguard.network.CloudClient
import org.focusguard.usage.*
import java.time.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun requestNotifications() { if(android.os.Build.VERSION.SDK_INT>=33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    private val vpnConsent=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> if(result.resultCode==RESULT_OK) startForegroundService(Intent(this,org.focusguard.web.DnsVpnService::class.java)) }
    fun startFilter() { val consent=android.net.VpnService.prepare(this); if(consent!=null) vpnConsent.launch(consent) else startForegroundService(Intent(this,org.focusguard.web.DnsVpnService::class.java)) }
    private var exportText=""
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri != null) contentResolver.openOutputStream(uri)?.use { it.write(exportText.toByteArray()) }
    }
    fun saveExport(text: String) { exportText=text; export.launch("focusguard-export.json") }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { GuardUI(this) } }
}
class GuardModel(app: android.app.Application) : AndroidViewModel(app) {
    val guard=app as FocusGuardApp
    val prefs=guard.preferences.flow.stateIn(viewModelScope,SharingStarted.Eagerly,androidx.datastore.preferences.core.emptyPreferences())
    val rules=guard.database.dao().rules().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val history=guard.database.dao().history().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val usage=MutableStateFlow<UsageReport?>(null)
    val days=MutableStateFlow<List<Pair<LocalDate,UsageReport?>>>(emptyList())
    val message=MutableStateFlow("")
    val busy=MutableStateFlow(false)
    val timer=FocusRepository(guard)
    val cloud=CloudClient(guard)
    val installed=guard.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
        .map { it.activityInfo.packageName to it.loadLabel(guard.packageManager).toString() }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        .filter { it.first != guard.packageName }
    init { refresh(); viewModelScope.launch { while(isActive) { val t=prefs.value.timer(); if(t.startMs>0 && t.durationMs>0 && t.remaining(System.currentTimeMillis())==0L) timer.finish(true); delay(1000) } } }
    fun run(block: suspend () -> Unit) { viewModelScope.launch { busy.value=true; try { block() } catch(e: Exception) { message.value=e.message ?: "Operation failed" } finally { busy.value=false } } }
    fun refresh(count: Int = 7) { run { withContext(Dispatchers.IO) { val repo=UsageRepository(guard); usage.value=repo.report(); days.value=(count-1 downTo 0).map { LocalDate.now().minusDays(it.toLong()).let { date -> date to repo.report(date) } } } } }
    fun bool(key: String,value: Boolean)=run { guard.preferences.bool(key,value); if(key in org.focusguard.sync.SettingsSync.booleanKeys) org.focusguard.sync.SettingsSync(guard).enqueue() }
    fun num(key: String,value: Long)=run { guard.preferences.number(key,value); if(key in org.focusguard.sync.SettingsSync.numberKeys) org.focusguard.sync.SettingsSync(guard).enqueue() }
    fun text(key: String,value: String)=run { guard.preferences.text(key,value); if(key in org.focusguard.sync.SettingsSync.textKeys) org.focusguard.sync.SettingsSync(guard).enqueue() }
    fun save(rule: AppRule)=run { guard.database.dao().save(rule); org.focusguard.sync.SettingsSync(guard).enqueue() }
    suspend fun export(): String {
        val sessions=JSONArray(); guard.database.dao().sessions().forEach { sessions.put(JSONObject().put("id",it.id).put("startedAt",it.startedAt).put("focusedMs",it.focusedMs).put("completed",it.completed)) }
        val rules=JSONArray(); guard.database.dao().allRules().forEach { rules.put(JSONObject().put("package",it.packageName).put("label",it.label).put("limitMinutes",it.limitMinutes).put("profile",it.profile)) }
        return JSONObject().put("version",1).put("sessions",sessions).put("rules",rules).toString(2)
    }
}
private fun duration(ms: Long): String { val seconds=ms.coerceAtLeast(0)/1000; return "%02d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60) }
@Composable private fun GuardUI(activity: MainActivity, vm: GuardModel = viewModel()) {
    val p by vm.prefs.collectAsState(); val rules by vm.rules.collectAsState(); val history by vm.history.collectAsState()
    val usage by vm.usage.collectAsState(); val days by vm.days.collectAsState(); val message by vm.message.collectAsState(); val busy by vm.busy.collectAsState()
    var page by remember { mutableStateOf("Home") }; var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { now=System.currentTimeMillis(); delay(1000) } }
    val t=p.timer(); val strict=p.flag("strict") && t.active(now)
    val colors=if(p.flag("dark")) darkColorScheme(primary=Color(0xFF82D9BC)) else lightColorScheme(primary=Color(0xFF176B58),background=Color(0xFFF4F7F3),surface=Color(0xFFF4F7F3))
    MaterialTheme(colorScheme=colors) {
        Scaffold(bottomBar={ NavigationBar { listOf("Home","Focus","Rules","Settings").forEach { label -> NavigationBarItem(selected=page==label,onClick={page=label},icon={Text(when(label){"Home"->"◈";"Focus"->"◷";"Rules"->"◇";else->"⚙"})},label={Text(label)}) } } }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("FOCUSGUARD",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                Text(if(p.flag("onboarded")) page else "Make room for what matters",style=MaterialTheme.typography.headlineLarge)
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(message.isNotEmpty()) Card { Column(Modifier.padding(12.dp)) { Text(message); TextButton(onClick={vm.message.value=""}) { Text("Dismiss") } } }
                if(!p.flag("onboarded")) {
                    Text("FocusGuard keeps your focus timer, history and app rules on this device. Cloud backup is optional.")
                    Text("Usage access reads app usage. Accessibility checks foreground apps and known short-video view identifiers to enforce rules. No screen text is saved or uploaded. You can revoke either permission at any time.")
                    Button(onClick={vm.bool("onboarded",true); page="Permissions"}) { Text("Set up FocusGuard") }
                } else when(page) {
                    "Home" -> {
                        Card { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Your time today",style=MaterialTheme.typography.titleMedium)
                            Text(usage?.let { duration(it.totalMs) } ?: "Usage access needed",style=MaterialTheme.typography.headlineLarge)
                            val goal=p.num("usageGoal",180).coerceAtLeast(1)
                            if(usage != null) LinearProgressIndicator(progress={ (usage!!.totalMs/(goal*60_000f)).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                            Text("Daily screen-time goal: $goal minutes")
                        } }
                        Text("Focused today: "+duration(history.filter { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()==LocalDate.now() }.sumOf { it.focusedMs }))
                        Text("${history.count { it.completed }} completed sessions")
                        Button(onClick={page="Focus"}) { Text("Begin a focus session") }
                        listOf("Analytics","History","Permissions","Short videos","Websites","Rooms","Profile").forEach { label -> OutlinedButton(onClick={page=label},modifier=Modifier.fillMaxWidth()) { Text(label) } }
                        TextButton(onClick={vm.refresh()}) { Text("Refresh usage") }
                    }
                    "Permissions" -> {
                        Text("Usage access: "+if(UsageRepository(activity).allowed()) "Enabled" else "Not enabled")
                        Button(onClick={activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))}) { Text("Open usage access settings") }
                        Text("App blocking requires your explicit activation of FocusGuard in Accessibility settings. Android may ask you to allow restricted settings for a sideloaded APK.")
                        Button(onClick={activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}) { Text("Open accessibility settings") }
                        Text("Daily limits cannot be evaluated when usage access is unavailable. Rules and focus schedules still work with accessibility enabled.")
                        TextButton(onClick={vm.refresh()}) { Text("Recheck usage access") }
                    }
                    "Analytics" -> {
                        Row { TextButton(onClick={vm.refresh(7)}) { Text("7 days") }; TextButton(onClick={vm.refresh(30)}) { Text("30 days") } }
                        if(usage==null) Text("Grant usage access to see real device statistics.") else {
                            val max=days.maxOfOrNull { it.second?.totalMs ?: 0 }?.coerceAtLeast(1) ?: 1
                            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                                val width=size.width/days.size.coerceAtLeast(1)
                                days.forEachIndexed { index,(_,report) -> val height=(report?.totalMs ?: 0).toFloat()/max*size.height; drawRect(Color(0xFF176B58),topLeft=androidx.compose.ui.geometry.Offset(index*width,size.height-height),size=androidx.compose.ui.geometry.Size(width*0.7f,height)) }
                            }
                            days.forEach { (date,report) -> Text("$date · ${report?.let { duration(it.totalMs) } ?: "Unavailable"}") }
                            val previous=days.dropLast(1).lastOrNull()?.second?.totalMs
                            if(previous!=null) Text("Change from yesterday: ${((usage!!.totalMs-previous)/60_000)} min")
                            Text("Unlock events: ${usage!!.unlocks ?: "Unavailable on this Android version"}")
                            Text("Activity resumes are launch estimates. Screen time is reconstructed from retained usage events; missing old events can undercount, and multiwindow totals can overlap.")
                            usage!!.apps.entries.sortedByDescending { it.value }.forEach { (pkg,ms) -> Text("${vm.installed.firstOrNull { it.first==pkg }?.second ?: pkg}\n${duration(ms)} · ${usage!!.launches[pkg] ?: 0} resumes") }
                        }
                    }
                    "Focus" -> {
                        Text(if(t.startMs==0L) "Ready when you are" else if(t.durationMs==0L) duration(t.elapsed(now)) else duration(t.remaining(now)),style=MaterialTheme.typography.displayMedium)
                        if(t.startMs==0L) {
                            NumberField("Session minutes (0 = stopwatch)",25) { value -> vm.num("sessionMinutes",value.toLong()) }
                            Button(onClick={vm.run { vm.timer.start(p.num("sessionMinutes",25)) }}) { Text("Start focus") }
                            Text("Short and long breaks are timed separately. Focus-only app rules pause during breaks.")
                            Row { TextButton(onClick={vm.run { vm.timer.start(5,"break") }}) { Text("5 min break") }; TextButton(onClick={vm.run { vm.timer.start(15,"break") }}) { Text("15 min break") } }
                        } else {
                            Text(if(t.pausedAtMs>0) "Paused" else "Session running · restored from saved timestamps")
                            Button(enabled=!strict,onClick={vm.run { vm.timer.pauseOrResume() }}) { Text(if(t.pausedAtMs>0) "Resume" else "Pause") }
                            if(strict) {
                                Text("Strict mode locks rule editing and pausing. Android permissions, settings and emergency access remain available.")
                                val requested=p.num("endRequested")
                                Button(onClick={if(requested==0L) vm.num("endRequested",now) else if(now-requested>=30_000) vm.run { vm.timer.finish(false) }}) { Text(if(requested==0L) "Request end (30-second cooldown)" else if(now-requested<30_000) "Wait ${(30_000-now+requested)/1000}s" else "Confirm end") }
                            } else Button(onClick={vm.run { vm.timer.finish(false) }}) { Text("End session") }
                            TextButton(onClick={vm.run { vm.timer.finish(false) }}) { Text("Emergency end session") }
                        }
                        Toggle("Strict mode",p.flag("strict"),!t.active(now)) { vm.bool("strict",it) }
                        Text("Selected apps in your active profile are blocked during focus. Stopwatch sessions require manual completion.")
                        NumberField("Daily focus goal (minutes)",p.num("focusGoal",90).toInt()) { vm.num("focusGoal",it.toLong()) }
                        Text("One uninterrupted step is progress.",color=MaterialTheme.colorScheme.primary)
                    }
                    "Rules" -> {
                        if(strict) Text("Rule editing is locked until the active session ends.")
                        var profile by remember(p.str("profile")) { mutableStateOf(p.str("profile","Default")) }
                        OutlinedTextField(profile,{profile=it},label={Text("Active profile")},enabled=!strict)
                        Button(enabled=!strict,onClick={vm.text("profile",profile.ifBlank { "Default" })}) { Text("Activate profile") }
                        Text("Each app belongs to one profile. Change its profile below to organize restrictions.")
                        var search by remember { mutableStateOf("") }
                        OutlinedTextField(search,{search=it},label={Text("Search installed apps")})
                        NumberField("Temporary allowance (minutes)",p.num("breakMinutes",5).toInt(),!strict) { vm.num("breakMinutes",it.coerceIn(1,60).toLong()) }
                        vm.installed.filter { it.second.contains(search,true) }.forEach { (pkg,label) ->
                            val rule=rules.firstOrNull { it.packageName==pkg }
                            Card { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Toggle(label,rule?.enabled==true,!strict) { vm.save((rule ?: AppRule(pkg,label,profile=p.str("profile","Default"))).copy(enabled=it)) }
                                if(rule?.enabled==true) RuleEditor(rule,strict,vm)
                            } }
                        }
                    }
                    "Short videos" -> {
                        Text("Experimental, opt-in detectors for YouTube Shorts and Instagram Reels. Only known visible view identifiers are matched; ordinary app use is allowed. Interface changes can cause missed detections. No detector has been verified on your device.")
                        Toggle("YouTube Shorts",p.flag("shorts"),!strict) { vm.bool("shorts",it) }
                        Toggle("Instagram Reels",p.flag("reels"),!strict) { vm.bool("reels",it) }
                        Text("If recognition fails, use whole-app rules for dependable package-level blocking. Other short-video apps are not supported.")
                    }
                    "Websites" -> {
                        Text("Experimental local system-DNS filter. Blocked domains and subdomains receive NXDOMAIN. Ordinary DNS queries are sent to Cloudflare 1.1.1.1; domains are not logged by FocusGuard. Private DNS, encrypted DNS, cached addresses and direct IP connections can bypass this filter. DNS over TCP is not supported. This cannot block individual encrypted URL paths.")
                        Text("Android allows one VPN at a time. Starting FocusGuard can disconnect another VPN. Disabling it restores normal DNS. Test it on your device before relying on it.")
                        var domains by remember(p.str("domains")) { mutableStateOf(p.str("domains")) }
                        OutlinedTextField(domains,{domains=it},label={Text("Blocked domains, one per line")},enabled=!strict)
                        Button(enabled=!strict,onClick={try { val normalized=domains.lines().filter { it.isNotBlank() }.map(org.focusguard.web.DnsCodec::normalize); vm.text("domains",normalized.joinToString("\n")) } catch(e: Exception) { vm.message.value=e.message.orEmpty() }}) { Text("Save domains") }
                        NumberField("Schedule start minute (0–1439)",p.num("webStart",0).toInt(),!strict) { vm.num("webStart",it.coerceIn(0,1439).toLong()) }
                        NumberField("Schedule end minute (0–1439)",p.num("webEnd",0).toInt(),!strict) { vm.num("webEnd",it.coerceIn(0,1439).toLong()) }
                        var weekdays by remember { mutableStateOf(p.str("webDays","1,2,3,4,5,6,7")) }
                        OutlinedTextField(weekdays,{weekdays=it},label={Text("Weekdays 1=Mon … 7=Sun")},enabled=!strict)
                        TextButton(enabled=!strict,onClick={ val values=weekdays.split(',').mapNotNull { it.trim().toIntOrNull() }; if(values.isNotEmpty() && values.all { it in 1..7 }) vm.text("webDays",values.joinToString(",")) else vm.message.value="Use weekdays 1 to 7" }) { Text("Save weekdays") }
                        Button(onClick={activity.startFilter()}) { Text("Enable with Android VPN consent") }
                        TextButton(onClick={activity.stopService(Intent(activity,org.focusguard.web.DnsVpnService::class.java))}) { Text("Stop DNS filter") }
                    }
                    "History" -> {
                        val dates=history.filter { it.focusedMs>=60_000 }.map { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.toSet()
                        var cursor=if(LocalDate.now() in dates) LocalDate.now() else LocalDate.now().minusDays(1); var streak=0
                        while(cursor in dates) { streak++; cursor=cursor.minusDays(1) }
                        Text("$streak day focus streak",style=MaterialTheme.typography.headlineSmall)
                        if(history.isEmpty()) Text("Your completed and interrupted sessions will appear here.")
                        history.forEach { session -> Card { Column(Modifier.padding(12.dp)) { Text(Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDateTime().toString()); Text("${duration(session.focusedMs)} · ${if(session.completed) "Completed" else "Ended early"}") } } }
                    }
                    "Rooms" -> {
                        Text("Join a room to share your focus state with its participants. Your local session keeps running if the connection drops. Room states can become stale while participants are offline. Leaderboards show self-reported synced focus time.")
                        var roomsJson by remember { mutableStateOf("[]") }; var selected by remember { mutableStateOf("") }; var membersJson by remember { mutableStateOf("[]") }; var leaderboardJson by remember { mutableStateOf("[]") }
                        var roomConnection by remember { mutableStateOf("Connecting…") }
                        LaunchedEffect(p.str("userId"),selected) {
                            if(p.str("userId").isNotBlank() && BuildConfig.SUPABASE_URL.isNotBlank()) {
                                suspend fun reload() { roomsJson=vm.cloud.rooms(); if(selected.isNotBlank()) { membersJson=vm.cloud.members(selected); leaderboardJson=vm.cloud.leaderboard(selected) }; roomConnection="Connected" }
                                launch { while(isActive) { try { reload() } catch(e: Exception) { roomConnection="Offline · retrying; personal timer remains active" }; delay(15_000) } }
                                launch { while(isActive) { try { vm.cloud.roomChanges().collect { reload() } } catch(e: Exception) { roomConnection="Realtime reconnecting" }; delay(5000) } }
                            } else roomConnection="Sign in to use rooms"
                        }
                        Text(roomConnection)
                        var name by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }
                        OutlinedTextField(name,{name=it},label={Text("Room name")})
                        Button(onClick={vm.run { vm.cloud.createRoom(name); roomsJson=vm.cloud.rooms() }}) { Text("Create room") }
                        OutlinedTextField(code,{code=it},label={Text("Invite code")})
                        Button(onClick={vm.run { vm.cloud.joinRoom(code); roomsJson=vm.cloud.rooms() }}) { Text("Join room") }
                        val rooms=try { JSONArray(roomsJson) } catch(e: Exception) { JSONArray() }
                        for(index in 0 until rooms.length()) {
                            val room=rooms.getJSONObject(index); val id=room.getString("id")
                            Card { Column(Modifier.padding(12.dp)) {
                                Text(room.getString("name"),style=MaterialTheme.typography.titleLarge)
                                Text("Invite: ${room.getString("invite_code")}")
                                Text("${room.optInt("duration_minutes",25)} minutes · ${room.optString("started_at","Not started")}")
                                TextButton(onClick={selected=id}) { Text("Show participants") }
                                if(room.getString("owner_id")==p.str("userId")) TextButton(onClick={vm.run { vm.cloud.startRoom(id,25); roomsJson=vm.cloud.rooms() }}) { Text("Start 25-minute group session") }
                                Button(enabled=!t.active(now),onClick={vm.run {
                                    val started=room.optString("started_at"); val durationMs=room.optInt("duration_minutes",25)*60_000L
                                    val remaining=try { (Instant.parse(started).toEpochMilli()+durationMs-System.currentTimeMillis()).coerceIn(0,durationMs) } catch(e: Exception) { durationMs }
                                    if(remaining==0L) error("This group session has ended; ask the owner to start another")
                                    vm.timer.start((remaining+59_999)/60_000)
                                    vm.guard.preferences.text("activeRoom",id)
                                    try { vm.cloud.setStatus(id,"focusing") } catch(e: Exception) { vm.message.value="Focus started offline. Shared room state could not update." }
                                }}) { Text("Focus with this room") }
                            } }
                        }
                        if(selected.isNotBlank()) {
                            Text("Participants",style=MaterialTheme.typography.titleMedium)
                            val members=try { JSONArray(membersJson) } catch(e: Exception) { JSONArray() }
                            for(index in 0 until members.length()) { val member=members.getJSONObject(index); Text("${member.getString("user_id").take(8)} · ${member.getString("status")} · updated ${member.getString("updated_at")}") }
                            Row { TextButton(onClick={vm.run { vm.cloud.setStatus(selected,"ready") }}) { Text("Ready") }; TextButton(onClick={vm.run { vm.cloud.setStatus(selected,"paused") }}) { Text("Paused") }; TextButton(onClick={vm.run { vm.cloud.setStatus(selected,"finished") }}) { Text("Finished") } }
                            Text("Leaderboard",style=MaterialTheme.typography.titleMedium)
                            val scores=try { JSONArray(leaderboardJson) } catch(e: Exception) { JSONArray() }
                            for(index in 0 until scores.length()) { val score=scores.getJSONObject(index); Text("${score.getString("user_id").take(8)} · ${duration(score.getLong("focused_ms"))}") }
                        }
                    }
                    "Profile" -> {
                        Text(if(BuildConfig.SUPABASE_URL.isBlank()) "Cloud is not configured. All offline features remain available." else "Cloud configured · "+if(p.str("userId").isBlank()) "signed out" else "signed in")
                        var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
                        OutlinedTextField(email,{email=it},label={Text("Email")})
                        OutlinedTextField(password,{password=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation())
                        Row { Button(onClick={vm.run { vm.cloud.authenticate(email,password,false); password="" }}) { Text("Sign in") }; TextButton(onClick={vm.run { vm.cloud.authenticate(email,password,true); password="" }}) { Text("Create account") } }
                        Toggle("Sync new focus sessions",p.flag("sync")) { value -> vm.run { if(value) check(p.str("userId").isNotBlank()) { "Sign in first" }; vm.guard.preferences.bool("sync",value); if(!value) vm.guard.database.dao().clearQueue() } }
                        Toggle("Also sync settings and app rules",p.flag("syncSettings"),!strict) { value -> vm.run { check(p.str("userId").isNotBlank()) { "Sign in first" }; vm.guard.preferences.bool("syncSettings",value); if(value) org.focusguard.sync.SettingsSync(vm.guard).enqueue() } }
                        Text("Settings sync includes selected app package names and domain rules, never raw usage. Timestamp conflicts resolve to the newest edit; keep device clocks accurate. Strict sessions defer configuration sync.")
                        Button(enabled=!strict,onClick={vm.run { vm.cloud.restoreSettings(); vm.message.value="Cloud settings restored" }}) { Text("Restore settings (replaces local rules)") }
                        Text("Only opted-in focus history is queued. Raw app usage stays on your device. Automatic sync runs when connected, approximately every 15 minutes.")
                        Button(onClick={vm.run { vm.message.value="Restored ${vm.cloud.restore()} sessions" }}) { Text("Restore cloud history") }
                        TextButton(onClick={vm.run { vm.cloud.signOut() }}) { Text("Sign out") }
                        Text("Your account ID: ${p.str("userId","Signed out")}")
                        var friendId by remember { mutableStateOf("") }; var friendsJson by remember { mutableStateOf("[]") }
                        OutlinedTextField(friendId,{friendId=it},label={Text("Friend's account UUID")})
                        Button(onClick={vm.run { java.util.UUID.fromString(friendId); vm.cloud.inviteFriend(friendId); friendsJson=vm.cloud.friends() }}) { Text("Send friend request") }
                        TextButton(onClick={vm.run { friendsJson=vm.cloud.friends() }}) { Text("Load friends and requests") }
                        val friends=try { JSONArray(friendsJson) } catch(e: Exception) { JSONArray() }
                        for(index in 0 until friends.length()) { val friend=friends.getJSONObject(index); Text("${friend.getString("user_id").take(8)} → ${friend.getString("friend_id").take(8)} · ${if(friend.getBoolean("accepted")) "Connected" else "Pending"}"); if(!friend.getBoolean("accepted") && friend.getString("friend_id")==p.str("userId")) TextButton(onClick={vm.run { vm.cloud.acceptFriend(friend.getString("user_id")); friendsJson=vm.cloud.friends() }}) { Text("Accept request") } }
                        var confirm by remember { mutableStateOf("") }
                        OutlinedTextField(confirm,{confirm=it},label={Text("Type DELETE to delete FocusGuard cloud data")})
                        Button(enabled=confirm=="DELETE",onClick={vm.run { vm.cloud.deleteAccount(); confirm="" }}) { Text("Delete FocusGuard cloud data") }
                    }
                    "Settings" -> {
                        Toggle("Dark theme",p.flag("dark")) { vm.bool("dark",it) }
                        Toggle("Session completion reminders",p.flag("notifications")) { vm.bool("notifications",it); if(it) activity.requestNotifications() }
                        NumberField("Daily screen-time goal (minutes)",p.num("usageGoal",180).toInt()) { vm.num("usageGoal",it.coerceAtLeast(1).toLong()) }
                        TextButton(onClick={page="Permissions"}) { Text("Manage permissions") }
                        Button(onClick={vm.run { activity.saveExport(vm.export()) }}) { Text("Export local history and rules") }
                        var deleteConfirm by remember { mutableStateOf(false) }
                        TextButton(onClick={deleteConfirm=true}) { Text("Delete local history") }
                        if(deleteConfirm) AlertDialog(onDismissRequest={deleteConfirm=false},title={Text("Delete local history?")},text={Text("This removes saved sessions and pending uploads. Cloud history is unaffected.")},confirmButton={TextButton(onClick={vm.run { vm.guard.database.dao().clearHistory(); vm.guard.database.dao().clearQueue() }; deleteConfirm=false}) { Text("Delete") }},dismissButton={TextButton(onClick={deleteConfirm=false}) { Text("Cancel") }})
                        Text("Privacy",style=MaterialTheme.typography.titleLarge)
                        Text("Your app usage, blocking rules and focus history are stored locally. Accessibility is used only to evaluate foreground apps and known view IDs; screen text is never retained. Optional Supabase synchronization uploads focus history after you enable it. Authentication credentials are sent only to your configured Supabase project. Delete FocusGuard cloud data from Profile. The shared sign-in account remains; its owner can request account deletion through the project administrator. This app contains no advertising or analytics SDK.")
                        Text("DNS filtering and short-video detection are experimental. Cloud features require a configured Supabase project. Clock changes affect timestamp-based timers. Device vendors can interrupt accessibility services; verify blocking after reboot.")
                    }
                }
            }
        }
    }
}
@Composable private fun Toggle(label: String,checked: Boolean,enabled: Boolean=true,onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(label,Modifier.weight(1f).padding(top=12.dp)); Switch(checked,onChange,enabled=enabled) }
}
@Composable private fun NumberField(label: String,initial: Int,enabled: Boolean=true,onSave:(Int)->Unit) {
    var text by remember(initial) { mutableStateOf(initial.toString()) }
    Row(Modifier.fillMaxWidth()) { OutlinedTextField(text,{text=it.filter(Char::isDigit).take(5)},label={Text(label)},modifier=Modifier.weight(1f),enabled=enabled); TextButton(enabled=enabled && text.toIntOrNull()!=null,onClick={text.toIntOrNull()?.let(onSave)}) { Text("Save") } }
}
@Composable private fun RuleEditor(rule: AppRule,strict: Boolean,vm: GuardModel) {
    Toggle("Block always",rule.always,!strict) { vm.save(rule.copy(always=it)) }
    Toggle("Block during focus",rule.focusOnly,!strict) { vm.save(rule.copy(focusOnly=it)) }
    NumberField("Daily limit (0 = none)",rule.limitMinutes,!strict) { vm.save(rule.copy(limitMinutes=it)) }
    var start by remember(rule.startMinute) { mutableStateOf(if(rule.startMinute<0) "" else "%02d:%02d".format(rule.startMinute/60,rule.startMinute%60)) }
    var end by remember(rule.endMinute) { mutableStateOf(if(rule.endMinute<0) "" else "%02d:%02d".format(rule.endMinute/60,rule.endMinute%60)) }
    var days by remember(rule.days) { mutableStateOf(rule.days) }; var profile by remember(rule.profile) { mutableStateOf(rule.profile) }
    OutlinedTextField(start,{start=it},label={Text("Schedule start HH:mm")},enabled=!strict)
    OutlinedTextField(end,{end=it},label={Text("Schedule end HH:mm")},enabled=!strict)
    OutlinedTextField(days,{days=it},label={Text("Weekdays 1=Mon … 7=Sun, comma separated")},enabled=!strict)
    OutlinedTextField(profile,{profile=it},label={Text("Profile")},enabled=!strict)
    TextButton(enabled=!strict,onClick={
        try { val a=LocalTime.parse(start); val b=LocalTime.parse(end); val set=days.split(',').map { it.trim().toInt() }; require(set.isNotEmpty() && set.all { it in 1..7 }); vm.save(rule.copy(startMinute=a.hour*60+a.minute,endMinute=b.hour*60+b.minute,days=set.distinct().joinToString(","),profile=profile.ifBlank { "Default" })) } catch(e: Exception) { vm.message.value="Use HH:mm and weekdays between 1 and 7" }
    }) { Text("Save schedule and profile") }
    TextButton(enabled=!strict,onClick={vm.save(rule.copy(startMinute=-1,endMinute=-1,profile=profile.ifBlank { "Default" }))}) { Text("Clear schedule / save profile") }
}


