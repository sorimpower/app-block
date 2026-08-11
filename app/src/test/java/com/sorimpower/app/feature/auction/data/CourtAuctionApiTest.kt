package com.sorimpower.app.feature.auction.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourtAuctionApiTest {
    @Test
    fun `법원 원본 구성행을 하나의 경매 물건으로 묶고 값을 정규화한다`() {
        val first = targetRow().copy(notifiedMinimumPrice = 1_200_000_000L)
        val second = targetRow().copy(address = "같은 물건의 두 번째 구성행")

        val items = groupCourtAuctionRows(listOf(first, second), "2026-08-07T09:10:00+09:00")

        assertEquals(1, items.size)
        with(items.single()) {
            assertEquals("item-1", itemKey)
            assertEquals(2, objectCount)
            assertEquals("2026-08-20", auctionDate)
            assertEquals("10:30", auctionTime)
            assertEquals(1_200_000_000L, minimumPrice)
            assertEquals(80.0, minimumPriceRate, 0.0)
            assertEquals("서울특별시", sido)
        }
    }

    @Test
    fun `서울 아파트 15억 이상 진행 중 조건 밖 원본 행은 제외한다`() {
        val rows = listOf(
            targetRow().copy(itemKey = "not-seoul", representativeSidoCode = "41"),
            targetRow().copy(itemKey = "not-apartment", usageName = "오피스텔"),
            targetRow().copy(itemKey = "too-cheap", appraisalPrice = 1_499_999_999L),
            targetRow().copy(itemKey = "not-active", isInProgress = false),
        )

        assertTrue(groupCourtAuctionRows(rows, "2026-08-07T09:10:00+09:00").isEmpty())
    }

    @Test
    fun `원본 그룹 키가 없으면 법원 사건 물건번호로 안정 키를 만든다`() {
        val item = groupCourtAuctionRows(
            listOf(targetRow().copy(itemKey = "")),
            "2026-08-07T09:10:00+09:00",
        ).single()

        assertEquals("B01-S01-1", item.itemKey)
    }

    private fun targetRow() = CourtAuctionRawRow(
        itemKey = "item-1",
        courtCode = "B01",
        courtName = "서울중앙지방법원",
        internalCaseNumber = "S01",
        caseNumber = "2026타경1",
        auctionItemNumber = "1",
        usageName = "아파트",
        appraisalPrice = 1_500_000_000L,
        minimumPrice = 700_000_000L,
        auctionDate = "20260820",
        auctionTime = "1030",
        address = "서울특별시 강남구 테스트로 1",
        representativeSidoCode = "11",
        isInProgress = true,
    )
}
