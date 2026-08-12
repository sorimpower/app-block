package com.sorimpower.app.feature.propertytax.data

import android.content.Context
import com.sorimpower.app.core.ai.AiModelId
import com.sorimpower.app.core.ai.AiModelRouter
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import com.sorimpower.app.feature.propertytax.domain.PropertyTaxPlanAnalysis
import com.sorimpower.app.feature.propertytax.domain.TaxLawVerificationStatus
import com.sorimpower.app.feature.propertytax.domain.TaxOfficialSource
import com.sorimpower.app.feature.propertytax.domain.TaxPlanScenario
import com.sorimpower.app.feature.propertytax.domain.TaxPlanTimelineItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal class OpenAiPropertyTaxPlanAnalyzer(context: Context?) {
    private val router = context?.let(::AiModelRouter)

    suspend fun analyze(input: String, previous: PropertyTaxPlanAnalysis?): PropertyTaxPlanAnalysis {
        val previousContext = previous?.let(::toJson)?.toString() ?: "이전 분석 없음"
        val prompt = """
            오늘은 ${LocalDate.now()}이다. 당신은 대한민국 부동산 매도계획 시뮬레이터다.
            사용자가 자유롭게 작성한 보유 자산, 분양권·조합원입주권, 재개발 이력, 명의, 거주와 매도계획을 날짜순 사실관계로 먼저 구조화하라.

            반드시 웹 검색 도구로 국가법령정보센터·국세청·기획재정부·행정안전부·위택스·국토교통부의 공식 최신 자료를 확인하라.
            소득세법 제89조·제95조·제104조, 소득세법 시행령 제154조·제155조·제156조의2·제156조의3·제159조의4·제162조와 관련 부칙·경과규정을 우선 확인하라.
            취득세 쟁점이 있으면 지방세법 제11조·제13조의2와 시행령 제28조의4도 확인하라.

            원칙:
            1. 부부 명의가 달라도 같은 세대의 주택·분양권 수 판정 가능성을 누락하지 마라.
            2. 분양권 취득일, 신축주택의 실제 취득일, 종전주택 양도기한, 세대전원 이사·거주 요건을 구분하라.
            3. 재개발 기존주택의 취득일·관리처분인가일·멸실일·준공일을 별도 사건으로 구분하라.
            4. 미래 매도일에는 현재 법령 기준의 조건부 전망이라고 명시하고 미래 법령을 확정하지 마라.
            5. 금액 정보가 없으면 세액을 만들지 말고 일반과세/비과세 가능성과 매도 순서만 비교하라.
            6. 비과세를 확정하지 말고 가능성, 충족 요건, 실패 조건과 증빙을 함께 써라.
            7. 사용자의 계획이 상충하면 원안과 수정안을 별도 시나리오로 만들어 비교하라.
            8. 추가 질문은 결론을 바꿀 가능성이 큰 최소 정보만 최대 6개로 제한하라.
            9. 이전 분석은 정답이 아니라 비교 자료다. 공식 법령과 새 입력에 비추어 틀린 내용은 이어받지 마라.

            JSON 객체만 반환하라:
            {
              "verificationStatus":"CURRENT|CHANGE_DETECTED|INCONCLUSIVE",
              "summary":"핵심 결론 3~5문장",
              "recommendedScenario":"가장 현실적인 시나리오 이름과 이유",
              "timeline":[{"date":"YYYY-MM 또는 기간/미정", "title":"", "detail":"", "status":"FACT|PLAN|DEADLINE|UNCERTAIN"}],
              "scenarios":[{
                "name":"",
                "verdict":"권장|조건부 가능|위험|비권장",
                "saleOrder":["1. ..."],
                "taxTreatment":["자산: 비과세 가능성/일반과세와 이유"],
                "advantages":[""],
                "risks":[""],
                "deadlines":[""]
              }],
              "keyFindings":[""],
              "assumptions":["현재 법령 기준의 가정"],
              "missingInformation":["결론을 바꿀 수 있는 최소 추가 질문"],
              "nextActions":["확인할 서류나 실행 순서"]
            }

            <user_situation_and_plan>
            ${input.trim()}
            </user_situation_and_plan>

            <previous_analysis_for_comparison>
            $previousContext
            </previous_analysis_for_comparison>
        """.trimIndent()
        val response = requireNotNull(router) { "AI 분석 실행 환경이 없습니다." }.generate(
            AiRequest(AiTaskType.PROPERTY_TAX_SCENARIO_COMPARISON, prompt, jsonOutput = true, reasoningEffort = "max"),
            AiModelId.OPENAI_DEEP,
        )
        return parse(
            response.text,
            response.sources.map { TaxOfficialSource(it.title, it.url) },
            response.checkedAt,
        )
    }

    fun parse(raw: String, sources: List<TaxOfficialSource> = emptyList(), checkedAt: Long? = null): PropertyTaxPlanAnalysis {
        val json = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        fun strings(parent: JSONObject, name: String): List<String> = parent.optJSONArray(name).strings()
        val timeline = json.optJSONArray("timeline")?.objects()?.map { item ->
            TaxPlanTimelineItem(
                date = item.optString("date", "미정"),
                title = item.optString("title", "일정"),
                detail = item.optString("detail"),
                status = item.optString("status", "UNCERTAIN"),
            )
        } ?: emptyList()
        val scenarios = json.optJSONArray("scenarios")?.objects()?.map { item ->
            TaxPlanScenario(
                name = item.optString("name", "대안"),
                verdict = item.optString("verdict", "조건부 가능"),
                saleOrder = strings(item, "saleOrder"),
                taxTreatment = strings(item, "taxTreatment"),
                advantages = strings(item, "advantages"),
                risks = strings(item, "risks"),
                deadlines = strings(item, "deadlines"),
            )
        } ?: emptyList()
        val parsedStatus = runCatching { TaxLawVerificationStatus.valueOf(json.optString("verificationStatus")) }
            .getOrDefault(TaxLawVerificationStatus.INCONCLUSIVE)
        return PropertyTaxPlanAnalysis(
            summary = json.optString("summary", "분석 결과를 요약하지 못했습니다."),
            recommendedScenario = json.optString("recommendedScenario"),
            timeline = timeline,
            scenarios = scenarios,
            keyFindings = strings(json, "keyFindings"),
            assumptions = strings(json, "assumptions"),
            missingInformation = strings(json, "missingInformation").take(6),
            nextActions = strings(json, "nextActions"),
            officialSources = sources.distinctBy(TaxOfficialSource::url),
            verificationStatus = if (sources.isEmpty()) TaxLawVerificationStatus.INCONCLUSIVE else parsedStatus,
            checkedAt = checkedAt,
        )
    }

    fun toJson(value: PropertyTaxPlanAnalysis): JSONObject = JSONObject()
        .put("verificationStatus", value.verificationStatus.name)
        .put("summary", value.summary)
        .put("recommendedScenario", value.recommendedScenario)
        .put("timeline", JSONArray(value.timeline.map { JSONObject().put("date", it.date).put("title", it.title).put("detail", it.detail).put("status", it.status) }))
        .put("scenarios", JSONArray(value.scenarios.map { scenario -> JSONObject()
            .put("name", scenario.name).put("verdict", scenario.verdict)
            .put("saleOrder", JSONArray(scenario.saleOrder)).put("taxTreatment", JSONArray(scenario.taxTreatment))
            .put("advantages", JSONArray(scenario.advantages)).put("risks", JSONArray(scenario.risks))
            .put("deadlines", JSONArray(scenario.deadlines))
        }))
        .put("keyFindings", JSONArray(value.keyFindings))
        .put("assumptions", JSONArray(value.assumptions))
        .put("missingInformation", JSONArray(value.missingInformation))
        .put("nextActions", JSONArray(value.nextActions))
        .put("officialSources", JSONArray(value.officialSources.map { JSONObject().put("title", it.title).put("url", it.url) }))
        .put("checkedAt", value.checkedAt)

    fun parseStored(raw: String): PropertyTaxPlanAnalysis {
        val json = JSONObject(raw)
        val sources = json.optJSONArray("officialSources")?.objects()?.mapNotNull {
            val url = it.optString("url").takeIf(String::isNotBlank) ?: return@mapNotNull null
            TaxOfficialSource(it.optString("title", url), url)
        } ?: emptyList()
        return parse(json.toString(), sources, json.optLong("checkedAt").takeIf { it > 0L })
    }
}

private fun JSONArray?.strings(): List<String> = this?.let { array ->
    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
} ?: emptyList()

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull(::optJSONObject)
