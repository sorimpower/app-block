package com.sorimpower.app.feature.auction.domain

enum class AuctionDocumentType(val label: String) {
    CASE_DETAIL("법원 사건상세"),
    SALE_SPECIFICATION("매각물건명세서"),
    OCCUPANCY_REPORT("현황조사서"),
    APPRAISAL_REPORT("감정평가서"),
    REGISTRY_RECORD("등기사항전부증명서"),
}

data class AuctionEvidenceDocument(
    val type: AuctionDocumentType,
    val text: String,
    val sourceId: String = "",
    val collectedAt: Long = System.currentTimeMillis(),
)

data class AuctionEvidenceBundle(
    val itemKey: String,
    val documents: List<AuctionEvidenceDocument>,
) {
    val availableTypes: Set<AuctionDocumentType> get() = documents
        .filter { it.text.isNotBlank() }
        .mapTo(linkedSetOf(), AuctionEvidenceDocument::type)

    val missingCourtDocuments: Set<AuctionDocumentType> get() = REQUIRED_COURT_DOCUMENTS - availableTypes
    val hasCourtEvidence: Boolean get() = AuctionDocumentType.CASE_DETAIL in availableTypes
    val hasRegistryRecord: Boolean get() = AuctionDocumentType.REGISTRY_RECORD in availableTypes

    companion object {
        val REQUIRED_COURT_DOCUMENTS = setOf(
            AuctionDocumentType.CASE_DETAIL,
            AuctionDocumentType.SALE_SPECIFICATION,
            AuctionDocumentType.OCCUPANCY_REPORT,
            AuctionDocumentType.APPRAISAL_REPORT,
        )
    }
}

enum class AuctionAnalysisStatus {
    WAITING_FOR_DOCUMENTS,
    ANALYZING,
    PRELIMINARY,
    COMPLETE,
    FAILED,
}

