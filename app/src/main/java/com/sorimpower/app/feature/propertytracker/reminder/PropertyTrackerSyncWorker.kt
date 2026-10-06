package com.sorimpower.app.feature.propertytracker.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sorimpower.app.feature.propertytracker.data.PropertyTrackerRepository
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class PropertyTrackerSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            PropertyTrackerRepository(applicationContext).syncAll(force = false)
            Result.success()
        } finally {
            PropertyTrackerSyncScheduler.scheduleNext(applicationContext)
        }
    }
}

object PropertyTrackerSyncScheduler {
    private const val UNIQUE_WORK_NAME = "property_tracker_daily_sync_v1"
    private const val SYNC_HOUR = 8
    private val KOREA_ZONE = ZoneId.of("Asia/Seoul")

    fun restore(context: Context) = schedule(context, ExistingWorkPolicy.KEEP)

    fun scheduleNext(context: Context) = schedule(context, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun schedule(context: Context, policy: ExistingWorkPolicy) {
        val now = ZonedDateTime.now(KOREA_ZONE)
        val next = nextRun(now)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<PropertyTrackerSyncWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
    }

    internal fun nextRun(now: ZonedDateTime): ZonedDateTime {
        val koreaNow = now.withZoneSameInstant(KOREA_ZONE)
        var next = koreaNow.toLocalDate().atTime(SYNC_HOUR, 0).atZone(KOREA_ZONE)
        if (!next.isAfter(koreaNow)) next = next.plusDays(1)
        return next
    }
}
