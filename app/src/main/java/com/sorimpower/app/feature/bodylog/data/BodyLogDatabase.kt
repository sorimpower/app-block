package com.sorimpower.app.feature.bodylog.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "weight_entries", indices = [Index("measuredAt")])
data class WeightEntryEntity(
    @androidx.room.PrimaryKey val id: String,
    val weightKg: Double,
    val measuredAt: Long,
    val zoneOffsetMinutes: Int,
    val bodyFatPercent: Double?,
    val condition: String?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "weight_goals")
data class WeightGoalEntity(
    @androidx.room.PrimaryKey val id: String,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val startedOnEpochDay: Long,
    val targetDateEpochDay: Long?,
    val status: String,
)

@Entity(tableName = "mounjaro_injections", indices = [Index("injectedAt")])
data class MounjaroInjectionEntity(
    @androidx.room.PrimaryKey val id: String,
    val injectedAt: Long,
    val doseMg: Double,
    val sideEffects: String,
    val note: String?,
    val reminderEnabled: Boolean,
    val reminderIntervalWeeks: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "meal_entries", indices = [Index("eatenAt")])
data class MealEntryEntity(
    @androidx.room.PrimaryKey val id: String,
    val mealType: String,
    val eatenAt: Long,
    val zoneOffsetMinutes: Int,
    val note: String?,
    val tags: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "meal_items",
    foreignKeys = [ForeignKey(
        entity = MealEntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["mealEntryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("mealEntryId")],
)
data class MealItemEntity(
    @androidx.room.PrimaryKey val id: String,
    val mealEntryId: String,
    val name: String,
    val amount: String?,
    val sortOrder: Int,
)

@Entity(
    tableName = "meal_photos",
    foreignKeys = [ForeignKey(
        entity = MealEntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["mealEntryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("mealEntryId")],
)
data class MealPhotoEntity(
    @androidx.room.PrimaryKey val id: String,
    val mealEntryId: String,
    val localPath: String,
    val thumbnailPath: String,
    val width: Int,
    val height: Int,
    val sortOrder: Int,
    val createdAt: Long,
)

data class MealWithDetails(
    @Embedded val meal: MealEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "mealEntryId") val items: List<MealItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "mealEntryId") val photos: List<MealPhotoEntity>,
)

@Dao
interface BodyLogDao {
    @Query("SELECT * FROM weight_entries ORDER BY measuredAt ASC")
    fun observeWeights(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_goals WHERE status = 'ACTIVE' ORDER BY startedOnEpochDay DESC LIMIT 1")
    fun observeActiveGoal(): Flow<WeightGoalEntity?>

    @Query("SELECT * FROM mounjaro_injections ORDER BY injectedAt DESC")
    fun observeMounjaroInjections(): Flow<List<MounjaroInjectionEntity>>

    @Query("SELECT * FROM mounjaro_injections ORDER BY injectedAt DESC LIMIT 1")
    suspend fun latestMounjaroInjection(): MounjaroInjectionEntity?

    @Transaction
    @Query("SELECT * FROM meal_entries ORDER BY eatenAt ASC")
    fun observeMeals(): Flow<List<MealWithDetails>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeight(entry: WeightEntryEntity)

    @Delete
    suspend fun deleteWeight(entry: WeightEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: WeightGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMounjaroInjection(injection: MounjaroInjectionEntity)

    @Delete
    suspend fun deleteMounjaroInjection(injection: MounjaroInjectionEntity)

    @Query("UPDATE weight_goals SET status = 'CANCELLED' WHERE status = 'ACTIVE'")
    suspend fun cancelActiveGoals()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealItems(items: List<MealItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPhotos(photos: List<MealPhotoEntity>)

    @Query("DELETE FROM meal_entries WHERE id = :mealId")
    suspend fun deleteMealById(mealId: String)

    @Transaction
    suspend fun replaceMeal(meal: MealEntryEntity, items: List<MealItemEntity>, photos: List<MealPhotoEntity>) {
        deleteMealById(meal.id)
        insertMeal(meal)
        insertMealItems(items)
        insertMealPhotos(photos)
    }
}

@Database(
    entities = [WeightEntryEntity::class, WeightGoalEntity::class, MounjaroInjectionEntity::class, MealEntryEntity::class, MealItemEntity::class, MealPhotoEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class BodyLogDatabase : RoomDatabase() {
    abstract fun dao(): BodyLogDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `mounjaro_injections` (
                        `id` TEXT NOT NULL,
                        `injectedAt` INTEGER NOT NULL,
                        `doseMg` REAL NOT NULL,
                        `sideEffects` TEXT NOT NULL,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_mounjaro_injections_injectedAt` ON `mounjaro_injections` (`injectedAt`)")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `mounjaro_injections` ADD COLUMN `reminderEnabled` INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE `mounjaro_injections` ADD COLUMN `reminderIntervalWeeks` INTEGER NOT NULL DEFAULT 1")
            }
        }
        @Volatile private var instance: BodyLogDatabase? = null

        fun get(context: Context): BodyLogDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                BodyLogDatabase::class.java,
                "body_log.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
