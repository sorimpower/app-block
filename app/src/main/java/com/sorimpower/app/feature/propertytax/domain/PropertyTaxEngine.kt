package com.sorimpower.app.feature.propertytax.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

object KoreanPropertyTaxRules2026 {
    private const val LAW_BASE = "https://www.law.go.kr"
    val version = TaxRuleVersion(
        id = "KR_PROPERTY_TAX_2026_08_REDEVELOPMENT",
        name = "대한민국 개인 주택·분양권·공동명의·정비사업 2026.08 교정판",
        effectiveFrom = LocalDate.of(2026, 7, 1),
        effectiveUntil = LocalDate.of(2026, 12, 31),
        sourceUpdatedAt = LocalDate.of(2026, 8, 12),
        sources = listOf(
            TaxSourceReference("지방세법 제11조 부동산 취득 세율", "국가법령정보센터", "$LAW_BASE/법령/지방세법/제11조", listOf("ACQ_STANDARD_HOME")),
            TaxSourceReference("지방세법 시행령 제28조의4 주택 수 산정", "국가법령정보센터", "$LAW_BASE/법령/지방세법시행령/제28조의4", listOf("ACQ_HOUSE_COUNT")),
            TaxSourceReference("지방세법 시행령 제109조 공정시장가액비율", "국가법령정보센터", "$LAW_BASE/법령/지방세법시행령/제109조", listOf("HOLDING_FAIR_VALUE")),
            TaxSourceReference("지방세법 제111조·제112조 재산세율·도시지역분", "국가법령정보센터", "$LAW_BASE/법령/지방세법/제111조", listOf("PROPERTY_TAX_RATE")),
            TaxSourceReference("종합부동산세법 제9조·시행령 제4조의3", "국가법령정보센터", "$LAW_BASE/법령/종합부동산세법/제9조", listOf("COMPREHENSIVE_HOME")),
            TaxSourceReference("소득세법 제95조·제103조·제104조", "국가법령정보센터", "$LAW_BASE/법령/소득세법/제104조", listOf("CAPITAL_GAIN_RATE", "LONG_TERM_DEDUCTION")),
            TaxSourceReference("소득세법 시행령 제154조", "국가법령정보센터", "$LAW_BASE/법령/소득세법시행령/제154조", listOf("ONE_HOME_EXEMPTION")),
            TaxSourceReference("소득세법 시행령 제156조의3", "국가법령정보센터", "$LAW_BASE/법령/소득세법시행령/제156조의3", listOf("ONE_HOME_PRESALE_SPECIAL")),
            TaxSourceReference("소득세법 시행령 제167조의10", "국가법령정보센터", "$LAW_BASE/법령/소득세법시행령/제167조의10", listOf("MULTI_HOME_SURCHARGE")),
            TaxSourceReference("종합부동산세법 제10조의2", "국가법령정보센터", "$LAW_BASE/법령/종합부동산세법/제10조의2", listOf("JOINT_ONE_HOME_SPECIAL")),
            TaxSourceReference("소득세법 시행령 제162조 양도·취득 시기", "국가법령정보센터", "$LAW_BASE/법령/소득세법시행령/제162조", listOf("REDEVELOPMENT_ORIGINAL_MEMBER")),
        ),
    )
}

class PropertyTaxEngine(private val ruleVersion: TaxRuleVersion = KoreanPropertyTaxRules2026.version) {
    private data class OwnerShare(val role: OwnerRole, val ratio: Double)
    private data class GraceDecision(val eligible: Boolean, val missing: List<String> = emptyList())

    fun portfolioImpact(
        properties: List<PropertyAsset>,
        transactions: List<ScenarioTransaction>,
        taxYear: Int,
    ): PortfolioImpactResult {
        val beforePortfolio = properties.filter { it.status == PropertyStatus.OWNED }
        val working = beforePortfolio.associateBy(PropertyAsset::id).toMutableMap()
        val beforeHolding = holding(beforePortfolio, taxYear)
        val stepResults = mutableListOf<ScenarioStepResult>()
        val affected = mutableSetOf<String>()
        val missing = mutableListOf<String>()
        val usedBasicDeduction = mutableMapOf<Pair<Int, OwnerRole>, Long>()
        var transactionTax = 0L

        transactions.sortedBy(ScenarioTransaction::sequence).forEach { transaction ->
            when (transaction.type) {
                ScenarioTransactionType.ACQUIRE -> {
                    val property = requireNotNull(transaction.acquiredProperty) { "가상 취득 부동산 정보가 없습니다." }
                        .copy(acquisitionDate = transaction.transactionDate, acquisitionPrice = transaction.transactionPrice, status = PropertyStatus.OWNED)
                    val calculation = acquisition(property, working.values.toList())
                    working[property.id] = property
                    transactionTax += calculation.result.totalTax
                    affected += property.id
                    missing += calculation.missingInputs
                    stepResults += ScenarioStepResult(
                        transaction,
                        calculation.result.totalTax,
                        holding(working.values.toList(), taxYear).result.totalTax,
                        "${property.name} 취득 후 ${working.size}개 부동산",
                        calculation.missingInputs,
                    )
                }

                ScenarioTransactionType.SELL -> {
                    val property = transaction.propertyId?.let(working::get)
                    if (property == null) {
                        val warning = "가상 매도 대상 부동산을 현재 포트폴리오에서 찾지 못했습니다."
                        missing += warning
                        stepResults += ScenarioStepResult(transaction, 0L, holding(working.values.toList(), taxYear).result.totalTax, warning, listOf(warning))
                    } else {
                        val year = transaction.transactionDate.year
                        val calculation = capitalGains(
                            SaleSimulationInput(
                                property = property,
                                expectedSaleDate = transaction.transactionDate,
                                expectedSalePrice = transaction.transactionPrice,
                                additionalNecessaryExpenses = 0L,
                                portfolioHouseCountAtSale = working.size,
                                portfolioAssets = working.values.toList(),
                                ownerBasicDeductionUsed = usedBasicDeduction[year to OwnerRole.OWNER] ?: 0L,
                                spouseBasicDeductionUsed = usedBasicDeduction[year to OwnerRole.SPOUSE] ?: 0L,
                            ),
                        )
                        calculation.result.ownerBasicDeductions.forEach { (owner, amount) ->
                            usedBasicDeduction[year to owner] = (usedBasicDeduction[year to owner] ?: 0L) + amount
                        }
                        transactionTax += calculation.result.totalEstimatedTax
                        working.remove(property.id)
                        affected += property.id
                        missing += calculation.missingInputs
                        stepResults += ScenarioStepResult(
                            transaction,
                            calculation.result.totalEstimatedTax,
                            holding(working.values.toList(), taxYear).result.totalTax,
                            "${property.name} 매도 후 ${working.size}개 부동산",
                            calculation.missingInputs,
                        )
                    }
                }
            }
        }
        val afterPortfolio = working.values.toList()
        val afterHolding = holding(afterPortfolio, taxYear)
        return PortfolioImpactResult(
            beforePortfolio = beforePortfolio,
            afterPortfolio = afterPortfolio,
            beforeHoldingTax = beforeHolding.result.totalTax,
            afterHoldingTax = afterHolding.result.totalTax,
            transactionTax = transactionTax,
            totalTaxChange = afterHolding.result.totalTax - beforeHolding.result.totalTax + transactionTax,
            affectedPropertyIds = affected,
            steps = stepResults,
            missingInputs = (missing + beforeHolding.missingInputs + afterHolding.missingInputs).distinct(),
            ruleVersion = ruleVersion,
        )
    }

