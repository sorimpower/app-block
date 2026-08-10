package com.sorimpower.app.feature.healthcheckup.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "health_checkups", indices = [Index("checkupDateEpochDay")])
data class HealthCheckupEntity(
    @PrimaryKey val id: String,
    val checkupDateEpochDay: Long,
    val hospitalName: String,
    val title: String,
    val originalFilePath: String,
    val originalFileName: String,
    val originalMimeType: String,
    val memo: String,
    val aiSummary: String,
    val createdAt: Long,
    val updatedAt: Long,
    val dataVersion: Long,
)

@Entity(
    tableName = "health_metrics",
    foreignKeys = [ForeignKey(
        entity = HealthCheckupEntity::class,
        parentColumns = ["id"],
        childColumns = ["checkupId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("checkupId"), Index("normalizedName")],
)
data class HealthMetricEntity(
    @PrimaryKey val id: String,
    val checkupId: String,
    val category: String,
    val name: String,
    val normalizedName: String,
    val value: Double?,
    val stringValue: String,
    val unit: String,
    val referenceMin: Double?,
    val referenceMax: Double?,
    val referenceText: String,
    val status: String,
    val sourceText: String,
    val sortOrder: Int,
)

@Entity(tableName = "health_ai_analyses", indices = [Index("type"), Index("dataVersion")])
data class HealthAiAnalysisEntity(
    @PrimaryKey val id: String,
    val type: String,
    val sourceIds: String,
    val model: String,
    val resultJson: String,
    val createdAt: Long,
    val dataVersion: String,
    val stale: Boolean,
)

@Entity(tableName = "screening_plans")
data class ScreeningPlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    val year: Int,
    val originalFilePath: String,
    val originalFileName: String,
    val originalMimeType: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "screening_options",
    foreignKeys = [ForeignKey(
        entity = ScreeningPlanEntity::class,
        parentColumns = ["id"],
        childColumns = ["screeningPlanId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("screeningPlanId")],
)
data class ScreeningOptionEntity(
    @PrimaryKey val id: String,
    val screeningPlanId: String,
    val name: String,
    val description: String,
    val groupName: String,
    val maxSelectableCount: Int?,
    val selected: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "screening_recommendations",
    foreignKeys = [ForeignKey(
        entity = ScreeningOptionEntity::class,
        parentColumns = ["id"],
        childColumns = ["screeningOptionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("screeningOptionId")],
)
data class ScreeningRecommendationEntity(
    @PrimaryKey val id: String,
    val screeningOptionId: String,
    val priority: String,
    val reasonsJson: String,
    val concernsJson: String,
    val missingInformationJson: String,
    val aiSummary: String,
    val createdAt: Long,
    val dataVersion: String,
)

@Entity(tableName = "health_insights", indices = [Index("createdAt")])
data class HealthInsightEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val summary: String,
    val sourceIds: String,
    val importance: Int,
    val createdAt: Long,
    val dismissed: Boolean,
)

data class HealthCheckupWithMetrics(
    @Embedded val checkup: HealthCheckupEntity,
    @Relation(parentColumn = "id", entityColumn = "checkupId") val metrics: List<HealthMetricEntity>,
)

@Dao
interface HealthCheckupDao {
    @Transaction
    @Query("SELECT * FROM health_checkups ORDER BY checkupDateEpochDay DESC")
    fun observeCheckups(): Flow<List<HealthCheckupWithMetrics>>

    @Transaction
    @Query("SELECT * FROM health_checkups WHERE id = :id LIMIT 1")
    fun observeCheckup(id: String): Flow<HealthCheckupWithMetrics?>

    @Transaction
    @Query("SELECT * FROM health_checkups ORDER BY checkupDateEpochDay DESC")
    suspend fun getCheckups(): List<HealthCheckupWithMetrics>

    @Query("SELECT * FROM health_checkups WHERE id = :id LIMIT 1")
    suspend fun getCheckupEntity(id: String): HealthCheckupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckup(checkup: HealthCheckupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetrics(metrics: List<HealthMetricEntity>)

    @Query("DELETE FROM health_metrics WHERE checkupId = :checkupId")
    suspend fun deleteMetrics(checkupId: String)

    @Query("DELETE FROM health_checkups WHERE id = :id")
    suspend fun deleteCheckup(id: String)

    @Query("UPDATE health_ai_analyses SET stale = 1 WHERE sourceIds LIKE '%' || :sourceId || '%'")
    suspend fun invalidateAnalyses(sourceId: String)

    @Query("UPDATE health_ai_analyses SET stale = 1 WHERE type = 'LONG_TERM'")
    suspend fun invalidateLongTermAnalyses()

    @Query("SELECT * FROM health_ai_analyses WHERE type = 'LONG_TERM' AND stale = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getFreshLongTermAnalysis(): HealthAiAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAiAnalysis(analysis: HealthAiAnalysisEntity)

    @Transaction
    suspend fun replaceCheckup(checkup: HealthCheckupEntity, metrics: List<HealthMetricEntity>) {
        upsertCheckup(checkup)
        deleteMetrics(checkup.id)
        insertMetrics(metrics)
        invalidateAnalyses(checkup.id)
        invalidateLongTermAnalyses()
    }
}

@Database(
    entities = [
        HealthCheckupEntity::class,
        HealthMetricEntity::class,
        HealthAiAnalysisEntity::class,
        ScreeningPlanEntity::class,
        ScreeningOptionEntity::class,
        ScreeningRecommendationEntity::class,
        HealthInsightEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class HealthCheckupDatabase : RoomDatabase() {
    abstract fun dao(): HealthCheckupDao

    companion object {
        @Volatile private var instance: HealthCheckupDatabase? = null

        fun get(context: Context): HealthCheckupDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HealthCheckupDatabase::class.java,
                "health_checkups.db",
            ).build().also { instance = it }
        }
    }
}
