package org.focusguard.web

import android.net.VpnService
import android.content.Intent
import android.app.*
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.*
import org.focusguard.FocusGuardApp
import org.focusguard.data.*
import org.focusguard.core.Schedule
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.*
import java.time.ZonedDateTime

class DnsVpnService : VpnService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var tunnel: ParcelFileDescriptor?=null
    private var prefs=androidx.datastore.preferences.core.emptyPreferences()
    override fun onCreate() { super.onCreate(); scope.launch { (application as FocusGuardApp).preferences.flow.collect { prefs=it } } }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="STOP") { stopSelf(); return START_NOT_STICKY }
        if(tunnel!=null) return START_NOT_STICKY
        val notifications=getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("dns","Domain filtering",NotificationManager.IMPORTANCE_LOW))
        val stop=PendingIntent.getService(this,0,Intent(this,DnsVpnService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"dns").setSmallIcon(org.focusguard.R.drawable.ic_guard).setContentTitle("FocusGuard DNS filter")
            .setContentText("System DNS only · encrypted DNS may bypass").setOngoing(true).addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
        startForeground(2,notification)
        tunnel=Builder().setSession("FocusGuard domain filter").addAddress("10.73.0.1",32).addDnsServer("10.73.0.2")
            .addRoute("10.73.0.2",32).setBlocking(true).setMtu(1500).establish()
        val tun=tunnel ?: run { stopSelf(); return START_NOT_STICKY }
        scope.launch {
            try {
                val input=FileInputStream(tun.fileDescriptor); val output=FileOutputStream(tun.fileDescriptor); val buffer=ByteArray(32767)
                while(isActive) {
                    val length=input.read(buffer); if(length<=0) continue
                    val packet=buffer.copyOf(length); val query=DnsCodec.extract(packet) ?: continue
                    val domain=DnsCodec.question(query) ?: continue
                    val blocked=prefs.str("domains").lines().mapNotNull { try { DnsCodec.normalize(it) } catch(e: Exception) { null } }.toSet()
                    val schedule=if(prefs.num("webStart",-1)>=0) Schedule(prefs.num("webStart").toInt(),prefs.num("webEnd").toInt(),prefs.str("webDays","1,2,3,4,5,6,7").split(',').mapNotNull(String::toIntOrNull).toSet()) else null
                    val deny=(schedule==null || schedule.active(ZonedDateTime.now())) && DnsCodec.matches(domain,blocked)
                    val response=if(deny) DnsCodec.denied(query) else forward(query)
                    if(response!=null) output.write(DnsCodec.reply(packet,response))
                }
            } catch(e: Exception) { if(isActive) stopSelf() }
        }
        return START_NOT_STICKY
    }
    private fun forward(query: ByteArray): ByteArray? = try {
        DatagramSocket().use { socket ->
            check(protect(socket)); socket.soTimeout=2000
            socket.connect(InetAddress.getByName("1.1.1.1"),53)
            socket.send(DatagramPacket(query,query.size)); val bytes=ByteArray(4096); val result=DatagramPacket(bytes,bytes.size); socket.receive(result)
            if(result.length<12 || bytes[0]!=query[0] || bytes[1]!=query[1]) null else bytes.copyOf(result.length)
        }
    } catch(e: Exception) { null }
    override fun onRevoke() { stopSelf() }
    override fun onDestroy() { tunnel?.close(); tunnel=null; scope.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
}
