package com.sorimpower.app.feature.propertytax.data

import android.content.Context
import com.sorimpower.app.core.ai.AiModelId
import com.sorimpower.app.feature.propertytax.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class PropertyTaxRepository(context: Context) {
    private companion object { const val TAX_ANALYSIS_PROMPT_VERSION = "property-tax-live-comparison-v2" }
    private val dao = PropertyTaxDatabase.get(context).dao()
    private val engine = PropertyTaxEngine()
    private val analyzer = OpenAiPropertyTaxAnalyzer(context)
    private val planAnalyzer = OpenAiPropertyTaxPlanAnalyzer(context)
    val properties = dao.observeProperties()
    val simulations = dao.observeSimulations()
    val revisions = dao.observeRevisions()
    val activeRule = dao.observeActiveRule()
    val scenarios = dao.observeScenarios()
    val scenarioTransactions = dao.observeScenarioTransactions()
    val latestPlan = dao.observeLatestTaxPlan()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val rule = KoreanPropertyTaxRules2026.version
        dao.insertRule(TaxRuleVersionEntity(rule.id, rule.name, rule.effectiveFrom.toString(), rule.effectiveUntil?.toString(), rule.sourceUpdatedAt.toString(), JSONArray(rule.sources.map { JSONObject().put("title", it.title).put("authority", it.authority).put("url", it.url) }).toString(), "ACTIVE", System.currentTimeMillis()))
        dao.archiveRulesExcept(rule.id)
    }

    suspend fun saveProperty(draft: PropertyDraft) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.upsertProperty(draft.entity(now))
    }

    suspend fun deleteProperty(id: String) = withContext(Dispatchers.IO) { dao.deleteProperty(id) }
    suspend fun deleteSimulation(id: String) = withContext(Dispatchers.IO) { dao.deleteSimulation(id) }

    suspend fun createScenario(name: String): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        dao.upsertScenario(PropertyTaxScenarioEntity(id, name.trim().ifBlank { "새 거래 시나리오" }, KoreanPropertyTaxRules2026.version.id, System.currentTimeMillis()))
        id
    }

    suspend fun addScenarioAcquisition(scenarioId: String, draft: PropertyDraft) = withContext(Dispatchers.IO) {
        val id = draft.id ?: "virtual-${UUID.randomUUID()}"
        val saved = draft.copy(id = id)
        dao.upsertScenarioTransaction(
            ScenarioTransactionEntity(
                id = UUID.randomUUID().toString(),
                scenarioId = scenarioId,
                sequence = dao.lastScenarioSequence(scenarioId) + 1,
                type = ScenarioTransactionType.ACQUIRE.name,
                propertyId = id,
                transactionDate = saved.acquisitionDate.toString(),
                transactionPrice = saved.acquisitionPrice,
                propertyDraftJson = saved.toJson().toString(),
            ),
        )
    }

    suspend fun addScenarioSale(scenarioId: String, propertyId: String, date: LocalDate, price: Long) = withContext(Dispatchers.IO) {
        dao.upsertScenarioTransaction(
            ScenarioTransactionEntity(
                id = UUID.randomUUID().toString(),
                scenarioId = scenarioId,
                sequence = dao.lastScenarioSequence(scenarioId) + 1,
                type = ScenarioTransactionType.SELL.name,
                propertyId = propertyId,
                transactionDate = date.toString(),
                transactionPrice = price,
                propertyDraftJson = null,
            ),
        )
    }

    suspend fun deleteScenario(id: String) = withContext(Dispatchers.IO) {
        dao.deleteScenarioTransactions(id)
        dao.deleteScenarioOnly(id)
    }

    suspend fun evaluateScenarios(
        properties: List<PropertyEntity>,
        scenarios: List<PropertyTaxScenarioEntity>,
        transactions: List<ScenarioTransactionEntity>,
        year: Int,
    ): Map<String, PortfolioImpactResult> = withContext(Dispatchers.Default) {
        val initial = properties.map(PropertyEntity::domain)
        scenarios.associate { scenario ->
            val steps = transactions.filter { it.scenarioId == scenario.id }.mapNotNull(ScenarioTransactionEntity::domain)
            scenario.id to engine.portfolioImpact(initial, steps, year)
        }
    }

    suspend fun dashboard(properties: List<PropertyEntity>, year: Int): TaxCalculation<HoldingTaxResult> = withContext(Dispatchers.Default) { engine.holding(properties.map(PropertyEntity::domain), year) }

    suspend fun acquisitionPreview(draft: PropertyDraft, portfolio: List<PropertyEntity>): TaxCalculation<AcquisitionTaxResult> = withContext(Dispatchers.Default) {
        engine.acquisition(draft.domain(), portfolio.map(PropertyEntity::domain))
    }

    suspend fun createSimulation(draft: SaleSimulationDraft): SaleSimulationEntity = withContext(Dispatchers.IO) {
        val property = dao.property(draft.propertyId)?.domain() ?: error("부동산 정보를 찾을 수 없습니다.")
        val portfolio = dao.ownedProperties().map(PropertyEntity::domain)
        val calculation = engine.capitalGains(draft.input(property, portfolio))
        if (!calculation.calculationAvailable) error(calculation.missingInputs.firstOrNull() ?: "해당 양도일에 적용할 세법 버전이 없습니다.")
        val result = calculation.result
        val now = System.currentTimeMillis()
        val entity = SaleSimulationEntity(
            id = UUID.randomUUID().toString(), propertyId = draft.propertyId,
            name = draft.name.ifBlank { "${property.name} 매도 시뮬레이션" }, expectedSaleDate = draft.date.toString(),
            expectedSalePrice = draft.price, additionalNecessaryExpenses = draft.expenses,
            portfolioHouseCountAtSale = portfolio.size, regulatedAreaAtSale = draft.regulatedAtSale,
            surchargeGraceEligible = false, postCompletionPresaleSpecialEligible = false,
            saleContractDate = draft.saleContractDate?.toString(), depositReceived = draft.depositReceived,
            landTransactionPermitRequired = draft.landTransactionPermitRequired,
            landTransactionPermitApplicationDate = draft.landTransactionPermitApplicationDate?.toString(),
            landTransactionPermitApproved = draft.landTransactionPermitApproved,
            extendedSurchargeGraceRegion = draft.extendedSurchargeGraceRegion,
            completedHomeMoveInDate = draft.completedHomeMoveInDate?.toString(),
            completedHomeResidenceEndDate = draft.completedHomeResidenceEndDate?.toString(),
            ownerBasicDeductionUsed = draft.ownerBasicDeductionUsed,
            spouseBasicDeductionUsed = draft.spouseBasicDeductionUsed,
            taxRuleVersionId = calculation.ruleVersion.id, totalEstimatedTax = result.totalEstimatedTax,
            nationalCapitalGainsTax = result.nationalCapitalGainsTax, localIncomeTax = result.localIncomeTax,
            capitalGain = result.capitalGain, longTermDeduction = result.longTermDeduction, taxBase = result.taxBase,
            confidence = calculation.confidence.name, missingInputsJson = jsonArray(calculation.missingInputs),
            appliedRulesJson = rulesJson(calculation.rules), calculationTraceJson = tracesJson(calculation.traces),
            createdAt = now, calculatedAt = now,
        )
        dao.upsertSimulation(entity)
        dao.insertRevision(SimulationRevisionEntity(UUID.randomUUID().toString(), entity.id, entity.taxRuleVersionId, entity.totalEstimatedTax, simulationResultJson(entity), now, null))
        entity
    }

    suspend fun recalculate(simulationId: String): SaleSimulationEntity = withContext(Dispatchers.IO) {
        val old = dao.simulation(simulationId) ?: error("시뮬레이션을 찾을 수 없습니다.")
        val property = dao.property(old.propertyId)?.domain() ?: error("부동산 정보를 찾을 수 없습니다.")
        val portfolio = dao.ownedProperties().map(PropertyEntity::domain)
        val calculation = engine.capitalGains(old.input(property, portfolio))
        if (!calculation.calculationAvailable) error(calculation.missingInputs.firstOrNull() ?: "해당 양도일에 적용할 세법 버전이 없습니다.")
        val result = calculation.result; val now = System.currentTimeMillis(); val previous = dao.latestRevision(old.id)
        val updated = old.copy(taxRuleVersionId = calculation.ruleVersion.id, totalEstimatedTax = result.totalEstimatedTax, nationalCapitalGainsTax = result.nationalCapitalGainsTax, localIncomeTax = result.localIncomeTax, capitalGain = result.capitalGain, longTermDeduction = result.longTermDeduction, taxBase = result.taxBase, confidence = calculation.confidence.name, missingInputsJson = jsonArray(calculation.missingInputs), appliedRulesJson = rulesJson(calculation.rules), calculationTraceJson = tracesJson(calculation.traces), calculatedAt = now)
        dao.upsertSimulation(updated)
        dao.insertRevision(SimulationRevisionEntity(UUID.randomUUID().toString(), old.id, calculation.ruleVersion.id, result.totalEstimatedTax, calculation.totalTaxJson(), now, previous?.id))
        updated
    }

    suspend fun analyze(simulationId: String): PropertyTaxAiAnalysis = withContext(Dispatchers.IO) {
        val simulation = dao.simulation(simulationId) ?: error("시뮬레이션을 찾을 수 없습니다.")
        val property = dao.property(simulation.propertyId) ?: error("부동산 정보를 찾을 수 없습니다.")
        // The last result is context for a comparison, never a response cache.
        // The analyzer still performs a mandatory live official-source search.
        val previous = dao.latestTaxAnalysis(simulationId, TAX_ANALYSIS_PROMPT_VERSION)
            ?.let { runCatching { analyzer.parseStored(it.resultJson) }.getOrNull() }
        val analysis = analyzer.analyze(property.domain(), simulation, previous)
        dao.insertTaxAnalysis(
            PropertyTaxAiCacheEntity(
                cacheKey = UUID.randomUUID().toString(),
                simulationId = simulationId,
                model = "gpt-5.6-sol",
                promptVersion = TAX_ANALYSIS_PROMPT_VERSION,
                resultJson = analyzer.toStoredJson(analysis),
                createdAt = analysis.checkedAt ?: System.currentTimeMillis(),
            ),
        )
        analysis
    }

    suspend fun analyzePlan(input: String): PropertyTaxPlanAnalysis = withContext(Dispatchers.IO) {
        val cleanInput = input.trim()
        require(cleanInput.length >= 20) { "현재 상황과 매도계획을 조금 더 자세히 적어주세요." }
        val previous = dao.latestTaxPlan()?.let { runCatching { planAnalyzer.parseStored(it.resultJson) }.getOrNull() }
        val analysis = planAnalyzer.analyze(cleanInput, previous)
        val now = System.currentTimeMillis()
        dao.insertTaxPlan(
            PropertyTaxAiPlanEntity(
                id = UUID.randomUUID().toString(),
                inputText = cleanInput,
                resultJson = planAnalyzer.toJson(analysis).toString(),
                model = AiModelId.OPENAI_DEEP.apiModelName,
                checkedAt = analysis.checkedAt,
                createdAt = now,
            ),
        )
        analysis
    }

    suspend fun clearPlans() = withContext(Dispatchers.IO) { dao.deleteTaxPlans() }

    fun parsePlan(entity: PropertyTaxAiPlanEntity?): PropertyTaxPlanAnalysis? =
        entity?.let { runCatching { planAnalyzer.parseStored(it.resultJson) }.getOrNull() }

    private fun TaxCalculation<SaleTaxResult>.totalTaxJson() = JSONObject().put("totalEstimatedTax", result.totalEstimatedTax).put("ruleVersion", ruleVersion.id).put("traces", JSONArray(traces.map { JSONObject().put("label", it.label).put("amount", it.amount).put("operation", it.operation) })).toString()
    private fun simulationResultJson(value: SaleSimulationEntity) = JSONObject().put("simulationId", value.id).put("saleDate", value.expectedSaleDate).put("salePrice", value.expectedSalePrice).put("regulatedAreaAtSale", value.regulatedAreaAtSale).put("saleContractDate", value.saleContractDate).put("depositReceived", value.depositReceived).put("landTransactionPermitRequired", value.landTransactionPermitRequired).put("landTransactionPermitApplicationDate", value.landTransactionPermitApplicationDate).put("landTransactionPermitApproved", value.landTransactionPermitApproved).put("extendedSurchargeGraceRegion", value.extendedSurchargeGraceRegion).put("completedHomeMoveInDate", value.completedHomeMoveInDate).put("completedHomeResidenceEndDate", value.completedHomeResidenceEndDate).put("ownerBasicDeductionUsed", value.ownerBasicDeductionUsed).put("spouseBasicDeductionUsed", value.spouseBasicDeductionUsed).put("capitalGain", value.capitalGain).put("longTermDeduction", value.longTermDeduction).put("taxBase", value.taxBase).put("nationalTax", value.nationalCapitalGainsTax).put("localTax", value.localIncomeTax).put("totalEstimatedTax", value.totalEstimatedTax).put("ruleVersion", value.taxRuleVersionId).put("missingInputs", JSONArray(value.missingInputsJson)).put("appliedRules", JSONArray(value.appliedRulesJson)).put("trace", JSONArray(value.calculationTraceJson)).toString()
    private fun jsonArray(values: List<String>) = JSONArray(values).toString()
    private fun rulesJson(values: List<AppliedTaxRule>) = JSONArray(values.map { JSONObject().put("ruleId", it.ruleId).put("applied", it.applied).put("reason", it.reason).put("sourceUrl", it.sourceUrl) }).toString()
    private fun tracesJson(values: List<CalculationTrace>) = JSONArray(values.map { JSONObject().put("label", it.label).put("amount", it.amount).put("operation", it.operation) }).toString()
}

