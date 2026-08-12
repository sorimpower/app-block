package com.sorimpower.app.feature.propertytax.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PropertyTaxEngineTest {
    private val engine = PropertyTaxEngine()

    @Test fun `6억원 주택 표준 취득세를 추적 가능하게 계산한다`() {
        val calculation = engine.acquisition(property(price = 600_000_000, acquired = LocalDate.of(2026, 8, 1)).copy(acquisitionRuralSpecialTax = 0), 1)
        assertEquals(6_000_000, calculation.result.acquisitionTax)
        assertEquals(600_000, calculation.result.localEducationTax)
        assertEquals(6_600_000, calculation.result.totalTax)
        assertTrue(calculation.rules.any { it.ruleId == "ACQ_STANDARD_HOME" && it.applied })
    }

    @Test fun `지분 취득도 전체 주택가액으로 세율 구간을 판정한다`() {
        val target = property(price = 1_200_000_000, acquired = LocalDate.of(2026, 8, 1)).copy(
            ownershipRatio = .5,
            acquisitionRuralSpecialTax = 0,
        )
        val result = engine.acquisition(target, 1).result
        assertEquals(18_000_000, result.acquisitionTax)
    }

    @Test fun `비주택을 주택 취득세율로 계산하지 않는다`() {
        val target = property(acquired = LocalDate.of(2026, 8, 1)).copy(propertyType = PropertyType.COMMERCIAL)
        val calculation = engine.acquisition(target, emptyList())
        assertFalse(calculation.calculationAvailable)
        assertEquals(0, calculation.result.totalTax)
    }

    @Test fun `1주택 공시가격으로 재산세 도시지역분 지역자원시설세를 분리 계산한다`() {
        val calculation = engine.holding(
            listOf(property(assessed = 600_000_000).copy(urbanAreaTaxApplicable = true, annualRegionalResourceTax = 10_000)),
            2026,
        )
        assertEquals(348_000, calculation.result.propertyTax)
        assertEquals(369_600, calculation.result.urbanAreaTax)
        assertEquals(10_000, calculation.result.regionalResourceTax)
        assertEquals(797_200, calculation.result.totalTax)
    }

    @Test fun `도시지역 여부를 모르면 도시지역분을 임의 부과하지 않는다`() {
        val calculation = engine.holding(listOf(property(assessed = 600_000_000).copy(annualRegionalResourceTax = 0)), 2026)
        assertEquals(0, calculation.result.urbanAreaTax)
        assertTrue(calculation.missingInputs.any { it.contains("도시지역분") })
    }

    @Test fun `배우자 단독명의 주택을 본인 소유와 합산하지 않는다`() {
        val owner = property(assessed = 800_000_000).copy(id = "owner", ownershipRatio = 1.0, spouseOwnershipRatio = 0.0, urbanAreaTaxApplicable = false, annualRegionalResourceTax = 0)
        val spouse = property(assessed = 800_000_000).copy(id = "spouse", ownershipRatio = 0.0, spouseOwnershipRatio = 1.0, urbanAreaTaxApplicable = false, annualRegionalResourceTax = 0)
        assertEquals(0, engine.holding(listOf(owner, spouse), 2026).result.comprehensiveRealEstateTax)
    }

    @Test fun `종부세 3주택 세율은 세대가 아니라 납세의무자별 주택 수로 판정한다`() {
        fun asset(id: String, ownerRatio: Double, spouseRatio: Double) = property(assessed = 2_000_000_000).copy(
            id = id, ownershipRatio = ownerRatio, spouseOwnershipRatio = spouseRatio,
            urbanAreaTaxApplicable = false, annualRegionalResourceTax = 0,
        )
        val splitOwners = listOf(asset("o1", 1.0, 0.0), asset("o2", 1.0, 0.0), asset("s1", 0.0, 1.0))
        val oneOwner = splitOwners.map { it.copy(ownershipRatio = 1.0, spouseOwnershipRatio = 0.0) }
        assertTrue(engine.holding(splitOwners, 2026).result.comprehensiveRealEstateTax < engine.holding(oneOwner, 2026).result.comprehensiveRealEstateTax)
    }

    @Test fun `공동명의 특례는 신청하지 않으면 개별 과세를 실제 합계로 사용한다`() {
        val joint = property(assessed = 2_400_000_000).copy(
            ownershipRatio = .5, spouseOwnershipRatio = .5,
            ownerBirthDate = LocalDate.of(1950, 1, 1), spouseBirthDate = LocalDate.of(1955, 1, 1),
            jointSpecialTaxpayer = OwnerRole.OWNER, urbanAreaTaxApplicable = false, annualRegionalResourceTax = 0,
        )
        val result = engine.holding(listOf(joint), 2026).result
        assertEquals(JointHoldingTaxMethod.SEPARATE, result.selectedJointTaxMethod)
        assertEquals(result.separateComprehensiveTax, result.comprehensiveRealEstateTax)
        assertTrue(result.jointSpecialComprehensiveTax != null)
    }

    @Test fun `공동명의 특례 신청과 정확한 생년월일로 고령 장기보유 공제를 적용한다`() {
        val joint = property(assessed = 3_000_000_000, acquired = LocalDate.of(2008, 1, 1)).copy(
            ownershipRatio = .5, spouseOwnershipRatio = .5,
            ownerBirthDate = LocalDate.of(1950, 7, 1), spouseBirthDate = LocalDate.of(1955, 1, 1),
            jointSpecialTaxpayer = OwnerRole.OWNER, jointComprehensiveTaxSpecialRequested = true,
            urbanAreaTaxApplicable = false, annualRegionalResourceTax = 0,
        )
        val result = engine.holding(listOf(joint), 2026).result
        assertEquals(80, result.jointSpecialCreditPercent)
        assertEquals(JointHoldingTaxMethod.ONE_HOME_SPECIAL, result.selectedJointTaxMethod)
    }

    @Test fun `양도일이 취득일 1년 전이면 월 반올림 없이 70퍼센트 단기세율을 적용한다`() {
        val home = property(price = 500_000_000, acquired = LocalDate.of(2025, 8, 31)).copy(regulatedAreaAtAcquisition = false)
        val calculation = engine.capitalGains(SaleSimulationInput(home, LocalDate.of(2026, 8, 1), 600_000_000, 0, 1))
        assertEquals(68_250_000, calculation.result.nationalCapitalGainsTax)
    }

    @Test fun `취득일 2년 전 매도는 1주택 비과세를 적용하지 않는다`() {
        val home = property(price = 500_000_000, acquired = LocalDate.of(2024, 8, 31)).copy(regulatedAreaAtAcquisition = false)
        val calculation = engine.capitalGains(SaleSimulationInput(home, LocalDate.of(2026, 8, 1), 700_000_000, 0, 1))
        assertFalse(calculation.result.oneHomeExemptionApplied)
        assertTrue(calculation.result.totalEstimatedTax > 0)
    }

    @Test fun `같은 과세연도에 이미 기본공제를 썼으면 다시 공제하지 않는다`() {
        val home = property(price = 500_000_000, acquired = LocalDate.of(2020, 1, 1)).copy(regulatedAreaAtAcquisition = false)
        val calculation = engine.capitalGains(SaleSimulationInput(home, LocalDate.of(2026, 8, 1), 700_000_000, 0, 2, ownerBasicDeductionUsed = 2_500_000))
        assertEquals(0, calculation.result.basicDeduction)
    }

    @Test fun `1주택 취득 후 1년이 지나 분양권을 사고 3년 내 매도하면 특례를 판정한다`() {
        val home = property(price = 700_000_000, acquired = LocalDate.of(2020, 1, 1)).copy(regulatedAreaAtAcquisition = false)
        val right = property(price = 500_000_000, acquired = LocalDate.of(2024, 2, 1)).copy(id = "right", propertyType = PropertyType.PRESALE_RIGHT)
        val calculation = engine.capitalGains(SaleSimulationInput(home, LocalDate.of(2026, 8, 1), 1_100_000_000, 0, 2, listOf(home, right), regulatedAreaAtSale = true))
        assertTrue(calculation.result.oneHomePresaleSpecialApplied)
        assertTrue(calculation.result.oneHomeExemptionApplied)
        assertEquals(0, calculation.result.totalEstimatedTax)
    }

    @Test fun `완공 후 특례는 체크값 하나가 아니라 날짜 요건을 모두 검증한다`() {
        val home = property(price = 700_000_000, acquired = LocalDate.of(2020, 1, 1)).copy(regulatedAreaAtAcquisition = false)
        val right = property(price = 500_000_000, acquired = LocalDate.of(2022, 2, 1)).copy(id = "right", propertyType = PropertyType.PRESALE_RIGHT, expectedCompletionDate = LocalDate.of(2024, 6, 1))
        val unchecked = engine.capitalGains(SaleSimulationInput(home, LocalDate.of(2026, 8, 1), 1_100_000_000, 0, 2, listOf(home, right), regulatedAreaAtSale = true, postCompletionPresaleSpecialEligible = true))
        assertFalse(unchecked.result.oneHomePresaleSpecialApplied)
        val verified = engine.capitalGains(SaleSimulationInput(home, LocalDate.of(2026, 8, 1), 1_100_000_000, 0, 2, listOf(home, right), regulatedAreaAtSale = true, completedHomeMoveInDate = LocalDate.of(2024, 7, 1), completedHomeResidenceEndDate = LocalDate.of(2025, 7, 1)))
        assertTrue(verified.result.oneHomePresaleSpecialApplied)
    }

    @Test fun `2026년 5월 9일 이후 중과 유예는 계약과 기한을 검증한다`() {
        val home = property(price = 700_000_000, acquired = LocalDate.of(2020, 1, 1)).copy(regulatedAreaAtAcquisition = false, capitalGainsSurchargeTreatment = TaxTreatment.INCLUDED)
        val other = property(price = 500_000_000, acquired = LocalDate.of(2022, 2, 1)).copy(id = "other")
        val calculation = engine.capitalGains(SaleSimulationInput(home, LocalDate.of(2026, 8, 1), 1_500_000_000, 0, 2, listOf(home, other), regulatedAreaAtSale = true, saleContractDate = LocalDate.of(2026, 4, 30), depositReceived = true, landTransactionPermitRequired = false))
        assertEquals(0, calculation.result.multiHomeSurchargePercent)
        assertTrue(calculation.rules.any { it.ruleId == "MULTI_HOME_SURCHARGE_GRACE" && it.applied })
    }

    @Test fun `조정대상지역 3번째 주택 취득은 12퍼센트 중과한다`() {
        val target = property(price = 800_000_000, acquired = LocalDate.of(2026, 8, 1)).copy(regulatedAreaAtAcquisition = true, acquisitionRuralSpecialTax = 0)
        val calculation = engine.acquisition(target, 3)
        assertEquals(96_000_000, calculation.result.acquisitionTax)
        assertTrue(calculation.rules.any { it.ruleId == "ACQ_MULTI_HOME" })
    }

    @Test fun `검증된 세법 기간 밖에는 2026 규칙을 임의 적용하지 않는다`() {
        assertFalse(engine.holding(listOf(property(assessed = 600_000_000)), 2027).calculationAvailable)
        val sale = engine.capitalGains(SaleSimulationInput(property(), LocalDate.of(2027, 1, 1), 900_000_000, 0, 1))
        assertFalse(sale.calculationAvailable)
        assertEquals(0, sale.result.totalEstimatedTax)
    }

    private fun property(
        price: Long = 500_000_000,
        assessed: Long? = null,
        acquired: LocalDate = LocalDate.of(2022, 1, 1),
    ) = PropertyAsset(
        "p1", "테스트 아파트", PropertyType.APARTMENT, "", acquired, price, 1.0, assessed,
        null, null, 0, 0, 0, 0, null, null, PropertyStatus.OWNED,
    )
}
