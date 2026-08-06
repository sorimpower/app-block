package com.sorimpower.app.feature.auction.domain

import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuctionModelsTest {
    @Test
    fun `지도 검색어는 주소를 우선하고 없으면 아파트명을 사용한다`() {
        assertEquals("서울특별시 송파구 가락동 1", item(address = " 서울특별시 송파구 가락동 1 ").mapSearchQuery())
        assertEquals("헬리오시티", item(address = "", buildingName = " 헬리오시티 ").mapSearchQuery())
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
    fun `10억원 경계값을 포함한 서울 진행중 아파트만 허용한다`() {
        assertTrue(item().matchesAuctionCriteria())
        assertFalse(item(appraisalPrice = 999_999_999L).matchesAuctionCriteria())
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
        appraisalPrice: Long = 1_000_000_000L,
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
}