data class PropertyDraft(
    val id: String? = null,
    val name: String,
    val propertyType: PropertyType,
    val address: String,
    val acquisitionDate: LocalDate,
    val acquisitionPrice: Long,
    val ownershipRatio: Double = 1.0,
    val officialAssessedValue: Long? = null,
    val currentEstimatedValue: Long? = null,
    val actualAcquisitionTax: Long? = null,
    val brokerageFee: Long = 0,
    val legalFee: Long = 0,
    val renovationCost: Long = 0,
    val otherNecessaryExpenses: Long = 0,
    val residenceStartDate: LocalDate? = null,
    val residenceEndDate: LocalDate? = null,
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
) {
    fun domain() = PropertyAsset(
        id ?: "preview", name, propertyType, address, acquisitionDate, acquisitionPrice, ownershipRatio,
        officialAssessedValue, currentEstimatedValue, actualAcquisitionTax, brokerageFee, legalFee,
        renovationCost, otherNecessaryExpenses, residenceStartDate, residenceEndDate, PropertyStatus.OWNED,
        spouseOwnershipRatio, regulatedAreaAtAcquisition, expectedCompletionDate, ownerBirthYear, spouseBirthYear,
        ownerBirthDate, spouseBirthDate, acquisitionContractDate, urbanAreaTaxApplicable,
        annualRegionalResourceTax, acquisitionRuralSpecialTax, acquisitionHouseCountTreatment,
        capitalGainsHouseCountTreatment, comprehensiveTaxTreatment, capitalGainsSurchargeTreatment,
        acquisitionSurchargeRelief, previousHomeDispositionDate, residenceRequirementExempt,
        jointComprehensiveTaxSpecialRequested, jointSpecialTaxpayer, redevelopmentHistory,
        managementDispositionApprovalDate, demolitionDate, redevelopmentCompletionDate,
        additionalContribution, settlementRefund, redevelopmentNecessaryExpenses,
    )
}