    /** 2026 Rule은 포트폴리오의 실제 자산 유형·법정 제외 선택을 직접 판정한다. */
    fun acquisition(property: PropertyAsset, portfolioBeforeAcquisition: List<PropertyAsset>): TaxCalculation<AcquisitionTaxResult> {
        if (property.redevelopmentHistory) {
            val actual = property.actualAcquisitionTax?.coerceAtLeast(0L)
            return TaxCalculation(
                result = AcquisitionTaxResult(0, 0, 0, actual ?: 0L, 0.0),
                rules = listOf(
                    AppliedTaxRule(
                        "REDEVELOPMENT_ACQUISITION_REVIEW",
                        true,
                        "재개발 신축주택의 취득세는 종전 토지·건축물, 증가 면적, 청산금과 감면 자료가 필요해 일반 주택 매수가로 재계산하지 않았습니다.",
                        ruleVersion.sources[0].url,
                    ),
                ),
                traces = listOf(CalculationTrace("신축 주택 취득세 실제 납부액", actual ?: 0L, if (actual == null) "미입력" else "고지서 입력")),
                missingInputs = if (actual == null) listOf("재개발 신축주택 취득세는 실제 고지서 납부액을 입력해야 합니다.") else listOf("입력한 실제 취득세의 세목별 구성과 감면 적정성은 별도 확인이 필요합니다."),
                confidence = CalculationConfidence.NEEDS_REVIEW,
                ruleVersion = ruleVersion,
                calculationAvailable = actual != null,
            )
        }
        if (!supports(property.acquisitionDate)) return unavailableAcquisition(property.acquisitionDate)
        if (property.propertyType == PropertyType.PRESALE_RIGHT || property.propertyType == PropertyType.ASSOCIATION_RIGHT) {
            return TaxCalculation(
                AcquisitionTaxResult(0, 0, 0, 0, 0.0),
                listOf(AppliedTaxRule("ACQ_PRESALE_DEFERRED", true, "권리 취득 시점이 아니라 완공 주택 취득 시점의 세대 주택 수로 취득세를 판정합니다.", ruleVersion.sources[0].url)),
                listOf(CalculationTrace("권리 계약가", property.acquisitionPrice), CalculationTrace("현재 취득세", 0, "완공 취득 시 계산")),
                listOf("완공일과 완공 취득 당시 세대 주택 수·조정대상지역을 입력해 다시 계산해야 합니다."),
                CalculationConfidence.NEEDS_REVIEW,
                ruleVersion,
            )
        }
        if (property.propertyType !in setOf(PropertyType.APARTMENT, PropertyType.HOUSE)) {
            return TaxCalculation(
                AcquisitionTaxResult(0, 0, 0, 0, 0.0),
                emptyList(),
                emptyList(),
                listOf("현재 취득세 엔진은 개인의 유상 주택 취득만 지원합니다. ${property.propertyType.label}은 주택 세율로 계산하지 않았습니다."),
                CalculationConfidence.NEEDS_REVIEW,
                ruleVersion,
                calculationAvailable = false,
            )
        }

        val missing = mutableListOf<String>()
        val portfolio = (portfolioBeforeAcquisition.filter { it.id != property.id } + property)
            .filter { it.status == PropertyStatus.OWNED && it.acquisitionDate <= property.acquisitionDate }
        val decisions = portfolio.map { it to acquisitionHouseCountDecision(it) }
        decisions.filter { it.second == null }.forEach { missing += "${it.first.name}: 취득세 주택 수 포함 여부를 확인해야 합니다." }
        val houseCountAfter = decisions.count { it.second == true }
        val householdRatio = householdRatio(property)
        val fullPrice = property.acquisitionPrice
        val taxBase = (fullPrice * householdRatio).roundToLong()
        val regulated = property.regulatedAreaAtAcquisition
        if (houseCountAfter >= 2 && regulated == null) missing += "취득 당시 조정대상지역 여부가 없어 다주택 취득 중과를 확정할 수 없습니다."

        val temporaryRelief = when (property.acquisitionSurchargeRelief) {
            AcquisitionSurchargeRelief.NONE -> false
            AcquisitionSurchargeRelief.TEMPORARY_TWO_HOME -> {
                val disposal = property.previousHomeDispositionDate
                when {
                    houseCountAfter != 2 -> {
                        missing += "일시적 2주택 특례는 취득 후 2주택인 경우에만 적용할 수 있습니다."
                        false
                    }
                    disposal == null -> {
                        missing += "일시적 2주택 특례의 종전주택 처분일이 없어 일반 다주택 세율로 계산했습니다."
                        false
                    }
                    disposal.isAfter(property.acquisitionDate.plusYears(3)) -> {
                        missing += "종전주택 처분일이 신규주택 취득일부터 3년을 초과해 일시적 2주택 특례를 적용하지 않았습니다."
                        false
                    }
                    else -> true
                }
            }
        }
        val surchargeRate = if (temporaryRelief) null else when {
            regulated == true && houseCountAfter >= 3 -> .12
            regulated == true && houseCountAfter == 2 -> .08
            regulated == false && houseCountAfter >= 4 -> .12
            regulated == false && houseCountAfter == 3 -> .08
            else -> null
        }
        val standardRate = when {
            fullPrice <= 600_000_000L -> .01
            fullPrice <= 900_000_000L -> continuousAcquisitionRate(fullPrice)
            else -> .03
        }
        val rate = surchargeRate ?: standardRate
        val acquisitionTax = (taxBase * rate).roundToLong()
        val educationTax = if (surchargeRate != null) (taxBase * .004).roundToLong() else (acquisitionTax * .1).roundToLong()
        val rural = property.acquisitionRuralSpecialTax?.coerceAtLeast(0L) ?: 0L
        if (property.acquisitionRuralSpecialTax == null) missing += "전용면적·감면 정보를 자동 추정하지 않고 농어촌특별세를 합계에서 제외했습니다. 고지서 또는 세무 계산값을 입력하세요."
        val total = acquisitionTax + educationTax + rural
        return TaxCalculation(
            AcquisitionTaxResult(acquisitionTax, educationTax, rural, total, total.toDouble() / taxBase.coerceAtLeast(1)),
            listOf(
                AppliedTaxRule(if (surchargeRate == null) "ACQ_STANDARD_HOME" else "ACQ_MULTI_HOME", true, if (surchargeRate == null) "전체 주택가액으로 세율 구간을 정하고 취득 지분가액에 표준세율을 적용했습니다." else "취득세상 $houseCountAfter 주택·조정대상지역 여부에 따른 ${(rate * 100).format(0)}% 중과세율을 적용했습니다.", ruleVersion.sources[0].url),
                AppliedTaxRule("ACQ_TEMPORARY_TWO_HOME", temporaryRelief, if (temporaryRelief) "취득 후 2주택이고 3년 내 종전주택 처분일이 확인되어 표준세율을 적용했습니다." else "일시적 2주택 특례를 적용하지 않았습니다.", ruleVersion.sources[1].url),
            ),
            listOf(
                CalculationTrace("전체 주택 취득가", fullPrice, "세율 구간 판정"),
                CalculationTrace("지분 반영 취득 과세표준", taxBase),
                CalculationTrace("취득세상 주택 수", houseCountAfter.toLong()),
                CalculationTrace("취득세", acquisitionTax, "× ${(rate * 100).format(4)}%"),
                CalculationTrace("지방교육세", educationTax),
                CalculationTrace("농어촌특별세", rural),
                CalculationTrace("예상 취득 단계 세금", total, "합계"),
            ),
            missing.distinct(),
            if (missing.isEmpty()) CalculationConfidence.MEDIUM else CalculationConfidence.NEEDS_REVIEW,
            ruleVersion,
        )
    }

