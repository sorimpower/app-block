package com.sorimpower.app.feature.auction.data

import android.content.Context
import com.sorimpower.app.feature.auction.domain.AuctionItem
import com.sorimpower.app.feature.auction.domain.AuctionAiAnalysis
import com.sorimpower.app.feature.auction.domain.AuctionAiCriteria
import com.sorimpower.app.feature.auction.domain.AuctionAnalysisStatus
import com.sorimpower.app.feature.auction.domain.AuctionDocumentType
import com.sorimpower.app.feature.auction.domain.AuctionEvidenceBundle
import com.sorimpower.app.feature.auction.domain.AuctionRiskLevel
import com.sorimpower.app.feature.auction.domain.isAuctionNewToday
import com.sorimpower.app.feature.auction.domain.matchesAuctionCriteria
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

data class AuctionRepositoryData(
    val items: List<AuctionItem> = emptyList(),
    val historyItems: List<AuctionItem> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val aiAnalyses: Map<String, AuctionAiAnalysis> = emptyMap(),
    val lastUpdatedAt: String? = null,
    val historyLastUpdatedAt: String? = null,
    val lastSuccessfulSyncAt: Long? = null,
    val hasCache: Boolean = false,
)

class AuctionRepository internal constructor(
    context: Context,
    private val courtAuctionApi: CourtAuctionApi = CourtAuctionApi(),
    private val evidenceSource: AuctionEvidenceSource = CourtAuctionEvidenceApi(),
    private val outcomeSource: AuctionOutcomeSource = CourtAuctionEvidenceApi(),
    private val rightsAnalyzer: AuctionRightsAnalyzer = OpenAiAuctionRightsAnalyzer(context.applicationContext),
) {
    private val dao = AuctionDatabase.get(context).dao()

    val data: Flow<AuctionRepositoryData> = combine(
        dao.observeItems(),
        dao.observeHistoryItems(),
        dao.observeMetadata(),
        dao.observeFavoriteKeys(),
        dao.observeAiAnalyses(),
    ) { items, historyItems, metadata, favoriteKeys, aiAnalyses ->
        AuctionRepositoryData(
            items = items.map(AuctionItemEntity::toDomain),
            historyItems = historyItems.map(AuctionHistoryItemEntity::toDomain),
            favoriteKeys = favoriteKeys.toSet(),
            aiAnalyses = aiAnalyses.associate { it.itemKey to it.toDomain() },
            lastUpdatedAt = metadata?.lastUpdatedAt,
            lastSuccessfulSyncAt = metadata?.lastSuccessfulSyncAt,
            hasCache = metadata?.baselineEstablished == true,
        )
    }.combine(dao.observeHistoryMetadata()) { repositoryData, historyMetadata ->
        repositoryData.copy(historyLastUpdatedAt = historyMetadata?.lastUpdatedAt)
    }

    suspend fun setFavorite(itemKey: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        if (favorite) {
            dao.upsertFavorite(AuctionFavoriteEntity(itemKey = itemKey, savedAt = System.currentTimeMillis()))
        } else {
            dao.deleteFavorite(itemKey)
        }
    }

    suspend fun deleteHistoryItem(itemKey: String) = withContext(Dispatchers.IO) {
        dao.deleteHistoryItemAndEmptyMetadata(itemKey)
    }

    /**
     * 진행 목록에서 사라진 사건은 매각·취하 등의 최종 결과가 바로 반영되지 않을 수 있다.
     * 종료 목록을 열 때 미확인/하루 이상 지난 항목 일부만 다시 확인해 법원 요청을 제한한다.
     */
    suspend fun refreshHistoryFinalResults() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.getHistoryItems()
            .asSequence()
            .filter { it.finalResultStatus.isBlank() || now - it.finalResultCheckedAt >= OUTCOME_RECHECK_INTERVAL_MILLIS }
            .take(MAX_OUTCOME_CHECKS_PER_REFRESH)
            .forEach { entity ->
                val item = entity.toDomain()
                val outcome = runCatching { outcomeSource.fetchFinalOutcome(item) }.getOrNull() ?: return@forEach
                dao.updateHistoryFinalResult(
                    itemKey = entity.itemKey,
                    status = outcome.status,
                    salePrice = outcome.salePrice,
                    resultDate = outcome.resultDate,
                    checkedAt = now,
                )
            }
    }

    suspend fun getCurrentItems(): List<AuctionItem> = withContext(Dispatchers.IO) {
        dao.getItems().map(AuctionItemEntity::toDomain)
    }

    suspend fun getAiAnalysisKeys(): Set<String> = withContext(Dispatchers.IO) {
        dao.getAiAnalysisKeys().toSet()
    }

    suspend fun recoverStaleAiAnalyses(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        dao.failStaleAiAnalyses(
            staleBefore = now - ANALYSIS_TIMEOUT_MILLIS,
            resolvedAt = now,
        )
    }

    suspend fun analyzeRights(
        item: AuctionItem,
        evidence: AuctionEvidenceBundle,
        criteria: AuctionAiCriteria,
        mode: AuctionAiAnalysisMode = AuctionAiAnalysisMode.MANUAL,
    ): AuctionAiAnalysis = analyzeRightsInternal(item, criteria, mode) { evidence }

    suspend fun analyzeRights(
        item: AuctionItem,
        criteria: AuctionAiCriteria,
        mode: AuctionAiAnalysisMode = AuctionAiAnalysisMode.MANUAL,
    ): AuctionAiAnalysis = analyzeRightsInternal(item, criteria, mode) { evidenceSource.fetch(item) }

    private suspend fun analyzeRightsInternal(
        item: AuctionItem,
        criteria: AuctionAiCriteria,
        mode: AuctionAiAnalysisMode,
        evidenceProvider: suspend () -> AuctionEvidenceBundle,
    ): AuctionAiAnalysis = withContext(Dispatchers.IO) {
        dao.upsertAiAnalysis(
            AuctionAiAnalysis(
                itemKey = item.itemKey,
                status = AuctionAnalysisStatus.ANALYZING,
                analyzedAt = System.currentTimeMillis(),
            ).toEntity(),
        )
        val result = runCatching {
            withTimeout(ANALYSIS_TIMEOUT_MILLIS) {
                rightsAnalyzer.analyze(item, evidenceProvider(), criteria, mode)
            }
        }
            .getOrElse { error ->
                AuctionAiAnalysis(
                    itemKey = item.itemKey,
                    status = AuctionAnalysisStatus.FAILED,
                    headline = "AI 권리분석을 완료하지 못했어요",
                    summary = if (error is TimeoutCancellationException) {
                        "응답 대기 시간이 지나 분석을 중단했어요. 잠시 후 다시 분석해 주세요."
                    } else {
                        error.message.orEmpty()
                    },
                    analyzedAt = System.currentTimeMillis(),
                )
            }
        dao.upsertAiAnalysis(result.toEntity())
        result
    }

    suspend fun needsAutomaticRefresh(now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val metadata = dao.getMetadata()
        shouldAutomaticallyRefreshAuctions(
            hasCache = metadata?.baselineEstablished == true,
            lastSuccessfulSyncAt = metadata?.lastSuccessfulSyncAt,
            lastAttemptAt = metadata?.lastAttemptAt,
            now = now,
        )
    }

    private companion object {
        const val ANALYSIS_TIMEOUT_MILLIS = 2 * 60 * 1000L
        const val MAX_OUTCOME_CHECKS_PER_REFRESH = 5
        const val OUTCOME_RECHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val attemptAt = System.currentTimeMillis()
        val metadataBeforeAttempt = dao.getMetadata()
        dao.upsertMetadata(
            metadataBeforeAttempt?.copy(lastAttemptAt = attemptAt)
                ?: AuctionSyncMetadataEntity(
                    lastUpdatedAt = null,
                    lastSuccessfulSyncAt = 0L,
                    baselineEstablished = false,
                    lastAttemptAt = attemptAt,
                ),
        )
        val snapshot = courtAuctionApi.fetchSnapshot()
        val filtered = snapshot.items
            .asSequence()
            .filter(AuctionItem::matchesAuctionCriteria)
            .groupBy(AuctionItem::itemKey)
            .map { (_, duplicates) -> duplicates.maxByOrNull(AuctionItem::collectedAt)!! }
            .sortedWith(compareBy<AuctionItem> { it.auctionDate.isBlank() }.thenBy(AuctionItem::auctionDate).thenBy(AuctionItem::auctionTime))

        val previousItems = dao.getItems().associateBy(AuctionItemEntity::itemKey)
        val previousMetadata = dao.getMetadata()
        val previousHistoryMetadata = dao.getHistoryMetadata()
        val now = System.currentTimeMillis()
        val baselineEstablished = previousMetadata?.baselineEstablished == true
        val entities = filtered.map { item ->
            val previous = previousItems[item.itemKey]
            item.toEntity(
                firstSeenAt = previous?.firstSeenAt ?: now,
                lastSeenAt = now,
                isNew = when {
                    !baselineEstablished -> false
                    previous == null -> true
                    isAuctionNewToday(previous.isNew, previous.firstSeenAt, now) -> true
                    else -> false
                },
            )
        }
        val currentKeys = entities.mapTo(hashSetOf(), AuctionItemEntity::itemKey)
        val removedItems = if (baselineEstablished) {
            previousItems.values
                .asSequence()
                .filter { it.itemKey !in currentKeys }
                .map { it.toHistoryEntity(snapshot.collectedAt) }
                .toList()
        } else {
            emptyList()
        }
        val historyMetadata = if (removedItems.isNotEmpty()) {
            AuctionSyncMetadataEntity(
                id = AuctionSyncMetadataEntity.HISTORY_ID,
                lastUpdatedAt = snapshot.collectedAt,
                lastSuccessfulSyncAt = now,
                baselineEstablished = true,
            )
        } else {
            previousHistoryMetadata?.copy(lastSuccessfulSyncAt = now, baselineEstablished = true)
                ?: AuctionSyncMetadataEntity(
                    id = AuctionSyncMetadataEntity.HISTORY_ID,
                    lastUpdatedAt = null,
                    lastSuccessfulSyncAt = now,
                    baselineEstablished = true,
                )
        }

        dao.replaceSnapshotAndAppendHistory(
            items = entities,
            metadata = AuctionSyncMetadataEntity(
                lastUpdatedAt = snapshot.collectedAt,
                lastSuccessfulSyncAt = now,
                baselineEstablished = true,
                lastAttemptAt = attemptAt,
            ),
            removedItems = removedItems,
            historyMetadata = historyMetadata,
        )
    }
}

