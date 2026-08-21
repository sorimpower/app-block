package com.sorimpower.app.feature.assets.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "assets")
data class AssetEntity(
    @androidx.room.PrimaryKey val id: String,
    val assetClass: String,
    val name: String,
    val valueKrw: Long,
    val providerId: String,
    val providerName: String,
    val badge: String,
    val valuationEpochDay: Long,
    val valuationMethod: String,
    val algorithmVersion: String?,
    val confidence: String?,
    val sourceStatus: String,
    val detail: String,
    val address: String,
    val lawdCd: String,
    val exclusiveAreaSqm: Double?,
    val ownershipPercent: Double,
    val modelYear: Int?,
    val trim: String,
    val mileageKm: Int?,
    val comparableMinKrw: Long?,
    val comparableMaxKrw: Long?,
    val comparableCount: Int,
    val latestComparableTradeEpochDay: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "asset_snapshots")
data class AssetSnapshotEntity(
    @androidx.room.PrimaryKey val id: String,
    val snapshotEpochDay: Long,
    val netWorthKrw: Long,
    val totalAssetsKrw: Long,
    val totalLiabilitiesKrw: Long,
    val stockProvider: String,
    val cryptoProvider: String,
    val realEstateDataset: String,
    val realEstateAlgorithm: String,
    val vehicleProvider: String,
    val fxProvider: String,
    val createdAt: Long,
)

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY assetClass, updatedAt DESC")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM asset_snapshots ORDER BY snapshotEpochDay DESC")
    fun observeSnapshots(): Flow<List<AssetSnapshotEntity>>

    @Query("SELECT * FROM assets")
    suspend fun getAssets(): List<AssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAsset(asset: AssetEntity)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteAsset(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: AssetSnapshotEntity)

    @Transaction
    suspend fun saveAssetAndSnapshot(asset: AssetEntity, snapshot: AssetSnapshotEntity) {
        upsertAsset(asset)
        upsertSnapshot(snapshot)
    }

    @Transaction
    suspend fun deleteAssetAndSnapshot(id: String, snapshot: AssetSnapshotEntity) {
        deleteAsset(id)
        upsertSnapshot(snapshot)
    }
}

@Database(entities = [AssetEntity::class, AssetSnapshotEntity::class], version = 2, exportSchema = false)
abstract class AssetDatabase : RoomDatabase() {
    abstract fun dao(): AssetDao

    companion object {
        @Volatile private var instance: AssetDatabase? = null

        fun get(context: Context): AssetDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AssetDatabase::class.java, "assets.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN lawdCd TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
