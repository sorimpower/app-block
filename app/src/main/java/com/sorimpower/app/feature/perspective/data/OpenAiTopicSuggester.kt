package com.sorimpower.app.feature.perspective.data

import android.content.Context
import com.sorimpower.app.core.ai.AiModelId
import com.sorimpower.app.core.ai.AiModelRouter
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import org.json.JSONObject

internal data class TopicSuggestionResult(
    val existingTopicId: String?,
    val proposedName: String,
    val description: String,
    val confidence: Double,
)

/** 제목·채널과 승인된 주제명만 보내 구체적인 관심 주제를 분류한다. 영상·음성은 전송하지 않는다. */
internal class OpenAiTopicSuggester(context: Context) {
    private val router = AiModelRouter(context)

    suspend fun suggest(video: WatchedVideoEntity, approvedTopics: List<PerspectiveTopicEntity>): TopicSuggestionResult {
        val topicList = approvedTopics.take(30).joinToString("\n") { "- ${it.id}: ${it.name}" }.ifBlank { "- 없음" }
        val response = router.generate(
            request = AiRequest(
                taskType = AiTaskType.PERSPECTIVE_TOPIC_SUGGESTION,
                userPrompt = """
                    YouTube 제목과 채널만 보고 한눈에 내용을 알 수 있는 구체적인 관심 주제 하나를 분류한다.
                    '주거생활', '금융시장', '사회', '경제', '건강', '자기계발'처럼 넓고 추상적인 이름은 금지한다.
                    대신 '집 구하기', '이사 준비', '전세 계약', '미국 주식', '반도체 주식', '채권 투자', '허리 운동'처럼
                    사용자가 무엇을 봤는지 바로 떠올릴 수 있는 대상·활동·시장 단위의 2~12자 한국어 이름을 쓴다.
                    기존 주제가 영상의 구체적인 대상을 정확히 나타낼 때만 existingTopicId를 사용한다.
                    기존 주제가 더 넓고 추상적이면 재사용하지 말고 세부 proposedName을 새로 제안한다.
                    채널명·인물명·영상 한 편만의 고유 문구처럼 재사용 불가능하게 좁은 이름은 금지한다. description은 25자 이내다.

                    제목: ${video.title.take(180)}
                    채널: ${video.channelName.take(80)}
                    기존 승인 주제:
                    $topicList

                    JSON만 반환:
                    {"existingTopicId":"","proposedName":"","description":"","confidence":0.0}
                """.trimIndent(),
            ),
            model = AiModelId.OPENAI_SMART,
        )
        val root = JSONObject(response.text.jsonObject())
        val existingId = root.optString("existingTopicId").trim().takeIf { id -> approvedTopics.any { it.id == id } }
        val proposedName = root.optString("proposedName").trim().take(24)
        if (existingId == null && proposedName.isBlank()) error("Luna가 주제를 제안하지 않았어요.")
        return TopicSuggestionResult(
            existingTopicId = existingId,
            proposedName = proposedName,
            description = root.optString("description").trim().take(60),
            confidence = root.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
        )
    }
}

private fun String.jsonObject(): String {
    val start = indexOf('{')
    val end = lastIndexOf('}')
    return if (start >= 0 && end > start) substring(start, end + 1) else this
}