    /** 명시적 주택 수 입력은 외부 Golden Case 호환용이며 앱 계산 경로에서는 포트폴리오 판정을 사용한다. */
    fun acquisition(property: PropertyAsset, houseCountAfter: Int): TaxCalculation<AcquisitionTaxResult> {
        val placeholders = (1 until houseCountAfter).map { index ->
            property.copy(id = "count-placeholder-$index", acquisitionDate = property.acquisitionDate.minusDays(1), acquisitionPrice = 0L)
        }
        return acquisition(property, placeholders)
    }

    fun holding(properties: List<PropertyAsset>, taxYear: Int): TaxCalculation<HoldingTaxResult> {
        if (!supports(taxYear)) return unavailableHolding(taxYear)
        val taxDate = LocalDate.of(taxYear, 6, 1)
        val owned = properties.filter {
            it.status == PropertyStatus.OWNED && it.acquisitionDate <= taxDate && it.propertyType in setOf(PropertyType.APARTMENT, PropertyType.HOUSE)
        }
        val missing = mutableListOf<String>()
        properties.filter { it.status == PropertyStatus.OWNED && it.propertyType !in setOf(PropertyType.APARTMENT, PropertyType.HOUSE, PropertyType.PRESALE_RIGHT, PropertyType.ASSOCIATION_RIGHT) }
            .forEach { missing += "${it.name}: 실제 주거용 과세 여부를 확인할 수 없어 보유세 합계에서 제외했습니다." }
        val oneHome = owned.size == 1
        val perProperty = owned.map { property ->
            val assessed = property.officialAssessedValue
            if (assessed == null) missing += "${property.name}: 공시가격이 없어 보유세 계산에서 제외했습니다."
            val fullAssessed = assessed ?: 0L
            val fairRatio = propertyFairRatio(oneHome, fullAssessed)
            val base = ownerShares(property).sumOf { (fullAssessed * it.ratio * fairRatio).roundToLong() }
            val propertyTax = ownerShares(property).sumOf { progressivePropertyTax((fullAssessed * it.ratio * fairRatio).roundToLong(), oneHome && fullAssessed <= 900_000_000L) }
            val education = (propertyTax * .20).roundToLong()
            val urban = when (property.urbanAreaTaxApplicable) {
                true -> (base * .0014).roundToLong()
                false -> 0L
                null -> {
                    missing += "${property.name}: 도시지역분 과세 대상 여부가 없어 도시지역분을 합계에서 제외했습니다."
                    0L
                }
            }
            val regional = property.annualRegionalResourceTax?.let { (it * householdRatio(property)).roundToLong() } ?: run {
                missing += "${property.name}: 연간 지역자원시설세가 없어 합계에서 제외했습니다."
                0L
            }
            PropertyHoldingTax(property.id, propertyTax, education, urban, propertyTax + education + urban + regional, regional)
        }

        val comprehensiveAssets = owned.filter { comprehensiveTaxDecision(it) }
        if (owned.size >= 2 && owned.any { it.comprehensiveTaxTreatment == TaxTreatment.AUTO }) {
            missing += "합산배제·상속·지방 저가주택 등 종부세 주택 수 특례는 자동 확정할 수 없습니다. 해당 주택의 종부세 처리 방식을 확인하세요."
        }
        val ownerAssessed = OwnerRole.entries.associateWith { owner ->
            comprehensiveAssets.sumOf { property -> ((property.officialAssessedValue ?: 0L) * ownerShare(property, owner)).roundToLong() }
        }
        val ownerHouseCount = OwnerRole.entries.associateWith { owner -> comprehensiveAssets.count { ownerShare(it, owner) > 0.0 } }
        val ownerPropertyTax = OwnerRole.entries.associateWith { owner ->
            owned.sumOf { property ->
                val full = property.officialAssessedValue ?: 0L
                val fair = propertyFairRatio(oneHome, full)
                progressivePropertyTax((full * ownerShare(property, owner) * fair).roundToLong(), oneHome && full <= 900_000_000L)
            }
        }
        val separate = OwnerRole.entries.sumOf { owner ->
            val assessed = ownerAssessed.getValue(owner)
            val baseValue = (max(0L, assessed - 900_000_000L) * .60).roundToLong()
            val gross = progressiveComprehensiveTax(baseValue, ownerHouseCount.getValue(owner) >= 3)
            val credit = comprehensivePropertyTaxCredit(ownerPropertyTax.getValue(owner), assessed, baseValue)
            max(0L, gross - credit)
        }

        val jointProperty = comprehensiveAssets.singleOrNull()
        val jointEligible = jointProperty != null && jointProperty.ownershipRatio > 0 && jointProperty.spouseOwnershipRatio > 0 && householdRatio(jointProperty) >= .9999
        val fullAssessed = comprehensiveAssets.sumOf { ((it.officialAssessedValue ?: 0L) * householdRatio(it)).roundToLong() }
        val specialTaxpayer = jointProperty?.let(::jointSpecialTaxpayer)
        val birthDate = when (specialTaxpayer) {
            OwnerRole.OWNER -> jointProperty?.ownerBirthDate
            OwnerRole.SPOUSE -> jointProperty?.spouseBirthDate
            null -> null
        }
        val age = birthDate?.let { Period.between(it, taxDate).years }
        val ageCredit = when {
            age == null || age < 60 -> 0
            age < 65 -> 20
            age < 70 -> 30
            else -> 40
        }
        val specialHoldingYears = jointProperty?.let { fullYears(it.acquisitionDate, taxDate) } ?: 0
        val holdingCredit = when {
            specialHoldingYears < 5 -> 0
            specialHoldingYears < 10 -> 20
            specialHoldingYears < 15 -> 40
            else -> 50
        }
        val specialCreditPercent = min(80, ageCredit + holdingCredit)
        val special = if (jointEligible) {
            val baseValue = (max(0L, fullAssessed - 1_200_000_000L) * .60).roundToLong()
            val gross = progressiveComprehensiveTax(baseValue, false)
            val propertyTax = perProperty.sumOf(PropertyHoldingTax::propertyTax)
            val credit = comprehensivePropertyTaxCredit(propertyTax, fullAssessed, baseValue)
            (max(0L, gross - credit) * (100 - specialCreditPercent) / 100.0).roundToLong()
        } else null
        if (jointEligible && specialTaxpayer == null) missing += "공동명의 지분이 같아 특례 납세의무자를 선택해야 합니다. 선택 전에는 고령자 공제를 적용하지 않았습니다."
        if (jointEligible && birthDate == null) missing += "공동명의 1주택 특례 납세의무자의 생년월일이 없어 과세기준일 만 나이 공제를 적용하지 않았습니다."
        val specialRequested = jointProperty?.jointComprehensiveTaxSpecialRequested == true
        if (specialRequested && !jointEligible) missing += "공동명의 1주택 특례를 신청으로 설정했지만 법정 공동명의 1주택 구조가 아닙니다."
        val method = if (specialRequested && special != null) JointHoldingTaxMethod.ONE_HOME_SPECIAL else JointHoldingTaxMethod.SEPARATE
        val comprehensive = if (method == JointHoldingTaxMethod.ONE_HOME_SPECIAL) special ?: separate else separate
        val deduction = if (method == JointHoldingTaxMethod.ONE_HOME_SPECIAL) 1_200_000_000L else 900_000_000L * ownerAssessed.values.count { it > 0 }
        val comprehensiveBase = if (method == JointHoldingTaxMethod.ONE_HOME_SPECIAL) {
            (max(0L, fullAssessed - 1_200_000_000L) * .60).roundToLong()
        } else {
            ownerAssessed.values.sumOf { (max(0L, it - 900_000_000L) * .60).roundToLong() }
        }
        val urbanTotal = perProperty.sumOf(PropertyHoldingTax::urbanAreaTax)
        val regionalTotal = perProperty.sumOf(PropertyHoldingTax::regionalResourceTax)
        val additional = perProperty.sumOf(PropertyHoldingTax::localEducationTax) + urbanTotal + regionalTotal + (comprehensive * .20).roundToLong()
        val result = HoldingTaxResult(
            taxYear = taxYear,
            properties = perProperty,
            propertyTax = perProperty.sumOf(PropertyHoldingTax::propertyTax),
            comprehensiveRealEstateTax = comprehensive,
            additionalTax = additional,
            totalTax = perProperty.sumOf(PropertyHoldingTax::propertyTax) + comprehensive + additional,
            separateComprehensiveTax = separate,
            jointSpecialComprehensiveTax = special,
            selectedJointTaxMethod = method,
            jointSpecialCreditPercent = if (method == JointHoldingTaxMethod.ONE_HOME_SPECIAL) specialCreditPercent else 0,
            urbanAreaTax = urbanTotal,
            regionalResourceTax = regionalTotal,
        )
        return TaxCalculation(
            result,
            listOf(
                AppliedTaxRule("HOLDING_FAIR_VALUE", true, if (oneHome) "2026년 1세대 1주택 공정시장가액비율 43~45%를 적용했습니다." else "2026년 주택 공정시장가액비율 60%를 적용했습니다.", ruleVersion.sources[2].url),
                AppliedTaxRule("PROPERTY_TAX_RATE", true, "주택 재산세와 입력된 도시지역분 대상 여부·지역자원시설세를 분리 적용했습니다.", ruleVersion.sources[3].url),
                AppliedTaxRule("COMPREHENSIVE_HOME", comprehensiveBase > 0, "납세의무자별 공시가격·주택 수로 세율을 정하고 법정 재산세 공제 산식을 적용했습니다.", ruleVersion.sources[4].url),
                AppliedTaxRule("JOINT_ONE_HOME_SPECIAL", method == JointHoldingTaxMethod.ONE_HOME_SPECIAL, if (method == JointHoldingTaxMethod.ONE_HOME_SPECIAL) "사용자가 신청으로 설정한 공동명의 1주택 특례를 적용했습니다." else "공동명의 특례 비교값만 제공하고 실제 합계에는 개별 과세를 적용했습니다.", ruleVersion.sources[9].url),
            ),
            listOf(
                CalculationTrace("부부 지분 반영 공시가격", ownerAssessed.values.sum()),
                CalculationTrace("종부세 기본공제 합계", deduction),
                CalculationTrace("종부세 과세표준", comprehensiveBase, "× 60%"),
                CalculationTrace("공동명의 특례 공제율", specialCreditPercent.toLong(), "%"),
                CalculationTrace("공동명의 개별 과세 종부세", separate),
                CalculationTrace("공동명의 1주택 특례 종부세", special ?: 0),
                CalculationTrace("재산세", result.propertyTax),
                CalculationTrace("도시지역분", urbanTotal),
                CalculationTrace("지역자원시설세", regionalTotal),
                CalculationTrace("종합부동산세", comprehensive),
                CalculationTrace("부가세·지방세", additional),
                CalculationTrace("연간 예상 보유세", result.totalTax, "합계"),
            ),
            (missing + "세부담상한·기타 감면·지방자치단체별 탄력세율은 입력 정보가 없어 반영하지 않았습니다.").distinct(),
            CalculationConfidence.NEEDS_REVIEW,
            ruleVersion,
        )
    }

