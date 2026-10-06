package com.sorimpower.app.feature.propertytracker.reminder

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class PropertyTrackerSyncSchedulerTest {
    private val korea = ZoneId.of("Asia/Seoul")

    @Test
    fun `오전 8시 전에는 오늘 8시를 예약한다`() {
        val now = ZonedDateTime.of(2026, 10, 6, 7, 30, 0, 0, korea)
        assertEquals(ZonedDateTime.of(2026, 10, 6, 8, 0, 0, 0, korea), PropertyTrackerSyncScheduler.nextRun(now))
    }

    @Test
    fun `오전 8시부터는 다음 날 8시를 예약한다`() {
        val now = ZonedDateTime.of(2026, 10, 6, 8, 0, 0, 0, korea)
        assertEquals(ZonedDateTime.of(2026, 10, 7, 8, 0, 0, 0, korea), PropertyTrackerSyncScheduler.nextRun(now))
    }
}