data class SaleSimulationDraft(
    val propertyId: String,
    val name: String,
    val date: LocalDate,
    val price: Long,
    val expenses: Long,
    val regulatedAtSale: Boolean?,
    val saleContractDate: LocalDate?,
    val depositReceived: Boolean,
    val landTransactionPermitRequired: Boolean?,
    val landTransactionPermitApplicationDate: LocalDate?,
    val landTransactionPermitApproved: Boolean,
    val extendedSurchargeGraceRegion: Boolean,
    val completedHomeMoveInDate: LocalDate?,
    val completedHomeResidenceEndDate: LocalDate?,
    val ownerBasicDeductionUsed: Long,
    val spouseBasicDeductionUsed: Long,
) {
    fun input(property: PropertyAsset, portfolio: List<PropertyAsset>) = SaleSimulationInput(
        property = property, expectedSaleDate = date, expectedSalePrice = price,
        additionalNecessaryExpenses = expenses, portfolioHouseCountAtSale = portfolio.size,
        portfolioAssets = portfolio, regulatedAreaAtSale = regulatedAtSale,
        saleContractDate = saleContractDate, depositReceived = depositReceived,
        landTransactionPermitRequired = landTransactionPermitRequired,
        landTransactionPermitApplicationDate = landTransactionPermitApplicationDate,
        landTransactionPermitApproved = landTransactionPermitApproved,
        extendedSurchargeGraceRegion = extendedSurchargeGraceRegion,
        completedHomeMoveInDate = completedHomeMoveInDate,
        completedHomeResidenceEndDate = completedHomeResidenceEndDate,
        ownerBasicDeductionUsed = ownerBasicDeductionUsed,
        spouseBasicDeductionUsed = spouseBasicDeductionUsed,
    )
}