private fun AuctionItemEntity.toDomain() = AuctionItem(
    itemKey, courtCode, courtName, internalCaseNumber, caseNumber, auctionItemNumber, usageName,
    appraisalPrice, minimumPrice, minimumPriceRate, failedCount, auctionDate, auctionTime,
    auctionPlace, address, sido, sigungu, dong, buildingName, courtDepartment, courtTel, note,
    interestCount, isInProgress, objectCount, collectedAt, firstSeenAt, lastSeenAt, isNew,
)

private fun AuctionItem.toEntity(firstSeenAt: Long, lastSeenAt: Long, isNew: Boolean) = AuctionItemEntity(
    itemKey, courtCode, courtName, internalCaseNumber, caseNumber, auctionItemNumber, usageName,
    appraisalPrice, minimumPrice, minimumPriceRate, failedCount, auctionDate, auctionTime,
    auctionPlace, address, sido, sigungu, dong, buildingName, courtDepartment, courtTel, note,
    interestCount, isInProgress, objectCount, collectedAt, firstSeenAt, lastSeenAt, isNew,
)

private fun AuctionHistoryItemEntity.toDomain() = AuctionItem(
    itemKey = itemKey,
    courtCode = courtCode.ifBlank { itemKey.take(7).takeIf { it.startsWith("B") }.orEmpty() },
    courtName = courtName,
    internalCaseNumber = internalCaseNumber,
    caseNumber = caseNumber,
    auctionItemNumber = auctionItemNumber,
    usageName = "아파트",
    appraisalPrice = appraisalPrice,
    minimumPrice = minimumPrice,
    minimumPriceRate = minimumPriceRate,
    failedCount = failedCount,
    auctionDate = auctionDate,
    auctionTime = auctionTime,
    auctionPlace = "",
    address = address,
    sido = sido,
    sigungu = sigungu,
    dong = dong,
    buildingName = buildingName,
    courtDepartment = courtDepartment,
    courtTel = "",
    note = note,
    interestCount = 0,
    isInProgress = false,
    objectCount = objectCount,
    collectedAt = collectedAt,
    historyCreatedAt = historyCreatedAt,
    historyStatus = historyStatus,
    historyReason = historyReason,
    finalResultStatus = finalResultStatus,
    finalSalePrice = finalSalePrice,
    finalResultDate = finalResultDate,
    finalResultCheckedAt = finalResultCheckedAt,
)

