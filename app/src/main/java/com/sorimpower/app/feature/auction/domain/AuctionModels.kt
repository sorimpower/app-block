package com.sorimpower.app.feature.auction.domain

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

const val MINIMUM_APPRAISAL_PRICE = 1_000_000_000L

data class AuctionItem(
    val itemKey: String,
    val courtCode: String,
    val courtName: String,
    val internalCaseNumber: String,
    val caseNumber: String,
    val auctionItemNumber: String,
    val usageName: String,
    val appraisalPrice: Long,
    val minimumPrice: Long,
    val minimumPriceRate: Double,
    val failedCount: Int,
    val auctionDate: String,
    val auctionTime: String,
    val auctionPlace: String,
    val address: String,
    val sido: String,
    val sigungu: String,
    val dong: String,
    val buildingName: String,
    val courtDepartment: String,
    val courtTel: String,
    val note: String,
    val interestCount: Int,
    val isInProgress: Boolean,
    val objectCount: Int,
    val collectedAt: String,
    val firstSeenAt: Long = 0L,
    val lastSeenAt: Long = 0L,
    val isNew: Boolean = false,
    val historyCreatedAt: String = "",
    val historyStatus: String = "",
    val historyReason: String = "",
)

data class AuctionCalendarTime(
    val startAtMillis: Long,
    val endAtMillis: Long,
    val isAllDay: Boolean,
    val timeZoneId: String,
)

enum class AuctionSortField(val label: String) {
    APPRAISAL_PRICE("감정가"),
    MINIMUM_PRICE("최저가"),
    AUCTION_DATE("매각기일"),
    FAILED_COUNT("유찰 횟수"),
    REMOVED_AT("종료 감지일"),
}

enum class AuctionSortDirection(val label: String) {
    ASCENDING("오름차순"),
    DESCENDING("내림차순"),
}

data class AuctionListFilter(
    val minAppraisalPrice: Long? = null,
    val maxAppraisalPrice: Long? = null,
    val startAuctionDate: LocalDate? = null,
    val endAuctionDate: LocalDate? = null,
    val minFailedCount: Int? = null,
    val maxFailedCount: Int? = null,
) {
    val isActive: Boolean get() = listOf(
        minAppraisalPrice, maxAppraisalPrice, startAuctionDate,
        endAuctionDate, minFailedCount, maxFailedCount,
    ).any { it != null }

    fun matches(item: AuctionItem): Boolean {
        if (minAppraisalPrice != null && item.appraisalPrice < minAppraisalPrice) return false
        if (maxAppraisalPrice != null && item.appraisalPrice > maxAppraisalPrice) return false
        if (minFailedCount != null && item.failedCount < minFailedCount) return false
        if (maxFailedCount != null && item.failedCount > maxFailedCount) return false
        val date = parseAuctionDate(item.auctionDate)
        if (startAuctionDate != null && (date == null || date < startAuctionDate)) return false
        if (endAuctionDate != null && (date == null || date > endAuctionDate)) return false
        return true
    }
}

fun filterAndSortAuctions(
    items: List<AuctionItem>,
    filter: AuctionListFilter,
    sortField: AuctionSortField,
    sortDirection: AuctionSortDirection,
    apartmentNameQuery: String = "",
): List<AuctionItem> {
    val comparator = when (sortField) {
        AuctionSortField.APPRAISAL_PRICE -> compareBy<AuctionItem> { it.appraisalPrice }
        AuctionSortField.MINIMUM_PRICE -> compareBy<AuctionItem> { it.minimumPrice }
        AuctionSortField.AUCTION_DATE -> compareBy<AuctionItem> { parseAuctionDate(it.auctionDate) ?: LocalDate.MAX }
        AuctionSortField.FAILED_COUNT -> compareBy<AuctionItem> { it.failedCount }
        AuctionSortField.REMOVED_AT -> compareBy<AuctionItem> { it.historyCreatedAt }
    }.thenBy(AuctionItem::itemKey)
    val normalizedQuery = apartmentNameQuery.trim()
    return items.asSequence()
        .filter(filter::matches)
        .filter { normalizedQuery.isBlank() || it.buildingName.contains(normalizedQuery, ignoreCase = true) }
        .sortedWith(if (sortDirection == AuctionSortDirection.ASCENDING) comparator else comparator.reversed())
        .toList()
}

