package com.sorimpower.app.feature.propertytracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PropertyTrackerModelsTest {
    @Test
    fun `네이버 억 단위 가격을 원으로 변환한다`() {
        assertEquals(2_980_000_000L, NaverLandProvider.parseKoreanPrice("29억 8,000"))
        assertEquals(900_000_000L, NaverLandProvider.parseKoreanPrice("9억"))
        assertEquals(87_500_000L, NaverLandProvider.parseKoreanPrice("8,750"))
        assertNull(NaverLandProvider.parseKoreanPrice(""))
    }

    @Test
    fun `단지 URL과 번호에서 complexNo를 추출한다`() {
        assertEquals("111515", NaverLandProvider.extractComplexNo("111515"))
        assertEquals("111515", NaverLandProvider.extractComplexNo("https://new.land.naver.com/complexes/111515?ms=37"))
        assertEquals("111515", NaverLandProvider.extractComplexNo("https://m.land.naver.com/complex/info/111515"))
        assertEquals("111515", NaverLandProvider.extractComplexNo("https://new.land.naver.com/api?complexNo=111515"))
        assertEquals("7", NaverLandProvider.extractAreaNo("https://new.land.naver.com/complexes/111515?areaNos=7"))
    }

    @Test
    fun `네이버 부동산 페이지에서 세션 토큰을 읽는다`() {
        val html = """<script>window.App={"state":{"token":{"token":"header.payload.signature"}}}</script>"""
        assertEquals("header.payload.signature", NaverLandProvider.extractSessionToken(html))
    }

    @Test
    fun `네이버 단지 검색 응답을 등록 후보로 변환한다`() {
        val results = NaverLandProvider.parseComplexSearch(
            """
            {
              "complexes": [{
                "complexNo": "111515",
                "complexName": "래미안원베일리",
                "cortarNo": "1165010700",
                "cortarAddress": "서울시 서초구 반포동",
                "totalHouseholdCount": 2990,
                "useApproveYmd": "20230830"
              }]
            }
            """.trimIndent(),
        )

        assertEquals(1, results.size)
        assertEquals("111515", results.single().complexNo)
        assertEquals("1165010700", results.single().cortarNo)
        assertEquals("서울시 서초구 반포동", results.single().address)
        assertEquals(2990, results.single().totalHouseholdCount)
    }

    @Test
    fun `네이버 단지 상세에서 전용면적과 평형번호를 읽는다`() {
        val detail = NaverLandProvider.parseComplexDetail(
            """
            {
              "complexDetail": {
                "complexNo": "111515",
                "complexName": "래미안원베일리",
                "cortarNo": "1165010700",
                "roadAddress": "서울시 서초구 반포대로 333"
              },
              "complexPyeongDetailList": [{
                "pyeongNo": "7",
                "pyeongName": "34",
                "supplyArea": 112.93,
                "exclusiveArea": 84.96,
                "householdCountByPyeong": 786
              }]
            }
            """.trimIndent(),
            "111515",
        )

        assertEquals("래미안원베일리", detail.complex.complexName)
        assertEquals("서울시 서초구 반포대로 333", detail.complex.address)
        assertEquals("7", detail.areas.single().areaNo)
        assertEquals(84.96, detail.areas.single().exclusiveAreaSqm, 0.001)
    }

    @Test
    fun `여러 네이버 평형번호를 정규화한다`() {
        assertEquals(listOf("1", "2"), NaverLandProvider.parseAreaNos("1, 2,1"))
        assertEquals(listOf("7"), NaverLandProvider.parseAreaNos("7"))
        assertEquals(emptyList<String>(), NaverLandProvider.parseAreaNos("잘못된 값"))
    }

    @Test
    fun `정상 조회에서 두 번 연속 사라져야 제거한다`() {
        assertEquals("MISSING_PENDING" to 1, PropertyTrackerRepository.nextMissingState("ACTIVE", 0))
        assertEquals("REMOVED" to 2, PropertyTrackerRepository.nextMissingState("MISSING_PENDING", 1))
        assertEquals("REMOVED" to 2, PropertyTrackerRepository.nextMissingState("REMOVED", 2))
    }

    @Test
    fun `여러 중개사가 올린 동일 매물을 한 건으로 묶는다`() {
        val first = listing("1", floor = "중/19", description = "한강뷰")
        val second = listing("2", floor = "중/19", description = "로열층", confirmDate = "20261006")
        val otherFloor = listing("3", floor = "저/19", description = "올수리")

        val groups = PropertyTrackerRepository.groupDuplicateListings(listOf(first, second, otherFloor))

        assertEquals(2, groups.size)
        val duplicate = groups.single { it.listings.size == 2 }
        assertEquals("2", duplicate.representative.articleNo)
        assertEquals(setOf("1", "2"), duplicate.listings.map(PropertyListingEntity::articleNo).toSet())
    }

    private fun listing(
        articleNo: String,
        floor: String,
        description: String,
        confirmDate: String = "20261001",
    ) = PropertyListingEntity(
        articleNo = articleNo,
        watchTargetId = "target",
        priceKrw = 2_000_000_000L,
        priceText = "20억",
        supplyAreaSqm = 73.0,
        exclusiveAreaSqm = 59.0,
        floorInfo = floor,
        direction = "남동향",
        buildingName = "504동",
        description = description,
        tags = "",
        confirmDate = confirmDate,
        sourceUrl = "https://example.com/$articleNo",
        firstSeenAt = 1L,
        lastSeenAt = 2L,
        status = "ACTIVE",
        consecutiveMisses = 0,
        removedAt = null,
    )
}
