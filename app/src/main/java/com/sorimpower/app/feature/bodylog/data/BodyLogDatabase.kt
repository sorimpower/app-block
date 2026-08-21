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

data class MealPhotoPaths(val localPath: String, val thumbnailPath: String)

@Entity(
    tableName = "meal_calorie_estimates",
    foreignKeys = [ForeignKey(
        entity = MealEntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["mealId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("mealId")],
)
data class MealCalorieEstimateEntity(
    @androidx.room.PrimaryKey val mealId: String,
    val estimatedCalories: Int,
    val summary: String,
    val sourceHash: String,
    val analyzedAt: Long,
)

@Entity(tableName = "daily_calorie_summaries")
data class DailyCalorieSummaryEntity(
    @androidx.room.PrimaryKey val dateEpochDay: Long,
    val estimatedCalories: Int,
    val summary: String,
    val mealCount: Int,
    val analyzedAt: Long,
)

@Entity(tableName = "exercise_entries", indices = [Index("exercisedAt")])
data class ExerciseEntryEntity(
    @androidx.room.PrimaryKey val id: String,
    val exercisedAt: Long,
    val exerciseType: String,
    val durationMinutes: Int,
    val intensity: String,
    val caloriesBurned: Int?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "daily_health_activity")
data class DailyHealthActivityEntity(
    @androidx.room.PrimaryKey val dateEpochDay: Long,
    val steps: Long,
    val distanceMeters: Double,
    val activeCalories: Double,
    val activeCaloriesEstimated: Boolean,
    val exerciseMinutes: Long,
    val updatedAt: Long,
)

@Entity(tableName = "inbody_results", indices = [Index("measuredAt")])
data class InBodyResultEntity(
    @androidx.room.PrimaryKey val id: String,
    val measuredAt: Long,
    val originalFilePath: String,
    val originalFileName: String,
    val originalMimeType: String,
    val metricsJson: String,
    val aiSummary: String,
    val analysisStatus: String,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
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

    @Query("SELECT * FROM daily_calorie_summaries ORDER BY dateEpochDay ASC")
    fun observeDailyCalorieSummaries(): Flow<List<DailyCalorieSummaryEntity>>

    @Query("SELECT * FROM meal_calorie_estimates ORDER BY analyzedAt ASC")
    fun observeMealCalorieEstimates(): Flow<List<MealCalorieEstimateEntity>>

    @Query("SELECT * FROM exercise_entries ORDER BY exercisedAt DESC")
    fun observeExercises(): Flow<List<ExerciseEntryEntity>>

    @Query("SELECT * FROM daily_health_activity ORDER BY dateEpochDay DESC")
    fun observeHealthActivity(): Flow<List<DailyHealthActivityEntity>>

    @Query("SELECT * FROM inbody_results ORDER BY measuredAt DESC")
    fun observeInBodyResults(): Flow<List<InBodyResultEntity>>

    @Transaction
    @Query("SELECT * FROM meal_entries WHERE eatenAt >= :from AND eatenAt < :until ORDER BY eatenAt ASC")
    suspend fun mealsBetween(from: Long, until: Long): List<MealWithDetails>

    @Transaction
    @Query("SELECT * FROM meal_entries WHERE id = :mealId LIMIT 1")
    suspend fun meal(mealId: String): MealWithDetails?

    @Query("SELECT * FROM meal_calorie_estimates WHERE mealId IN (:mealIds)")
    suspend fun mealCalorieEstimates(mealIds: List<String>): List<MealCalorieEstimateEntity>

    @Query("SELECT * FROM meal_calorie_estimates WHERE mealId = :mealId LIMIT 1")
    suspend fun mealCalorieEstimate(mealId: String): MealCalorieEstimateEntity?

    @Query("SELECT id FROM meal_entries WHERE id NOT IN (SELECT mealId FROM meal_calorie_estimates) ORDER BY eatenAt ASC")
    suspend fun mealIdsWithoutCalorieEstimate(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMealCalorieEstimate(value: MealCalorieEstimateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyCalorieSummary(value: DailyCalorieSummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercise(value: ExerciseEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHealthActivity(values: List<DailyHealthActivityEntity>)

    @Delete
    suspend fun deleteExercise(value: ExerciseEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInBodyResult(value: InBodyResultEntity)

    @Delete
    suspend fun deleteInBodyResult(value: InBodyResultEntity)

    @Query("DELETE FROM daily_calorie_summaries WHERE dateEpochDay = :dateEpochDay")
    suspend fun deleteDailyCalorieSummary(dateEpochDay: Long)

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

    @Query("SELECT localPath, thumbnailPath FROM meal_photos")
    suspend fun allMealPhotoPaths(): List<MealPhotoPaths>

    @Transaction
    suspend fun replaceMeal(meal: MealEntryEntity, items: List<MealItemEntity>, photos: List<MealPhotoEntity>) {
        deleteMealById(meal.id)
        insertMeal(meal)
        insertMealItems(items)
        insertMealPhotos(photos)
    }
}

@Database(
    entities = [WeightEntryEntity::class, WeightGoalEntity::class, MounjaroInjectionEntity::class, MealEntryEntity::class, MealItemEntity::class, MealPhotoEntity::class, MealCalorieEstimateEntity::class, DailyCalorieSummaryEntity::class, ExerciseEntryEntity::class, DailyHealthActivityEntity::class, InBodyResultEntity::class],
    version = 8,
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
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS daily_calorie_summaries (dateEpochDay INTEGER NOT NULL, estimatedCalories INTEGER NOT NULL, summary TEXT NOT NULL, mealCount INTEGER NOT NULL, analyzedAt INTEGER NOT NULL, PRIMARY KEY(dateEpochDay))")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `meal_calorie_estimates` (
                        `mealId` TEXT NOT NULL,
                        `estimatedCalories` INTEGER NOT NULL,
                        `summary` TEXT NOT NULL,
                        `sourceHash` TEXT NOT NULL,
                        `analyzedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`mealId`),
                        FOREIGN KEY(`mealId`) REFERENCES `meal_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_calorie_estimates_mealId` ON `meal_calorie_estimates` (`mealId`)")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exercise_entries` (
                        `id` TEXT NOT NULL,
                        `exercisedAt` INTEGER NOT NULL,
                        `exerciseType` TEXT NOT NULL,
                        `durationMinutes` INTEGER NOT NULL,
                        `intensity` TEXT NOT NULL,
                        `caloriesBurned` INTEGER,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_entries_exercisedAt` ON `exercise_entries` (`exercisedAt`)")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `inbody_results` (
                        `id` TEXT NOT NULL,
                        `measuredAt` INTEGER NOT NULL,
                        `originalFilePath` TEXT NOT NULL,
                        `originalFileName` TEXT NOT NULL,
                        `originalMimeType` TEXT NOT NULL,
                        `metricsJson` TEXT NOT NULL,
                        `aiSummary` TEXT NOT NULL,
                        `analysisStatus` TEXT NOT NULL,
                        `errorMessage` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_inbody_results_measuredAt` ON `inbody_results` (`measuredAt`)")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `daily_health_activity` (`dateEpochDay` INTEGER NOT NULL, `steps` INTEGER NOT NULL, `distanceMeters` REAL NOT NULL, `activeCalories` REAL NOT NULL, `exerciseMinutes` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`dateEpochDay`))")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `daily_health_activity` ADD COLUMN `activeCaloriesEstimated` INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: BodyLogDatabase? = null

        fun get(context: Context): BodyLogDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                BodyLogDatabase::class.java,
                "body_log.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { instance = it }
        }
    }
}
