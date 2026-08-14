package com.sorimpower.app.feature.bodylog.data

import android.content.Context
import com.sorimpower.app.core.ai.AiModelId
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
            AiRequest(
                taskType = AiTaskType.BODY_LOG_PROGRESS_ANALYSIS,
                userPrompt = createPrompt(state, weights),
                reasoningEffort = "high",
            ),
            model = AiModelId.OPENAI_SMART,
        )
        return parse(response.text)
    }

    private fun createPrompt(state: BodyLogState, weights: List<WeightEntryEntity>): String = buildString {
        appendLine("당신은 다이어트 전문 의료진의 관점에서 마운자로 주사·식습관·체중 추이를 함께 검토하는 기록 분석기다.")
        appendLine("진단·처방·용량 변경 지시는 하지 말고, 담백하고 전문적인 한국어 존댓말로 쓴다. 감정적 위로나 장황한 설명은 금지한다.")
        appendLine("단순 기록 나열은 금지한다. 날짜·수치가 있는 경우 반드시 근거로 들고, 체중 변화와 식사 패턴·마운자로 투여 시점/간격/부작용의 관계를 해석한다. 인과관계를 단정할 근거가 부족하면 가능성으로 표현한다.")
        appendLine("headline은 가장 중요한 임상적 해석을 한 문장 45자 이내로 쓴다. trendSummary는 체중 추이·식습관·주사 기록을 연결해 2~3문장(260자 이내)으로 작성한다. encouragement는 정서적 위로 대신 정체·감량 속도·섭취 패턴 중 핵심 원인과 해석을 최대 2문장(200자 이내)으로 쓴다.")
        appendLine("mealAssessment에는 식사를 제대로 하고 있는지 별도로 평가한다. 기록된 식사를 평소 섭취의 대표 표본으로 보고, 일반적인 1인분·식사 구성 기준을 보수적으로 적용해 식사 규칙성, 단백질 식품·채소/식이섬유 포함 여부, 당류·음주·야식·고열량 식사 빈도, 마운자로 사용 중 식사량 과소 또는 끼니 결손 가능성을 검토한다. 좋음/보완 필요/판단 어려움 중 하나를 먼저 명시하고 근거를 2문장 이내(180자 이내)로 쓴다.")
        appendLine("calorieAssessment에는 아래 일별 AI 추정 섭취 칼로리를 별도로 해석한다. 최근 평균, 목표 감량 칼로리 대비 과다·과소, 날짜별 변동 폭을 평가한다. AI 추정치임을 전제로 단정하지 말고, 최소 섭취 기준보다 반복적으로 낮거나 감량 목표보다 지속적으로 높은 패턴만 핵심으로 짚는다. 좋음/보완 필요/판단 어려움 중 하나를 먼저 명시하고 2문장 이내(180자 이내)로 쓴다.")
        appendLine("정확한 섭취량·영양성분이 기록되지 않아도 분석을 중단하거나 기록 보완을 요청하지 않는다. 기록된 음식명·횟수·메모에서 합리적으로 추정해 실용적인 판단을 제시하되, 수치화한 칼로리·영양소를 확정값처럼 말하지 않는다. 마운자로는 최근 투여일·용량·간격·부작용 기록만 근거로 한다.")
        appendLine("nextSteps는 추가 기록 요청보다 현재 식사 습관에서 바로 조정할 수 있는 실천을 우선해 최대 3개, 항목당 55자 이내로 작성한다.")
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
        val currentWeight = state.latestWeight?.weightKg
        if (currentWeight != null) {
            val age = java.time.LocalDate.now().year - 1989
            val basal = 10.0 * currentWeight + 6.25 * 171.0 - 5.0 * age + 5.0
            val maintenance = ((basal * 1.35) / 10.0).toInt() * 10
            val diet = if (state.activeGoal?.targetWeightKg?.let { currentWeight > it } == true) (maintenance - 500).coerceAtLeast(1_500) else maintenance
            appendLine("칼로리 해석 기준(1989년생·171cm 남성, 가벼운 활동 가정): 최소 1,500kcal, 감량 목표 ${diet}kcal, 유지 ${maintenance}kcal")
        }
        appendLine("최근 일별 AI 추정 섭취 칼로리(최신순):")
        state.dailyCalories.sortedByDescending { it.dateEpochDay }.take(MAX_CALORIE_SUMMARIES).forEach { summary ->
            appendLine("- ${java.time.LocalDate.ofEpochDay(summary.dateEpochDay)} ${summary.estimatedCalories}kcal, 식사 ${summary.mealCount}개, 요약=${summary.summary}")
        }
        if (state.dailyCalories.isEmpty()) appendLine("- 기록 없음")
        appendLine("반드시 유효한 JSON 객체만 반환하고, 키는 headline, trendSummary, encouragement, mealAssessment, calorieAssessment, nextSteps, safetyNote만 사용한다.")
    }

    private fun parse(response: String): BodyLogAiAnalysis {
        val json = JSONObject(response)
        return BodyLogAiAnalysis(
            headline = json.optString("headline").ifBlank { "현재 기록을 함께 살펴봤어요" },
            trendSummary = json.optString("trendSummary"),
            encouragement = json.optString("encouragement"),
            mealAssessment = json.optString("mealAssessment"),
            calorieAssessment = json.optString("calorieAssessment"),
            nextSteps = json.optJSONArray("nextSteps").toStringList(),
            safetyNote = json.optString("safetyNote").ifBlank { DEFAULT_SAFETY_NOTE },
        )
    }

    private companion object {
        const val MINIMUM_WEIGHT_RECORDS = 2
        const val MAX_WEIGHT_RECORDS = 90
        const val MAX_INJECTION_RECORDS = 12
        const val MAX_MEAL_RECORDS = 60
        const val MAX_CALORIE_SUMMARIES = 30
        const val DEFAULT_SAFETY_NOTE = "이 기능은 기록 해석을 돕는 용도이며 의료 진단이나 처방 조언이 아닙니다."
    }
}

private fun org.json.JSONArray?.toStringList(): List<String> = buildList {
    val source = this@toStringList ?: return@buildList
    for (index in 0 until source.length()) {
        source.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
    }
}
