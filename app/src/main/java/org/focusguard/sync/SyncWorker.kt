package org.focusguard.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.focusguard.FocusGuardApp
import org.focusguard.BuildConfig
import org.focusguard.data.*
import org.focusguard.network.CloudClient

class SyncWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val app=applicationContext as FocusGuardApp
        val p=app.preferences.snapshot()
        if(!p.flag("sync") || BuildConfig.SUPABASE_URL.isEmpty() || app.tokens.get("token").isEmpty()) return Result.success()
        if(p.flag("strict") && p.timer().active(System.currentTimeMillis())) return Result.retry()
        return try {
            val cloud=CloudClient(app)
            SyncDrain.run(app.database.dao().pending(),{app.preferences.snapshot().flag("sync")},{cloud.upload(it)},{app.database.dao().acknowledge(it)})
            Result.success()
        } catch(e: Exception) { Result.retry() }
    }
}
