package com.sorimpower.app.feature.perspective.data

import android.content.Context
import com.sorimpower.app.core.ai.AiModelRouter
import com.sorimpower.app.core.ai.AiModelId
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

enum class InterestPeriod(val label: String, val type: String) {
    DAY("매일", "day"), WEEK("매주", "week"), MONTH("매월", "month"), YEAR("매년", "year");
}

data class InterestCategory(val name: String, val percentage: Int)
data class PersonalInterestInsight(val label: String, val text: String)
data class PerspectiveState(
    val videos: List<WatchedVideoEntity> = emptyList(),
    val analyses: List<InterestPeriodAnalysisEntity> = emptyList(),
    val loaded: Boolean = false,
)

class PerspectiveRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = PerspectiveDatabase.get(appContext).dao()
    private val router = AiModelRouter(appContext)
    private val youtubeHistoryResolver = YoutubeHistoryResolver()

    val state: Flow<PerspectiveState> = combine(dao.observeVideos(), dao.observeAnalyses()) { videos, analyses ->
        PerspectiveState(videos = videos, analyses = analyses, loaded = true)
    }

    /** Clears periodic work created by earlier app versions. Analyses now run only from the button. */
    suspend fun initialize() = withContext(Dispatchers.IO) { InterestAnalysisScheduler.cancel(appContext) }

    /** Playback detection only collects local history. It never calls AI per video. */
    suspend fun recordPlayback(title: String, channel: String, mediaId: String?, durationSec: Long, watchedSec: Long, playbackEnded: Boolean = false): WatchedVideoEntity? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val youtubeId = mediaId?.takeIf(::isVideoId) ?: "auto_${sha256(title.trim().lowercase()).take(24)}"
        val existing = dao.videoByYoutubeId(youtubeId)
            ?: youtubeId.takeUnless(::isVideoId)?.let { dao.latestVideoByTitleAndChannel(title.trim(), channel.trim()) }
        val resolvedId = youtubeId.takeIf(::isVideoId) ?: existing?.youtubeVideoId ?: youtubeId
        val item = WatchedVideoEntity(
            id = existing?.id ?: UUID.randomUUID().toString(), youtubeVideoId = resolvedId,
            url = if (isVideoId(resolvedId)) "https://www.youtube.com/watch?v=$resolvedId" else existing?.url.orEmpty(),
            title = title.trim().ifBlank { existing?.title ?: "YouTube 영상" }, channelName = channel.trim().ifBlank { existing?.channelName.orEmpty() },
            durationSec = maxOf(durationSec, existing?.durationSec ?: 0), watchedSec = maxOf(watchedSec, existing?.watchedSec ?: 0),
            watchedAt = System.currentTimeMillis(), source = "auto", analysisStatus = "collected", playbackEnded = playbackEnded,
            contentHash = sha256("$resolvedId|$title|$channel"),
        )
        dao.upsertVideo(item)
        item
    }

    suspend fun deleteWatchRecord(videoId: String) = withContext(Dispatchers.IO) { dao.deleteVideo(videoId) }

    suspend fun resolveWatchedVideoPlayback(video: WatchedVideoEntity): WatchedVideoPlayback? = withContext(Dispatchers.IO) {
        video.youtubeVideoId.takeIf(::isVideoId)?.let { id ->
            val url = "https://www.youtube.com/watch?v=$id"
            if (video.url != url) dao.updateVideoAddress(video.id, id, url)
            return@withContext WatchedVideoPlayback(id, url, "https://i.ytimg.com/vi/$id/mqdefault.jpg")
        }
        youtubeHistoryResolver.resolve(video.title, video.channelName)?.also { resolved ->
            resolved.videoId?.let { dao.updateVideoAddress(video.id, it, resolved.url) }
        }
    }

    suspend fun runDueAnalyses() = withContext(Dispatchers.IO) {
        InterestPeriod.entries.forEach { period ->
            val window = window(period)
            if (dao.analysis(period.type, window.key) == null) analyze(period, force = false)
        }
    }

    suspend fun analyze(period: InterestPeriod, force: Boolean = true): InterestPeriodAnalysisEntity = withContext(Dispatchers.IO) {
        val window = window(period)
        dao.analysis(period.type, window.key)?.takeUnless { force }?.let { return@withContext it }
        val videos = dao.videos().filter { it.source == "auto" && it.watchedAt >= window.from && it.watchedAt < window.until }
        require(videos.isNotEmpty()) { "${period.label} 분석을 위한 시청 기록이 아직 없어요." }
        val previous = dao.analyses(period.type).firstOrNull { it.periodKey != window.key }
        val result = analyzeWithAi(period, window.label, videos, previous)
        val analysis = InterestPeriodAnalysisEntity(period.type, window.key, window.label, videos.size, result.categories.toJson(), result.summary, result.trendSummary, result.personalInsights.toInsightsJson())
        dao.upsertAnalysis(analysis)
        analysis
    }

    private suspend fun analyzeWithAi(period: InterestPeriod, label: String, videos: List<WatchedVideoEntity>, previous: InterestPeriodAnalysisEntity?): AiInterestResult {
        val watched = videos.sortedByDescending(WatchedVideoEntity::watchedAt).take(80).joinToString("\n") { "- ${it.title.take(120)} | ${it.channelName.take(50)}" }
        val previousContext = previous?.let { "직전 ${period.label} 분석: ${it.summary}\n직전 분야 비중: ${it.categoriesJson}" } ?: "직전 분석 없음"
        val response = router.generate(AiRequest(
            taskType = AiTaskType.YOUTUBE_INTEREST_HISTORY_ANALYSIS,
            userPrompt = """
                YouTube 시청 기록을 개인의 관심 변화 히스토리로 요약한다. 영상 하나의 주장, 생각지도, 추천은 만들지 않는다.
                기간: $label (${videos.size}개)
                시청 기록:
                $watched

                $previousContext

                제목과 채널명에 근거해 분야를 2~5개로 묶고, 비중 합계는 100으로 한다. 변화는 직전 같은 주기와 비교 가능한 경우에만 말한다.
                personalInsights에는 이 기록에서 조심스럽게 읽히는 사용자 관찰을 정확히 3개 넣는다: 관심 방향, 콘텐츠 취향, 탐색 방식.
                성격·정체성·능력·정치 성향을 단정하거나 진단하지 말고, "~로 보입니다", "~를 자주 확인하는 편으로 읽힙니다"처럼 관찰 가설로 쓴다. 기록만으로 알 수 없는 사실은 추론하지 않는다.
                반드시 아래 JSON만 반환한다.
                {"categories":[{"name":"분야명","percentage":40}],"summary":"이 기간에 어떤 분야를 많이 봤는지 한두 문장","trendSummary":"관심 흐름이 어떻게 변했는지 한 문장, 비교 불가면 누적 관찰 문장","personalInsights":[{"label":"관심 방향","text":"관찰 기반 문장"},{"label":"콘텐츠 취향","text":"관찰 기반 문장"},{"label":"탐색 방식","text":"관찰 기반 문장"}]}
            """.trimIndent(),
        ), model = AiModelId.OPENAI_SMART)
        val root = JSONObject(response.text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```"))
        val categories = root.optJSONArray("categories").orEmpty().let { array ->
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { item ->
                item.optString("name").trim().takeIf(String::isNotBlank)?.let { InterestCategory(it.take(24), item.optInt("percentage").coerceIn(0, 100)) }
            } }
        }.take(5)
        require(categories.isNotEmpty()) { "분야를 정리하지 못했어요." }
        val insights = root.optJSONArray("personalInsights").orEmpty().let { array ->
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { item ->
                val label = item.optString("label").trim(); val text = item.optString("text").trim()
                if (label.isBlank() || text.isBlank()) null else PersonalInterestInsight(label.take(20), text.take(180))
            } }
        }.take(3)
        return AiInterestResult(categories, root.optString("summary").trim(), root.optString("trendSummary").trim(), insights)
    }

    private data class Window(val key: String, val label: String, val from: Long, val until: Long)
    private fun window(period: InterestPeriod): Window {
        val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
        val start = when (period) {
            InterestPeriod.DAY -> today
            InterestPeriod.WEEK -> today.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
            InterestPeriod.MONTH -> today.withDayOfMonth(1)
            InterestPeriod.YEAR -> today.withDayOfYear(1)
        }
        val key = when (period) { InterestPeriod.DAY -> start.toString(); InterestPeriod.WEEK -> "${start.year}-W${start.get(WeekFields.ISO.weekOfWeekBasedYear()).toString().padStart(2, '0')}"; InterestPeriod.MONTH -> "${start.year}-${start.monthValue.toString().padStart(2, '0')}"; InterestPeriod.YEAR -> start.year.toString() }
        val label = when (period) { InterestPeriod.DAY -> "${start.monthValue}월 ${start.dayOfMonth}일"; InterestPeriod.WEEK -> "${start.monthValue}월 ${start.dayOfMonth}일 주간"; InterestPeriod.MONTH -> "${start.year}년 ${start.monthValue}월"; InterestPeriod.YEAR -> "${start.year}년" }
        return Window(key, label, start.atStartOfDay(zone).toInstant().toEpochMilli(), today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

    private data class AiInterestResult(val categories: List<InterestCategory>, val summary: String, val trendSummary: String, val personalInsights: List<PersonalInterestInsight>)
}

fun String.toInterestCategories(): List<InterestCategory> = runCatching {
    val array = JSONArray(this); (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { InterestCategory(it.optString("name"), it.optInt("percentage")) } }
}.getOrDefault(emptyList())
private fun List<InterestCategory>.toJson() = JSONArray(map { JSONObject().put("name", it.name).put("percentage", it.percentage) }).toString()
fun String.toPersonalInterestInsights(): List<PersonalInterestInsight> = runCatching {
    val array = JSONArray(this); (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { PersonalInterestInsight(it.optString("label"), it.optString("text")) } }
}.getOrDefault(emptyList())
private fun List<PersonalInterestInsight>.toInsightsJson() = JSONArray(map { JSONObject().put("label", it.label).put("text", it.text) }).toString()
private fun JSONArray?.orEmpty() = this ?: JSONArray()
private fun isVideoId(value: String) = value.matches(Regex("[A-Za-z0-9_-]{11}"))
private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
