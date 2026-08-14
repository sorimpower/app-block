package com.sorimpower.app.feature.perspective.data

import android.content.Context
import com.sorimpower.app.feature.perspective.reminder.TopicSuggestionNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class PerspectiveState(
    val topics: List<PerspectiveTopicEntity> = emptyList(),
    val topicSuggestions: List<TopicSuggestionEntity> = emptyList(),
    val videos: List<WatchedVideoEntity> = emptyList(),
    val videoTopics: List<VideoTopicEntity> = emptyList(),
    val analyses: List<VideoAnalysisEntity> = emptyList(),
    val perspectives: List<PerspectiveEntity> = emptyList(),
    val recommendedVideos: List<PerspectiveRecommendedVideoEntity> = emptyList(),
    val nodes: List<ThoughtNodeEntity> = emptyList(),
    val edges: List<ThoughtEdgeEntity> = emptyList(),
    val moments: List<ExpansionMomentEntity> = emptyList(),
    val reports: List<WeeklyPerspectiveReportEntity> = emptyList(),
    val loaded: Boolean = false,
) {
    fun topicName(id: String): String = topics.firstOrNull { it.id == id }?.name ?: "기타"
    fun videoTopicIds(videoId: String): Set<String> = videoTopics.filter { it.videoId == videoId }.mapTo(mutableSetOf(), VideoTopicEntity::topicId)
}

private data class PerspectiveCoreState(
    val topics: List<PerspectiveTopicEntity>,
    val videos: List<WatchedVideoEntity>,
    val videoTopics: List<VideoTopicEntity>,
    val analyses: List<VideoAnalysisEntity>,
    val perspectives: List<PerspectiveEntity>,
    val recommendedVideos: List<PerspectiveRecommendedVideoEntity>,
)

class PerspectiveRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = PerspectiveDatabase.get(appContext).dao()
    private val terraAnalyzer = TerraPerspectiveAnalyzer(appContext)
    private val geminiAnalyzer = GeminiPerspectiveAnalyzer(appContext)
    private val youtubeVideoResolver = YoutubeVideoResolver()
    private val youtubePerspectiveSearch = YoutubePerspectiveSearch()
    private val topicSuggester = OpenAiTopicSuggester(appContext)

    val state: Flow<PerspectiveState> = combine(
        dao.observeTopics(), dao.observeVideos(), dao.observeVideoTopics(), dao.observeAnalyses(), dao.observePerspectives(),
    ) { topics, videos, videoTopics, analyses, perspectives ->
        PerspectiveCoreState(topics, videos, videoTopics, analyses, perspectives, emptyList())
    }.combine(dao.observeRecommendedVideos()) { core, recommendedVideos ->
        core.copy(recommendedVideos = recommendedVideos)
    }.combine(dao.observeNodes()) { core, nodes ->
        PerspectiveState(topics = core.topics, videos = core.videos, videoTopics = core.videoTopics, analyses = core.analyses, perspectives = core.perspectives, recommendedVideos = core.recommendedVideos, nodes = nodes)
    }
        .combine(dao.observeEdges()) { state, edges -> state.copy(edges = edges) }
        .combine(dao.observeMoments()) { state, moments -> state.copy(moments = moments) }
        .combine(dao.observeTopicSuggestions()) { state, suggestions -> state.copy(topicSuggestions = suggestions) }
        .combine(dao.observeReports()) { state, reports -> state.copy(reports = reports, loaded = true) }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        generateWeeklyReport()
    }

    suspend fun setTopicEnabled(id: String, enabled: Boolean) = dao.setTopicEnabled(id, enabled)

    suspend fun deleteWatchRecord(videoId: String) = withContext(Dispatchers.IO) {
        dao.deleteVideoAndRelated(videoId)
        generateWeeklyReport()
    }

    suspend fun updateTopic(id: String, name: String, description: String) = withContext(Dispatchers.IO) {
        val normalizedName = name.trim().take(30)
        require(normalizedName.isNotBlank()) { "주제 이름을 입력해 주세요." }
        val current = dao.topic(id) ?: error("수정할 주제를 찾지 못했어요.")
        val duplicate = dao.topicByName(normalizedName)
        require(duplicate == null || duplicate.id == id) { "같은 이름의 주제가 이미 있어요." }
        dao.upsertTopics(
            listOf(
                current.copy(
                    name = normalizedName,
                    description = description.trim().take(100),
                    updatedAt = System.currentTimeMillis(),
                ),
            ),
        )
        generateWeeklyReport()
    }

    suspend fun recordPlayback(
        title: String,
        channel: String,
        mediaId: String?,
        durationSec: Long,
        watchedSec: Long,
        playbackEnded: Boolean = false,
    ): WatchedVideoEntity? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val youtubeId = mediaId?.takeIf(::isVideoId) ?: "auto_${sha256(title.trim().lowercase()).take(24)}"
        val url = if (isVideoId(youtubeId)) "https://www.youtube.com/watch?v=$youtubeId" else ""
        saveVideo(youtubeId, url, title.trim(), channel.trim(), "auto", durationSec, watchedSec, playbackEnded)
    }

    private suspend fun saveVideo(
        youtubeId: String,
        url: String,
        title: String,
        channel: String,
        source: String,
        durationSec: Long,
        watchedSec: Long,
        playbackEnded: Boolean = false,
    ): WatchedVideoEntity {
        val existing = dao.videoByYoutubeId(youtubeId)
            // URL을 나중에 찾은 기존 기록도 다시 auto ID로 들어오는 MediaSession 갱신과 합친다.
            ?: youtubeId.takeUnless(::isVideoId)?.let { dao.latestVideoByTitleAndChannel(title.trim(), channel.trim()) }
        val now = System.currentTimeMillis()
        val item = WatchedVideoEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            youtubeVideoId = youtubeId,
            url = url.ifBlank { existing?.url.orEmpty() },
            title = title.ifBlank { existing?.title ?: "YouTube 영상" },
            channelName = channel.ifBlank { existing?.channelName.orEmpty() },
            durationSec = maxOf(durationSec, existing?.durationSec ?: 0),
            watchedSec = maxOf(watchedSec, existing?.watchedSec ?: 0),
            watchedAt = now,
            source = if (existing?.source == "share") "share" else source,
            analysisStatus = existing?.analysisStatus ?: "unclassified",
            playbackEnded = playbackEnded,
            contentHash = sha256("$youtubeId|$title|$channel"),
        )
        dao.upsertVideo(item)
        val meaningful = item.watchedSec >= 120
        val previousSuggestion = dao.topicSuggestion(item.id)
        val retryFailed = previousSuggestion?.status == "failed" && (
            previousSuggestion.model != TOPIC_MODEL || System.currentTimeMillis() - previousSuggestion.updatedAt >= TOPIC_RETRY_INTERVAL_MS
        )
        if (meaningful && dao.topicIdsForVideo(item.id).isEmpty() && (previousSuggestion == null || retryFailed)) {
            suggestTopic(item)
        }
        if (playbackEnded && isExploreEligible(item)) notifyExistingTopicExplore(item)
        generateWeeklyReport()
        return item
    }

    private suspend fun suggestTopic(video: WatchedVideoEntity) {
        val processing = TopicSuggestionEntity(videoId = video.id, proposedName = "", model = TOPIC_MODEL, status = "processing")
        dao.upsertTopicSuggestion(processing)
        runCatching { topicSuggester.suggest(video, dao.topics().filter(PerspectiveTopicEntity::enabled)) }
            .onSuccess { result ->
                val existingTopic = result.existingTopicId?.let { dao.topic(it) }
                    ?: result.proposedName.takeIf(String::isNotBlank)?.let { dao.topicByName(it) }
                if (existingTopic != null) {
                    linkVideoToTopic(video, existingTopic, result.confidence)
                    dao.upsertTopicSuggestion(processing.copy(
                        proposedName = existingTopic.name,
                        description = existingTopic.description,
                        confidence = result.confidence,
                        status = "approved",
                        updatedAt = System.currentTimeMillis(),
                    ))
                    dao.video(video.id)?.takeIf(::isExploreEligible)
                        ?.takeIf(WatchedVideoEntity::playbackEnded)
                        ?.let { notifyExistingTopicExplore(it) }
                } else {
                    val suggestion = processing.copy(
                        proposedName = result.proposedName,
                        description = result.description,
                        confidence = result.confidence,
                        status = "pending",
                        updatedAt = System.currentTimeMillis(),
                    )
                    dao.upsertTopicSuggestion(suggestion)
                    TopicSuggestionNotifier.show(appContext, suggestion, video)
                }
            }
            .onFailure {
                dao.upsertTopicSuggestion(processing.copy(status = "failed", updatedAt = System.currentTimeMillis()))
            }
    }

    /** 주제 분류는 재생 중에 끝나도 되지만, 다른 관점 알림은 영상 종료 시점에만 보낸다. */
    private suspend fun notifyExistingTopicExplore(video: WatchedVideoEntity) {
        val suggestion = dao.topicSuggestion(video.id)?.takeIf { it.status == "approved" } ?: return
        val topicId = dao.topicIdsForVideo(video.id).firstOrNull() ?: return
        val topic = dao.topic(topicId) ?: return
        if (TopicSuggestionNotifier.showExplore(appContext, topic, video)) {
            dao.setTopicSuggestionStatus(video.id, "explore_notified")
        }
    }

    private fun isExploreEligible(video: WatchedVideoEntity): Boolean =
        video.watchedSec >= MINIMUM_WATCH_SECONDS &&
            (video.durationSec <= 0 || video.watchedSec.toDouble() / video.durationSec >= MINIMUM_WATCH_RATIO)

    suspend fun acceptTopicSuggestion(videoId: String) = withContext(Dispatchers.IO) {
        val suggestion = dao.topicSuggestion(videoId)?.takeIf { it.status == "pending" } ?: return@withContext
        val video = dao.video(videoId) ?: return@withContext
        val topic = dao.topicByName(suggestion.proposedName) ?: PerspectiveTopicEntity(
            id = "user_${sha256(suggestion.proposedName.lowercase()).take(16)}",
            name = suggestion.proposedName,
            description = suggestion.description,
            enabled = true,
            userApproved = true,
        ).also { dao.upsertTopics(listOf(it)) }
        linkVideoToTopic(video, topic, suggestion.confidence)
        dao.setTopicSuggestionStatus(videoId, "approved")
        TopicSuggestionNotifier.cancel(appContext, videoId)
        generateWeeklyReport()
    }

    suspend fun dismissTopicSuggestion(videoId: String) = withContext(Dispatchers.IO) {
        dao.setTopicSuggestionStatus(videoId, "dismissed")
        TopicSuggestionNotifier.cancel(appContext, videoId)
    }

    private suspend fun linkVideoToTopic(video: WatchedVideoEntity, topic: PerspectiveTopicEntity, confidence: Double) {
        dao.upsertVideoTopics(listOf(VideoTopicEntity(video.id, topic.id, confidence)))
        dao.upsertNodes(listOf(
            ThoughtNodeEntity("video:${video.id}:${topic.id}", topic.id, "video", videoId = video.id, label = video.title, status = "visited"),
        ))
        dao.upsertVideo(video.copy(analysisStatus = if (video.analysisStatus == "deepAnalyzed") video.analysisStatus else "classified"))
    }

    /** 기본은 Terra의 풍부한 공개 정보 분석, premiumVideo는 Gemini의 실제 영상·음성 분석이다. */
    suspend fun deepAnalyze(videoId: String, premiumVideo: Boolean = false): VideoAnalysisEntity = withContext(Dispatchers.IO) {
        val video = dao.video(videoId) ?: error("영상을 찾지 못했어요.")
        val topicNames = dao.topics().filter(PerspectiveTopicEntity::enabled).joinToString(", ", transform = PerspectiveTopicEntity::name)
        if (topicNames.isBlank()) error("먼저 알림이나 주제 관리에서 추천 주제를 등록해 주세요.")
        val context = youtubeVideoResolver.resolve(
            video.youtubeVideoId.takeIf(::isVideoId),
            video.title,
            video.channelName,
        ) ?: error("YouTube 공개 정보에서 정확히 일치하는 영상을 찾지 못했어요. 다른 영상을 분석하지 않기 위해 중단했습니다.")
        val analysisVideo = if (video.url != context.url || video.youtubeVideoId != context.videoId) {
            dao.updateVideoAddress(video.id, context.videoId, context.url)
            video.copy(youtubeVideoId = context.videoId, url = context.url)
        } else video
        val sourceHash = sha256("${analysisVideo.contentHash}|${context.description}|${context.tags}|${context.chapters}|${context.transcript}")
        val cached = dao.analysis(videoId)
        val cachedPremium = cached?.model == GEMINI_VIDEO_MODEL
        if (cached != null && cached.sourceHash == sourceHash && cached.promptVersion == PROMPT_VERSION && (!premiumVideo || cachedPremium)) return@withContext cached
        val result = if (premiumVideo) geminiAnalyzer.analyzeVideo(analysisVideo, topicNames) else terraAnalyzer.analyze(analysisVideo, context, topicNames)
        val analysis = VideoAnalysisEntity(
            videoId = video.id,
            topic = result.topic,
            mainClaim = result.mainClaim,
            subClaimsJson = jsonArray(result.subClaims),
            evidenceJson = jsonArray(result.evidence),
            assumptionsJson = jsonArray(result.assumptions),
            stakeholdersJson = jsonArray(result.stakeholders),
            missingPerspectivesJson = jsonArray(result.perspectives.map(DeepPerspective::label)),
            confidence = result.confidence,
            model = result.model,
            promptVersion = PROMPT_VERSION,
            sourceHash = sourceHash,
            analysisBasis = if (premiumVideo) "실제 영상·음성 + YouTube 공개 정보" else buildList {
                add("제목·채널·설명·태그·챕터·공개 메타데이터")
                if (context.hasTranscript) add("공개 자막")
            }.joinToString(" + "),
            transcriptIncluded = context.hasTranscript && !premiumVideo,
        )
        dao.upsertAnalysis(analysis)
        dao.upsertVideo(analysisVideo.copy(analysisStatus = "deepAnalyzed"))
        val enabledTopics = dao.topics().filter(PerspectiveTopicEntity::enabled)
        val linkedTopicId = dao.topicIdsForVideo(video.id).firstOrNull()
        val topic = linkedTopicId?.let { id -> enabledTopics.firstOrNull { it.id == id } }
            ?: enabledTopics.firstOrNull { it.name.equals(result.topic, ignoreCase = true) }
            ?: error("이 영상의 주제를 먼저 등록해 주세요.")
        val videoNodeId = "video:${video.id}:${topic.id}"
        dao.upsertNodes(listOf(ThoughtNodeEntity(videoNodeId, topic.id, "video", videoId = video.id, label = video.title, status = "visited")))
        val perspectives = result.perspectives.take(4).map { suggestion ->
            PerspectiveEntity(
                id = UUID.randomUUID().toString(), topicId = topic.id, videoId = video.id,
                label = suggestion.label, description = suggestion.description,
                representativeQuestion = suggestion.question, searchQuery = suggestion.searchQuery,
            )
        }
        dao.upsertPerspectives(perspectives)
        savePerspectiveRecommendations(perspectives)
        dao.upsertNodes(perspectives.map { p -> ThoughtNodeEntity("perspective:${p.id}", topic.id, "perspective", perspectiveId = p.id, label = p.label, status = "suggested") })
        dao.upsertEdges(perspectives.map { p -> ThoughtEdgeEntity(UUID.randomUUID().toString(), topic.id, videoNodeId, "perspective:${p.id}", "suggested") })
        generateWeeklyReport()
        analysis
    }

    /** 관점별 검색어는 화면에 노출하지 않고, 실제 검증 가능한 YouTube 영상 카드로 캐시한다. */
    private suspend fun savePerspectiveRecommendations(perspectives: List<PerspectiveEntity>) {
        val byQuery = runCatching { youtubePerspectiveSearch.find(perspectives.map(PerspectiveEntity::searchQuery)) }.getOrElse { emptyMap() }
        val items = perspectives.flatMap { perspective ->
            byQuery[perspective.searchQuery].orEmpty().take(2).mapIndexed { rank, video ->
                PerspectiveRecommendedVideoEntity(
                    id = "${perspective.id}:${video.videoId}",
                    perspectiveId = perspective.id,
                    title = video.title,
                    channelName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl,
                    url = video.url,
                    publishedAt = video.publishedAt,
                    rank = rank,
                )
            }
        }
        if (items.isNotEmpty()) dao.upsertRecommendedVideos(items)
    }

    suspend fun markPerspectiveVisited(id: String) = withContext(Dispatchers.IO) {
        val item = dao.perspective(id) ?: return@withContext
        val previous = dao.latestVisitedPerspective(item.topicId)
        dao.markPerspectiveVisited(id)
        dao.upsertNodes(listOf(ThoughtNodeEntity("perspective:${item.id}", item.topicId, "perspective", perspectiveId = item.id, label = item.label, status = "visited", createdAt = item.createdAt)))
        if (previous != null && previous.id != item.id && previous.label != item.label) {
            dao.upsertEdgeFromPerspective(previous, item)
            dao.upsertMoment(ExpansionMomentEntity(
                id = UUID.randomUUID().toString(), topicId = item.topicId,
                fromLabel = previous.label, toLabel = item.label,
                title = "${previous.label}에서 ${item.label}(으)로",
                description = "기존 탐색 경로에서 새로운 질문으로 이동했습니다.",
                reason = item.representativeQuestion,
                aiConfidence = 0.7,
            ))
        }
        generateWeeklyReport()
    }

    private suspend fun PerspectiveDao.upsertEdgeFromPerspective(from: PerspectiveEntity, to: PerspectiveEntity) {
        upsertEdges(listOf(ThoughtEdgeEntity(UUID.randomUUID().toString(), to.topicId, "perspective:${from.id}", "perspective:${to.id}", "selected")))
    }

    suspend fun generateWeeklyReport() = withContext(Dispatchers.IO) {
        val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val from = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val videos = dao.videoSnapshot().filter { it.watchedAt >= from && it.watchedSec >= 120 }
        val topics = dao.topics()
        val links = dao.videoTopicSnapshot()
        val topicCounts = links.filter { link -> videos.any { it.id == link.videoId } }.groupingBy(VideoTopicEntity::topicId).eachCount()
        val dominant = topicCounts.entries.sortedByDescending(Map.Entry<String, Int>::value).take(3).mapNotNull { entry -> topics.firstOrNull { it.id == entry.key }?.name }
        val perspectives = dao.perspectiveSnapshot()
        val visited = perspectives.filter { it.status == "visited" }.map(PerspectiveEntity::label).distinct().take(5)
        val under = perspectives.filter { it.status == "suggested" }.map(PerspectiveEntity::label).distinct().take(5)
        val summary = if (videos.isEmpty()) "이번 주에는 아직 분석할 YouTube 시청 기록이 없습니다." else if (under.isEmpty()) {
            "이번 주에는 ${dominant.joinToString(" · ").ifBlank { "여러 주제" }} 콘텐츠를 주로 접했습니다. 다른 관점 분석을 실행하면 덜 본 세계가 나타납니다."
        } else "이번 주에는 ${dominant.joinToString(" · ")} 콘텐츠를 주로 접했고, ${under.take(3).joinToString(" · ")} 관점은 아직 탐색하지 않았습니다."
        dao.upsertReport(WeeklyPerspectiveReportEntity(weekStart.toEpochDay(), jsonArray(dominant), jsonArray(visited), jsonArray(under), summary))
    }

    private companion object {
        const val PROMPT_VERSION = "perspective-analysis-v2"
        const val GEMINI_VIDEO_MODEL = "gemini-3.5-flash-video"
        const val TOPIC_MODEL = "gpt-5.6-luna"
        const val TOPIC_RETRY_INTERVAL_MS = 24 * 60 * 60 * 1_000L
        const val MINIMUM_WATCH_SECONDS = 120L
        const val MINIMUM_WATCH_RATIO = 0.5
    }
}

private fun isVideoId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-]{11}"))
private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
private fun jsonArray(values: List<String>): String = JSONArray(values).toString()

private suspend fun PerspectiveDao.videoSnapshot(): List<WatchedVideoEntity> = observeVideos().first()
private suspend fun PerspectiveDao.videoTopicSnapshot(): List<VideoTopicEntity> = observeVideoTopics().first()
private suspend fun PerspectiveDao.perspectiveSnapshot(): List<PerspectiveEntity> = observePerspectives().first()
