package com.sorimpower.app.feature.bodylog.data

import android.content.Context
import com.sorimpower.app.core.ai.AiModelRouter
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import com.sorimpower.app.feature.bodylog.domain.BodyLogAiAnalysis
import com.sorimpower.app.feature.bodylog.domain.BodyLogState
import com.sorimpower.app.feature.bodylog.domain.MealType
import com.sorimpower.app.feature.bodylog.domain.localDate
import org.json.JSONObject

internal class OpenAiBodyLogAnalyzer(
    private val context: Context,
) {
    private val aiRouter = AiModelRouter(context)

    suspend fun analyze(state: BodyLogState): BodyLogAiAnalysis {
        val weights = state.weights.sortedByDescending { it.measuredAt }.take(MAX_WEIGHT_RECORDS)
        if (weights.size < MINIMUM_WEIGHT_RECORDS) {
            return BodyLogAiAnalysis(
                headline = "체중 기록을 조금 더 모아볼까요?",
                trendSummary = "추세를 보려면 서로 다른 날의 체중 기록이 최소 2개 필요해요.",
                encouragement = "하루 한 번, 비슷한 시간대에 기록하면 작은 변화도 더 정확히 볼 수 있어요.",
                nextSteps = listOf("다음 체중을 기록해 주세요", "가능하면 같은 조건에서 측정해 주세요"),
                safetyNote = "이 기능은 의료 진단이나 처방 조언을 제공하지 않아요.",
            )
        }
        val response = aiRouter.generate(
            AiRequest(AiTaskType.BODY_LOG_PROGRESS_ANALYSIS, createPrompt(state, weights)),
        )
        return parse(response.text)
    }

    private fun createPrompt(state: BodyLogState, weights: List<WeightEntryEntity>): String = buildString {
        appendLine("당신은 다이어트 전문 의료진의 관점에서 마운자로 주사·식습관·체중 추이를 함께 검토하는 기록 분석기다.")
        appendLine("진단·처방·용량 변경 지시는 하지 말고, 담백하고 전문적인 한국어 존댓말로 쓴다. 감정적 위로나 장황한 설명은 금지한다.")
        appendLine("반드시 핵심만 쓴다: headline은 한 문장 35자 이내, trendSummary는 체중 추이·식습관·주사 기록을 함께 연결한 최대 2문장(180자 이내), encouragement는 현재 가장 중요한 해석 1~2문장(160자 이내)이다.")
        appendLine("식단은 기록된 빈도·구성·메모만 근거로 하고, 마운자로는 최근 투여일·용량·간격·부작용 기록만 근거로 한다. 기록이 부족하면 한 문장으로 한계를 밝힌다.")
        appendLine("nextSteps는 우선순위가 높은 실천 또는 기록 항목만 최대 2개, 항목당 45자 이내로 작성한다.")
        appendLine("safetyNote는 심각하거나 지속되는 부작용 기록이 있을 때만 진료 상담 문구를 한 문장으로 쓰고, 없으면 빈 문자열로 둔다.")
        appendLine("오늘: ${java.time.LocalDate.now()}")
        state.activeGoal?.let { goal ->
            appendLine("목표: 시작 ${goal.startWeightKg}kg, 목표 ${goal.targetWeightKg}kg, 시작일 ${java.time.LocalDate.ofEpochDay(goal.startedOnEpochDay)}")
        } ?: appendLine("목표: 설정하지 않음")
        appendLine("최근 체중 기록(최신순):")
        weights.forEach { weight ->
            appendLine("- ${weight.localDate()} ${weight.weightKg}kg, 체지방=${weight.bodyFatPercent ?: "미기록"}, 컨디션=${weight.condition ?: "미기록"}, 메모=${weight.note ?: "없음"}")
        }
        appendLine("최근 마운자로 주사 기록(최신순):")
        state.mounjaroInjections.sortedByDescending { it.injectedAt }.take(MAX_INJECTION_RECORDS).forEach { injection ->
            appendLine("- ${injection.localDate()} ${injection.doseMg}mg, 부작용=${injection.sideEffects.ifBlank { "미기록" }}, 메모=${injection.note ?: "없음"}")
        }
        if (state.mounjaroInjections.isEmpty()) appendLine("- 기록 없음")
        appendLine("최근 식단 기록(최신순):")
        state.meals.sortedByDescending { it.meal.eatenAt }.take(MAX_MEAL_RECORDS).forEach { meal ->
            val items = meal.items.joinToString(", ") { item -> "${item.name}${item.amount?.let { amount -> "($amount)" }.orEmpty()}" }
            appendLine("- ${meal.meal.localDate()} ${MealType.from(meal.meal.mealType).label}, 음식=${items.ifBlank { "미기록" }}, 태그=${meal.meal.tags.ifBlank { "없음" }}, 메모=${meal.meal.note ?: "없음"}")
        }
        if (state.meals.isEmpty()) appendLine("- 기록 없음")
        appendLine("반드시 유효한 JSON 객체만 반환하고, 키는 headline, trendSummary, encouragement, nextSteps, safetyNote만 사용한다.")
    }

    private fun parse(response: String): BodyLogAiAnalysis {
        val json = JSONObject(response)
        return BodyLogAiAnalysis(
            headline = json.optString("headline").ifBlank { "현재 기록을 함께 살펴봤어요" },
            trendSummary = json.optString("trendSummary"),
            encouragement = json.optString("encouragement"),
            nextSteps = json.optJSONArray("nextSteps").toStringList(),
            safetyNote = json.optString("safetyNote").ifBlank { DEFAULT_SAFETY_NOTE },
        )
    }

    private companion object {
        const val MINIMUM_WEIGHT_RECORDS = 2
        const val MAX_WEIGHT_RECORDS = 90
        const val MAX_INJECTION_RECORDS = 12
        const val MAX_MEAL_RECORDS = 60
        const val DEFAULT_SAFETY_NOTE = "이 기능은 기록 해석을 돕는 용도이며 의료 진단이나 처방 조언이 아닙니다."
    }
}

private fun org.json.JSONArray?.toStringList(): List<String> = buildList {
    val source = this@toStringList ?: return@buildList
    for (index in 0 until source.length()) {
        source.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
    }
}
