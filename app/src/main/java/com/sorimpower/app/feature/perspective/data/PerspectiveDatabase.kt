package com.sorimpower.app.feature.perspective.data

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "perspective_topics")
data class PerspectiveTopicEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val userApproved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "topic_suggestions", indices = [Index("status"), Index("createdAt")])
data class TopicSuggestionEntity(
    @PrimaryKey val videoId: String,
    val proposedName: String,
    val description: String = "",
    val confidence: Double = 0.0,
    val model: String,
    val status: String = "pending",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

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
    val source: String,
    val analysisStatus: String = "classified",
    @ColumnInfo(defaultValue = "0") val playbackEnded: Boolean = false,
    val contentHash: String,
)

@Entity(tableName = "video_topics", primaryKeys = ["videoId", "topicId"], indices = [Index("topicId")])
data class VideoTopicEntity(val videoId: String, val topicId: String, val confidence: Double)

@Entity(tableName = "video_analyses")
data class VideoAnalysisEntity(
    @PrimaryKey val videoId: String,
    val topic: String,
    val mainClaim: String,
    val subClaimsJson: String = "[]",
    val evidenceJson: String = "[]",
    val assumptionsJson: String = "[]",
    val stakeholdersJson: String = "[]",
    val missingPerspectivesJson: String = "[]",
    val confidence: Double = 0.0,
    val model: String,
    val promptVersion: String,
    val sourceHash: String,
    @ColumnInfo(defaultValue = "''") val analysisBasis: String = "",
    @ColumnInfo(defaultValue = "0") val transcriptIncluded: Boolean = false,
    val analyzedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "perspectives", indices = [Index("topicId"), Index("videoId")])
data class PerspectiveEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val videoId: String,
    val label: String,
    val description: String,
    val representativeQuestion: String,
    val searchQuery: String,
    val status: String = "suggested",
    val createdAt: Long = System.currentTimeMillis(),
    val visitedAt: Long? = null,
)

/** 관점별로 YouTube Data API에서 찾은 실제 추천 영상. AI 결과와 분리해 캐시한다. */
@Entity(tableName = "perspective_recommended_videos", indices = [Index("perspectiveId")])
data class PerspectiveRecommendedVideoEntity(
    @PrimaryKey val id: String,
    val perspectiveId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String = "",
    val url: String,
    val publishedAt: String = "",
    val duration: String = "",
    val rank: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "thought_nodes", indices = [Index("topicId"), Index("videoId"), Index("perspectiveId")])
data class ThoughtNodeEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val type: String,
    val videoId: String? = null,
    val perspectiveId: String? = null,
    val label: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "thought_edges", indices = [Index("topicId"), Index("fromNodeId"), Index("toNodeId")])
data class ThoughtEdgeEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val type: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "expansion_moments", indices = [Index("topicId"), Index("occurredAt")])
data class ExpansionMomentEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val fromLabel: String,
    val toLabel: String,
    val title: String,
    val description: String,
    val reason: String,
    val occurredAt: Long = System.currentTimeMillis(),
    val aiConfidence: Double = 0.0,
)