private fun SaleSimulationEntity.input(property: PropertyAsset, portfolio: List<PropertyAsset>) = SaleSimulationInput(
    property = property, expectedSaleDate = LocalDate.parse(expectedSaleDate), expectedSalePrice = expectedSalePrice,
    additionalNecessaryExpenses = additionalNecessaryExpenses, portfolioHouseCountAtSale = portfolioHouseCountAtSale,
    portfolioAssets = portfolio, regulatedAreaAtSale = regulatedAreaAtSale,
    surchargeGraceEligible = surchargeGraceEligible,
    postCompletionPresaleSpecialEligible = postCompletionPresaleSpecialEligible,
    saleContractDate = saleContractDate?.let(LocalDate::parse), depositReceived = depositReceived,
    landTransactionPermitRequired = landTransactionPermitRequired,
    landTransactionPermitApplicationDate = landTransactionPermitApplicationDate?.let(LocalDate::parse),
    landTransactionPermitApproved = landTransactionPermitApproved,
    extendedSurchargeGraceRegion = extendedSurchargeGraceRegion,
    completedHomeMoveInDate = completedHomeMoveInDate?.let(LocalDate::parse),
    completedHomeResidenceEndDate = completedHomeResidenceEndDate?.let(LocalDate::parse),
    ownerBasicDeductionUsed = ownerBasicDeductionUsed,
    spouseBasicDeductionUsed = spouseBasicDeductionUsed,
)

