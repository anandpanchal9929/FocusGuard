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
        return try {
            val cloud=CloudClient(app)
            for(op in app.database.dao().pending()) {
                if(!app.preferences.snapshot().flag("sync")) return Result.success()
                cloud.upload(op)
                app.database.dao().acknowledge(op.id)
            }
            Result.success()
        } catch(e: Exception) { Result.retry() }
    }
}
