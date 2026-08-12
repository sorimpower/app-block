package com.sorimpower.app.feature.propertytax.domain

import java.time.LocalDate

enum class PropertyType(val label: String) {
    APARTMENT("아파트"), HOUSE("주택"), PRESALE_RIGHT("분양권"), ASSOCIATION_RIGHT("입주권"),
    OFFICETEL("오피스텔"), COMMERCIAL("상가"), LAND("토지"), OTHER("기타")
}

enum class PropertyStatus { OWNED, SOLD }
enum class CalculationConfidence(val label: String) { HIGH("높음"), MEDIUM("보통"), NEEDS_REVIEW("확인 필요") }
enum class JointHoldingTaxMethod(val label: String) { AUTO_MIN("유리한 방식 자동 비교"), SEPARATE("공동명의 개별 과세"), ONE_HOME_SPECIAL("공동명의 1주택 특례") }
enum class OwnerRole(val label: String) { OWNER("본인"), SPOUSE("배우자") }
enum class TaxTreatment(val label: String) { AUTO("일반 기준 자동"), INCLUDED("포함"), EXCLUDED("제외") }
enum class AcquisitionSurchargeRelief(val label: String) { NONE("해당 없음"), TEMPORARY_TWO_HOME("일시적 2주택") }

data class PropertyAsset(
    val id: String,
    val name: String,
    val propertyType: PropertyType,
    val address: String,
    val acquisitionDate: LocalDate,
    val acquisitionPrice: Long,
    val ownershipRatio: Double,
    val officialAssessedValue: Long?,
    val currentEstimatedValue: Long?,
    val actualAcquisitionTax: Long?,
    val brokerageFee: Long,
    val legalFee: Long,
    val renovationCost: Long,
    val otherNecessaryExpenses: Long,
    val residenceStartDate: LocalDate?,
    val residenceEndDate: LocalDate?,
    val status: PropertyStatus,
    val spouseOwnershipRatio: Double = 0.0,
    val regulatedAreaAtAcquisition: Boolean? = null,
    val expectedCompletionDate: LocalDate? = null,
    val ownerBirthYear: Int? = null,
    val spouseBirthYear: Int? = null,
    val ownerBirthDate: LocalDate? = null,
    val spouseBirthDate: LocalDate? = null,
    val acquisitionContractDate: LocalDate? = null,
    val urbanAreaTaxApplicable: Boolean? = null,
    val annualRegionalResourceTax: Long? = null,
    val acquisitionRuralSpecialTax: Long? = null,
    val acquisitionHouseCountTreatment: TaxTreatment = TaxTreatment.AUTO,
    val capitalGainsHouseCountTreatment: TaxTreatment = TaxTreatment.AUTO,
    val comprehensiveTaxTreatment: TaxTreatment = TaxTreatment.AUTO,
    val capitalGainsSurchargeTreatment: TaxTreatment = TaxTreatment.AUTO,
    val acquisitionSurchargeRelief: AcquisitionSurchargeRelief = AcquisitionSurchargeRelief.NONE,
    val previousHomeDispositionDate: LocalDate? = null,
    val residenceRequirementExempt: Boolean = false,
    val jointComprehensiveTaxSpecialRequested: Boolean = false,
    val jointSpecialTaxpayer: OwnerRole? = null,
    val redevelopmentHistory: Boolean = false,
    val managementDispositionApprovalDate: LocalDate? = null,
    val demolitionDate: LocalDate? = null,
    val redevelopmentCompletionDate: LocalDate? = null,
    val additionalContribution: Long = 0,
    val settlementRefund: Long = 0,
    val redevelopmentNecessaryExpenses: Long = 0,
)

data class TaxRuleVersion(
    val id: String,
    val name: String,
    val effectiveFrom: LocalDate,
    val sourceUpdatedAt: LocalDate,
    val sources: List<TaxSourceReference>,
    val effectiveUntil: LocalDate? = null,
)

data class TaxSourceReference(val title: String, val authority: String, val url: String, val ruleIds: List<String>)
data class AppliedTaxRule(val ruleId: String, val applied: Boolean, val reason: String, val sourceUrl: String)
data class CalculationTrace(val label: String, val amount: Long, val operation: String = "")

data class TaxCalculation<T>(
    val result: T,
    val rules: List<AppliedTaxRule>,
    val traces: List<CalculationTrace>,
    val missingInputs: List<String>,
    val confidence: CalculationConfidence,
    val ruleVersion: TaxRuleVersion,
    val calculationAvailable: Boolean = true,
)

data class AcquisitionTaxResult(
    val acquisitionTax: Long,
    val localEducationTax: Long,
    val ruralSpecialTax: Long,
    val totalTax: Long,
    val effectiveRate: Double,
)

data class PropertyHoldingTax(
    val propertyId: String,
    val propertyTax: Long,
    val localEducationTax: Long,
    val urbanAreaTax: Long,
    val total: Long,
    val regionalResourceTax: Long = 0,
)

data class HoldingTaxResult(
    val taxYear: Int,
    val properties: List<PropertyHoldingTax>,
    val propertyTax: Long,
    val comprehensiveRealEstateTax: Long,
    val additionalTax: Long,
    val totalTax: Long,
    val separateComprehensiveTax: Long = comprehensiveRealEstateTax,
    val jointSpecialComprehensiveTax: Long? = null,
    val selectedJointTaxMethod: JointHoldingTaxMethod = JointHoldingTaxMethod.SEPARATE,
    val jointSpecialCreditPercent: Int = 0,
    val urbanAreaTax: Long = 0,
    val regionalResourceTax: Long = 0,
)

