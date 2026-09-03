package com.sorimpower.app.feature.bodylog.data

import android.content.Context
import com.sorimpower.app.core.ai.AiModelRouter
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import org.json.JSONObject

internal data class ExerciseCalorieEstimate(val estimatedCalories: Int)

/** Conservative single-session estimate; a manually entered value is never replaced. */
internal class OpenAiExerciseCalorieAnalyzer(context: Context) {
    private val router = AiModelRouter(context)

    suspend fun analyze(exercise: ExerciseEntryEntity, weightKg: Double?): ExerciseCalorieEstimate {
        val response = router.generate(AiRequest(
            taskType = AiTaskType.BODY_LOG_DAILY_CALORIE_ANALYSIS,
            userPrompt = """
                한국 성인의 운동 기록 한 건에서 소모 칼로리를 보수적으로 추정한다. 의료 조언이나 체중 평가는 하지 않는다.
                운동: ${exercise.exerciseType}, 시간: ${exercise.durationMinutes}분, 강도: ${exercise.intensity}, 메모: ${exercise.note ?: "없음"}, 체중: ${weightKg?.let { "${it}kg" } ?: "미기록"}
                기록된 정보만 사용하고, 체중이 없으면 일반 성인 기준의 중간값을 사용한다. 반드시 JSON만 반환한다: {"estimatedCalories":0}
            """.trimIndent(),
        ))
        return ExerciseCalorieEstimate(JSONObject(response.text.trim()).optInt("estimatedCalories", 0).coerceIn(10, 3_000))
    }
}