private fun PropertyDraft.entity(now: Long) = PropertyEntity(
    id = id ?: UUID.randomUUID().toString(), name = name.trim(), propertyType = propertyType.name,
    address = address.trim(), acquisitionDate = acquisitionDate.toString(), acquisitionPrice = acquisitionPrice,
    ownershipRatio = ownershipRatio, officialAssessedValue = officialAssessedValue,
    currentEstimatedValue = currentEstimatedValue, actualAcquisitionTax = actualAcquisitionTax,
    brokerageFee = brokerageFee, legalFee = legalFee, renovationCost = renovationCost,
    otherNecessaryExpenses = otherNecessaryExpenses, residenceStartDate = residenceStartDate?.toString(),
    residenceEndDate = residenceEndDate?.toString(), status = PropertyStatus.OWNED.name,
    spouseOwnershipRatio = spouseOwnershipRatio, regulatedAreaAtAcquisition = regulatedAreaAtAcquisition,
    expectedCompletionDate = expectedCompletionDate?.toString(), ownerBirthYear = ownerBirthYear,
    spouseBirthYear = spouseBirthYear, ownerBirthDate = ownerBirthDate?.toString(),
    spouseBirthDate = spouseBirthDate?.toString(), acquisitionContractDate = acquisitionContractDate?.toString(),
    urbanAreaTaxApplicable = urbanAreaTaxApplicable, annualRegionalResourceTax = annualRegionalResourceTax,
    acquisitionRuralSpecialTax = acquisitionRuralSpecialTax,
    acquisitionHouseCountTreatment = acquisitionHouseCountTreatment.name,
    capitalGainsHouseCountTreatment = capitalGainsHouseCountTreatment.name,
    comprehensiveTaxTreatment = comprehensiveTaxTreatment.name,
    capitalGainsSurchargeTreatment = capitalGainsSurchargeTreatment.name,
    acquisitionSurchargeRelief = acquisitionSurchargeRelief.name,
    previousHomeDispositionDate = previousHomeDispositionDate?.toString(),
    residenceRequirementExempt = residenceRequirementExempt,
    jointComprehensiveTaxSpecialRequested = jointComprehensiveTaxSpecialRequested,
    jointSpecialTaxpayer = jointSpecialTaxpayer?.name,
    redevelopmentHistory = redevelopmentHistory,
    managementDispositionApprovalDate = managementDispositionApprovalDate?.toString(),
    demolitionDate = demolitionDate?.toString(),
    redevelopmentCompletionDate = redevelopmentCompletionDate?.toString(),
    additionalContribution = additionalContribution,
    settlementRefund = settlementRefund,
    redevelopmentNecessaryExpenses = redevelopmentNecessaryExpenses,
    createdAt = now, updatedAt = now,
)