data class SaleTaxResult(
    val acquisitionPrice: Long,
    val salePrice: Long,
    val necessaryExpenses: Long,
    val capitalGain: Long,
    val longTermDeduction: Long,
    val taxableGain: Long,
    val basicDeduction: Long,
    val taxBase: Long,
    val nationalCapitalGainsTax: Long,
    val localIncomeTax: Long,
    val totalEstimatedTax: Long,
    val houseCountIncludingRights: Int = 1,
    val oneHomeExemptionApplied: Boolean = false,
    val oneHomePresaleSpecialApplied: Boolean = false,
    val multiHomeSurchargePercent: Int = 0,
    val ownerBasicDeductions: Map<OwnerRole, Long> = emptyMap(),
)

data class SaleSimulationInput(
    val property: PropertyAsset,
    val expectedSaleDate: LocalDate,
    val expectedSalePrice: Long,
    val additionalNecessaryExpenses: Long,
    val portfolioHouseCountAtSale: Int,
    val portfolioAssets: List<PropertyAsset> = emptyList(),
    val regulatedAreaAtSale: Boolean? = null,
    val surchargeGraceEligible: Boolean = false,
    val postCompletionPresaleSpecialEligible: Boolean = false,
    val saleContractDate: LocalDate? = null,
    val depositReceived: Boolean = false,
    val landTransactionPermitRequired: Boolean? = null,
    val landTransactionPermitApplicationDate: LocalDate? = null,
    val landTransactionPermitApproved: Boolean = false,
    val extendedSurchargeGraceRegion: Boolean = false,
    val completedHomeMoveInDate: LocalDate? = null,
    val completedHomeResidenceEndDate: LocalDate? = null,
    val ownerBasicDeductionUsed: Long = 0,
    val spouseBasicDeductionUsed: Long = 0,
)

enum class ScenarioTransactionType(val label: String) { ACQUIRE("가상 취득"), SELL("가상 매도") }

data class ScenarioTransaction(
    val id: String,
    val sequence: Int,
    val type: ScenarioTransactionType,
    val transactionDate: LocalDate,
    val transactionPrice: Long,
    val propertyId: String?,
    val acquiredProperty: PropertyAsset? = null,
)

data class ScenarioStepResult(
    val transaction: ScenarioTransaction,
    val tax: Long,
    val holdingTaxAfter: Long,
    val description: String,
    val missingInputs: List<String>,
)

data class PortfolioImpactResult(
    val beforePortfolio: List<PropertyAsset>,
    val afterPortfolio: List<PropertyAsset>,
    val beforeHoldingTax: Long,
    val afterHoldingTax: Long,
    val transactionTax: Long,
    val totalTaxChange: Long,
    val affectedPropertyIds: Set<String>,
    val steps: List<ScenarioStepResult>,
    val missingInputs: List<String>,
    val ruleVersion: TaxRuleVersion,
)

enum class TaxLawVerificationStatus { CURRENT, CHANGE_DETECTED, INCONCLUSIVE }

data class TaxLawChange(
    val ruleId: String,
    val title: String,
    val effectiveDate: String,
    val transitionRule: String,
    val impact: String,
)

data class TaxOfficialSource(val title: String, val url: String)

data class PropertyTaxAiAnalysis(
    val summary: String,
    val majorChanges: List<String>,
    val reasons: List<String>,
    val risks: List<String>,
    val missingInformation: List<String>,
    val suggestedScenarios: List<String>,
    val verificationStatus: TaxLawVerificationStatus = TaxLawVerificationStatus.INCONCLUSIVE,
    val verificationSummary: String = "공식 법령 검증 결과를 확인하지 못했습니다.",
    val calculationSafe: Boolean = false,
    val detectedLawChanges: List<TaxLawChange> = emptyList(),
    val officialSources: List<TaxOfficialSource> = emptyList(),
    val checkedAt: Long? = null,
    val previousCheckedAt: Long? = null,
    val comparisonSummary: String = "",
    val correctedPreviousFindings: List<String> = emptyList(),
    val newlyDetectedDifferences: List<String> = emptyList(),
    val unchangedFindings: List<String> = emptyList(),
)

data class TaxPlanTimelineItem(
    val date: String,
    val title: String,
    val detail: String,
    val status: String,
)

data class TaxPlanScenario(
    val name: String,
    val verdict: String,
    val saleOrder: List<String>,
    val taxTreatment: List<String>,
    val advantages: List<String>,
    val risks: List<String>,
    val deadlines: List<String>,
)

data class PropertyTaxPlanAnalysis(
    val summary: String,
    val recommendedScenario: String,
    val timeline: List<TaxPlanTimelineItem>,
    val scenarios: List<TaxPlanScenario>,
    val keyFindings: List<String>,
    val assumptions: List<String>,
    val missingInformation: List<String>,
    val nextActions: List<String>,
    val officialSources: List<TaxOfficialSource>,
    val verificationStatus: TaxLawVerificationStatus,
    val checkedAt: Long?,
)
