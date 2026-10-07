package com.sorimpower.app.feature.propertytracker.data

import com.sorimpower.app.core.ai.AiModelId
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import com.sorimpower.app.core.ai.OpenAiProvider
import java.time.LocalDate

internal class PropertyAiAnalyzer(
    private val provider: OpenAiProvider = OpenAiProvider(),
) {
    suspend fun analyzeListings(
        target: PropertyWatchTargetEntity,
        listings: List<PropertyListingEntity>,
        trades: List<PropertyActualTradeEntity>,
    ): String {
        val groups = PropertyTrackerRepository.groupDuplicateListings(listings)
            .filter { it.representative.status == "ACTIVE" }
            .sortedBy { it.representative.priceKrw }
            .take(60)
        require(groups.isNotEmpty()) { "분석할 현재 매물이 없습니다." }
        val prompt = buildString {
            appendLine("아래는 사용자가 직접 추적 중인 ${target.apartmentName} ${target.exclusiveAreaSqm}㎡대 매물 데이터다.")
            appendLine("광고 문구를 사실로 단정하지 말고, 가격·동·층·방향·수리/인테리어·입주 가능 여부를 근거로 최적 후보를 분석하라.")
            appendLine("'로얄동/로얄층'은 제공된 문구만으로 확정하지 말고 근거가 부족하면 미확인이라고 명시하라.")
            appendLine("추천 1순위와 대안 2개를 제시하고, 각 후보의 장점·주의점·확인할 질문·실거래 대비 가격 차이를 한국어로 간결하게 작성하라.")
            appendLine("중복 중개사 등록 수는 인기나 품질의 증거로 쓰지 말라. 미래 가격 상승을 단정하지 말라.")
            appendLine("[최근 실거래]")
            trades.sortedByDescending(PropertyActualTradeEntity::tradeDate).take(20).forEach {
                appendLine("${it.tradeDate} | ${it.priceKrw}원 | ${it.floor}층 | 전용 ${it.exclusiveAreaSqm}㎡")
            }
            if (trades.isEmpty()) appendLine("조회된 실거래 없음")
            appendLine("[현재 매물]")
            groups.forEachIndexed { index, group ->
                val item = group.representative
                appendLine(
                    "후보${index + 1} | article=${item.articleNo} | ${item.priceKrw}원 | ${item.buildingName.ifBlank { "동 미상" }} | " +
                        "${item.floorInfo.ifBlank { "층 미상" }} | ${item.direction.ifBlank { "방향 미상" }} | " +
                        "태그=${item.tags.ifBlank { "없음" }} | 설명=${item.description.ifBlank { "없음" }} | " +
                        "동일매물 중개사등록=${group.listings.size} | 링크=${item.sourceUrl}",
                )
            }
            appendLine("출력은 '한눈에 결론 / 추천 순위 / 후보별 근거 / 반드시 확인할 것 / 데이터 한계' 순서로 작성하라.")
        }
        return provider.generate(
            AiModelId.OPENAI_SMART,
            AiRequest(
                taskType = AiTaskType.PROPERTY_LISTING_ANALYSIS,
                userPrompt = prompt,
                jsonOutput = false,
                reasoningEffort = "medium",
            ),
        ).text
    }

    suspend fun analyzeComparison(
        currentHome: PropertyWatchTargetEntity,
        targets: List<PropertyWatchTargetEntity>,
        listings: List<PropertyListingEntity>,
        snapshots: List<PropertyAskingSnapshotEntity>,
        trades: List<PropertyActualTradeEntity>,
    ): String {
        require(targets.isNotEmpty()) { "비교할 단지를 한 곳 이상 선택해 주세요." }
        val allTargets = (listOf(currentHome) + targets).distinctBy(PropertyWatchTargetEntity::id)
        val prompt = buildString {
            appendLine("현재 집과 후보 아파트의 실제 저장 데이터를 바탕으로 갈아타기 관점의 비교 분석을 작성하라.")
            appendLine("오른 정도, 내린 정도, 매물 수, 최근 1년 거래 건수, 과거 고점·저점, 현재 호가와 최근 실거래 차이, 현재 집과의 가격 갭을 비교하라.")
            appendLine("수집 이력이 짧거나 실거래가 없으면 계산 불가라고 명시하고 임의로 과거 값을 만들지 말라. 투자 수익을 보장하거나 매수를 단정하지 말라.")
            appendLine("현재 집=${currentHome.apartmentName}")
            allTargets.forEach { target ->
                val targetListings = PropertyTrackerRepository.groupDuplicateListings(
                    listings.filter { it.watchTargetId == target.id && it.status == "ACTIVE" },
                ).map { it.representative }
                val targetSnapshots = snapshots.filter { it.watchTargetId == target.id }.sortedBy { it.epochDay }
                val targetTrades = trades.filter { it.watchTargetId == target.id }
                    .filter { runCatching { LocalDate.parse(it.tradeDate) }.getOrNull()?.let { date -> date >= LocalDate.now().minusMonths(12) } == true }
                    .sortedByDescending(PropertyActualTradeEntity::tradeDate)
                val askingPrices = targetListings.map(PropertyListingEntity::priceKrw).filter { it > 0 }
                val currentMin = askingPrices.minOrNull()
                val currentMax = askingPrices.maxOrNull()
                val first = targetSnapshots.firstOrNull()
                val last = targetSnapshots.lastOrNull()
                val peak = targetSnapshots.maxOfOrNull(PropertyAskingSnapshotEntity::maxPriceKrw)
                val trough = targetSnapshots.filter { it.minPriceKrw > 0 }.minOfOrNull(PropertyAskingSnapshotEntity::minPriceKrw)
                appendLine("[${target.apartmentName} | 전용 ${target.exclusiveAreaSqm}㎡ | ${if (target.id == currentHome.id) "현재 집" else "후보"}]")
                appendLine("현재호가=${currentMin ?: "없음"}~${currentMax ?: "없음"}원, 현재매물=${targetListings.size}건")
                appendLine("수집시작=${first?.epochDay ?: "없음"}, 시작호가=${first?.minPriceKrw ?: "없음"}~${first?.maxPriceKrw ?: "없음"}원")
                appendLine("최근수집=${last?.epochDay ?: "없음"}, 최근호가=${last?.minPriceKrw ?: "없음"}~${last?.maxPriceKrw ?: "없음"}원, 저장기간고점=${peak ?: "없음"}원, 저장기간저점=${trough ?: "없음"}원")
                appendLine("최근1년거래=${targetTrades.size}건, 최근거래=${targetTrades.firstOrNull()?.let { "${it.tradeDate} ${it.priceKrw}원 ${it.floor}층" } ?: "없음"}")
            }
            val homeSnapshots = snapshots.filter { it.watchTargetId == currentHome.id }.associateBy(PropertyAskingSnapshotEntity::epochDay)
            targets.forEach { target ->
                val gaps = snapshots.filter { it.watchTargetId == target.id }.mapNotNull { targetSnapshot ->
                    homeSnapshots[targetSnapshot.epochDay]?.let { home ->
                        targetSnapshot.epochDay to ((targetSnapshot.minPriceKrw - home.minPriceKrw) to (targetSnapshot.maxPriceKrw - home.maxPriceKrw))
                    }
                }.sortedBy { it.first }
                appendLine("[${target.apartmentName}와 현재 집의 동일수집일 갭]")
                if (gaps.isEmpty()) appendLine("동일 수집일 없음")
                else gaps.takeLast(36).forEach { (day, gap) -> appendLine("epochDay=$day | 최저갭=${gap.first}원 | 최고갭=${gap.second}원") }
            }
            appendLine("출력은 '갈아타기 결론 / 단지별 변화 / 거래 활발도 / 고점·저점 및 호가-실거래 차이 / 현재 집과의 갭 / 추천 순위 / 데이터 한계' 순서로 작성하라.")
        }
        return provider.generate(
            AiModelId.OPENAI_SMART,
            AiRequest(
                taskType = AiTaskType.PROPERTY_COMPARISON_ANALYSIS,
                userPrompt = prompt,
                jsonOutput = false,
                reasoningEffort = "medium",
            ),
        ).text
    }
}
