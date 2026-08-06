package com.sorimpower.app.feature.auction.data

import android.content.Context
import com.sorimpower.app.feature.auction.domain.AuctionItem
import com.sorimpower.app.feature.auction.domain.matchesAuctionCriteria
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AuctionRepositoryData(
    val items: List<AuctionItem> = emptyList(),
    val historyItems: List<AuctionItem> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val lastUpdatedAt: String? = null,
    val historyLastUpdatedAt: String? = null,
    val lastSuccessfulSyncAt: Long? = null,
    val hasCache: Boolean = false,
)

private data class AuctionApiPage(
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val totalPages: Int,
    val lastUpdatedAt: String?,
    val items: List<AuctionItem>,
)

class AuctionRepository(context: Context) {
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

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val firstPage = loadPage(1, TYPE_ACTIVE)
        require(firstPage.page == 1) { "첫 페이지 번호가 올바르지 않습니다." }
        require(firstPage.totalPages in 0..MAX_TOTAL_PAGES) { "전체 페이지 수가 허용 범위를 벗어났습니다." }

        val allItems = firstPage.items.toMutableList()
        for (page in 2..firstPage.totalPages) {
            val next = loadPage(page, TYPE_ACTIVE)
            require(next.page == page) { "요청한 페이지와 응답 페이지가 다릅니다." }
            require(next.totalPages == firstPage.totalPages) { "동기화 중 전체 페이지 수가 변경되었습니다." }
            allItems += next.items
        }

        val filtered = allItems
            .asSequence()
            .filter(AuctionItem::matchesAuctionCriteria)
            .groupBy(AuctionItem::itemKey)
            .map { (_, duplicates) -> duplicates.maxByOrNull(AuctionItem::collectedAt)!! }
            .sortedWith(compareBy<AuctionItem> { it.auctionDate.isBlank() }.thenBy(AuctionItem::auctionDate).thenBy(AuctionItem::auctionTime))

        val previousItems = dao.getItems().associateBy(AuctionItemEntity::itemKey)
        val previousMetadata = dao.getMetadata()
        val now = System.currentTimeMillis()
        val baselineEstablished = previousMetadata?.baselineEstablished == true
        val sameSnapshot = previousMetadata?.lastUpdatedAt == firstPage.lastUpdatedAt
        val entities = filtered.map { item ->
            val previous = previousItems[item.itemKey]
            item.toEntity(
                firstSeenAt = previous?.firstSeenAt ?: now,
                lastSeenAt = now,
                isNew = when {
                    !baselineEstablished -> false
                    previous == null -> true
                    sameSnapshot -> previous.isNew
                    else -> false
                },
            )
        }

        dao.replaceSnapshot(
            items = entities,
            metadata = AuctionSyncMetadataEntity(
                lastUpdatedAt = firstPage.lastUpdatedAt,
                lastSuccessfulSyncAt = now,
                baselineEstablished = true,
            ),
        )