    fun capitalGains(input: SaleSimulationInput): TaxCalculation<SaleTaxResult> {
        if (!supports(input.expectedSaleDate)) return unavailableSale(input.expectedSaleDate)
        val property = input.property
        if (property.propertyType !in setOf(PropertyType.APARTMENT, PropertyType.HOUSE)) {
            return TaxCalculation(
                emptySaleResult(), emptyList(), emptyList(),
                listOf("현재 양도세 엔진은 주택 양도만 지원합니다. ${property.propertyType.label}은 계산하지 않았습니다."),
                CalculationConfidence.NEEDS_REVIEW, ruleVersion, calculationAvailable = false,
            )
        }
        val missing = mutableListOf<String>()
        val shares = ownerShares(property)
        val householdRatio = shares.sumOf(OwnerShare::ratio).coerceAtMost(1.0)
        val redevelopmentBasis = if (property.redevelopmentHistory) {
            max(0L, property.acquisitionPrice + property.additionalContribution - property.settlementRefund)
        } else property.acquisitionPrice
        val acquisition = (redevelopmentBasis * householdRatio).roundToLong()
        val sale = (input.expectedSalePrice * householdRatio).roundToLong()
        val expenses = (((property.actualAcquisitionTax ?: 0L) + property.brokerageFee + property.legalFee + property.renovationCost +
            property.otherNecessaryExpenses + property.redevelopmentNecessaryExpenses + input.additionalNecessaryExpenses) * householdRatio).roundToLong()
        val gain = max(0L, sale - acquisition - expenses)
        val holdingYears = fullYears(property.acquisitionDate, input.expectedSaleDate)
        val assets = input.portfolioAssets.ifEmpty { listOf(property) }.filter { it.status == PropertyStatus.OWNED && it.acquisitionDate <= input.expectedSaleDate }
        val countDecisions = assets.associateWith(::capitalGainsHouseCountDecision)
        countDecisions.filterValues { it == null }.keys.forEach { missing += "${it.name}: 양도세 주택 수 포함 여부를 확인해야 합니다." }
        val houseCount = if (input.portfolioAssets.isEmpty()) input.portfolioHouseCountAtSale else countDecisions.values.count { it == true }
        if (input.portfolioAssets.isEmpty() && input.portfolioHouseCountAtSale > 1) missing += "주택 수만 입력되어 분양권·입주권·주택별 제외 규정을 검증하지 못했습니다."
        val homes = assets.filter { it.propertyType in setOf(PropertyType.APARTMENT, PropertyType.HOUSE) && countDecisions[it] == true }
        val rights = assets.filter { it.propertyType == PropertyType.PRESALE_RIGHT && countDecisions[it] == true }
        val right = rights.singleOrNull()
        val presaleDirectSpecial = homes.size == 1 && homes.single().id == property.id && right != null &&
            !right.acquisitionDate.isBefore(property.acquisitionDate.plusYears(1)) &&
            !input.expectedSaleDate.isAfter(right.acquisitionDate.plusYears(3))
        val completion = right?.expectedCompletionDate
        val moveIn = input.completedHomeMoveInDate
        val residenceEnd = input.completedHomeResidenceEndDate
        val presaleCompletionSpecial = homes.size == 1 && homes.single().id == property.id && right != null && completion != null && moveIn != null && residenceEnd != null &&
            !input.expectedSaleDate.isAfter(completion.plusYears(3)) &&
            !moveIn.isBefore(completion) && !moveIn.isAfter(completion.plusYears(3)) &&
            !residenceEnd.isBefore(moveIn.plusYears(1))
        if (input.postCompletionPresaleSpecialEligible && !presaleCompletionSpecial) {
            missing += "기존 완공 후 특례 체크값은 더 이상 단독 근거로 사용하지 않습니다. 완공일·이사일·1년 계속 거주 확인일을 모두 입력해야 합니다."
        }
        val presaleSpecial = presaleDirectSpecial || presaleCompletionSpecial
        val holdingQualified = !input.expectedSaleDate.isBefore(property.acquisitionDate.plusYears(2))
        val residenceYears = property.residenceStartDate?.let { start ->
            val end = minOf(property.residenceEndDate ?: input.expectedSaleDate, input.expectedSaleDate)
            fullYears(start, end)
        } ?: 0
        val residenceRequirementApplies = property.regulatedAreaAtAcquisition == true &&
            !property.acquisitionDate.isBefore(LocalDate.of(2017, 8, 3)) && !property.residenceRequirementExempt
        val residenceQualified = when {
            property.regulatedAreaAtAcquisition == null -> false
            residenceRequirementApplies -> residenceYears >= 2
            else -> true
        }
        val oneHomeCandidate = (homes.size == 1 && rights.isEmpty()) || presaleSpecial
        val oneHomeExemption = oneHomeCandidate && holdingQualified && residenceQualified
        val highPriceRatio = if (oneHomeExemption) max(0.0, (input.expectedSalePrice - 1_200_000_000L).toDouble() / input.expectedSalePrice.coerceAtLeast(1)) else 1.0
        val taxableRawGain = (gain * highPriceRatio).roundToLong()

        val grace = if (!oneHomeExemption && houseCount >= 2 && input.regulatedAreaAtSale == true) surchargeGraceDecision(input, property) else GraceDecision(false)
        missing += grace.missing
        if (input.surchargeGraceEligible && !grace.eligible) missing += "기존 중과 유예 체크값은 더 이상 단독 근거로 사용하지 않습니다. 계약·계약금·토지거래허가·양도기한을 모두 검증해야 합니다."
        val surchargeExcluded = property.capitalGainsSurchargeTreatment == TaxTreatment.EXCLUDED
        if (!oneHomeExemption && houseCount >= 2 && property.capitalGainsSurchargeTreatment == TaxTreatment.AUTO) {
            missing += "저가주택·임대주택·부득이한 사유 등 다주택 중과 제외주택 여부를 확인해야 합니다."
        }
        val surchargePercent = if (!oneHomeExemption && input.regulatedAreaAtSale == true && !grace.eligible && !surchargeExcluded) when {
            houseCount >= 3 -> 30
            houseCount == 2 -> 20
            else -> 0
        } else 0
        if (input.regulatedAreaAtSale == null && houseCount >= 2) missing += "양도 당시 조정대상지역 여부가 없어 다주택 중과 적용을 확정할 수 없습니다."
        val deductionRate = when {
            oneHomeExemption && residenceYears >= 2 -> (min(40, holdingYears * 4) + min(40, if (residenceYears == 2) 8 else residenceYears * 4)) / 100.0
            surchargePercent > 0 -> 0.0
            holdingYears >= 3 -> min(30, holdingYears * 2) / 100.0
            else -> 0.0
        }
        val longTerm = (taxableRawGain * deductionRate).roundToLong()
        val taxableGain = max(0L, taxableRawGain - longTerm)
        var basic = 0L
        var base = 0L
        var national = 0L
        val ownerDeductions = mutableMapOf<OwnerRole, Long>()
        shares.forEach { owner ->
            val ownerTaxable = (taxableGain * (owner.ratio / householdRatio.coerceAtLeast(.0001))).roundToLong()
            val alreadyUsed = when (owner.role) {
                OwnerRole.OWNER -> input.ownerBasicDeductionUsed
                OwnerRole.SPOUSE -> input.spouseBasicDeductionUsed
            }.coerceIn(0L, 2_500_000L)
            val ownerBasic = min(max(0L, 2_500_000L - alreadyUsed), ownerTaxable)
            val ownerBase = max(0L, ownerTaxable - ownerBasic)
            val progressive = progressiveIncomeTax(ownerBase) + (ownerBase * surchargePercent / 100.0).roundToLong()
            val shortTerm = when {
                input.expectedSaleDate.isBefore(property.acquisitionDate.plusYears(1)) -> (ownerBase * .70).roundToLong()
                input.expectedSaleDate.isBefore(property.acquisitionDate.plusYears(2)) -> (ownerBase * .60).roundToLong()
                else -> 0L
            }
            ownerDeductions[owner.role] = ownerBasic
            basic += ownerBasic
            base += ownerBase
            national += max(progressive, shortTerm)
        }
        val local = (national * .10).roundToLong()
        if (rights.isNotEmpty() && !presaleSpecial) missing += "분양권이 주택 수에 포함되며 1주택+1분양권 비과세 특례의 양도 또는 완공 후 입주·계속 거주 요건을 충족하지 못했습니다."
        if (property.regulatedAreaAtAcquisition == null) missing += "취득 당시 조정대상지역 여부를 입력해야 1세대 1주택 거주요건을 판정할 수 있습니다."
        if (property.redevelopmentHistory) {
            missing += "재개발 양도 취득원가의 추가분담금·청산금 안분은 관리처분계획과 조합 정산서 원본을 기준으로 최종 세무 검증이 필요합니다."
            if (property.managementDispositionApprovalDate == null) missing += "재개발 관리처분계획 인가일이 없어 기존 주택에서 조합원입주권으로 전환된 시점을 검증하지 못했습니다."
            if (property.demolitionDate == null) missing += "기존 주택 철거·멸실일이 없어 주택과 조합원입주권의 기간 구분을 검증하지 못했습니다."
            if (property.redevelopmentCompletionDate == null) missing += "신축 주택 사용승인·준공일이 없어 신축주택 취득 시점과 입주 기간을 검증하지 못했습니다."
            if (property.actualAcquisitionTax == null) missing += "신축 주택의 실제 취득세가 없어 양도세 필요경비에서 제외했습니다."
            if (property.settlementRefund > property.acquisitionPrice + property.additionalContribution) missing += "청산금 환급액이 기존 주택 취득가와 추가분담금 합계를 초과합니다. 입력값을 확인하세요."
            if (property.managementDispositionApprovalDate?.isBefore(property.acquisitionDate) == true) missing += "기존 주택 취득일보다 관리처분계획 인가일이 빠릅니다. 원조합원이 아니라 승계 조합원입주권 취득인지 확인하세요."
            if (property.demolitionDate?.isBefore(property.acquisitionDate) == true) missing += "기존 주택 취득일보다 철거·멸실일이 빠릅니다. 자산 유형과 취득일을 확인하세요."
            if (property.demolitionDate != null && property.redevelopmentCompletionDate?.isBefore(property.demolitionDate) == true) missing += "신축 주택 준공일이 철거·멸실일보다 빠릅니다. 날짜를 확인하세요."
        }
        if (residenceRequirementApplies && property.residenceStartDate == null) missing += "실거주 시작일이 없어 조정대상지역 취득 주택의 2년 거주요건을 충족하지 못한 것으로 계산했습니다."
        if (!residenceRequirementApplies && property.residenceStartDate == null && oneHomeCandidate && input.expectedSalePrice > 1_200_000_000L) missing += "실거주기간을 입력하면 고가 1주택 장기보유특별공제율이 달라질 수 있습니다."
        if (expenses == 0L) missing += "증빙 가능한 필요경비가 입력되지 않았습니다."
        val result = SaleTaxResult(acquisition, sale, expenses, gain, longTerm, taxableGain, basic, base, national, local, national + local, houseCount, oneHomeExemption, presaleSpecial && oneHomeExemption, surchargePercent, ownerDeductions)
        return TaxCalculation(
            result,
            listOf(
                AppliedTaxRule("ONE_HOME_PRESALE_SPECIAL", presaleSpecial, if (presaleCompletionSpecial) "완공일·3년 내 이사·1년 계속 거주·종전주택 양도기한을 날짜로 검증했습니다." else "종전주택 취득 1년 후 분양권 취득 및 분양권 취득일부터 3년 이내 양도를 판정했습니다.", ruleVersion.sources[7].url),
                AppliedTaxRule("ONE_HOME_EXEMPTION", oneHomeExemption, if (oneHomeExemption) "정확한 날짜 기준 2년 보유와 필요한 거주요건을 충족해 12억원 기준을 적용했습니다." else "1세대 1주택 비과세 요건을 충족하지 못했습니다.", ruleVersion.sources[6].url),
                AppliedTaxRule("MULTI_HOME_SURCHARGE_GRACE", grace.eligible, if (grace.eligible) "계약·계약금·허가 및 법정 양도기한을 검증해 2026년 중과 유예를 적용했습니다." else "다주택 중과 유예를 적용하지 않았습니다.", ruleVersion.sources[8].url),
                AppliedTaxRule("MULTI_HOME_SURCHARGE", surchargePercent > 0, if (surchargePercent > 0) "조정대상지역 다주택 중과 +${surchargePercent}%p를 적용하고 장기보유특별공제를 배제했습니다." else "다주택 중과를 적용하지 않았습니다.", ruleVersion.sources[8].url),
                AppliedTaxRule("LONG_TERM_DEDUCTION", deductionRate > 0, if (oneHomeExemption) "정확한 보유·거주기간으로 1세대 1주택 공제율을 계산했습니다." else "정확한 보유기간으로 일반 장기보유특별공제를 계산했습니다.", ruleVersion.sources[5].url),
                AppliedTaxRule("REDEVELOPMENT_ORIGINAL_MEMBER", property.redevelopmentHistory, if (property.redevelopmentHistory) "기존 주택 취득가에 추가분담금을 더하고 청산금 환급액을 차감해 양도세 취득원가를 구성했습니다. 관리처분·멸실·준공일의 법적 효과는 AI 공식 법령 검증 대상으로 남겼습니다." else "재개발 승계 이력이 없는 일반 주택입니다.", ruleVersion.sources[10].url),
            ),
            listOf(
                CalculationTrace("세대 주택·분양권 수", houseCount.toLong()),
                CalculationTrace("정확한 보유연수", holdingYears.toLong()),
                CalculationTrace("부부 지분 반영 양도가", sale),
                CalculationTrace("부부 지분 반영 취득가", acquisition, "차감"),
                CalculationTrace("재개발 추가분담금", if (property.redevelopmentHistory) (property.additionalContribution * householdRatio).roundToLong() else 0L, "취득원가 가산"),
                CalculationTrace("재개발 청산금 환급", if (property.redevelopmentHistory) (property.settlementRefund * householdRatio).roundToLong() else 0L, "취득원가 차감"),
                CalculationTrace("필요경비", expenses, "차감"),
                CalculationTrace("전체 양도차익", gain),
                CalculationTrace("12억원 초과 과세대상 양도차익", taxableRawGain, if (oneHomeExemption) "고가주택 안분" else "비과세 미적용"),
                CalculationTrace("장기보유특별공제", longTerm, "${(deductionRate * 100).format(1)}%"),
                CalculationTrace("소유자별 연간 기본공제 합계", basic, "기사용액 차감 후"),
                CalculationTrace("과세표준 합계", base),
                CalculationTrace("다주택 중과", surchargePercent.toLong(), "%p"),
                CalculationTrace("국세 합계", national),
                CalculationTrace("지방소득세", local, "국세 × 10%"),
                CalculationTrace("예상 양도세", result.totalEstimatedTax, "부부 합계"),
            ),
            missing.distinct(),
            if (missing.isEmpty()) CalculationConfidence.MEDIUM else CalculationConfidence.NEEDS_REVIEW,
            ruleVersion,
        )
    }

