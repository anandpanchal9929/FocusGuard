package org.focusguard.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class AppRule(
    @PrimaryKey val packageName: String, val label: String, val enabled: Boolean = true,
    val always: Boolean = false, val focusOnly: Boolean = true, val limitMinutes: Int = 0,
    val startMinute: Int = -1, val endMinute: Int = -1, val days: String = "1,2,3,4,5",
    val profile: String = "Default", val bypassUntil: Long = 0
)
@Entity data class FocusSession(@PrimaryKey val id: String, val startedAt: Long, val endedAt: Long, val focusedMs: Long, val completed: Boolean)
@Entity data class Outbox(@PrimaryKey val id: String, val tableName: String, val payload: String, val createdAt: Long)
@Dao interface GuardDao {
    @Query("SELECT * FROM AppRule ORDER BY label") fun rules(): Flow<List<AppRule>>
    @Query("SELECT * FROM AppRule") suspend fun allRules(): List<AppRule>
    @Upsert suspend fun save(rule: AppRule)
    @Query("DELETE FROM AppRule WHERE packageName=:pkg") suspend fun remove(pkg: String)
    @Query("DELETE FROM AppRule") suspend fun clearRules()
    @Transaction suspend fun replaceRules(rules: List<AppRule>) { clearRules(); rules.forEach { save(it) } }
    @Query("SELECT * FROM FocusSession ORDER BY startedAt DESC") fun history(): Flow<List<FocusSession>>
    @Query("SELECT * FROM FocusSession ORDER BY startedAt DESC") suspend fun sessions(): List<FocusSession>
    @Upsert suspend fun session(value: FocusSession)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun enqueue(value: Outbox)
    @Query("SELECT * FROM Outbox ORDER BY createdAt LIMIT 50") suspend fun pending(): List<Outbox>
    @Query("DELETE FROM Outbox WHERE id=:id") suspend fun acknowledge(id: String)
    @Query("DELETE FROM Outbox") suspend fun clearQueue()
    @Query("DELETE FROM FocusSession") suspend fun clearHistory()
    @Transaction suspend fun finish(session: FocusSession, operation: Outbox?) { session(session); if(operation != null) enqueue(operation) }
}
@Database(entities = [AppRule::class, FocusSession::class, Outbox::class], version = 1, exportSchema = false)
abstract class GuardDatabase : RoomDatabase() { abstract fun dao(): GuardDao }
