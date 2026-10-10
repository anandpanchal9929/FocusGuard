package org.focusguard.focus
import android.app.*
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import org.focusguard.FocusGuardApp
import org.focusguard.MainActivity
import org.focusguard.R
import org.focusguard.data.*

class TimerService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var ticker: Job?=null
    private val app get()=application as FocusGuardApp
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("timer","Focus timer",NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel("complete","Session reminders",NotificationManager.IMPORTANCE_DEFAULT))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        startForeground(1,Notification.Builder(this,"timer").setSmallIcon(R.drawable.ic_guard).setContentTitle("FocusGuard session").setContentIntent(open).setOngoing(true).build())
        ticker?.cancel()
        ticker=scope.launch {
            while(isActive) {
                val p=app.preferences.snapshot(); val timer=p.timer(); val now=System.currentTimeMillis()
                if(timer.startMs==0L) { stopSelf(); break }
                if(timer.durationMs>0 && timer.remaining(now)==0L) {
                    FocusRepository(app).finish(true)
                    if(p.flag("notifications")) manager.notify(3,Notification.Builder(this@TimerService,"complete").setSmallIcon(R.drawable.ic_guard).setContentTitle("Session complete").setContentText("Take a breath. Your progress is saved.").setContentIntent(open).setAutoCancel(true).build())
                    stopSelf(); break
                }
                val time=if(timer.durationMs==0L) timer.elapsed(now) else timer.remaining(now)
                manager.notify(1,Notification.Builder(this@TimerService,"timer").setSmallIcon(R.drawable.ic_guard).setContentTitle("FocusGuard · ${p.str("timerKind","focus")}").setContentText("${time/60_000}m ${time/1000%60}s ${if(timer.pausedAtMs>0) "· paused" else ""}").setOngoing(true).setContentIntent(open).build())
                delay(1000)
            }
        }
        return START_STICKY
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
