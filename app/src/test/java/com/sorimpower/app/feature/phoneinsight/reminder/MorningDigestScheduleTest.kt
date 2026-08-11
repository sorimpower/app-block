package com.sorimpower.app.feature.phoneinsight.reminder

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MorningDigestScheduleTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test fun `오전 8시 전에는 오늘 오전 분석으로 예약한다`() {
        val now = ZonedDateTime.of(2026, 8, 11, 7, 59, 59, 0, seoul)
        assertEquals(ZonedDateTime.of(2026,8,11,8,0,0,0,seoul), InsightDigestSchedule.next(now,InsightDigestSlot.MORNING))
    }

    @Test fun `오전 8시 이후에는 오후 7시 분석으로 예약한다`() {
        val now = ZonedDateTime.of(2026, 8, 11, 8, 0, 0, 0, seoul)
        assertEquals(ZonedDateTime.of(2026,8,11,19,0,0,0,seoul), InsightDigestSchedule.next(now,InsightDigestSlot.EVENING))
    }

    @Test fun `오후 7시 이후에는 다음날 오후 분석으로 예약한다`() {
        val now = ZonedDateTime.of(2026, 8, 11, 19, 0, 0, 0, seoul)
        assertEquals(ZonedDateTime.of(2026,8,12,19,0,0,0,seoul), InsightDigestSchedule.next(now,InsightDigestSlot.EVENING))
    }
}
