package com.sorimpower.app.feature.perspective.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

object InterestAnalysisScheduler {
    private const val WORK_NAME = "youtube_interest_history_analysis"
    private const val TARGET_DATE = "target_date"

    /** Queues one final snapshot for 23:55 local time; a completed worker queues tomorrow's. */
    fun scheduleNext(context: Context, appendAfterRunningWork: Boolean = false) {
        val zone = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        val scheduled = now.toLocalDate().atTime(23, 55).atZone(zone).let { if (it.isAfter(now)) it else it.plusDays(1) }
        val request = OneTimeWorkRequestBuilder<InterestAnalysisWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(Duration.between(now, scheduled))
            .setInputData(workDataOf(TARGET_DATE to scheduled.toLocalDate().toString()))
            .build()
        val policy = if (appendAfterRunningWork) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(WORK_NAME, policy, request)
    }
}

class InterestAnalysisWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val target = inputData.getString("target_date")?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() } ?: LocalDate.now()
        return try {
            PerspectiveRepository(applicationContext).runScheduledAnalyses(target)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            InterestAnalysisScheduler.scheduleNext(applicationContext, appendAfterRunningWork = true)
        }
    }
}
