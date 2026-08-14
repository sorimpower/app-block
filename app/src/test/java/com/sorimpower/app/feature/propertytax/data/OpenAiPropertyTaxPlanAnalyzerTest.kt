package com.sorimpower.app.feature.propertytax.data

import com.sorimpower.app.feature.propertytax.domain.TaxLawVerificationStatus
import com.sorimpower.app.feature.propertytax.domain.TaxOfficialSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiPropertyTaxPlanAnalyzerTest {
    @Test
    fun `자연어 계획 분석 JSON을 타임라인과 시나리오로 파싱한다`() {
        val raw = """
            {
              "verificationStatus":"CURRENT",
              "summary":"검단을 먼저 처분하는 대안을 우선 검토합니다.",
              "recommendedScenario":"검단 일반과세 후 청량리 비과세 검토",
              "timeline":[{"date":"2020-01","title":"청량리 취득","detail":"본인 명의","status":"FACT"}],
              "scenarios":[{
                "name":"검단 우선 매도",
                "verdict":"권장",
                "saleOrder":["1. 검단 매도","2. 청량리 매도"],
                "taxTreatment":["검단: 일반과세 가능성"],
                "advantages":["청량리 매도 시 1주택 상태"],
                "risks":["미래 세법 재확인"],
                "deadlines":["매도 직전 법령 확인"]
              }],
              "keyFindings":["부부는 세대 기준 확인"],
              "assumptions":["현재 법령 기준"],
              "missingInformation":["검단 실제 잔금일"],
              "nextActions":["분양계약서 확인"]
            }
        """.trimIndent()

        val analyzer = OpenAiPropertyTaxPlanAnalyzer(null)
        val result = analyzer.parse(raw, listOf(TaxOfficialSource("법령", "https://law.go.kr/test")), 1L)

        assertEquals(TaxLawVerificationStatus.CURRENT, result.verificationStatus)
        assertEquals("청량리 취득", result.timeline.single().title)
        assertEquals("권장", result.scenarios.single().verdict)
        assertTrue(result.missingInformation.contains("검단 실제 잔금일"))
    }
}