    private fun supports(date: LocalDate): Boolean = !date.isBefore(ruleVersion.effectiveFrom) && (ruleVersion.effectiveUntil == null || !date.isAfter(ruleVersion.effectiveUntil))
    private fun supports(year: Int): Boolean = year == ruleVersion.effectiveFrom.year && (ruleVersion.effectiveUntil?.year == null || year <= ruleVersion.effectiveUntil.year)

    private fun unavailableAcquisition(date: LocalDate) = TaxCalculation(
        AcquisitionTaxResult(0, 0, 0, 0, 0.0), emptyList(), emptyList(),
        listOf("$date 취득에 적용할 검증된 Rule Version이 없습니다. 2026년 규칙을 임의 적용하지 않았습니다."),
        CalculationConfidence.NEEDS_REVIEW, ruleVersion, calculationAvailable = false,
    )

    private fun unavailableHolding(year: Int) = TaxCalculation(
        HoldingTaxResult(year, emptyList(), 0, 0, 0, 0), emptyList(), emptyList(),
        listOf("${year}년 보유세에 적용할 검증된 Rule Version이 없습니다. 2026년 규칙을 임의 적용하지 않았습니다."),
        CalculationConfidence.NEEDS_REVIEW, ruleVersion, calculationAvailable = false,
    )

