package com.sorimpower.app.feature.auction.data

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuctionSyncPolicyTest {
    @Test
    fun `오전 9시 전에는 전날 갱신 캐시를 사용한다`() {
        assertFalse(
            shouldAutomaticallyRefreshAuctions(
                hasCache = true,
                lastSuccessfulSyncAt = seoulMillis(2026, 8, 5, 9, 10),
                lastAttemptAt = null,
                now = seoulMillis(2026, 8, 6, 8, 30),
            ),
        )
    }

    @Test
    fun `오전 9시 이후에는 오늘 갱신하지 않은 캐시를 새로고침한다`() {
        assertTrue(
            shouldAutomaticallyRefreshAuctions(
                hasCache = true,
                lastSuccessfulSyncAt = seoulMillis(2026, 8, 5, 9, 10),
                lastAttemptAt = null,
                now = seoulMillis(2026, 8, 6, 9, 1),
            ),
        )
    }

    @Test
    fun `오늘 오전 9시 이후 갱신한 캐시는 다시 호출하지 않는다`() {
        assertFalse(
            shouldAutomaticallyRefreshAuctions(
                hasCache = true,
                lastSuccessfulSyncAt = seoulMillis(2026, 8, 6, 9, 5),
                lastAttemptAt = seoulMillis(2026, 8, 6, 9, 5),
                now = seoulMillis(2026, 8, 6, 15, 0),
            ),
        )
    }

    @Test
    fun `캐시가 없으면 시간과 관계없이 호출한다`() {
        assertTrue(
            shouldAutomaticallyRefreshAuctions(
                hasCache = false,
                lastSuccessfulSyncAt = null,
                lastAttemptAt = seoulMillis(2026, 8, 6, 6, 55),
                now = seoulMillis(2026, 8, 6, 7, 0),
            ),
        )
    }

    @Test
    fun `실패 후 30분 동안 자동 재시도를 제한한다`() {
        val attemptAt = seoulMillis(2026, 8, 6, 9, 0)
        assertFalse(
            shouldAutomaticallyRefreshAuctions(
                hasCache = true,
                lastSuccessfulSyncAt = seoulMillis(2026, 8, 5, 9, 0),
                lastAttemptAt = attemptAt,
                now = attemptAt + AUCTION_RETRY_COOLDOWN_MILLIS - 1,
            ),
        )
        assertTrue(
            shouldAutomaticallyRefreshAuctions(
                hasCache = true,
                lastSuccessfulSyncAt = seoulMillis(2026, 8, 5, 9, 0),
                lastAttemptAt = attemptAt,
                now = attemptAt + AUCTION_RETRY_COOLDOWN_MILLIS,
            ),
        )
    }

    private fun seoulMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.of("Asia/Seoul"))
            .toInstant()
            .toEpochMilli()
}
