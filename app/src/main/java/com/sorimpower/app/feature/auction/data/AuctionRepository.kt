package com.sorimpower.app.feature.auction.data

import android.content.Context
import com.sorimpower.app.feature.auction.domain.AuctionItem
import com.sorimpower.app.feature.auction.domain.isAuctionNewToday
import com.sorimpower.app.feature.auction.domain.matchesAuctionCriteria
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

data class AuctionRepositoryData(
    val items: List<AuctionItem> = emptyList(),
    val historyItems: List<AuctionItem> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val lastUpdatedAt: String? = null,
    val historyLastUpdatedAt: String? = null,
    val lastSuccessfulSyncAt: Long? = null,
    val hasCache: Boolean = false,
)

class AuctionRepository internal constructor(
    context: Context,
    private val courtAuctionApi: CourtAuctionApi = CourtAuctionApi(),
) {
    private val dao = AuctionDatabase.get(context).dao()

    val data: Flow<AuctionRepositoryData> = combine(
        dao.observeItems(),
        dao.observeHistoryItems(),
        dao.observeMetadata(),
        dao.observeFavoriteKeys(),
    ) { items, historyItems, metadata, favoriteKeys ->
        AuctionRepositoryData(
            items = items.map(AuctionItemEntity::toDomain),
            historyItems = historyItems.map(AuctionHistoryItemEntity::toDomain),
            favoriteKeys = favoriteKeys.toSet(),
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

    suspend fun needsAutomaticRefresh(now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val metadata = dao.getMetadata()
        shouldAutomaticallyRefreshAuctions(
            hasCache = metadata?.baselineEstablished == true,
            lastSuccessfulSyncAt = metadata?.lastSuccessfulSyncAt,
            lastAttemptAt = metadata?.lastAttemptAt,
            now = now,
        )
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
    courtCode = "",
    courtName = courtName,
    internalCaseNumber = "",
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
)

private fun AuctionItemEntity.toHistoryEntity(removedAt: String) = AuctionHistoryItemEntity(
    itemKey = itemKey,
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
)