    private fun unavailableSale(date: LocalDate) = TaxCalculation(
        emptySaleResult(), emptyList(), emptyList(),
        listOf("$date 양도에 적용할 검증된 Rule Version이 없습니다. 2026년 규칙을 임의 적용하지 않았습니다."),
        CalculationConfidence.NEEDS_REVIEW, ruleVersion, calculationAvailable = false,
    )

    private fun emptySaleResult() = SaleTaxResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    private fun ownerShares(property: PropertyAsset): List<OwnerShare> = buildList {
        if (property.ownershipRatio > 0) add(OwnerShare(OwnerRole.OWNER, property.ownershipRatio.coerceIn(0.0, 1.0)))
        if (property.spouseOwnershipRatio > 0) add(OwnerShare(OwnerRole.SPOUSE, property.spouseOwnershipRatio.coerceIn(0.0, 1.0)))
    }

    private fun ownerShare(property: PropertyAsset, owner: OwnerRole): Double = when (owner) {
        OwnerRole.OWNER -> property.ownershipRatio
        OwnerRole.SPOUSE -> property.spouseOwnershipRatio
    }.coerceIn(0.0, 1.0)

    private fun householdRatio(property: PropertyAsset): Double = ownerShares(property).sumOf(OwnerShare::ratio).coerceIn(0.0, 1.0)