enum class AuctionRiskLevel {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

data class AuctionAiAnalysis(
    val itemKey: String,
    val status: AuctionAnalysisStatus,
    val riskLevel: AuctionRiskLevel = AuctionRiskLevel.UNKNOWN,
    val suitabilityScore: Int = 0,
    val headline: String = "",
    val summary: String = "",
    val riskItems: List<String> = emptyList(),
    val requiredChecks: List<String> = emptyList(),
    val evidenceTypes: Set<AuctionDocumentType> = emptySet(),
    val missingDocumentTypes: Set<AuctionDocumentType> = emptySet(),
    val analyzedAt: Long = 0L,
    val modelName: String = "",
    val promptVersion: String = "",
) {
    val isNotificationEligible: Boolean get() =
        status == AuctionAnalysisStatus.COMPLETE &&
            riskLevel in setOf(AuctionRiskLevel.LOW, AuctionRiskLevel.MEDIUM) &&
            suitabilityScore >= 70 &&
            missingDocumentTypes.isEmpty()
}

data class AuctionAiCriteria(
    val minimumBidPrice: Long? = null,
    val maxBidPrice: Long? = null,
    val minimumSuitabilityScore: Int = 50,
    val preferredDistricts: Set<String> = emptySet(),
    val minimumDiscountRate: Double? = null,
    val maximumRiskLevel: AuctionRiskLevel = AuctionRiskLevel.MEDIUM,
    val allowOccupiedProperty: Boolean = false,
    val extraRequest: String = "",
)

data class AuctionAiPreferences(
    val dailyRecommendationEnabled: Boolean = false,
    val minimumBidPrice: Long? = 2_000_000_000L,
    val maxBidPrice: Long? = 3_000_000_000L,
    val minimumSuitabilityScore: Int = 50,
    val preferredDistricts: Set<String> = emptySet(),
    val minimumDiscountRate: Double? = null,
    val maximumRiskLevel: AuctionRiskLevel = AuctionRiskLevel.MEDIUM,
    val allowOccupiedProperty: Boolean = false,
    val extraRequest: String = "",
    val notificationHour: Int = 8,
) {
    fun toCriteria() = AuctionAiCriteria(
        minimumBidPrice = minimumBidPrice,
        maxBidPrice = maxBidPrice,
        minimumSuitabilityScore = minimumSuitabilityScore.coerceIn(0, 100),
        preferredDistricts = preferredDistricts,
        minimumDiscountRate = minimumDiscountRate,
        maximumRiskLevel = maximumRiskLevel,
        allowOccupiedProperty = allowOccupiedProperty,
        extraRequest = extraRequest,
    )
}

fun AuctionItem.matchesAiPreferences(preferences: AuctionAiPreferences): Boolean {
    if (preferences.minimumBidPrice != null && (minimumPrice <= 0L || minimumPrice < preferences.minimumBidPrice)) return false
    if (preferences.maxBidPrice != null && (minimumPrice <= 0L || minimumPrice > preferences.maxBidPrice)) return false
    if (preferences.preferredDistricts.isNotEmpty() &&
        sigungu !in preferences.preferredDistricts &&
        preferences.preferredDistricts.none { district -> address.contains(district) }
    ) return false
    val discountRate = auctionDiscountRate()
    if (preferences.minimumDiscountRate != null && discountRate < preferences.minimumDiscountRate) return false
    return isInProgress
}

fun AuctionItem.auctionDiscountRate(): Double =
    if (minimumPriceRate in 0.01..100.0) (100.0 - minimumPriceRate).coerceAtLeast(0.0) else 0.0

fun AuctionAiAnalysis.isPreliminaryRecommendationEligible(criteria: AuctionAiCriteria): Boolean {
    if (status !in setOf(AuctionAnalysisStatus.PRELIMINARY, AuctionAnalysisStatus.COMPLETE)) return false
    if (suitabilityScore < criteria.minimumSuitabilityScore) return false
    val allowedRisks = when (criteria.maximumRiskLevel) {
        AuctionRiskLevel.LOW -> setOf(AuctionRiskLevel.LOW)
        AuctionRiskLevel.MEDIUM -> setOf(AuctionRiskLevel.LOW, AuctionRiskLevel.MEDIUM)
        AuctionRiskLevel.HIGH -> setOf(AuctionRiskLevel.LOW, AuctionRiskLevel.MEDIUM, AuctionRiskLevel.HIGH)
        AuctionRiskLevel.CRITICAL -> setOf(AuctionRiskLevel.LOW, AuctionRiskLevel.MEDIUM, AuctionRiskLevel.HIGH, AuctionRiskLevel.CRITICAL)
        AuctionRiskLevel.UNKNOWN -> emptySet()
    }
    if (riskLevel !in allowedRisks) return false
    // 추천 후보를 문서 종류 개수로 제한하지 않는다. 사건상세를 확보하지 못한
    // 경우는 이미 WAITING_FOR_DOCUMENTS 상태라 위 상태 검사에서 제외된다.
    return true
}

fun validateAuctionAiAnalysis(
    analysis: AuctionAiAnalysis,
    evidence: AuctionEvidenceBundle,
): AuctionAiAnalysis {
    val score = analysis.suitabilityScore.coerceIn(0, 100)
    val missing = AuctionDocumentType.entries.toSet() - evidence.availableTypes
    val status = when {
        !evidence.hasCourtEvidence -> AuctionAnalysisStatus.WAITING_FOR_DOCUMENTS
        !evidence.hasRegistryRecord -> AuctionAnalysisStatus.PRELIMINARY
        analysis.status == AuctionAnalysisStatus.FAILED -> AuctionAnalysisStatus.FAILED
        else -> AuctionAnalysisStatus.COMPLETE
    }
    return analysis.copy(
        status = status,
        riskLevel = if (status == AuctionAnalysisStatus.WAITING_FOR_DOCUMENTS) AuctionRiskLevel.UNKNOWN else analysis.riskLevel,
        suitabilityScore = score,
        requiredChecks = (analysis.requiredChecks + missing.map { "${it.label} 확인" }).distinct(),
        evidenceTypes = evidence.availableTypes,
        missingDocumentTypes = missing,
    )
}
