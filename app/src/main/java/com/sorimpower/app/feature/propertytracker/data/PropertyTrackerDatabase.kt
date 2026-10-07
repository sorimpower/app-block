package com.sorimpower.app.feature.propertytracker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "property_watch_targets")
data class PropertyWatchTargetEntity(
    @PrimaryKey val id: String,
    val apartmentName: String,
    val complexNo: String,
    val areaNo: String,
    val exclusiveAreaSqm: Double,
    val lawdCd: String,
    val isCurrentHome: Boolean,
    val isMoveTarget: Boolean,
    val isCompareSelected: Boolean,
    val createdAt: Long,
    val lastSyncAt: Long?,
    val lastSyncStatus: String,
    val lastSyncMessage: String,
)

@Entity(tableName = "property_listings")
data class PropertyListingEntity(
    @PrimaryKey val articleNo: String,
    val watchTargetId: String,
    val priceKrw: Long,
    val priceText: String,
    val supplyAreaSqm: Double?,
    val exclusiveAreaSqm: Double?,
    val floorInfo: String,
    val direction: String,
    val buildingName: String,
    val description: String,
    val tags: String,
    val confirmDate: String,
    val sourceUrl: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val status: String,
    val consecutiveMisses: Int,
    val removedAt: Long?,
)

@Entity(tableName = "property_asking_snapshots")
data class PropertyAskingSnapshotEntity(
    @PrimaryKey val id: String,
    val watchTargetId: String,
    val epochDay: Long,
    val minPriceKrw: Long,
    // 기존 DB 호환용 필드. 화면과 비교 계산에는 사용하지 않는다.
    val medianPriceKrw: Long,
    val maxPriceKrw: Long,
    val activeCount: Int,
    val newCount: Int,
    val removedCount: Int,
    val createdAt: Long,
)

@Entity(tableName = "property_actual_trades")
data class PropertyActualTradeEntity(
    @PrimaryKey val id: String,
    val watchTargetId: String,
    val apartmentName: String,
    val exclusiveAreaSqm: Double,
    val priceKrw: Long,
    val tradeDate: String,
    val floor: String,
)

@Entity(tableName = "property_listing_events")
data class PropertyListingEventEntity(
    @PrimaryKey val id: String,
    val watchTargetId: String,
    val articleNo: String,
    val type: String,
    val previousPriceKrw: Long?,
    val priceKrw: Long,
    val occurredAt: Long,
)

@Entity(tableName = "property_sync_runs")
data class PropertySyncRunEntity(
    @PrimaryKey val epochDay: Long,
    val status: String,
    val attemptedAt: Long,
    val completedAt: Long?,
    val successCount: Int,
    val failureCount: Int,
    val message: String,
)

@Dao
interface PropertyTrackerDao {
    @Query("SELECT * FROM property_watch_targets ORDER BY isCurrentHome DESC, createdAt")
    fun observeTargets(): Flow<List<PropertyWatchTargetEntity>>

    @Query("SELECT * FROM property_listings ORDER BY lastSeenAt DESC")
    fun observeListings(): Flow<List<PropertyListingEntity>>

    @Query("SELECT * FROM property_asking_snapshots ORDER BY epochDay")
    fun observeSnapshots(): Flow<List<PropertyAskingSnapshotEntity>>

    @Query("SELECT * FROM property_actual_trades ORDER BY tradeDate DESC")
    fun observeTrades(): Flow<List<PropertyActualTradeEntity>>

    @Query("SELECT * FROM property_listing_events ORDER BY occurredAt DESC LIMIT 300")
    fun observeEvents(): Flow<List<PropertyListingEventEntity>>

    @Query("SELECT * FROM property_sync_runs ORDER BY epochDay DESC LIMIT 1")
    fun observeLatestRun(): Flow<PropertySyncRunEntity?>

    @Query("SELECT * FROM property_watch_targets ORDER BY createdAt")
    suspend fun getTargets(): List<PropertyWatchTargetEntity>

    @Query("SELECT * FROM property_watch_targets WHERE id = :id")
    suspend fun getTarget(id: String): PropertyWatchTargetEntity?

    @Query("SELECT * FROM property_listings WHERE watchTargetId = :targetId")
    suspend fun getListings(targetId: String): List<PropertyListingEntity>

    @Query("SELECT * FROM property_sync_runs WHERE epochDay = :epochDay")
    suspend fun getRun(epochDay: Long): PropertySyncRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTarget(target: PropertyWatchTargetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertListings(listings: List<PropertyListingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: PropertyAskingSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrades(trades: List<PropertyActualTradeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(events: List<PropertyListingEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRun(run: PropertySyncRunEntity)

    @Transaction
    suspend fun tryStartDailyRun(epochDay: Long, attemptedAt: Long): Boolean {
        if (getRun(epochDay) != null) return false
        upsertRun(PropertySyncRunEntity(epochDay, "RUNNING", attemptedAt, null, 0, 0, "조회 중"))
        return true
    }

    @Query("UPDATE property_watch_targets SET isCurrentHome = 0")
    suspend fun clearCurrentHome()

    @Query("UPDATE property_watch_targets SET isCurrentHome = :selected WHERE id = :id")
    suspend fun updateCurrentHome(id: String, selected: Boolean)

    @Query("UPDATE property_watch_targets SET isMoveTarget = :selected WHERE id = :id")
    suspend fun updateMoveTarget(id: String, selected: Boolean)

    @Query("UPDATE property_watch_targets SET isCompareSelected = :selected WHERE id = :id")
    suspend fun updateCompareSelected(id: String, selected: Boolean)

    @Query("DELETE FROM property_watch_targets WHERE id = :id")
    suspend fun deleteTargetRow(id: String)

    @Query("DELETE FROM property_listings WHERE watchTargetId = :id")
    suspend fun deleteListings(id: String)

    @Query("DELETE FROM property_asking_snapshots WHERE watchTargetId = :id")
    suspend fun deleteSnapshots(id: String)

    @Query("DELETE FROM property_actual_trades WHERE watchTargetId = :id")
    suspend fun deleteTrades(id: String)

    @Query("DELETE FROM property_listing_events WHERE watchTargetId = :id")
    suspend fun deleteEvents(id: String)

    @Transaction
    suspend fun selectCurrentHome(id: String, selected: Boolean) {
        clearCurrentHome()
        if (selected) updateCurrentHome(id, true)
    }

    @Transaction
    suspend fun deleteTarget(id: String) {
        deleteListings(id)
        deleteSnapshots(id)
        deleteTrades(id)
        deleteEvents(id)
        deleteTargetRow(id)
    }
}

@Database(
    entities = [
        PropertyWatchTargetEntity::class,
        PropertyListingEntity::class,
        PropertyAskingSnapshotEntity::class,
        PropertyActualTradeEntity::class,
        PropertyListingEventEntity::class,
        PropertySyncRunEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PropertyTrackerDatabase : RoomDatabase() {
    abstract fun dao(): PropertyTrackerDao

    companion object {
        @Volatile private var instance: PropertyTrackerDatabase? = null

        fun get(context: Context): PropertyTrackerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PropertyTrackerDatabase::class.java,
                "property_tracker.db",
            ).build().also { instance = it }
        }
    }
}
