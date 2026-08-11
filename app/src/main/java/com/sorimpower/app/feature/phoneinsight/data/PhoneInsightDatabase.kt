package com.sorimpower.app.feature.phoneinsight.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.sorimpower.app.feature.phoneinsight.domain.*
import org.json.JSONArray
import org.json.JSONObject

class InsightDbConverters {
    @TypeConverter fun sourceToString(value:InsightSourceType)=value.name
    @TypeConverter fun stringToSource(value:String)=enumValueOf<InsightSourceType>(value)
    @TypeConverter fun typeToString(value:InsightType)=value.name
    @TypeConverter fun stringToType(value:String)=enumValueOf<InsightType>(value)
    @TypeConverter fun importanceToString(value:InsightImportance)=value.name
    @TypeConverter fun stringToImportance(value:String)=enumValueOf<InsightImportance>(value)
    @TypeConverter fun statusToString(value:InsightStatus)=value.name
    @TypeConverter fun stringToStatus(value:String)=enumValueOf<InsightStatus>(value)
    @TypeConverter fun candidateStateToString(value:InsightCandidateState)=value.name
    @TypeConverter fun stringToCandidateState(value:String)=enumValueOf<InsightCandidateState>(value)
    @TypeConverter fun runStatusToString(value:InsightRunStatus)=value.name
    @TypeConverter fun stringToRunStatus(value:String)=enumValueOf<InsightRunStatus>(value)
    @TypeConverter fun rangeToString(value:SmsScanRange)=value.name
    @TypeConverter fun stringToRange(value:String)=enumValueOf<SmsScanRange>(value)
    @TypeConverter fun frequencyToString(value:InsightAnalysisFrequency)=value.name
    @TypeConverter fun stringToFrequency(value:String)=enumValueOf<InsightAnalysisFrequency>(value)
    @TypeConverter fun settingsToString(value:InsightSourceSettings)=JSONObject().put("accessUris",JSONArray(value.accessUris.toList())).put("packages",JSONArray(value.selectedPackages.toList())).toString()
    @TypeConverter fun stringToSettings(value:String):InsightSourceSettings=runCatching{val json=JSONObject(value);val uris=json.optJSONArray("accessUris")?.let{array->(0 until array.length()).mapNotNull{array.optString(it).takeIf(String::isNotBlank)}}?.toSet()?:json.optString("accessUri").takeIf(String::isNotBlank)?.let(::setOf).orEmpty();val packages=json.optJSONArray("packages")?.let{array->(0 until array.length()).mapNotNull{array.optString(it).takeIf(String::isNotBlank)}}?.toSet()?:json.optString("packages").split(',').filter(String::isNotBlank).toSet();InsightSourceSettings(uris,packages)}.getOrDefault(InsightSourceSettings())
}

@Entity(tableName = "insight_source_configs")
data class InsightSourceConfigEntity(@PrimaryKey val type: InsightSourceType, val enabled: Boolean, val permissionGranted: Boolean, val lastScanAt: Long?, val lastScanItemCount: Int, val initialScanCompleted: Boolean, val scanRange: SmsScanRange, val analysisFrequency: InsightAnalysisFrequency, @ColumnInfo(name="settingsJson")val settings:InsightSourceSettings=InsightSourceSettings())

@Entity(tableName = "processed_insight_sources", primaryKeys = ["sourceType", "sourceId"])
data class ProcessedInsightSourceEntity(val sourceType: InsightSourceType, val sourceId: String, val contentHash: Int, val processedAt: Long)

/** Temporary, locally queued candidates. The text is removed immediately after AI processing. */
@Entity(tableName = "insight_candidates", indices = [Index(value = ["sourceType", "sourceId"], unique = true)])
data class InsightCandidateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: InsightSourceType,
    val sourceId: String,
    val senderOrApp: String,
    val text: String,
    val occurredAt: Long,
    val attachmentUri: String? = null,
    val state: InsightCandidateState = InsightCandidateState.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val claimedAt: Long? = null,
)

@Entity(tableName = "phone_insights", indices = [Index(value = ["groupKey"], unique = true), Index("status"), Index("dueAt")])
data class PhoneInsightEntity(@PrimaryKey val id: String, val groupKey: String, val type: InsightType, val title: String, val description: String, val dueAt: Long?, val amount: Long?, val importance: InsightImportance, val status: InsightStatus, val sourceType: InsightSourceType, val sourceId: String, val senderOrApp: String, val confidence: Double, val createdAt: Long, val updatedAt: Long, val lastNotifiedAt: Long?)

