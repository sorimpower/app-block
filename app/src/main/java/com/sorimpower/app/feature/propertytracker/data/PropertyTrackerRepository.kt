package com.sorimpower.app.feature.propertytracker.data

import android.content.Context
import com.sorimpower.app.feature.assets.data.MolitRealEstateProvider
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AddPropertyTarget(
    val apartmentName: String,
    val complexInput: String,
    val areaNo: String,
    val exclusiveAreaSqm: Double,
    val lawdCd: String,
)

data class PropertySyncResult(
    val performed: Boolean,
    val successCount: Int,
    val failureCount: Int,
    val message: String,
)

class PropertyTrackerRepository(
    context: Context,
    private val naverProvider: NaverLandProvider = NaverLandProvider(),
    private val molitProvider: MolitRealEstateProvider = MolitRealEstateProvider(),
) {
    private val dao = PropertyTrackerDatabase.get(context).dao()

    val targets: Flow<List<PropertyWatchTargetEntity>> = dao.observeTargets()
    val listings: Flow<List<PropertyListingEntity>> = dao.observeListings()
    val snapshots: Flow<List<PropertyAskingSnapshotEntity>> = dao.observeSnapshots()
    val trades: Flow<List<PropertyActualTradeEntity>> = dao.observeTrades()
    val events: Flow<List<PropertyListingEventEntity>> = dao.observeEvents()
    val latestRun: Flow<PropertySyncRunEntity?> = dao.observeLatestRun()

    suspend fun addTarget(input: AddPropertyTarget): Result<Unit> = runCatching {
        val name = input.apartmentName.trim()
        require(name.isNotBlank()) { "아파트 이름을 입력해 주세요." }
        val complexNo = NaverLandProvider.extractComplexNo(input.complexInput)
            ?: error("네이버 부동산 단지 URL 또는 숫자 단지번호를 확인해 주세요.")
        require(input.exclusiveAreaSqm > 0) { "전용면적을 확인해 주세요." }
        val lawdCd = input.lawdCd.filter(Char::isDigit)
        require(lawdCd.isBlank() || lawdCd.length == 5) { "법정동 코드는 5자리입니다." }
        val areaNo = input.areaNo.filter(Char::isDigit).ifBlank {
            NaverLandProvider.extractAreaNo(input.complexInput).orEmpty()
        }
        require(dao.getTargets().none { it.complexNo == complexNo && kotlin.math.abs(it.exclusiveAreaSqm - input.exclusiveAreaSqm) < 0.1 }) {
            "이미 등록한 단지·평형입니다."
        }
        val now = System.currentTimeMillis()
        dao.upsertTarget(
            PropertyWatchTargetEntity(
                id = UUID.randomUUID().toString(),
                apartmentName = name,
                complexNo = complexNo,
                areaNo = areaNo,
                exclusiveAreaSqm = input.exclusiveAreaSqm,
                lawdCd = lawdCd,
                isCurrentHome = false,
                isMoveTarget = false,
                isCompareSelected = false,
                createdAt = now,
                lastSyncAt = null,
                lastSyncStatus = "WAITING",
                lastSyncMessage = "내일 오전 8시 또는 지금 동기화에서 조회합니다.",
            ),
        )
    }

    suspend fun deleteTarget(id: String) = dao.deleteTarget(id)

    suspend fun setCurrentHome(id: String, selected: Boolean) = dao.selectCurrentHome(id, selected)

    suspend fun setMoveTarget(id: String, selected: Boolean): Result<Unit> = runCatching {
        val targets = dao.getTargets()
        if (selected && targets.count(PropertyWatchTargetEntity::isMoveTarget) >= MAX_SELECTIONS) {
            error("갈아타기 후보는 최대 5개까지 선택할 수 있습니다.")
        }
        dao.updateMoveTarget(id, selected)
    }

    suspend fun setCompareSelected(id: String, selected: Boolean): Result<Unit> = runCatching {
        val targets = dao.getTargets()
        if (selected && targets.count(PropertyWatchTargetEntity::isCompareSelected) >= MAX_SELECTIONS) {
            error("비교 단지는 최대 5개까지 선택할 수 있습니다.")
        }
        dao.updateCompareSelected(id, selected)
    }

    suspend fun syncAll(force: Boolean = false): PropertySyncResult = SYNC_MUTEX.withLock {
        val now = System.currentTimeMillis()
        val epochDay = LocalDate.now(KOREA_ZONE).toEpochDay()
        val targets = dao.getTargets()
        if (targets.isEmpty()) {
            return@withLock PropertySyncResult(false, 0, 0, "먼저 관심 단지와 평형을 등록해 주세요.")
        }
        if (!force && !dao.tryStartDailyRun(epochDay, now)) {
            return@withLock PropertySyncResult(false, 0, 0, "오늘 동기화는 이미 실행했습니다.")
        }
        if (force) dao.upsertRun(PropertySyncRunEntity(epochDay, "RUNNING", now, null, 0, 0, "조회 중"))
        var successes = 0
        var failures = 0
        val messages = mutableListOf<String>()
        for (target in targets) {
            val askingResult = runCatching { naverProvider.fetchListings(target) }
            if (askingResult.isSuccess) {
                val fetched = askingResult.getOrThrow()
                saveSuccessfulAskingSync(target, fetched, now, epochDay)
                successes++
                val actualTradeMessage = syncActualTrades(target)
                val message = buildString {
                    append("호가 ${fetched.size}건")
                    if (actualTradeMessage.isNotBlank()) append(" · $actualTradeMessage")
                }
                dao.upsertTarget(target.copy(lastSyncAt = now, lastSyncStatus = "SUCCESS", lastSyncMessage = message))
            } else {
                failures++
                val reason = askingResult.exceptionOrNull()?.message ?: "알 수 없는 오류"
                messages += "${target.apartmentName}: $reason"
                dao.upsertTarget(target.copy(lastSyncAt = now, lastSyncStatus = "FAILED", lastSyncMessage = reason))
            }
        }
        val status = when {
            failures == 0 -> "SUCCESS"
            successes == 0 -> "FAILED"
            else -> "PARTIAL"
        }
        val message = messages.joinToString("\n").ifBlank { "관심 단지 ${successes}곳 동기화 완료" }
        dao.upsertRun(PropertySyncRunEntity(epochDay, status, now, System.currentTimeMillis(), successes, failures, message))
        PropertySyncResult(true, successes, failures, message)
    }

    private suspend fun saveSuccessfulAskingSync(
        target: PropertyWatchTargetEntity,
        fetched: List<NaverLandListing>,
        now: Long,
        epochDay: Long,
    ) {
        val old = dao.getListings(target.id)
        val oldById = old.associateBy(PropertyListingEntity::articleNo)
        val fetchedIds = fetched.mapTo(hashSetOf(), NaverLandListing::articleNo)
        val events = mutableListOf<PropertyListingEventEntity>()
        var newCount = 0
        var removedCount = 0

        val current = fetched.map { item ->
            val previous = oldById[item.articleNo]
            val eventType = when {
                previous == null -> "ADDED"
                previous.status == "REMOVED" -> "RELISTED"
                previous.priceKrw != item.priceKrw -> "PRICE_CHANGED"
                else -> null
            }
            if (previous == null) newCount++
            if (eventType != null) {
                events += PropertyListingEventEntity(
                    id = "${target.id}:${item.articleNo}:$now:$eventType",
                    watchTargetId = target.id,
                    articleNo = item.articleNo,
                    type = eventType,
                    previousPriceKrw = previous?.priceKrw,
                    priceKrw = item.priceKrw,
                    occurredAt = now,
                )
            }
            PropertyListingEntity(
                articleNo = item.articleNo,
                watchTargetId = target.id,
                priceKrw = item.priceKrw,
                priceText = item.priceText,
                supplyAreaSqm = item.supplyAreaSqm,
                exclusiveAreaSqm = item.exclusiveAreaSqm,
                floorInfo = item.floorInfo,
                direction = item.direction,
                buildingName = item.buildingName,
                description = item.description,
                tags = item.tags,
                confirmDate = item.confirmDate,
                sourceUrl = item.sourceUrl,
                firstSeenAt = previous?.firstSeenAt ?: now,
                lastSeenAt = now,
                status = "ACTIVE",
                consecutiveMisses = 0,
                removedAt = null,
            )
        }
        val notSeen = old.filter { it.articleNo !in fetchedIds }.map { previous ->
            if (previous.status == "REMOVED") return@map previous
            val (status, misses) = nextMissingState(previous.status, previous.consecutiveMisses)
            val removed = status == "REMOVED"
            if (removed) {
                removedCount++
                events += PropertyListingEventEntity(
                    id = "${target.id}:${previous.articleNo}:$now:REMOVED",
                    watchTargetId = target.id,
                    articleNo = previous.articleNo,
                    type = "REMOVED",
                    previousPriceKrw = previous.priceKrw,
                    priceKrw = previous.priceKrw,
                    occurredAt = now,
                )
            }
            previous.copy(
                status = status,
                consecutiveMisses = misses,
                removedAt = if (removed) now else null,
            )
        }
        if (current.isNotEmpty() || notSeen.isNotEmpty()) dao.upsertListings(current + notSeen)
        if (events.isNotEmpty()) dao.upsertEvents(events)
        val prices = current.map(PropertyListingEntity::priceKrw).sorted()
        dao.upsertSnapshot(
            PropertyAskingSnapshotEntity(
                id = "${target.id}:$epochDay",
                watchTargetId = target.id,
                epochDay = epochDay,
                minPriceKrw = prices.firstOrNull() ?: 0,
                medianPriceKrw = medianPrice(prices),
                maxPriceKrw = prices.lastOrNull() ?: 0,
                activeCount = current.size,
                newCount = newCount,
                removedCount = removedCount,
                createdAt = now,
            ),
        )
    }

    private suspend fun syncActualTrades(target: PropertyWatchTargetEntity): String {
        if (target.lawdCd.isBlank()) return "실거래 미연동(법정동 코드 필요)"
        return runCatching {
            val result = molitProvider.lookup(target.lawdCd, target.apartmentName, target.exclusiveAreaSqm)
            val trades = result.trades.map { trade ->
                PropertyActualTradeEntity(
                    id = "${target.id}:${trade.tradeDate}:${trade.priceKrw}:${trade.floor}",
                    watchTargetId = target.id,
                    apartmentName = trade.apartmentName,
                    exclusiveAreaSqm = trade.exclusiveAreaSqm,
                    priceKrw = trade.priceKrw,
                    tradeDate = trade.tradeDate,
                    floor = trade.floor,
                )
            }
            if (trades.isNotEmpty()) dao.upsertTrades(trades)
            "실거래 ${trades.size}건"
        }.getOrElse { "실거래 조회 실패" }
    }

    companion object {
        private val KOREA_ZONE = ZoneId.of("Asia/Seoul")
        private val SYNC_MUTEX = Mutex()
        private const val REQUIRED_MISSES_FOR_REMOVAL = 2
        private const val MAX_SELECTIONS = 5

        fun medianPrice(sortedPrices: List<Long>): Long {
            if (sortedPrices.isEmpty()) return 0
            val middle = sortedPrices.size / 2
            return if (sortedPrices.size % 2 == 1) sortedPrices[middle]
            else ((sortedPrices[middle - 1] + sortedPrices[middle]) / 2.0).roundToLong()
        }

        fun nextMissingState(previousStatus: String, previousMisses: Int): Pair<String, Int> {
            if (previousStatus == "REMOVED") return "REMOVED" to previousMisses
            val misses = previousMisses + 1
            return (if (misses >= REQUIRED_MISSES_FOR_REMOVAL) "REMOVED" else "MISSING_PENDING") to misses
        }
    }
}
