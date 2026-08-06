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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    val lastAttemptAt: Long? = null,
) {
    companion object {
        const val ID = "auction"
        const val HISTORY_ID = "history"
    }
}

@Entity(tableName = "auction_favorites")
data class AuctionFavoriteEntity(
    @PrimaryKey val itemKey: String,
    val savedAt: Long,
)

@Entity(tableName = "auction_history_items")
data class AuctionHistoryItemEntity(
    @PrimaryKey val itemKey: String,
    val courtName: String,
    val caseNumber: String,
    val auctionItemNumber: String,
    val appraisalPrice: Long,
    val minimumPrice: Long,
    val minimumPriceRate: Double,
    val failedCount: Int,
    val auctionDate: String,
    val auctionTime: String,
    val address: String,
    val sido: String,
    val sigungu: String,
    val dong: String,
    val buildingName: String,
    val courtDepartment: String,
    val note: String,
    val objectCount: Int,
    val collectedAt: String,
    val historyCreatedAt: String,
    val historyStatus: String,
    val historyReason: String,
)

@Dao
interface AuctionDao {
    @Query("SELECT * FROM auction_items ORDER BY CASE WHEN auctionDate = '' THEN 1 ELSE 0 END, auctionDate ASC, auctionTime ASC")
    fun observeItems(): Flow<List<AuctionItemEntity>>

    @Query("SELECT * FROM auction_sync_metadata WHERE id = 'auction' LIMIT 1")
    fun observeMetadata(): Flow<AuctionSyncMetadataEntity?>

    @Query("SELECT itemKey FROM auction_favorites ORDER BY savedAt DESC")
    fun observeFavoriteKeys(): Flow<List<String>>

    @Query("SELECT * FROM auction_history_items ORDER BY historyCreatedAt DESC")
    fun observeHistoryItems(): Flow<List<AuctionHistoryItemEntity>>

    @Query("SELECT * FROM auction_sync_metadata WHERE id = 'history' LIMIT 1")
    fun observeHistoryMetadata(): Flow<AuctionSyncMetadataEntity?>

    @Query("SELECT * FROM auction_items")
    suspend fun getItems(): List<AuctionItemEntity>

    @Query("SELECT * FROM auction_sync_metadata WHERE id = 'auction' LIMIT 1")
    suspend fun getMetadata(): AuctionSyncMetadataEntity?

    @Query("SELECT * FROM auction_sync_metadata WHERE id = 'history' LIMIT 1")
    suspend fun getHistoryMetadata(): AuctionSyncMetadataEntity?

    @Query("DELETE FROM auction_items")
    suspend fun deleteAllItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<AuctionItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: AuctionSyncMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(favorite: AuctionFavoriteEntity)

    @Query("DELETE FROM auction_favorites WHERE itemKey = :itemKey")
    suspend fun deleteFavorite(itemKey: String)

    @Query("DELETE FROM auction_history_items")
    suspend fun deleteAllHistoryItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItems(items: List<AuctionHistoryItemEntity>)

    @Transaction
    suspend fun replaceSnapshot(items: List<AuctionItemEntity>, metadata: AuctionSyncMetadataEntity) {
        deleteAllItems()
        insertItems(items)
        upsertMetadata(metadata)
    }

    @Transaction
    suspend fun replaceHistorySnapshot(items: List<AuctionHistoryItemEntity>, metadata: AuctionSyncMetadataEntity) {
        deleteAllHistoryItems()
        insertHistoryItems(items)
        upsertMetadata(metadata)
    }
}

@Database(
    entities = [AuctionItemEntity::class, AuctionSyncMetadataEntity::class, AuctionFavoriteEntity::class, AuctionHistoryItemEntity::class],
    version = 4,
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `auction_favorites` (`itemKey` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`itemKey`))",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `auction_history_items` (
                        `itemKey` TEXT NOT NULL,
                        `courtName` TEXT NOT NULL,
                        `caseNumber` TEXT NOT NULL,
                        `auctionItemNumber` TEXT NOT NULL,
                        `appraisalPrice` INTEGER NOT NULL,
                        `minimumPrice` INTEGER NOT NULL,
                        `minimumPriceRate` REAL NOT NULL,
                        `failedCount` INTEGER NOT NULL,
                        `auctionDate` TEXT NOT NULL,
                        `auctionTime` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `sido` TEXT NOT NULL,
                        `sigungu` TEXT NOT NULL,
                        `dong` TEXT NOT NULL,
                        `buildingName` TEXT NOT NULL,
                        `courtDepartment` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `objectCount` INTEGER NOT NULL,
                        `collectedAt` TEXT NOT NULL,
                        `historyCreatedAt` TEXT NOT NULL,
                        `historyStatus` TEXT NOT NULL,
                        `historyReason` TEXT NOT NULL,
                        PRIMARY KEY(`itemKey`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `auction_sync_metadata` ADD COLUMN `lastAttemptAt` INTEGER")
            }
        }
    }
}