@Entity(tableName = "weekly_perspective_reports")
data class WeeklyPerspectiveReportEntity(
    @PrimaryKey val weekStartEpochDay: Long,
    val dominantTopicsJson: String,
    val dominantPerspectivesJson: String,
    val underExposedPerspectivesJson: String,
    val summary: String,
    val generatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface PerspectiveDao {
    @Query("SELECT * FROM perspective_topics WHERE userApproved = 1 ORDER BY createdAt ASC") fun observeTopics(): Flow<List<PerspectiveTopicEntity>>
    @Query("SELECT * FROM topic_suggestions WHERE status = 'pending' ORDER BY createdAt DESC") fun observeTopicSuggestions(): Flow<List<TopicSuggestionEntity>>
    @Query("SELECT * FROM watched_videos ORDER BY watchedAt DESC") fun observeVideos(): Flow<List<WatchedVideoEntity>>
    @Query("SELECT * FROM video_topics") fun observeVideoTopics(): Flow<List<VideoTopicEntity>>
    @Query("SELECT * FROM video_analyses ORDER BY analyzedAt DESC") fun observeAnalyses(): Flow<List<VideoAnalysisEntity>>
    @Query("SELECT * FROM perspectives ORDER BY createdAt DESC") fun observePerspectives(): Flow<List<PerspectiveEntity>>
    @Query("SELECT * FROM perspective_recommended_videos ORDER BY perspectiveId, rank") fun observeRecommendedVideos(): Flow<List<PerspectiveRecommendedVideoEntity>>
    @Query("SELECT * FROM thought_nodes ORDER BY createdAt ASC") fun observeNodes(): Flow<List<ThoughtNodeEntity>>
    @Query("SELECT * FROM thought_edges ORDER BY createdAt ASC") fun observeEdges(): Flow<List<ThoughtEdgeEntity>>
    @Query("SELECT * FROM expansion_moments ORDER BY occurredAt DESC") fun observeMoments(): Flow<List<ExpansionMomentEntity>>
    @Query("SELECT * FROM weekly_perspective_reports ORDER BY weekStartEpochDay DESC") fun observeReports(): Flow<List<WeeklyPerspectiveReportEntity>>
    @Query("SELECT COUNT(*) FROM perspective_topics WHERE userApproved = 1") suspend fun topicCount(): Int
    @Query("SELECT * FROM perspective_topics WHERE userApproved = 1") suspend fun topics(): List<PerspectiveTopicEntity>
    @Query("SELECT * FROM perspective_topics WHERE id = :id AND userApproved = 1 LIMIT 1") suspend fun topic(id: String): PerspectiveTopicEntity?
    @Query("SELECT * FROM perspective_topics WHERE userApproved = 1 AND name = :name COLLATE NOCASE LIMIT 1") suspend fun topicByName(name: String): PerspectiveTopicEntity?
    @Query("SELECT * FROM topic_suggestions WHERE videoId = :videoId LIMIT 1") suspend fun topicSuggestion(videoId: String): TopicSuggestionEntity?
    @Query("SELECT topicId FROM video_topics WHERE videoId = :videoId ORDER BY confidence DESC") suspend fun topicIdsForVideo(videoId: String): List<String>
    @Query("SELECT * FROM watched_videos WHERE youtubeVideoId = :youtubeId LIMIT 1") suspend fun videoByYoutubeId(youtubeId: String): WatchedVideoEntity?
    @Query("SELECT * FROM watched_videos WHERE title = :title AND channelName = :channel ORDER BY watchedAt DESC LIMIT 1") suspend fun latestVideoByTitleAndChannel(title: String, channel: String): WatchedVideoEntity?
    @Query("SELECT * FROM watched_videos WHERE id = :id LIMIT 1") suspend fun video(id: String): WatchedVideoEntity?
    @Query("UPDATE watched_videos SET youtubeVideoId = :youtubeVideoId, url = :url WHERE id = :id") suspend fun updateVideoAddress(id: String, youtubeVideoId: String, url: String)
    @Query("SELECT * FROM video_analyses WHERE videoId = :videoId LIMIT 1") suspend fun analysis(videoId: String): VideoAnalysisEntity?
    @Query("SELECT * FROM perspectives WHERE id = :id LIMIT 1") suspend fun perspective(id: String): PerspectiveEntity?
    @Query("SELECT * FROM perspectives WHERE topicId = :topicId AND status = 'visited' ORDER BY visitedAt DESC LIMIT 1") suspend fun latestVisitedPerspective(topicId: String): PerspectiveEntity?
    @Query("UPDATE perspectives SET status = 'opened' WHERE id = :id AND status = 'suggested'") suspend fun markPerspectiveOpened(id: String)
    @Query("UPDATE perspective_topics SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id") suspend fun setTopicEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())
    @Query("UPDATE perspectives SET status = 'visited', visitedAt = :visitedAt WHERE id = :id") suspend fun markPerspectiveVisited(id: String, visitedAt: Long = System.currentTimeMillis())
    @Query("""
        SELECT p.* FROM perspectives p
        INNER JOIN perspective_recommended_videos r ON r.perspectiveId = p.id
        WHERE p.status = 'opened'
          AND (r.url LIKE '%' || :youtubeVideoId || '%'
               OR (r.title = :title AND r.channelName = :channelName))
    """) suspend fun openedPerspectivesForRecommendation(youtubeVideoId: String, title: String, channelName: String): List<PerspectiveEntity>
    @Query("UPDATE topic_suggestions SET status = :status, updatedAt = :updatedAt WHERE videoId = :videoId") suspend fun setTopicSuggestionStatus(videoId: String, status: String, updatedAt: Long = System.currentTimeMillis())
    @Query("DELETE FROM thought_edges WHERE fromNodeId IN (SELECT id FROM thought_nodes WHERE videoId = :videoId OR perspectiveId IN (SELECT id FROM perspectives WHERE videoId = :videoId)) OR toNodeId IN (SELECT id FROM thought_nodes WHERE videoId = :videoId OR perspectiveId IN (SELECT id FROM perspectives WHERE videoId = :videoId))") suspend fun deleteEdgesForVideo(videoId: String)
    @Query("DELETE FROM thought_nodes WHERE videoId = :videoId OR perspectiveId IN (SELECT id FROM perspectives WHERE videoId = :videoId)") suspend fun deleteNodesForVideo(videoId: String)
    @Query("DELETE FROM perspectives WHERE videoId = :videoId") suspend fun deletePerspectivesForVideo(videoId: String)
    @Query("DELETE FROM perspective_recommended_videos WHERE perspectiveId IN (SELECT id FROM perspectives WHERE videoId = :videoId)") suspend fun deleteRecommendedVideosForVideo(videoId: String)
    @Query("DELETE FROM video_analyses WHERE videoId = :videoId") suspend fun deleteAnalysisForVideo(videoId: String)
    @Query("DELETE FROM video_topics WHERE videoId = :videoId") suspend fun deleteTopicsForVideo(videoId: String)
    @Query("DELETE FROM topic_suggestions WHERE videoId = :videoId") suspend fun deleteSuggestionForVideo(videoId: String)
    @Query("DELETE FROM watched_videos WHERE id = :videoId") suspend fun deleteVideo(videoId: String)

    @Transaction
    suspend fun deleteVideoAndRelated(videoId: String) {
        deleteEdgesForVideo(videoId)
        deleteNodesForVideo(videoId)
        deleteRecommendedVideosForVideo(videoId)
        deletePerspectivesForVideo(videoId)
        deleteAnalysisForVideo(videoId)
        deleteTopicsForVideo(videoId)
        deleteSuggestionForVideo(videoId)
        deleteVideo(videoId)
    }
    @Upsert suspend fun upsertTopics(items: List<PerspectiveTopicEntity>)
    @Upsert suspend fun upsertTopicSuggestion(item: TopicSuggestionEntity)
    @Upsert suspend fun upsertVideo(item: WatchedVideoEntity)
    @Upsert suspend fun upsertVideoTopics(items: List<VideoTopicEntity>)
    @Upsert suspend fun upsertAnalysis(item: VideoAnalysisEntity)
    @Upsert suspend fun upsertPerspectives(items: List<PerspectiveEntity>)
    @Upsert suspend fun upsertRecommendedVideos(items: List<PerspectiveRecommendedVideoEntity>)
    @Upsert suspend fun upsertNodes(items: List<ThoughtNodeEntity>)
    @Upsert suspend fun upsertEdges(items: List<ThoughtEdgeEntity>)
    @Upsert suspend fun upsertMoment(item: ExpansionMomentEntity)
    @Upsert suspend fun upsertReport(item: WeeklyPerspectiveReportEntity)
}

