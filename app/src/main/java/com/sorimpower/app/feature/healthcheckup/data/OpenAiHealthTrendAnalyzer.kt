package com.sorimpower.app.feature.healthcheckup.data

import android.content.Context
import com.sorimpower.app.core.ai.AiModelRouter
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import com.sorimpower.app.feature.healthcheckup.domain.LongTermHealthAnalysis
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal class OpenAiHealthTrendAnalyzer(private val context: Context) {
    suspend fun analyze(checkups: List<HealthCheckupWithMetrics>): LongTermHealthAnalysis {
        require(checkups.size >= 2) { "건강 추이를 분석하려면 서로 다른 건강검진 기록이 2개 이상 필요해요." }
        val response = AiModelRouter(context).generate(
            AiRequest(AiTaskType.HEALTH_TREND_ANALYSIS, createPrompt(checkups)),
        )
        return parseLongTermAnalysisJson(response.text)
    }

    private fun createPrompt(checkups: List<HealthCheckupWithMetrics>): String = buildString {
        appendLine("당신은 여러 해의 건강검진에서 검수 완료된 검사값을 비교하는 임상의 관점의 의료 기록 분석 도우미다.")
        appendLine("정보를 나열하지 말고 임상적으로 중요한 변화만 우선순위로 정리하라. 정상·변화 없는 항목은 핵심이 아니면 생략한다.")
        appendLine("summary는 현재 상태와 가장 중요한 의미를 2문장 이내로 쓴다. attentionChanges는 최대 3개이며 각 문장은 '항목: 변화 → 의학적 의미' 형식으로 쓴다.")
        appendLine("recommendations는 최대 3개이며 '다음 행동'만 쓴다. 필요하면 어떤 진료과 또는 어떤 재검을 의료진과 상의할지 구체적으로 적는다.")
        appendLine("확정 진단, 처방, 약물 변경 지시는 하지 말고, 수치가 한 번뿐이거나 비교가 불가하면 그 한계를 명시한다. 수치에 없는 원인·생활습관을 추측하지 마라.")
        appendLine("반드시 JSON 객체만 반환하고 최상위 키는 summary, positiveChanges, attentionChanges, stableAreas, recommendations, missingInformation, medicalConsultationSuggested로 고정하라.")
        appendLine("검수 완료된 검진 데이터(날짜순):")
        checkups.sortedBy { it.checkup.checkupDateEpochDay }.forEach { checkup ->
            appendLine("\n[${LocalDate.ofEpochDay(checkup.checkup.checkupDateEpochDay)} ${checkup.checkup.title.ifBlank { "건강검진" }}]")
            checkup.metrics.sortedBy { it.sortOrder }.forEach { metric ->
                appendLine("- category=${metric.category}, name=${metric.normalizedName.ifBlank { metric.name }}, value=${metric.value ?: ""}, text=${metric.stringValue}, unit=${metric.unit}, ref=${metric.referenceText}, status=${metric.status}")
            }
        }
    }

    companion object {
        const val MODEL_NAME = "gpt-5.6-luna"
    }
}

internal fun parseLongTermAnalysis(entity: HealthAiAnalysisEntity): LongTermHealthAnalysis = parseLongTermAnalysisJson(entity.resultJson, entity.createdAt)

internal fun parseLongTermAnalysisJson(jsonText: String, analyzedAt: Long = System.currentTimeMillis()): LongTermHealthAnalysis {
    val json = JSONObject(jsonText)
    return LongTermHealthAnalysis(
        summary = json.optString("summary").ifBlank { "등록된 건강검진 기록을 종합했어요." },
        positiveChanges = json.optJSONArray("positiveChanges").stringList(),
        attentionChanges = json.optJSONArray("attentionChanges").stringList(),
        stableAreas = json.optJSONArray("stableAreas").stringList(),
        recommendations = json.optJSONArray("recommendations").stringList(),
        missingInformation = json.optJSONArray("missingInformation").stringList(),
        medicalConsultationSuggested = json.optBoolean("medicalConsultationSuggested", false),
        analyzedAt = analyzedAt,
    )
}

internal fun LongTermHealthAnalysis.toJson(): String = JSONObject().apply {
    put("summary", summary)
    put("positiveChanges", JSONArray(positiveChanges))
    put("attentionChanges", JSONArray(attentionChanges))
    put("stableAreas", JSONArray(stableAreas))
    put("recommendations", JSONArray(recommendations))
    put("missingInformation", JSONArray(missingInformation))
    put("medicalConsultationSuggested", medicalConsultationSuggested)
}.toString()

private fun JSONArray?.stringList(): List<String> = buildList {
    val array = this@stringList ?: return@buildList
    for (index in 0 until array.length()) array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
}