private fun AuctionItemEntity.toHistoryEntity(removedAt: String) = AuctionHistoryItemEntity(
    itemKey = itemKey,
    courtCode = courtCode,
    internalCaseNumber = internalCaseNumber,
    courtName = courtName,
    caseNumber = caseNumber,
    auctionItemNumber = auctionItemNumber,
    appraisalPrice = appraisalPrice,
    minimumPrice = minimumPrice,
    minimumPriceRate = minimumPriceRate,
    failedCount = failedCount,
    auctionDate = auctionDate,
    auctionTime = auctionTime,
    address = address,
    sido = sido,
    sigungu = sigungu,
    dong = dong,
    buildingName = buildingName,
    courtDepartment = courtDepartment,
    note = note,
    objectCount = objectCount,
    collectedAt = collectedAt,
    historyCreatedAt = removedAt,
    historyStatus = "REMOVED",
    historyReason = "진행 중 검색 결과에서 사라짐",
    finalResultStatus = "",
    finalSalePrice = 0L,
    finalResultDate = "",
    finalResultCheckedAt = 0L,
)

private fun AuctionAiAnalysisEntity.toDomain() = AuctionAiAnalysis(
    itemKey = itemKey,
    status = enumValueOrDefault(status, AuctionAnalysisStatus.FAILED),
    riskLevel = enumValueOrDefault(riskLevel, AuctionRiskLevel.UNKNOWN),
    suitabilityScore = suitabilityScore.coerceIn(0, 100),
    headline = headline,
    summary = summary,
    riskItems = riskItemsJson.jsonStringList(),
    requiredChecks = requiredChecksJson.jsonStringList(),
    evidenceTypes = evidenceTypes.enumSet(),
    missingDocumentTypes = missingDocumentTypes.enumSet(),
    analyzedAt = analyzedAt,
    modelName = modelName,
    promptVersion = promptVersion,
)

private fun AuctionAiAnalysis.toEntity() = AuctionAiAnalysisEntity(
    itemKey = itemKey,
    status = status.name,
    riskLevel = riskLevel.name,
    suitabilityScore = suitabilityScore.coerceIn(0, 100),
    headline = headline,
    summary = summary,
    riskItemsJson = JSONArray(riskItems).toString(),
    requiredChecksJson = JSONArray(requiredChecks).toString(),
    evidenceTypes = evidenceTypes.joinToString(",", transform = AuctionDocumentType::name),
    missingDocumentTypes = missingDocumentTypes.joinToString(",", transform = AuctionDocumentType::name),
    analyzedAt = analyzedAt,
    modelName = modelName,
    promptVersion = promptVersion,
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

private fun String.jsonStringList(): List<String> = runCatching {
    val source = JSONArray(this)
    buildList {
        for (index in 0 until source.length()) {
            source.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }
}.getOrDefault(emptyList())

private fun String.enumSet(): Set<AuctionDocumentType> = split(',')
    .mapNotNull { value -> AuctionDocumentType.entries.firstOrNull { it.name == value } }
    .toSet()