        refreshHistory(now)
    }

    private suspend fun refreshHistory(now: Long) {
        val firstPage = loadPage(1, TYPE_HISTORY)
        require(firstPage.page == 1) { "이력 첫 페이지 번호가 올바르지 않습니다." }
        require(firstPage.totalPages in 0..MAX_TOTAL_PAGES) { "이력 전체 페이지 수가 허용 범위를 벗어났습니다." }
        val allItems = firstPage.items.toMutableList()
        for (page in 2..firstPage.totalPages) {
            val next = loadPage(page, TYPE_HISTORY)
            require(next.page == page) { "요청한 이력 페이지와 응답 페이지가 다릅니다." }
            require(next.totalPages == firstPage.totalPages) { "이력 동기화 중 전체 페이지 수가 변경되었습니다." }
            allItems += next.items
        }
        val historyItems = allItems
            .asSequence()
            .filter { it.itemKey.isNotBlank() && it.historyStatus == HISTORY_STATUS_REMOVED }
            .groupBy(AuctionItem::itemKey)
            .map { (_, duplicates) -> duplicates.maxByOrNull(AuctionItem::historyCreatedAt)!! }
            .sortedByDescending(AuctionItem::historyCreatedAt)
            .map(AuctionItem::toHistoryEntity)
        dao.replaceHistorySnapshot(
            historyItems,
            AuctionSyncMetadataEntity(
                id = AuctionSyncMetadataEntity.HISTORY_ID,
                lastUpdatedAt = allItems.map(AuctionItem::historyCreatedAt).filter(String::isNotBlank).maxOrNull()
                    ?: firstPage.lastUpdatedAt,
                lastSuccessfulSyncAt = now,
                baselineEstablished = true,
            ),
        )
    }

    private fun loadPage(page: Int, type: String): AuctionApiPage {
        val historyQuery = if (type == TYPE_HISTORY) "&historyStatus=$HISTORY_STATUS_REMOVED" else ""
        val connection = (URL("$API_URL?type=$type&page=$page&pageSize=$PAGE_SIZE$historyQuery").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("경매 API HTTP 오류: $status")
            if (!body.trimStart().startsWith("{")) throw IOException("경매 API가 JSON이 아닌 응답을 반환했습니다.")
            parsePage(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePage(body: String): AuctionApiPage {
        val root = JSONObject(body)
        if (!root.optBoolean("success", false)) {
            throw IOException(root.stringOrEmpty("message").ifBlank { "경매 API 요청에 실패했습니다." })
        }
        val array = root.optJSONArray("items") ?: throw IOException("경매 목록 필드가 없습니다.")
        val items = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(item.toAuctionItem())
            }
        }
        return AuctionApiPage(
            page = root.optInt("page", 1),
            pageSize = root.optInt("pageSize", PAGE_SIZE),
            totalCount = root.optInt("totalCount", items.size),
            totalPages = root.optInt("totalPages", if (items.isEmpty()) 0 else 1),
            lastUpdatedAt = root.nullableString("lastUpdatedAt"),
            items = items,
        )
    }

    private fun JSONObject.toAuctionItem() = AuctionItem(
        itemKey = stringOrEmpty("itemKey"),
        courtCode = stringOrEmpty("courtCode"),
        courtName = stringOrEmpty("courtName"),
        internalCaseNumber = stringOrEmpty("internalCaseNumber"),
        caseNumber = stringOrEmpty("caseNumber"),
        auctionItemNumber = stringOrEmpty("auctionItemNumber"),
        usageName = stringOrEmpty("usageName"),
        appraisalPrice = optLong("appraisalPrice", 0L),
        minimumPrice = optLong("minimumPrice", 0L),
        minimumPriceRate = optDouble("minimumPriceRate", 0.0),
        failedCount = optInt("failedCount", 0),
        auctionDate = stringOrEmpty("auctionDate"),
        auctionTime = stringOrEmpty("auctionTime"),
        auctionPlace = stringOrEmpty("auctionPlace"),
        address = stringOrEmpty("address"),
        sido = stringOrEmpty("sido"),
        sigungu = stringOrEmpty("sigungu"),
        dong = stringOrEmpty("dong"),
        buildingName = stringOrEmpty("buildingName"),
        courtDepartment = stringOrEmpty("courtDepartment"),
        courtTel = stringOrEmpty("courtTel"),
        note = stringOrEmpty("note"),
        interestCount = optInt("interestCount", 0),
        isInProgress = optBoolean("isInProgress", false),
        objectCount = optInt("objectCount", 0),
        collectedAt = stringOrEmpty("collectedAt"),
        historyCreatedAt = stringOrEmpty("historyCreatedAt"),
        historyStatus = stringOrEmpty("historyStatus"),
        historyReason = stringOrEmpty("historyReason"),
    )

    private fun JSONObject.stringOrEmpty(key: String): String = if (isNull(key)) "" else optString(key, "")
    private fun JSONObject.nullableString(key: String): String? = stringOrEmpty(key).ifBlank { null }

    companion object {
        private const val API_URL = "https://script.google.com/macros/s/AKfycbw-ryPQ_7E9lOOtxxO4dl0FxoQYf0B5iivY5i4vA1IbYmuP57NYH1dNvWmlxooPVPT70A/exec"
        private const val PAGE_SIZE = 20
        private const val MAX_TOTAL_PAGES = 50
        private const val CONNECT_TIMEOUT_MILLIS = 15_000
        private const val READ_TIMEOUT_MILLIS = 20_000
        private const val TYPE_ACTIVE = "active"
        private const val TYPE_HISTORY = "history"
        private const val HISTORY_STATUS_REMOVED = "REMOVED"
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

private fun AuctionItem.toHistoryEntity() = AuctionHistoryItemEntity(
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
    historyCreatedAt = historyCreatedAt,
    historyStatus = historyStatus,
    historyReason = historyReason,
)
