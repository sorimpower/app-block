package com.sorimpower.app.feature.propertytax.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.propertytax.data.*
import com.sorimpower.app.feature.propertytax.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PropertyTaxUiState(
    val properties: List<PropertyEntity> = emptyList(),
    val simulations: List<SaleSimulationEntity> = emptyList(),
    val revisions: List<SimulationRevisionEntity> = emptyList(),
    val scenarios: List<PropertyTaxScenarioEntity> = emptyList(),
    val scenarioTransactions: List<ScenarioTransactionEntity> = emptyList(),
    val scenarioImpacts: Map<String, PortfolioImpactResult> = emptyMap(),
    val taxYear: Int = LocalDate.now().year,
    val activeRule: TaxRuleVersionEntity? = null,
    val holding: TaxCalculation<HoldingTaxResult>? = null,
    val acquisitionTaxes: Map<String, TaxCalculation<AcquisitionTaxResult>> = emptyMap(),
    val working: Boolean = false,
    val message: String? = null,
    val aiAnalysis: PropertyTaxAiAnalysis? = null,
    val planInput: String = "",
    val planAnalysis: PropertyTaxPlanAnalysis? = null,
    val planCheckedAt: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PropertyTaxViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PropertyTaxRepository(application)
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val analysis = MutableStateFlow<PropertyTaxAiAnalysis?>(null)
    private val taxYear = MutableStateFlow(LocalDate.now().year)
    private val plan = repository.latestPlan.map { entity -> entity to repository.parsePlan(entity) }
    private val calculations = combine(repository.properties, taxYear) { values, year ->
        val owned = values.filter { it.status == PropertyStatus.OWNED.name }
        val holding = repository.dashboard(values, year)
        val acquisitions = owned.associate { entity ->
            entity.id to repository.acquisitionPreview(entity.draft(), owned)
        }
        holding to acquisitions
    }
    private val scenarioCalculations = combine(repository.properties, repository.scenarios, repository.scenarioTransactions, taxYear) { properties, scenarios, transactions, year ->
        Triple(scenarios, transactions, repository.evaluateScenarios(properties, scenarios, transactions, year))
    }
    @Suppress("UNCHECKED_CAST")
    val state = combine(repository.properties, repository.simulations, repository.revisions, repository.activeRule, calculations, scenarioCalculations, taxYear, working, message, analysis, plan) { values ->
        @Suppress("UNCHECKED_CAST")
        val calculationsValue = values[4] as Pair<TaxCalculation<HoldingTaxResult>, Map<String, TaxCalculation<AcquisitionTaxResult>>>
        @Suppress("UNCHECKED_CAST")
        val scenarioValue = values[5] as Triple<List<PropertyTaxScenarioEntity>, List<ScenarioTransactionEntity>, Map<String, PortfolioImpactResult>>
        @Suppress("UNCHECKED_CAST")
        val planValue = values[10] as Pair<PropertyTaxAiPlanEntity?, PropertyTaxPlanAnalysis?>
        PropertyTaxUiState(
            properties = values[0] as List<PropertyEntity>, simulations = values[1] as List<SaleSimulationEntity>, revisions = values[2] as List<SimulationRevisionEntity>,
            scenarios = scenarioValue.first, scenarioTransactions = scenarioValue.second, scenarioImpacts = scenarioValue.third,
            taxYear = values[6] as Int, activeRule = values[3] as TaxRuleVersionEntity?, holding = calculationsValue.first, acquisitionTaxes = calculationsValue.second,
            working = values[7] as Boolean, message = values[8] as String?, aiAnalysis = values[9] as PropertyTaxAiAnalysis?,
            planInput = planValue.first?.inputText.orEmpty(), planAnalysis = planValue.second,
            planCheckedAt = planValue.first?.checkedAt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PropertyTaxUiState())

    init { viewModelScope.launch { repository.initialize() } }
    fun saveProperty(draft: PropertyDraft) = work("부동산을 저장했습니다.") { repository.saveProperty(draft) }
    fun deleteProperty(id: String) = work("부동산을 삭제했습니다.") { repository.deleteProperty(id) }
    fun createSimulation(draft: SaleSimulationDraft) = work("시뮬레이션을 저장했습니다.") { repository.createSimulation(draft) }
    fun deleteSimulation(id: String) = work("시뮬레이션을 삭제했습니다.") { repository.deleteSimulation(id) }
    fun createScenario(name: String) = work("거래 시나리오를 만들었습니다.") { repository.createScenario(name) }
    fun addScenarioAcquisition(scenarioId: String, draft: PropertyDraft) = work("가상 취득 단계를 추가했습니다.") { repository.addScenarioAcquisition(scenarioId, draft) }
    fun addScenarioSale(scenarioId: String, propertyId: String, date: LocalDate, price: Long) = work("가상 매도 단계를 추가했습니다.") { repository.addScenarioSale(scenarioId, propertyId, date, price) }
    fun deleteScenario(id: String) = work("거래 시나리오를 삭제했습니다.") { repository.deleteScenario(id) }
    fun setTaxYear(year: Int) { taxYear.value = year.coerceIn(2000, LocalDate.now().year + 20) }
    fun recalculate(id: String) = work("최신 활성 세법 버전으로 재계산했습니다. 기존 결과는 Revision에 보존됩니다.") { repository.recalculate(id) }
    fun analyze(id: String) = work { analysis.value = repository.analyze(id) }
    fun analyzePlan(input: String) = work { repository.analyzePlan(input) }
    fun clearPlan() = work("AI 계획 분석을 삭제했습니다.") { repository.clearPlans() }
    fun dismissAnalysis() { analysis.value = null }
    fun clearMessage() { message.value = null }
    private fun work(success: String? = null, block: suspend () -> Unit) {
        if (working.value) return
        viewModelScope.launch {
            working.value = true
            runCatching { block() }.onSuccess { if (success != null) message.value = success }.onFailure { message.value = it.message ?: "작업을 완료하지 못했습니다." }
            working.value = false
        }
    }
}

private fun PropertyEntity.draft() = PropertyDraft(
    id, name, PropertyType.valueOf(propertyType), address, LocalDate.parse(acquisitionDate), acquisitionPrice,
    ownershipRatio, officialAssessedValue, currentEstimatedValue, actualAcquisitionTax, brokerageFee, legalFee,
    renovationCost, otherNecessaryExpenses, residenceStartDate?.let(LocalDate::parse), residenceEndDate?.let(LocalDate::parse),
    spouseOwnershipRatio, regulatedAreaAtAcquisition, expectedCompletionDate?.let(LocalDate::parse), ownerBirthYear,
    spouseBirthYear, ownerBirthDate?.let(LocalDate::parse), spouseBirthDate?.let(LocalDate::parse),
    acquisitionContractDate?.let(LocalDate::parse), urbanAreaTaxApplicable, annualRegionalResourceTax,
    acquisitionRuralSpecialTax, runCatching { TaxTreatment.valueOf(acquisitionHouseCountTreatment) }.getOrDefault(TaxTreatment.AUTO),
    runCatching { TaxTreatment.valueOf(capitalGainsHouseCountTreatment) }.getOrDefault(TaxTreatment.AUTO),
    runCatching { TaxTreatment.valueOf(comprehensiveTaxTreatment) }.getOrDefault(TaxTreatment.AUTO),
    runCatching { TaxTreatment.valueOf(capitalGainsSurchargeTreatment) }.getOrDefault(TaxTreatment.AUTO),
    runCatching { AcquisitionSurchargeRelief.valueOf(acquisitionSurchargeRelief) }.getOrDefault(AcquisitionSurchargeRelief.NONE),
    previousHomeDispositionDate?.let(LocalDate::parse), residenceRequirementExempt,
    jointComprehensiveTaxSpecialRequested, jointSpecialTaxpayer?.let { runCatching { OwnerRole.valueOf(it) }.getOrNull() },
    redevelopmentHistory, managementDispositionApprovalDate?.let(LocalDate::parse),
    demolitionDate?.let(LocalDate::parse), redevelopmentCompletionDate?.let(LocalDate::parse),
    additionalContribution, settlementRefund, redevelopmentNecessaryExpenses,
)
