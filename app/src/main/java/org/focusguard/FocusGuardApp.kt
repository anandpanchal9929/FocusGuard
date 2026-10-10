package org.focusguard

import android.app.Application
import androidx.room.Room
import androidx.work.*
import org.focusguard.data.*
import org.focusguard.sync.SyncWorker
import java.util.concurrent.TimeUnit

class FocusGuardApp : Application() {
    val database by lazy { Room.databaseBuilder(this, GuardDatabase::class.java, "focusguard.db").build() }
    val preferences by lazy { Preferences(this) }
    val tokens by lazy { org.focusguard.network.TokenVault(this) }
    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("cloud-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
