package com.sorimpower.app.feature.perspective.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Raw viewing history is kept locally; it is the only per-video data used by this feature. */
@Entity(tableName = "watched_videos", indices = [Index("youtubeVideoId", unique = true), Index("watchedAt")])
data class WatchedVideoEntity(
    @PrimaryKey val id: String,
    val youtubeVideoId: String,
    val url: String,
    val title: String,
    val channelName: String = "",
    val durationSec: Long = 0,
    val watchedSec: Long = 0,
    val watchedAt: Long = System.currentTimeMillis(),
    val source: String = "auto",
    val analysisStatus: String = "collected",
    @ColumnInfo(defaultValue = "0") val playbackEnded: Boolean = false,
    val contentHash: String,
)

/** One AI snapshot per calendar period: daily, weekly, monthly, or yearly. */
@Entity(tableName = "interest_period_analyses", primaryKeys = ["periodType", "periodKey"], indices = [Index("generatedAt")])
data class InterestPeriodAnalysisEntity(
    val periodType: String,
    val periodKey: String,
    val periodLabel: String,
    val videoCount: Int,
    @ColumnInfo(defaultValue = "0") val periodFrom: Long = 0,
    @ColumnInfo(defaultValue = "0") val periodUntil: Long = 0,
    val categoriesJson: String,
    val summary: String,
    val trendSummary: String,
    @ColumnInfo(defaultValue = "'[]'") val personalInsightsJson: String = "[]",
    @ColumnInfo(defaultValue = "'[]'") val recommendedVideosJson: String = "[]",
    val generatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface PerspectiveDao {
    @Query("SELECT * FROM watched_videos ORDER BY watchedAt DESC") fun observeVideos(): Flow<List<WatchedVideoEntity>>
    @Query("SELECT * FROM watched_videos ORDER BY watchedAt DESC") suspend fun videos(): List<WatchedVideoEntity>
    @Query("SELECT * FROM watched_videos WHERE youtubeVideoId = :youtubeId LIMIT 1") suspend fun videoByYoutubeId(youtubeId: String): WatchedVideoEntity?
    @Query("SELECT * FROM watched_videos WHERE title = :title AND channelName = :channel ORDER BY watchedAt DESC LIMIT 1") suspend fun latestVideoByTitleAndChannel(title: String, channel: String): WatchedVideoEntity?
    @Query("UPDATE watched_videos SET youtubeVideoId = :youtubeVideoId, url = :url WHERE id = :id") suspend fun updateVideoAddress(id: String, youtubeVideoId: String, url: String)
    @Query("DELETE FROM watched_videos WHERE id = :videoId") suspend fun deleteVideo(videoId: String)
    @Upsert suspend fun upsertVideo(item: WatchedVideoEntity)

    @Query("SELECT * FROM interest_period_analyses ORDER BY generatedAt DESC") fun observeAnalyses(): Flow<List<InterestPeriodAnalysisEntity>>
    @Query("SELECT * FROM interest_period_analyses WHERE periodType = :type ORDER BY periodKey DESC") suspend fun analyses(type: String): List<InterestPeriodAnalysisEntity>
    @Query("SELECT * FROM interest_period_analyses WHERE periodType = :type AND periodKey = :key LIMIT 1") suspend fun analysis(type: String, key: String): InterestPeriodAnalysisEntity?
    @Upsert suspend fun upsertAnalysis(item: InterestPeriodAnalysisEntity)
}

@Database(entities = [WatchedVideoEntity::class, InterestPeriodAnalysisEntity::class], version = 10, exportSchema = false)
abstract class PerspectiveDatabase : RoomDatabase() {
    abstract fun dao(): PerspectiveDao

    companion object {
        @Volatile private var instance: PerspectiveDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE perspective_topics ADD COLUMN userApproved INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS topic_suggestions (videoId TEXT NOT NULL, proposedName TEXT NOT NULL, description TEXT NOT NULL, confidence REAL NOT NULL, model TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(videoId))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_topic_suggestions_status ON topic_suggestions(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_topic_suggestions_createdAt ON topic_suggestions(createdAt)")
        } }
        private val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE watched_videos ADD COLUMN playbackEnded INTEGER NOT NULL DEFAULT 0") } }
        private val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE video_analyses ADD COLUMN analysisBasis TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE video_analyses ADD COLUMN transcriptIncluded INTEGER NOT NULL DEFAULT 0")
        } }
        private val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS perspective_recommended_videos (id TEXT NOT NULL, perspectiveId TEXT NOT NULL, title TEXT NOT NULL, channelName TEXT NOT NULL, thumbnailUrl TEXT NOT NULL, url TEXT NOT NULL, publishedAt TEXT NOT NULL, duration TEXT NOT NULL, rank INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_perspective_recommended_videos_perspectiveId ON perspective_recommended_videos(perspectiveId)")
        } }
        private val MIGRATION_5_6 = object : Migration(5, 6) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS interest_period_analyses (periodType TEXT NOT NULL, periodKey TEXT NOT NULL, periodLabel TEXT NOT NULL, videoCount INTEGER NOT NULL, categoriesJson TEXT NOT NULL, summary TEXT NOT NULL, trendSummary TEXT NOT NULL, generatedAt INTEGER NOT NULL, PRIMARY KEY(periodType, periodKey))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_interest_period_analyses_generatedAt ON interest_period_analyses(generatedAt)")
            listOf("perspective_topics", "topic_suggestions", "video_topics", "video_analyses", "perspectives", "perspective_recommended_videos", "thought_nodes", "thought_edges", "expansion_moments", "weekly_perspective_reports").forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        } }
        // v6 installed briefly with the same columns but a different Room identity hash.
        // Advancing the version preserves all collected viewing history while accepting that schema.
        private val MIGRATION_6_7 = object : Migration(6, 7) { override fun migrate(db: SupportSQLiteDatabase) = Unit }
        private val MIGRATION_7_8 = object : Migration(7, 8) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE interest_period_analyses ADD COLUMN personalInsightsJson TEXT NOT NULL DEFAULT '[]'")
        } }
        private val MIGRATION_8_9 = object : Migration(8, 9) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE interest_period_analyses ADD COLUMN recommendedVideosJson TEXT NOT NULL DEFAULT '[]'")
        } }
        private val MIGRATION_9_10 = object : Migration(9, 10) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE interest_period_analyses ADD COLUMN periodFrom INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE interest_period_analyses ADD COLUMN periodUntil INTEGER NOT NULL DEFAULT 0")
        } }
        fun get(context: Context): PerspectiveDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PerspectiveDatabase::class.java, "perspective.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build().also { instance = it }
        }
    }
}
