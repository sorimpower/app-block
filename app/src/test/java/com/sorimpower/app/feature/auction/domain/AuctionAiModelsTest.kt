package com.sorimpower.app.feature.auction.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuctionAiModelsTest {
    @Test
    fun `법원 사건상세와 문서 3종만 있으면 예비 분석으로 제한한다`() {
        val evidence = bundle(
            AuctionDocumentType.CASE_DETAIL,
            AuctionDocumentType.SALE_SPECIFICATION,
            AuctionDocumentType.OCCUPANCY_REPORT,
            AuctionDocumentType.APPRAISAL_REPORT,
        )

        val result = validateAuctionAiAnalysis(completedAnalysis(), evidence)

        assertEquals(AuctionAnalysisStatus.PRELIMINARY, result.status)
        assertTrue(AuctionDocumentType.REGISTRY_RECORD in result.missingDocumentTypes)
        assertFalse(result.isNotificationEligible)
    }

    @Test
    fun `등기까지 있을 때만 완료 분석과 추천 알림을 허용한다`() {
        val evidence = bundle(*AuctionDocumentType.entries.toTypedArray())

        val result = validateAuctionAiAnalysis(completedAnalysis(), evidence)

        assertEquals(AuctionAnalysisStatus.COMPLETE, result.status)
        assertTrue(result.missingDocumentTypes.isEmpty())
        assertTrue(result.isNotificationEligible)
    }

    @Test
    fun `핵심 법원 문서가 없으면 분석 점수를 알림에 사용하지 않는다`() {
        val evidence = bundle(AuctionDocumentType.APPRAISAL_REPORT)

        val result = validateAuctionAiAnalysis(completedAnalysis(), evidence)

        assertEquals(AuctionAnalysisStatus.WAITING_FOR_DOCUMENTS, result.status)
        assertEquals(AuctionRiskLevel.UNKNOWN, result.riskLevel)
        assertFalse(result.isNotificationEligible)
    }

    @Test
    fun `등기 원문이 없어도 핵심 공개문서와 낮은 위험이면 예비 추천을 허용한다`() {
        val analysis = completedAnalysis().copy(
            status = AuctionAnalysisStatus.PRELIMINARY,
            evidenceTypes = setOf(
                AuctionDocumentType.CASE_DETAIL,
                AuctionDocumentType.SALE_SPECIFICATION,
                AuctionDocumentType.OCCUPANCY_REPORT,
            ),
            missingDocumentTypes = setOf(AuctionDocumentType.REGISTRY_RECORD),
        )

        assertTrue(analysis.isPreliminaryRecommendationEligible(AuctionAiCriteria()))
    }

    @Test
    fun `매각명세서가 없거나 허용 위험도를 넘으면 예비 추천하지 않는다`() {
        val missingSpecification = completedAnalysis().copy(
            status = AuctionAnalysisStatus.PRELIMINARY,
            evidenceTypes = setOf(AuctionDocumentType.CASE_DETAIL, AuctionDocumentType.OCCUPANCY_REPORT),
        )
        val highRisk = completedAnalysis().copy(
            status = AuctionAnalysisStatus.PRELIMINARY,
            riskLevel = AuctionRiskLevel.HIGH,
            evidenceTypes = setOf(
                AuctionDocumentType.CASE_DETAIL,
                AuctionDocumentType.SALE_SPECIFICATION,
                AuctionDocumentType.OCCUPANCY_REPORT,
            ),
        )

        assertFalse(missingSpecification.isPreliminaryRecommendationEligible(AuctionAiCriteria()))
        assertFalse(highRisk.isPreliminaryRecommendationEligible(AuctionAiCriteria(maximumRiskLevel = AuctionRiskLevel.MEDIUM)))
    }

    private fun bundle(vararg types: AuctionDocumentType) = AuctionEvidenceBundle(
        itemKey = "item",
        documents = types.map { AuctionEvidenceDocument(it, "근거 내용") },
    )

    private fun completedAnalysis() = AuctionAiAnalysis(
        itemKey = "item",
        status = AuctionAnalysisStatus.COMPLETE,
        riskLevel = AuctionRiskLevel.LOW,
        suitabilityScore = 88,
    )
}
