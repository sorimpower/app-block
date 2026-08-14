package com.sorimpower.app.feature.bodylog.data

import android.content.Context
import com.sorimpower.app.core.ai.AiModelRouter
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import com.sorimpower.app.feature.bodylog.domain.MealType
import org.json.JSONObject

internal data class MealCalorieEstimate(
    val estimatedCalories: Int,
    val summary: String,
)

/** Estimates one saved meal once. Daily totals are calculated locally from these cached estimates. */
internal class OpenAiMealCalorieAnalyzer(context: Context) {
    private val router = AiModelRouter(context)

    suspend fun analyze(meal: MealWithDetails): MealCalorieEstimate {
        val response = router.generate(
            AiRequest(
                // Legacy wire identifier retained for the currently deployed Firebase callable contract.
                taskType = AiTaskType.BODY_LOG_DAILY_CALORIE_ANALYSIS,
                userPrompt = buildPrompt(meal),
            ),
        )
        val json = JSONObject(response.text.trim())
        return MealCalorieEstimate(
            estimatedCalories = json.optInt("estimatedCalories", 0).coerceIn(20, 5_000),
            summary = json.optString("summary").trim().take(160),
        )
    }

    private fun buildPrompt(meal: MealWithDetails) = buildString {
        appendLine("당신은 한국 성인의 한 끼 식사 기록에서 섭취 열량을 추정하는 영양 기록 분석기다.")
        appendLine("아래 한 끼의 음식명·양·메모를 보고 일반적인 1인분 기준을 보수적으로 적용해 이 식사만의 총 kcal를 추정한다.")
        appendLine("정확한 중량이 없어도 추가 기록을 요청하지 않는다. 열량은 범위가 아닌 하나의 정수 추정값으로 내고, 확정 수치가 아닌 대략값이라는 점을 summary에서 짧게 밝힌다.")
        appendLine("기록된 간식·음료·소스·주류는 포함하되 기록에 없는 음식이나 다른 끼니를 임의로 추가하지 않는다. 의료적 조언이나 체중 평가는 하지 않는다.")
        appendLine("반드시 JSON 객체만 반환: {\"estimatedCalories\": 0, \"summary\": \"120자 이내의 추정 근거\"}")
        val items = meal.items.sortedBy { it.sortOrder }.joinToString(", ") { item -> item.name + (item.amount?.let { " (" + it + ")" } ?: "") }
        appendLine("- " + MealType.from(meal.meal.mealType).label + ": " + items.ifBlank { "음식명 미기록" } + ", 메모=" + (meal.meal.note ?: "없음") + ", 태그=" + meal.meal.tags.ifBlank { "없음" })
    }
}