private fun PropertyDraft.toJson() = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("propertyType", propertyType.name)
    .put("address", address)
    .put("acquisitionDate", acquisitionDate.toString())
    .put("acquisitionPrice", acquisitionPrice)
    .put("ownershipRatio", ownershipRatio)
    .put("spouseOwnershipRatio", spouseOwnershipRatio)
    .put("regulatedAreaAtAcquisition", regulatedAreaAtAcquisition)
    .put("expectedCompletionDate", expectedCompletionDate?.toString())
    .put("ownerBirthYear", ownerBirthYear)
    .put("spouseBirthYear", spouseBirthYear)
    .put("ownerBirthDate", ownerBirthDate?.toString())
    .put("spouseBirthDate", spouseBirthDate?.toString())
    .put("acquisitionContractDate", acquisitionContractDate?.toString())
    .put("urbanAreaTaxApplicable", urbanAreaTaxApplicable)
    .put("annualRegionalResourceTax", annualRegionalResourceTax)
    .put("acquisitionRuralSpecialTax", acquisitionRuralSpecialTax)
    .put("acquisitionHouseCountTreatment", acquisitionHouseCountTreatment.name)
    .put("capitalGainsHouseCountTreatment", capitalGainsHouseCountTreatment.name)
    .put("comprehensiveTaxTreatment", comprehensiveTaxTreatment.name)
    .put("capitalGainsSurchargeTreatment", capitalGainsSurchargeTreatment.name)
    .put("acquisitionSurchargeRelief", acquisitionSurchargeRelief.name)
    .put("previousHomeDispositionDate", previousHomeDispositionDate?.toString())
    .put("residenceRequirementExempt", residenceRequirementExempt)
    .put("jointComprehensiveTaxSpecialRequested", jointComprehensiveTaxSpecialRequested)
    .put("jointSpecialTaxpayer", jointSpecialTaxpayer?.name)
    .put("redevelopmentHistory", redevelopmentHistory)
    .put("managementDispositionApprovalDate", managementDispositionApprovalDate?.toString())
    .put("demolitionDate", demolitionDate?.toString())
    .put("redevelopmentCompletionDate", redevelopmentCompletionDate?.toString())
    .put("additionalContribution", additionalContribution)
    .put("settlementRefund", settlementRefund)
    .put("redevelopmentNecessaryExpenses", redevelopmentNecessaryExpenses)
    .put("officialAssessedValue", officialAssessedValue)
    .put("currentEstimatedValue", currentEstimatedValue)
    .put("actualAcquisitionTax", actualAcquisitionTax)
    .put("brokerageFee", brokerageFee)
    .put("legalFee", legalFee)
    .put("renovationCost", renovationCost)
    .put("otherNecessaryExpenses", otherNecessaryExpenses)
    .put("residenceStartDate", residenceStartDate?.toString())
    .put("residenceEndDate", residenceEndDate?.toString())

