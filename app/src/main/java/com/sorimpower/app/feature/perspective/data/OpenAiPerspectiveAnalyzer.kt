package com.sorimpower.app.feature.perspective.data

import android.content.Context
import com.sorimpower.app.core.ai.AiModelId
import com.sorimpower.app.core.ai.AiModelRouter
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import org.json.JSONObject

data class DeepPerspective(
    val label: String,
    val description: String,
    val question: String,
    val searchQuery: String,
)

data class DeepVideoResult(
    val topic: String,
    val mainClaim: String,
    val subClaims: List<String>,
    val evidence: List<String>,
    val assumptions: List<String>,
    val stakeholders: List<String>,
    val perspectives: List<DeepPerspective>,
    val confidence: Double,
    val model: String,
)

@Suppress("UNUSED_PARAMETER")
internal class TerraPerspectiveAnalyzer(context: Context) {
    private val router = AiModelRouter(context)

    suspend fun analyze(video: WatchedVideoEntity, context: YoutubeAnalysisContext, topicNames: String): DeepVideoResult {
        val response = router.generate(
            request = AiRequest(
                taskType = AiTaskType.PERSPECTIVE_METADATA_ANALYSIS,
                userPrompt = metadataPrompt(video, context, topicNames),
                reasoningEffort = "medium",
            ),
            model = AiModelId.OPENAI_SMART,
        )
        return parse(response.text, video, "gpt-5.6-terra-medium")
    }

    private fun metadataPrompt(video: WatchedVideoEntity, context: YoutubeAnalysisContext, topicNames: String) = """
        당신은 사용자를 평가하지 않고 콘텐츠 노출과 논점만 구조화하는 유튜브 분석기다.
        아래는 YouTube 공개 메타데이터와, 공개적으로 제공되어 확보된 경우에만 포함한 자막이다.
        제공되지 않은 정보·영상 장면·발언을 지어내지 말고, 자막이 없으면 제목/설명 기반의 추정임을 mainClaim에 짧게 밝혀라.
        찬성/반대 이분법 대신 원인, 시간축, 이해관계자, 측정 기준이 다른 최대 4개의 탐색 관점을 만든다.
        각 관점은 이 영상을 본 사람이 다음 영상을 검색할 때 그대로 쓸 수 있을 만큼 구체적이고 검증 가능한 질문이어야 한다.
        label은 질문의 핵심을 12자 이내로 요약한다. '형평성', '정책 효과', '시장 영향', '경제적 관점', '사회적 관점' 같은 카테고리명 수준의 표현은 금지한다.
        question에는 비교 대상, 시점, 지역 또는 측정 지표 중 가능한 요소를 포함한다.

        제목: ${video.title}
        채널: ${video.channelName}
        URL: ${context.url}
        설명: ${context.description.take(8_000)}
        태그: ${context.tags.joinToString(", ")}
        카테고리: ${context.category}
        게시일: ${context.publishedAt}
        재생시간: ${context.duration}
        조회수: ${context.viewCount}
        챕터: ${context.chapters.joinToString(" | ")}
        자막 확보 여부: ${if (context.hasTranscript) "예" else "아니오"}
        자막/대본: ${context.transcript.take(20_000)}
        앱 주제 후보: $topicNames

        JSON만 반환한다.
        {"topic":"앱 주제 후보 중 하나","mainClaim":"","subClaims":[""],"evidence":[""],"assumptions":[""],"stakeholders":[""],"perspectives":[{"label":"짧은 관점명","description":"왜 다른 질문인지","question":"대표 질문","searchQuery":"YouTube 탐색 검색어"}],"confidence":0.0}
    """.trimIndent()
}

internal class GeminiPerspectiveAnalyzer(context: Context) {
    private val geminiVideo = GeminiYoutubeVideoProvider()

