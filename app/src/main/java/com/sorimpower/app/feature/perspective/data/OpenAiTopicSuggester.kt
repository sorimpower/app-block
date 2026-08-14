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

/** 제목·채널과 승인된 주제명만 보내는 저비용 Luna 분류기. 영상·음성은 전송하지 않는다. */
internal class OpenAiTopicSuggester(context: Context) {
    private val router = AiModelRouter(context)

    suspend fun suggest(video: WatchedVideoEntity, approvedTopics: List<PerspectiveTopicEntity>): TopicSuggestionResult {
        val topicList = approvedTopics.take(30).joinToString("\n") { "- ${it.id}: ${it.name}" }.ifBlank { "- 없음" }
        val response = router.generate(
            request = AiRequest(
                taskType = AiTaskType.PERSPECTIVE_TOPIC_SUGGESTION,
                userPrompt = """
                    YouTube 제목과 채널만 보고 재사용 가능한 상위 관심 주제 하나를 분류한다.
                    기존 주제와 의미가 같으면 existingTopicId에 그 ID를 쓰고 proposedName은 빈 문자열로 둔다.
                    맞는 기존 주제가 없으면 existingTopicId는 빈 문자열, proposedName은 2~12자의 넓고 안정적인 한국어 주제명으로 쓴다.
                    채널명·인물명·영상 한 편의 고유 문구처럼 너무 좁은 이름은 금지한다. description은 25자 이내다.

                    제목: ${video.title.take(180)}
                    채널: ${video.channelName.take(80)}
                    기존 승인 주제:
                    $topicList

                    JSON만 반환:
                    {"existingTopicId":"","proposedName":"","description":"","confidence":0.0}
                """.trimIndent(),
            ),
            model = AiModelId.OPENAI_FAST,
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