fun AuctionItem.matchesAuctionCriteria(): Boolean =
    itemKey.isNotBlank() &&
        (sido.trim() == "서울특별시" || address.trim().startsWith("서울특별시")) &&
        appraisalPrice >= MINIMUM_APPRAISAL_PRICE &&
        isInProgress &&
        usageName.trim() == "아파트"

fun AuctionItem.mapSearchQuery(): String = address.trim().ifBlank { buildingName.trim() }

fun auctionCaseNumberForCopy(caseNumber: String): String {
    val markerIndex = caseNumber.lastIndexOf("타경")
    if (markerIndex < 0) return caseNumber.trim()
    return caseNumber.substring(markerIndex + "타경".length).filter(Char::isDigit)
}

fun AuctionItem.calendarTime(): AuctionCalendarTime? {
    val date = parseAuctionDate(auctionDate) ?: return null
    val time = normalizeAuctionTime(auctionTime)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    return if (time != null) {
        val zone = ZoneId.of("Asia/Seoul")
        val start = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        AuctionCalendarTime(
            startAtMillis = start,
            endAtMillis = start + 60 * 60 * 1_000L,
            isAllDay = false,
            timeZoneId = zone.id,
        )
    } else {
        val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        AuctionCalendarTime(start, end, isAllDay = true, timeZoneId = "UTC")
    }
}

fun favoriteAuctionItems(
    activeItems: List<AuctionItem>,
    historyItems: List<AuctionItem>,
    favoriteKeys: Set<String>,
): List<AuctionItem> = (activeItems + historyItems)
    .distinctBy(AuctionItem::itemKey)
    .filter { it.itemKey in favoriteKeys }

fun normalizeAuctionTime(value: String): String? {
    val trimmed = value.trim()
    if (Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(trimmed)) return trimmed
    return Regex("(?:^|\\s)((?:[01]\\d|2[0-3]):[0-5]\\d)(?::[0-5]\\d)?(?:\\s|$)")
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
}

fun formatAuctionPrice(value: Long): String {
    if (value <= 0L) return "가격 정보 없음"
    val eok = value / 100_000_000L
    val remainder = value % 100_000_000L
    val man = remainder / 10_000L
    return when {
        eok > 0L && man > 0L -> "${eok}억 ${"%,d".format(Locale.KOREA, man)}만 원"
        eok > 0L -> "${eok}억 원"
        else -> "${"%,d".format(Locale.KOREA, man)}만 원"
    }
}

fun parseAuctionDate(value: String): LocalDate? = try {
    LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
} catch (_: DateTimeParseException) {
    null
}

fun auctionDateLabel(item: AuctionItem): String {
    val date = parseAuctionDate(item.auctionDate) ?: return "기일 미정"
    val dateText = date.format(DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREAN))
    return normalizeAuctionTime(item.auctionTime)?.let { "$dateText $it" } ?: dateText
}

fun auctionDdayLabel(item: AuctionItem, today: LocalDate = LocalDate.now()): String {
    val date = parseAuctionDate(item.auctionDate) ?: return "기일 미정"
    val days = date.toEpochDay() - today.toEpochDay()
    return when {
        days > 0 -> "D-$days"
        days == 0L -> "오늘"
        else -> "기일 경과"
    }
}

fun formatAuctionUpdatedAt(value: String?): String? = value?.let {
    runCatching {
        OffsetDateTime.parse(it).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN))
    }.getOrNull()
}

fun AuctionItem.removedAtLabel(): String? = formatAuctionUpdatedAt(historyCreatedAt.ifBlank { null })

val AuctionItem.isRemoved: Boolean get() = historyStatus == "REMOVED"

fun isAuctionNewToday(
    isNew: Boolean,
    firstSeenAt: Long,
    now: Long = System.currentTimeMillis(),
): Boolean {
    if (!isNew || firstSeenAt <= 0L) return false
    val zone = ZoneId.of("Asia/Seoul")
    return Instant.ofEpochMilli(firstSeenAt).atZone(zone).toLocalDate() ==
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
}

fun isAuctionDataStale(value: String?, now: OffsetDateTime = OffsetDateTime.now()): Boolean {
    val updatedAt = value?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return false
    return Duration.between(updatedAt.toInstant(), now.toInstant()).toHours() >= 36
}