@Database(
    entities = [PerspectiveTopicEntity::class, TopicSuggestionEntity::class, WatchedVideoEntity::class, VideoTopicEntity::class, VideoAnalysisEntity::class, PerspectiveEntity::class, PerspectiveRecommendedVideoEntity::class, ThoughtNodeEntity::class, ThoughtEdgeEntity::class, ExpansionMomentEntity::class, WeeklyPerspectiveReportEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class PerspectiveDatabase : RoomDatabase() {
    abstract fun dao(): PerspectiveDao

    companion object {
        @Volatile private var instance: PerspectiveDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE perspective_topics ADD COLUMN userApproved INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE TABLE IF NOT EXISTS topic_suggestions (videoId TEXT NOT NULL, proposedName TEXT NOT NULL, description TEXT NOT NULL, confidence REAL NOT NULL, model TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(videoId))")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_topic_suggestions_status ON topic_suggestions(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_topic_suggestions_createdAt ON topic_suggestions(createdAt)")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE watched_videos ADD COLUMN playbackEnded INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE video_analyses ADD COLUMN analysisBasis TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE video_analyses ADD COLUMN transcriptIncluded INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS perspective_recommended_videos (id TEXT NOT NULL, perspectiveId TEXT NOT NULL, title TEXT NOT NULL, channelName TEXT NOT NULL, thumbnailUrl TEXT NOT NULL, url TEXT NOT NULL, publishedAt TEXT NOT NULL, duration TEXT NOT NULL, rank INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_perspective_recommended_videos_perspectiveId ON perspective_recommended_videos(perspectiveId)")
            }
        }
        fun get(context: Context): PerspectiveDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PerspectiveDatabase::class.java, "perspective.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { instance = it }
        }
    }
}