    suspend fun analyzeVideo(video: WatchedVideoEntity, topicNames: String): DeepVideoResult {
        val prompt = prompt(video, topicNames)
        val url = video.url.trim()
        require(url.isNotBlank()) { "정확한 YouTube 주소가 없어 실제 영상 분석을 시작할 수 없어요." }
        // 영상의 화면·음성 맥락을 직접 읽을 수 있는 Gemini만 사용한다.
        val raw = try {
            geminiVideo.analyze(url, prompt)
        } catch (error: Throwable) {
            error("Gemini 3.5 Flash 영상 분석에 실패했습니다. ${error.message.orEmpty()}")
        }
        return parse(raw, video, "gemini-3.5-flash-video")
    }

    private fun prompt(video: WatchedVideoEntity, topicNames: String) = """
        당신은 사용자를 평가하지 않고 콘텐츠 노출과 논점만 구조화하는 유튜브 분석기다.
        사용자가 YouTube에서 본 영상의 공개 URL·제목·채널을 확인하고, 검색으로 검증 가능한 공개 정보만 활용한다.
        실제 영상 음성이나 전체 내용을 확인하지 못했다면 제목만으로 주장을 지어내지 말고 confidence를 낮추며 불확실성을 mainClaim에 명시한다.
        찬성/반대 이분법 대신 원인, 시간축, 이해관계자, 측정 기준이 다른 최대 4개의 탐색 관점을 만든다.
        각 관점은 이 영상을 본 사람이 다음 영상을 검색할 때 그대로 쓸 수 있을 만큼 구체적이고 검증 가능한 질문이어야 한다.
        label은 질문의 핵심을 12자 이내로 요약한다. '형평성', '정책 효과', '시장 영향', '경제적 관점', '사회적 관점' 같은 카테고리명 수준의 표현은 금지한다.
        question에는 비교 대상, 시점, 지역 또는 측정 지표 중 가능한 요소를 포함한다.
        정치성향·성격·확증편향 점수 등 사용자를 단정하는 표현은 금지한다.

        영상 URL: ${video.url}
        제목: ${video.title}
        채널: ${video.channelName}
        앱 주제 후보: $topicNames

        다음 JSON만 반환한다.
        {"topic":"앱 주제 후보 중 하나","mainClaim":"확인 가능한 핵심 주장 또는 확인 불가 설명","subClaims":[""],"evidence":["영상/공개 정보에서 확인 가능한 근거"],"assumptions":["주장의 전제"],"stakeholders":["이해관계자"],"perspectives":[{"label":"짧은 관점명","description":"왜 다른 질문인지","question":"대표 질문","searchQuery":"YouTube 탐색 검색어"}],"confidence":0.0}
    """.trimIndent()
}

private fun parse(raw: String, video: WatchedVideoEntity, model: String): DeepVideoResult {
        val root = JSONObject(raw.extractJsonObject())
        val perspectives = root.optJSONArray("perspectives").objects().mapNotNull { item ->
            val label = item.optString("label").trim()
            if (label.isBlank()) null else DeepPerspective(
                label = label,
                description = item.optString("description").trim(),
                question = item.optString("question").trim(),
                searchQuery = item.optString("searchQuery").trim().ifBlank { "$label ${video.title}" },
            )
        }.take(4)
        return DeepVideoResult(
            topic = root.optString("topic").trim().ifBlank { "기타" },
            mainClaim = root.optString("mainClaim").trim().ifBlank { "공개 정보만으로 핵심 주장을 확인하기 어렵습니다." },
            subClaims = root.optJSONArray("subClaims").strings(),
            evidence = root.optJSONArray("evidence").strings(),
            assumptions = root.optJSONArray("assumptions").strings(),
            stakeholders = root.optJSONArray("stakeholders").strings(),
            perspectives = perspectives,
            confidence = root.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
            model = model,
        )
}

private fun String.extractJsonObject(): String {
    val start = indexOf('{')
    val end = lastIndexOf('}')
    return if (start >= 0 && end > start) substring(start, end + 1) else this
}

private fun org.json.JSONArray?.strings(): List<String> = buildList {
    val array = this@strings ?: return@buildList
    repeat(array.length()) { index -> array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add) }
}

private fun org.json.JSONArray?.objects(): List<JSONObject> = buildList {
    val array = this@objects ?: return@buildList
    repeat(array.length()) { index -> array.optJSONObject(index)?.let(::add) }
}
