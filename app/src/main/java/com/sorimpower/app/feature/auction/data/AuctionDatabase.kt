package com.sorimpower.app.feature.auction.data

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

@Entity(tableName = "auction_items")
data class AuctionItemEntity(
    @PrimaryKey val itemKey: String,
    val courtCode: String,
    val courtName: String,
    val internalCaseNumber: String,
    val caseNumber: String,
    val auctionItemNumber: String,
    val usageName: String,
    val appraisalPrice: Long,
    val minimumPrice: Long,
    val minimumPriceRate: Double,
    val failedCount: Int,
    val auctionDate: String,
    val auctionTime: String,
    val auctionPlace: String,
    val address: String,
    val sido: String,
    val sigungu: String,
    val dong: String,
    val buildingName: String,
    val courtDepartment: String,
    val courtTel: String,
    val note: String,
    val interestCount: Int,
    val isInProgress: Boolean,
    val objectCount: Int,
    val collectedAt: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val isNew: Boolean,
)

@Entity(tableName = "auction_sync_metadata")
data class AuctionSyncMetadataEntity(
    @PrimaryKey val id: String = ID,
    val lastUpdatedAt: String?,
    val lastSuccessfulSyncAt: Long,
    val baselineEstablished: Boolean,
) {
    companion object { const val ID = "auction" }
}

@Dao
interface AuctionDao {
    @Query("SELECT * FROM auction_items ORDER BY CASE WHEN auctionDate = '' THEN 1 ELSE 0 END, auctionDate ASC, auctionTime ASC")
    fun observeItems(): Flow<List<AuctionItemEntity>>

    @Query("SELECT * FROM auction_sync_metadata WHERE id = 'auction' LIMIT 1")
    fun observeMetadata(): Flow<AuctionSyncMetadataEntity?>

    @Query("SELECT * FROM auction_items")
    suspend fun getItems(): List<AuctionItemEntity>

    @Query("SELECT * FROM auction_sync_metadata WHERE id = 'auction' LIMIT 1")
    suspend fun getMetadata(): AuctionSyncMetadataEntity?

    @Query("DELETE FROM auction_items")
    suspend fun deleteAllItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<AuctionItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: AuctionSyncMetadataEntity)

    @Transaction
    suspend fun replaceSnapshot(items: List<AuctionItemEntity>, metadata: AuctionSyncMetadataEntity) {
        deleteAllItems()
        insertItems(items)
        upsertMetadata(metadata)
    }
}

@Database(
    entities = [AuctionItemEntity::class, AuctionSyncMetadataEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AuctionDatabase : RoomDatabase() {
    abstract fun dao(): AuctionDao

    companion object {
        @Volatile private var instance: AuctionDatabase? = null

        fun get(context: Context): AuctionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AuctionDatabase::class.java,
                "auction.db",
            ).build().also { instance = it }
        }
    }
}