private fun ScenarioTransactionEntity.domain(): ScenarioTransaction? = runCatching {
    val typeValue = ScenarioTransactionType.valueOf(type)
    val draft = propertyDraftJson?.let { json ->
        val value = JSONObject(json)
        PropertyAsset(
            id = value.getString("id"),
            name = value.getString("name"),
            propertyType = PropertyType.valueOf(value.getString("propertyType")),
            address = value.optString("address"),
            acquisitionDate = LocalDate.parse(value.getString("acquisitionDate")),
            acquisitionPrice = value.getLong("acquisitionPrice"),
            ownershipRatio = value.optDouble("ownershipRatio", 1.0),
            officialAssessedValue = value.optLongOrNull("officialAssessedValue"),
            currentEstimatedValue = value.optLongOrNull("currentEstimatedValue"),
            actualAcquisitionTax = value.optLongOrNull("actualAcquisitionTax"),
            brokerageFee = value.optLong("brokerageFee"),
            legalFee = value.optLong("legalFee"),
            renovationCost = value.optLong("renovationCost"),
            otherNecessaryExpenses = value.optLong("otherNecessaryExpenses"),
            residenceStartDate = value.optDate("residenceStartDate"),
            residenceEndDate = value.optDate("residenceEndDate"),
            status = PropertyStatus.OWNED,
            spouseOwnershipRatio = value.optDouble("spouseOwnershipRatio", 0.0),
            regulatedAreaAtAcquisition = if (value.isNull("regulatedAreaAtAcquisition")) null else value.optBoolean("regulatedAreaAtAcquisition"),
            expectedCompletionDate = value.optString("expectedCompletionDate").takeIf(String::isNotBlank)?.let(LocalDate::parse),
            ownerBirthYear = value.optIntOrNull("ownerBirthYear"),
            spouseBirthYear = value.optIntOrNull("spouseBirthYear"),
            ownerBirthDate = value.optDate("ownerBirthDate"),
            spouseBirthDate = value.optDate("spouseBirthDate"),
            acquisitionContractDate = value.optDate("acquisitionContractDate"),
            urbanAreaTaxApplicable = value.optBooleanOrNull("urbanAreaTaxApplicable"),
            annualRegionalResourceTax = value.optLongOrNull("annualRegionalResourceTax"),
            acquisitionRuralSpecialTax = value.optLongOrNull("acquisitionRuralSpecialTax"),
            acquisitionHouseCountTreatment = value.optEnum("acquisitionHouseCountTreatment", TaxTreatment.AUTO),
            capitalGainsHouseCountTreatment = value.optEnum("capitalGainsHouseCountTreatment", TaxTreatment.AUTO),
            comprehensiveTaxTreatment = value.optEnum("comprehensiveTaxTreatment", TaxTreatment.AUTO),
            capitalGainsSurchargeTreatment = value.optEnum("capitalGainsSurchargeTreatment", TaxTreatment.AUTO),
            acquisitionSurchargeRelief = value.optEnum("acquisitionSurchargeRelief", AcquisitionSurchargeRelief.NONE),
            previousHomeDispositionDate = value.optDate("previousHomeDispositionDate"),
            residenceRequirementExempt = value.optBoolean("residenceRequirementExempt", false),
            jointComprehensiveTaxSpecialRequested = value.optBoolean("jointComprehensiveTaxSpecialRequested", false),
            jointSpecialTaxpayer = value.optString("jointSpecialTaxpayer").takeIf(String::isNotBlank)?.let { runCatching { OwnerRole.valueOf(it) }.getOrNull() },
            redevelopmentHistory = value.optBoolean("redevelopmentHistory", false),
            managementDispositionApprovalDate = value.optDate("managementDispositionApprovalDate"),
            demolitionDate = value.optDate("demolitionDate"),
            redevelopmentCompletionDate = value.optDate("redevelopmentCompletionDate"),
            additionalContribution = value.optLong("additionalContribution"),
            settlementRefund = value.optLong("settlementRefund"),
            redevelopmentNecessaryExpenses = value.optLong("redevelopmentNecessaryExpenses"),
        )
    }
    ScenarioTransaction(id, sequence, typeValue, LocalDate.parse(transactionDate), transactionPrice, propertyId, draft)
}.getOrNull()

private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
private fun JSONObject.optBooleanOrNull(key: String): Boolean? = if (isNull(key) || !has(key)) null else optBoolean(key)
private fun JSONObject.optDate(key: String): LocalDate? = optString(key).takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String, fallback: T): T = runCatching { enumValueOf<T>(optString(key)) }.getOrDefault(fallback)

fun PropertyEntity.domain() = PropertyAsset(
    id, name, PropertyType.valueOf(propertyType), address, LocalDate.parse(acquisitionDate), acquisitionPrice,
    ownershipRatio, officialAssessedValue, currentEstimatedValue, actualAcquisitionTax, brokerageFee,
    legalFee, renovationCost, otherNecessaryExpenses, residenceStartDate?.let(LocalDate::parse),
    residenceEndDate?.let(LocalDate::parse), PropertyStatus.valueOf(status), spouseOwnershipRatio,
    regulatedAreaAtAcquisition, expectedCompletionDate?.let(LocalDate::parse), ownerBirthYear, spouseBirthYear,
    ownerBirthDate?.let(LocalDate::parse), spouseBirthDate?.let(LocalDate::parse), acquisitionContractDate?.let(LocalDate::parse),
    urbanAreaTaxApplicable, annualRegionalResourceTax, acquisitionRuralSpecialTax,
    runCatching { TaxTreatment.valueOf(acquisitionHouseCountTreatment) }.getOrDefault(TaxTreatment.AUTO),
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
