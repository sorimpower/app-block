package com.sorimpower.app.feature.auction.data

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

private val SEOUL_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
private val DAILY_REFRESH_TIME: LocalTime = LocalTime.of(9, 0)
internal const val AUCTION_RETRY_COOLDOWN_MILLIS = 30 * 60 * 1_000L

internal fun shouldAutomaticallyRefreshAuctions(
    hasCache: Boolean,
    lastSuccessfulSyncAt: Long?,
    lastAttemptAt: Long?,
    now: Long,
): Boolean {
    if (!hasCache || lastSuccessfulSyncAt == null) return true
    val elapsedSinceAttempt = lastAttemptAt?.let(now::minus)
    if (elapsedSinceAttempt != null && elapsedSinceAttempt in 0 until AUCTION_RETRY_COOLDOWN_MILLIS) return false

    val current = Instant.ofEpochMilli(now).atZone(SEOUL_ZONE_ID)
    val refreshDate = if (current.toLocalTime() >= DAILY_REFRESH_TIME) {
        current.toLocalDate()
    } else {
        current.toLocalDate().minusDays(1)
    }
    val latestRefreshCutoff = refreshDate
        .atTime(DAILY_REFRESH_TIME)
        .atZone(SEOUL_ZONE_ID)
        .toInstant()
        .toEpochMilli()
    return lastSuccessfulSyncAt < latestRefreshCutoff
}