@Entity(tableName="insight_analysis_runs")
data class InsightAnalysisRunEntity(@PrimaryKey(autoGenerate=true)val id:Long=0,val startedAt:Long,val finishedAt:Long?=null,val status:InsightRunStatus=InsightRunStatus.RUNNING,val candidateCount:Int=0,val insightCount:Int=0,val aiCalls:Int=0,val error:String?=null,val model:String?=null,val inputTokens:Int=0,val outputTokens:Int=0,val estimatedCostMicros:Long?=null)

@Entity(tableName="insight_source_runs",indices=[Index("runId"),Index("sourceType")])
data class InsightSourceRunEntity(@PrimaryKey(autoGenerate=true)val id:Long=0,val runId:Long,val sourceType:InsightSourceType,val startedAt:Long,val finishedAt:Long,val status:InsightRunStatus,val scannedCount:Int,val freshCount:Int,val candidateCount:Int,val error:String?=null,val aiCalls:Int=0,val model:String?=null,val inputTokens:Int=0,val outputTokens:Int=0,val estimatedCostMicros:Long?=null)

@Dao interface PhoneInsightDao {
    @Query("SELECT * FROM insight_source_configs ORDER BY type") fun observeConfigs(): Flow<List<InsightSourceConfigEntity>>
    @Query("SELECT * FROM phone_insights WHERE status IN ('ACTIVE','REVIEW') ORDER BY CASE importance WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, dueAt IS NULL, dueAt") fun observeActive(): Flow<List<PhoneInsightEntity>>
    @Query("SELECT * FROM phone_insights WHERE status IN ('ACTIVE','REVIEW') ORDER BY CASE importance WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, dueAt IS NULL, dueAt") suspend fun activeNow(): List<PhoneInsightEntity>
    @Query("SELECT * FROM phone_insights WHERE status IN ('ACTIVE','REVIEW') AND dueAt >= :start AND dueAt < :end ORDER BY CASE importance WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, dueAt") suspend fun activeBetween(start:Long,end:Long): List<PhoneInsightEntity>
    @Query("SELECT * FROM phone_insights WHERE id=:id LIMIT 1") suspend fun insight(id:String): PhoneInsightEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertConfigs(values: List<InsightSourceConfigEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertConfig(value: InsightSourceConfigEntity)
    @Query("SELECT * FROM insight_source_configs WHERE type=:type") suspend fun config(type: InsightSourceType): InsightSourceConfigEntity?
    @Query("SELECT * FROM insight_source_configs") suspend fun allConfigs(): List<InsightSourceConfigEntity>
    @Query("SELECT sourceId FROM processed_insight_sources WHERE sourceType=:type") suspend fun processedIds(type: InsightSourceType): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun markProcessed(values: List<ProcessedInsightSourceEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun queueCandidates(values: List<InsightCandidateEntity>)
    @Query("SELECT * FROM insight_candidates WHERE state='PENDING' AND sourceType='CALL_RECORDING' ORDER BY occurredAt DESC LIMIT :limit") suspend fun pendingAudio(limit:Int):List<InsightCandidateEntity>
    @Query("SELECT * FROM insight_candidates WHERE state='PENDING' AND (sourceType IN ('SCREENSHOT','DOCUMENT') OR (attachmentUri IS NOT NULL AND sourceType!='CALL_RECORDING')) ORDER BY occurredAt DESC LIMIT :limit") suspend fun pendingImages(limit:Int):List<InsightCandidateEntity>
    @Query("SELECT * FROM insight_candidates WHERE state='PENDING' AND sourceType NOT IN ('CALL_RECORDING','SCREENSHOT','DOCUMENT') AND attachmentUri IS NULL ORDER BY occurredAt DESC LIMIT :limit") suspend fun pendingPlain(limit:Int):List<InsightCandidateEntity>
    @Query("UPDATE insight_candidates SET state='PROCESSING', claimedAt=:now WHERE id IN (:ids) AND state='PENDING'") suspend fun markClaimed(ids:List<Long>,now:Long)
    @Transaction suspend fun claimCandidates():List<InsightCandidateEntity>{val values=(pendingAudio(2)+pendingImages(6)+pendingPlain(12)).distinctBy{it.id};if(values.isNotEmpty())markClaimed(values.map{it.id},System.currentTimeMillis());return values}
    @Query("UPDATE insight_candidates SET state='PENDING', claimedAt=NULL WHERE state='PROCESSING' AND claimedAt<:before") suspend fun releaseStaleClaims(before:Long)
    @Query("UPDATE insight_candidates SET state=CASE WHEN retryCount+1>=3 THEN 'FAILED' ELSE 'PENDING' END, retryCount=retryCount+1, lastError=:error, claimedAt=:now WHERE id IN (:ids)") suspend fun failCandidates(ids:List<Long>,error:String,now:Long)
    @Query("SELECT COUNT(*) FROM insight_candidates WHERE sourceType=:type AND state='PENDING'") suspend fun queuedCandidateCount(type:InsightSourceType): Int
    @Query("DELETE FROM insight_candidates WHERE id IN (:ids)") suspend fun deleteCandidates(ids: List<Long>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertInsights(values: List<PhoneInsightEntity>)
    @Query("SELECT * FROM phone_insights WHERE updatedAt>=:createdAfter ORDER BY updatedAt DESC LIMIT 500") suspend fun recentInsights(createdAfter:Long):List<PhoneInsightEntity>
    @Query("UPDATE phone_insights SET status=:status, updatedAt=:now WHERE id=:id") suspend fun updateStatus(id: String, status: InsightStatus, now: Long)
    @Query("DELETE FROM phone_insights WHERE sourceType=:type") suspend fun deleteInsights(type: InsightSourceType)
    @Query("DELETE FROM processed_insight_sources WHERE sourceType=:type") suspend fun deleteProcessed(type: InsightSourceType)
    @Query("UPDATE phone_insights SET lastNotifiedAt=:now WHERE id IN (:ids)") suspend fun markNotified(ids: List<String>, now: Long)
    @Insert suspend fun insertRun(value:InsightAnalysisRunEntity):Long
    @Query("UPDATE insight_analysis_runs SET finishedAt=:finishedAt,status=:status,candidateCount=:candidateCount,insightCount=:insightCount,aiCalls=:aiCalls,error=:error,model=:model,inputTokens=:inputTokens,outputTokens=:outputTokens,estimatedCostMicros=:estimatedCostMicros WHERE id=:id") suspend fun finishRun(id:Long,finishedAt:Long,status:InsightRunStatus,candidateCount:Int,insightCount:Int,aiCalls:Int,error:String?,model:String?,inputTokens:Int,outputTokens:Int,estimatedCostMicros:Long?)
    @Insert suspend fun insertSourceRun(value:InsightSourceRunEntity)
    @Query("SELECT * FROM insight_analysis_runs ORDER BY startedAt DESC LIMIT 1") fun observeLatestRun():Flow<InsightAnalysisRunEntity?>
    @Query("SELECT * FROM insight_analysis_runs WHERE status='SUCCESS' ORDER BY startedAt DESC LIMIT 1") suspend fun latestSuccessfulRun():InsightAnalysisRunEntity?
    @Query("SELECT * FROM insight_source_runs WHERE runId=(SELECT id FROM insight_analysis_runs ORDER BY startedAt DESC LIMIT 1) ORDER BY sourceType") fun observeLatestSourceRuns():Flow<List<InsightSourceRunEntity>>
    @Query("UPDATE insight_source_runs SET aiCalls=:aiCalls,model=:model,inputTokens=:inputTokens,outputTokens=:outputTokens,estimatedCostMicros=:estimatedCostMicros WHERE runId=:runId AND sourceType=:sourceType") suspend fun updateSourceUsage(runId:Long,sourceType:InsightSourceType,aiCalls:Int,model:String?,inputTokens:Int,outputTokens:Int,estimatedCostMicros:Long?)
    @Query("UPDATE insight_source_runs SET status='FAILED',error=:error,finishedAt=:finishedAt WHERE runId=:runId AND sourceType=:sourceType") suspend fun markSourceRunFailed(runId:Long,sourceType:InsightSourceType,error:String,finishedAt:Long)
    @Query("DELETE FROM processed_insight_sources WHERE processedAt<:before") suspend fun deleteOldProcessed(before:Long)
    @Query("DELETE FROM insight_candidates WHERE state='FAILED' AND claimedAt<:before") suspend fun deleteOldCandidates(before:Long)
    @Query("DELETE FROM insight_candidates WHERE sourceType=:type") suspend fun deleteCandidatesForSource(type:InsightSourceType)
    @Query("DELETE FROM phone_insights WHERE (status IN ('COMPLETED','DISMISSED') AND updatedAt<:terminalBefore) OR (status='EXPIRED' AND updatedAt<:expiredBefore)") suspend fun deleteOldInsights(terminalBefore:Long,expiredBefore:Long)
    @Query("DELETE FROM insight_source_runs WHERE runId IN (SELECT id FROM insight_analysis_runs WHERE startedAt<:before)") suspend fun deleteOldSourceRuns(before:Long)
    @Query("DELETE FROM insight_analysis_runs WHERE startedAt<:before") suspend fun deleteOldRuns(before:Long)
    @Query("UPDATE phone_insights SET status='EXPIRED',updatedAt=:now WHERE status IN ('ACTIVE','REVIEW') AND dueAt<:todayStart") suspend fun expirePastDue(todayStart:Long,now:Long)
    @Query("UPDATE phone_insights SET status='EXPIRED',updatedAt=:now WHERE status IN ('ACTIVE','REVIEW') AND dueAt IS NULL AND createdAt<:before") suspend fun expireOldUndated(before:Long,now:Long)
}

@Database(entities=[InsightSourceConfigEntity::class, ProcessedInsightSourceEntity::class, InsightCandidateEntity::class, PhoneInsightEntity::class,InsightAnalysisRunEntity::class,InsightSourceRunEntity::class], version=5, exportSchema=false)
@TypeConverters(InsightDbConverters::class)
abstract class PhoneInsightDatabase: RoomDatabase() {
    abstract fun dao(): PhoneInsightDao
    companion object {
        @Volatile private var instance: PhoneInsightDatabase? = null
        private val migration1To2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE insight_source_configs ADD COLUMN analysisFrequency TEXT NOT NULL DEFAULT 'DAILY'") }
        }
        private val migration2To3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE insight_source_configs ADD COLUMN settingsJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("CREATE TABLE IF NOT EXISTS insight_candidates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceType TEXT NOT NULL, sourceId TEXT NOT NULL, senderOrApp TEXT NOT NULL, text TEXT NOT NULL, occurredAt INTEGER NOT NULL, attachmentUri TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_insight_candidates_sourceType_sourceId ON insight_candidates (sourceType, sourceId)")
            }
        }
        private val migration3To4=object:androidx.room.migration.Migration(3,4){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){
            db.execSQL("ALTER TABLE insight_candidates ADD COLUMN state TEXT NOT NULL DEFAULT 'PENDING'")
            db.execSQL("ALTER TABLE insight_candidates ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE insight_candidates ADD COLUMN lastError TEXT")
            db.execSQL("ALTER TABLE insight_candidates ADD COLUMN claimedAt INTEGER")
            db.execSQL("CREATE TABLE IF NOT EXISTS insight_analysis_runs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startedAt INTEGER NOT NULL, finishedAt INTEGER, status TEXT NOT NULL, candidateCount INTEGER NOT NULL, insightCount INTEGER NOT NULL, aiCalls INTEGER NOT NULL, error TEXT)")
        }}
        internal val migration4To5=object:androidx.room.migration.Migration(4,5){override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase){
            db.execSQL("ALTER TABLE insight_analysis_runs ADD COLUMN model TEXT")
            db.execSQL("ALTER TABLE insight_analysis_runs ADD COLUMN inputTokens INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE insight_analysis_runs ADD COLUMN outputTokens INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE insight_analysis_runs ADD COLUMN estimatedCostMicros INTEGER")
            db.execSQL("CREATE TABLE IF NOT EXISTS insight_source_runs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, runId INTEGER NOT NULL, sourceType TEXT NOT NULL, startedAt INTEGER NOT NULL, finishedAt INTEGER NOT NULL, status TEXT NOT NULL, scannedCount INTEGER NOT NULL, freshCount INTEGER NOT NULL, candidateCount INTEGER NOT NULL, error TEXT, aiCalls INTEGER NOT NULL DEFAULT 0, model TEXT, inputTokens INTEGER NOT NULL DEFAULT 0, outputTokens INTEGER NOT NULL DEFAULT 0, estimatedCostMicros INTEGER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_insight_source_runs_runId ON insight_source_runs (runId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_insight_source_runs_sourceType ON insight_source_runs (sourceType)")
        }}
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context.applicationContext, PhoneInsightDatabase::class.java, "phone_insights.db").addMigrations(migration1To2, migration2To3,migration3To4,migration4To5).build().also { instance=it } }
    }
}