    private fun acquisitionHouseCountDecision(property: PropertyAsset): Boolean? = when (property.acquisitionHouseCountTreatment) {
        TaxTreatment.INCLUDED -> true
        TaxTreatment.EXCLUDED -> false
        TaxTreatment.AUTO -> when (property.propertyType) {
            PropertyType.APARTMENT, PropertyType.HOUSE, PropertyType.ASSOCIATION_RIGHT -> true
            PropertyType.PRESALE_RIGHT -> !property.acquisitionDate.isBefore(LocalDate.of(2020, 8, 12))
            PropertyType.OFFICETEL -> null
            else -> false
        }
    }

    private fun capitalGainsHouseCountDecision(property: PropertyAsset): Boolean? = when (property.capitalGainsHouseCountTreatment) {
        TaxTreatment.INCLUDED -> true
        TaxTreatment.EXCLUDED -> false
        TaxTreatment.AUTO -> when (property.propertyType) {
            PropertyType.APARTMENT, PropertyType.HOUSE -> true
            PropertyType.PRESALE_RIGHT, PropertyType.ASSOCIATION_RIGHT -> !property.acquisitionDate.isBefore(LocalDate.of(2021, 1, 1))
            PropertyType.OFFICETEL -> null
            else -> false
        }
    }

    private fun comprehensiveTaxDecision(property: PropertyAsset): Boolean = when (property.comprehensiveTaxTreatment) {
        TaxTreatment.INCLUDED -> true
        TaxTreatment.EXCLUDED -> false
        TaxTreatment.AUTO -> property.propertyType in setOf(PropertyType.APARTMENT, PropertyType.HOUSE)
    }

