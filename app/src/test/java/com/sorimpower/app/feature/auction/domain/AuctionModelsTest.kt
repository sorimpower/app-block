package com.sorimpower.app.feature.auction.domain

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuctionModelsTest {
    @Test
    fun `사건번호 복사는 타경 뒤 번호만 반환한다`() {
        assertEquals("1109", auctionCaseNumberForCopy("2025타경1109"))
        assertEquals("1802", auctionCaseNumberForCopy("2025타경 1802"))
        assertEquals("52290", auctionCaseNumberForCopy("2025타경52290 (중복)"))
        assertEquals("원본번호", auctionCaseNumberForCopy("원본번호"))
    }

    @Test
    fun `매각일과 시간이 있으면 서울 시간 한시간 일정으로 변환한다`() {
        val calendarTime = item(auctionDate = "2026-08-20").copy(auctionTime = "10:30").calendarTime()!!
        assertEquals(seoulMillis(2026, 8, 20, 10, 30), calendarTime.startAtMillis)
        assertEquals(seoulMillis(2026, 8, 20, 11, 30), calendarTime.endAtMillis)
        assertFalse(calendarTime.isAllDay)
        assertEquals("Asia/Seoul", calendarTime.timeZoneId)
    }

    @Test
    fun `매각 시간 없이 날짜만 있으면 종일 일정으로 변환한다`() {
        val calendarTime = item(auctionDate = "2026-08-20").copy(auctionTime = "").calendarTime()!!
        assertTrue(calendarTime.isAllDay)
        assertEquals("UTC", calendarTime.timeZoneId)
        assertEquals(24 * 60 * 60 * 1_000L, calendarTime.endAtMillis - calendarTime.startAtMillis)
        assertNull(item(auctionDate = "invalid").calendarTime())
    }

    @Test
    fun `지도 검색어는 구와 동을 포함한 아파트명을 우선한다`() {
        val apartment = item(
            address = "서울특별시 송파구 가락동 1",
            buildingName = " 헬리오시티 ",
        ).copy(sigungu = "송파구", dong = "가락동")

        assertEquals("서울특별시 송파구 가락동 헬리오시티", apartment.mapSearchQuery())
        assertEquals("서울특별시 강남구 도곡동 1", item(address = " 서울특별시 강남구 도곡동 1 ").mapSearchQuery())
    }

    @Test
    fun `단지명이 없으면 주소를 경매 카드 제목으로 사용한다`() {
        assertEquals(
            "송파구 가락동 1",
            item(address = "서울특별시 송파구 가락동 1", buildingName = "").displayTitle(),
        )
        assertEquals(
            "2025타경1",
            item(address = "", buildingName = "아파트").displayTitle(),
        )
    }

    @Test
    fun `종료 사건은 감지 시각 최신순으로 정렬하고 시각을 표시한다`() {
        val older = item().copy(itemKey = "older", historyCreatedAt = "2026-08-05T09:00:00+09:00", historyStatus = "REMOVED")
        val newer = item().copy(itemKey = "newer", historyCreatedAt = "2026-08-06T09:00:00+09:00", historyStatus = "REMOVED")

        assertEquals(
            listOf("newer", "older"),
            filterAndSortAuctions(
                listOf(older, newer),
                AuctionListFilter(),
                AuctionSortField.REMOVED_AT,
                AuctionSortDirection.DESCENDING,
            ).map(AuctionItem::itemKey),
        )
        assertTrue(newer.isRemoved)
        assertEquals("8월 6일 09:00", newer.removedAtLabel())
    }

    @Test
    fun `진행 중에 저장한 관심 키는 종료 사건에서도 유지한다`() {
        val endedFavorite = item().copy(itemKey = "favorite", historyStatus = "REMOVED")
        val endedNormal = item().copy(itemKey = "normal", historyStatus = "REMOVED")

        assertEquals(
            listOf(endedFavorite),
            favoriteAuctionItems(emptyList(), listOf(endedFavorite, endedNormal), setOf("favorite")),
        )
    }

    @Test
    fun `15억원 경계값을 포함한 서울 진행중 아파트만 허용한다`() {
        assertTrue(item().matchesAuctionCriteria())
        assertFalse(item(appraisalPrice = 1_499_999_999L).matchesAuctionCriteria())
        assertFalse(item(sido = "경기도", address = "경기도 성남시").matchesAuctionCriteria())
        assertFalse(item(usageName = "오피스텔").matchesAuctionCriteria())
        assertFalse(item(isInProgress = false).matchesAuctionCriteria())
    }

    @Test
    fun `주소가 서울이면 비어있는 시도 값을 보완한다`() {
        assertTrue(item(sido = "", address = "서울특별시 강남구 도곡동").matchesAuctionCriteria())
    }

    @Test
    fun `Google Sheets Date 문자열에서 시분만 추출한다`() {
        assertEquals("10:00", normalizeAuctionTime("Sat Dec 30 1899 10:00:00 GMT+0827 (한국 표준시)"))
        assertEquals("09:35", normalizeAuctionTime("09:35"))
        assertNull(normalizeAuctionTime("시간 미정"))
    }

    @Test
    fun `원 단위 가격을 억과 만원 단위로 표시한다`() {
        assertEquals("10억 원", formatAuctionPrice(1_000_000_000L))
        assertEquals("12억 5,000만 원", formatAuctionPrice(1_250_000_000L))
        assertEquals("9,500만 원", formatAuctionPrice(95_000_000L))
    }

    @Test
    fun `매각기일의 남은 날짜를 표시한다`() {
        val today = LocalDate.of(2026, 8, 6)
        assertEquals("D-4", auctionDdayLabel(item(auctionDate = "2026-08-10"), today))
        assertEquals("오늘", auctionDdayLabel(item(auctionDate = "2026-08-06"), today))
        assertEquals("기일 경과", auctionDdayLabel(item(auctionDate = "2026-08-05"), today))
        assertEquals("기일 미정", auctionDdayLabel(item(auctionDate = "invalid"), today))
    }

    @Test
    fun `36시간 이상 지난 데이터만 오래된 것으로 표시한다`() {
        val now = OffsetDateTime.parse("2026-08-07T21:27:15+09:00")
        assertFalse(isAuctionDataStale("2026-08-06T15:27:15+09:00", now))
        assertTrue(isAuctionDataStale("2026-08-06T09:27:15+09:00", now))
    }

    @Test
    fun `신규 배지는 서울 날짜 기준 오늘 처음 발견된 사건에만 표시한다`() {
        val now = seoulMillis(2026, 8, 7, 0, 5)
        assertTrue(isAuctionNewToday(true, seoulMillis(2026, 8, 7, 0, 1), now))
        assertFalse(isAuctionNewToday(true, seoulMillis(2026, 8, 6, 23, 59), now))
        assertFalse(isAuctionNewToday(false, seoulMillis(2026, 8, 7, 0, 1), now))
    }

    @Test
    fun `감정가 최저가 매각기일 유찰횟수 범위를 필터링하고 선택 기준으로 정렬한다`() {
        val items = listOf(
            item(auctionDate = "2026-08-20").copy(itemKey = "high", appraisalPrice = 2_000_000_000L, minimumPrice = 900_000_000L, failedCount = 1),
            item(auctionDate = "2026-08-10").copy(itemKey = "middle", appraisalPrice = 1_500_000_000L, minimumPrice = 1_300_000_000L, failedCount = 3),
            item(auctionDate = "2026-08-15").copy(itemKey = "low", appraisalPrice = 1_000_000_000L, minimumPrice = 1_000_000_000L, failedCount = 0),
        )
        val priceAndFailureFilter = AuctionListFilter(
            minAppraisalPrice = 1_500_000_000L,
            maxFailedCount = 2,
        )

        assertEquals(
            listOf("high"),
            filterAndSortAuctions(items, priceAndFailureFilter, AuctionSortField.AUCTION_DATE, AuctionSortDirection.ASCENDING).map { it.itemKey },
        )
        assertEquals(
            listOf("high", "middle", "low"),
            filterAndSortAuctions(items, AuctionListFilter(), AuctionSortField.APPRAISAL_PRICE, AuctionSortDirection.DESCENDING).map { it.itemKey },
        )
        assertEquals(
            listOf("middle", "low", "high"),
            filterAndSortAuctions(items, AuctionListFilter(), AuctionSortField.MINIMUM_PRICE, AuctionSortDirection.DESCENDING).map { it.itemKey },
        )
        assertEquals(
            listOf("middle", "high", "low"),
            filterAndSortAuctions(items, AuctionListFilter(), AuctionSortField.FAILED_COUNT, AuctionSortDirection.DESCENDING).map { it.itemKey },
        )
        assertEquals(
            listOf("middle", "low"),
            filterAndSortAuctions(
                items,
                AuctionListFilter(startAuctionDate = LocalDate.of(2026, 8, 10), endAuctionDate = LocalDate.of(2026, 8, 15)),
                AuctionSortField.AUCTION_DATE,
                AuctionSortDirection.ASCENDING,
            ).map { it.itemKey },
        )
    }

    @Test
    fun `아파트명 일부 검색은 공백을 무시하고 건물명에서 찾는다`() {
        val items = listOf(
            item().copy(itemKey = "helio", buildingName = "헬리오시티"),
            item().copy(itemKey = "tower", buildingName = "타워팰리스"),
        )

        assertEquals(
            listOf("helio"),
            filterAndSortAuctions(
                items,
                AuctionListFilter(),
                AuctionSortField.AUCTION_DATE,
                AuctionSortDirection.ASCENDING,
                "  헬리오  ",
            ).map { it.itemKey },
        )
    }

    private fun item(
        appraisalPrice: Long = 1_500_000_000L,
        sido: String = "서울특별시",
        address: String = "서울특별시 강남구 도곡동",
        usageName: String = "아파트",
        isInProgress: Boolean = true,
        auctionDate: String = "2026-08-10",
        buildingName: String = "아파트",
    ) = AuctionItem(
        itemKey = "key",
        courtCode = "court",
        courtName = "서울중앙지방법원",
        internalCaseNumber = "internal",
        caseNumber = "2025타경1",
        auctionItemNumber = "1",
        usageName = usageName,
        appraisalPrice = appraisalPrice,
        minimumPrice = appraisalPrice,
        minimumPriceRate = 100.0,
        failedCount = 0,
        auctionDate = auctionDate,
        auctionTime = "10:00",
        auctionPlace = "법정",
        address = address,
        sido = sido,
        sigungu = "강남구",
        dong = "도곡동",
        buildingName = buildingName,
        courtDepartment = "경매1계",
        courtTel = "",
        note = "",
        interestCount = 0,
        isInProgress = isInProgress,
        objectCount = 1,
        collectedAt = "2026-08-06T15:27:15+09:00",
    )

    private fun seoulMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.of("Asia/Seoul"))
            .toInstant()
            .toEpochMilli()
}