    private fun jointSpecialTaxpayer(property: PropertyAsset): OwnerRole? = when {
        property.ownershipRatio > property.spouseOwnershipRatio -> OwnerRole.OWNER
        property.spouseOwnershipRatio > property.ownershipRatio -> OwnerRole.SPOUSE
        else -> property.jointSpecialTaxpayer
    }

    private fun surchargeGraceDecision(input: SaleSimulationInput, property: PropertyAsset): GraceDecision {
        if (input.expectedSaleDate.isBefore(property.acquisitionDate.plusYears(2))) return GraceDecision(false)
        val graceEnd = LocalDate.of(2026, 5, 9)
        if (!input.expectedSaleDate.isAfter(graceEnd)) return GraceDecision(true)
        val contract = input.saleContractDate ?: return GraceDecision(false, listOf("2026년 다주택 중과 유예 판정에 매매계약일이 필요합니다."))
        if (!input.depositReceived) return GraceDecision(false, listOf("2026년 다주택 중과 유예 판정에 계약금 수령 증빙이 필요합니다."))
        val months = if (input.extendedSurchargeGraceRegion) 6L else 4L
        return when (input.landTransactionPermitRequired) {
            null -> GraceDecision(false, listOf("토지거래허가 대상 여부가 없어 2026년 중과 유예 경과규정을 판정할 수 없습니다."))
            false -> {
                val eligible = !contract.isAfter(graceEnd) && !input.expectedSaleDate.isAfter(contract.plusMonths(months))
                GraceDecision(eligible, if (eligible) emptyList() else listOf("비허가 대상 주택은 2026년 5월 9일까지 계약하고 계약일부터 ${months}개월 내 양도해야 합니다."))
            }
            true -> {
                val application = input.landTransactionPermitApplicationDate
                if (application == null || application.isAfter(graceEnd) || !input.landTransactionPermitApproved) {
                    GraceDecision(false, listOf("토지거래허가 대상 주택은 2026년 5월 9일까지 허가 신청 후 허가받은 사실이 필요합니다."))
                } else {
                    val regionalHardEnd = if (input.extendedSurchargeGraceRegion) LocalDate.of(2026, 11, 9) else LocalDate.of(2026, 9, 9)
                    val deadline = if (contract.isAfter(graceEnd)) minOf(contract.plusMonths(months), regionalHardEnd) else contract.plusMonths(months)
                    val eligible = !input.expectedSaleDate.isAfter(deadline)
                    GraceDecision(eligible, if (eligible) emptyList() else listOf("토지거래허가 대상 주택의 법정 양도기한 $deadline 을 초과했습니다."))
                }
            }
        }
    }

    private fun fullYears(start: LocalDate, end: LocalDate): Int = if (end.isBefore(start)) 0 else Period.between(start, end).years

    private fun continuousAcquisitionRate(fullPrice: Long): Double {
        val percent = BigDecimal.valueOf(fullPrice).multiply(BigDecimal.valueOf(2))
            .divide(BigDecimal.valueOf(300_000_000L), 10, RoundingMode.HALF_UP)
            .subtract(BigDecimal.valueOf(3))
            .setScale(4, RoundingMode.HALF_UP)
        return percent.divide(BigDecimal.valueOf(100)).toDouble()
    }

    private fun propertyFairRatio(oneHome: Boolean, assessed: Long): Double = if (oneHome) when {
        assessed <= 300_000_000L -> .43
        assessed <= 600_000_000L -> .44
        else -> .45
    } else .60

    private fun comprehensivePropertyTaxCredit(actualPropertyTax: Long, assessed: Long, comprehensiveBase: Long): Long {
        if (actualPropertyTax <= 0L || assessed <= 0L || comprehensiveBase <= 0L) return 0L
        val numerator = progressivePropertyTax((comprehensiveBase * .60).roundToLong(), false)
        val denominator = progressivePropertyTax((assessed * .60).roundToLong(), false)
        if (denominator <= 0L) return 0L
        return min(actualPropertyTax, (actualPropertyTax * numerator.toDouble() / denominator).roundToLong())
    }

    private fun progressivePropertyTax(base: Long, special: Boolean): Long = when {
        special && base <= 60_000_000L -> (base * .0005).roundToLong()
        special && base <= 150_000_000L -> 30_000L + ((base - 60_000_000L) * .001).roundToLong()
        special && base <= 300_000_000L -> 120_000L + ((base - 150_000_000L) * .002).roundToLong()
        special -> 420_000L + ((base - 300_000_000L) * .0035).roundToLong()
        base <= 60_000_000L -> (base * .001).roundToLong()
        base <= 150_000_000L -> 60_000L + ((base - 60_000_000L) * .0015).roundToLong()
        base <= 300_000_000L -> 195_000L + ((base - 150_000_000L) * .0025).roundToLong()
        else -> 570_000L + ((base - 300_000_000L) * .004).roundToLong()
    }

    private fun progressiveComprehensiveTax(base: Long, threeOrMore: Boolean): Long {
        val brackets = if (threeOrMore) {
            listOf(300_000_000L to .005, 600_000_000L to .007, 1_200_000_000L to .010, 2_500_000_000L to .020, 5_000_000_000L to .030, 9_400_000_000L to .040, Long.MAX_VALUE to .050)
        } else {
            listOf(300_000_000L to .005, 600_000_000L to .007, 1_200_000_000L to .010, 2_500_000_000L to .013, 5_000_000_000L to .015, 9_400_000_000L to .020, Long.MAX_VALUE to .027)
        }
        var previous = 0L
        var remaining = base
        var tax = 0.0
        for ((limit, rate) in brackets) {
            if (remaining <= 0) break
            val width = if (limit == Long.MAX_VALUE) remaining else min(remaining, limit - previous)
            tax += width * rate
            remaining -= width
            previous = limit
        }
        return tax.roundToLong()
    }

    private fun progressiveIncomeTax(base: Long): Long = when {
        base <= 14_000_000L -> (base * .06).roundToLong()
        base <= 50_000_000L -> (base * .15 - 1_260_000).roundToLong()
        base <= 88_000_000L -> (base * .24 - 5_760_000).roundToLong()
        base <= 150_000_000L -> (base * .35 - 15_440_000).roundToLong()
        base <= 300_000_000L -> (base * .38 - 19_940_000).roundToLong()
        base <= 500_000_000L -> (base * .40 - 25_940_000).roundToLong()
        base <= 1_000_000_000L -> (base * .42 - 35_940_000).roundToLong()
        else -> (base * .45 - 65_940_000).roundToLong()
    }.coerceAtLeast(0L)

    private fun Double.format(digits: Int) = "% .${digits}f".format(this).trim()
}
