package com.sorimpower.app.feature.bodylog.reminder

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sorimpower.app.feature.bodylog.data.BodyLogRepository

class MealCalorieAnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val mealId = inputData.getString(KEY_MEAL_ID)?.takeIf(String::isNotBlank) ?: return Result.failure()
        return try {
            BodyLogRepository(applicationContext).analyzeMealCalories(mealId)
            Result.success()
        } catch (error: Throwable) {
            Log.w("MealCalorieWorker", "Meal calorie analysis failed: $mealId, attempt=$runAttemptCount", error)
            if (runAttemptCount < 2) Result.retry()
            else if (inputData.getBoolean(KEY_BACKFILL, false)) Result.success() else Result.failure()
        }
    }

    companion object {
        const val KEY_MEAL_ID = "meal_id"
        const val KEY_BACKFILL = "backfill"
    }
}

/** Each saved/edited meal gets one persisted AI job, then its day's total is recalculated immediately. */
object MealCalorieAnalysisScheduler {
    private const val BACKFILL_WORK_NAME = "meal_calorie_estimate_backfill_v1"
    private fun workName(mealId: String) = "meal_calorie_analysis_$mealId"

    fun enqueue(context: Context, mealId: String) {
        val request = request(mealId, backfill = false)
        WorkManager.getInstance(context).enqueueUniqueWork(workName(mealId), ExistingWorkPolicy.REPLACE, request)
    }

    fun enqueueBackfill(context: Context, mealIds: List<String>) {
        val requests = mealIds.distinct().map { request(it, backfill = true) }
        if (requests.isEmpty()) return
        val manager = WorkManager.getInstance(context)
        var chain = manager.beginUniqueWork(BACKFILL_WORK_NAME, ExistingWorkPolicy.KEEP, requests.first())
        requests.drop(1).forEach { chain = chain.then(it) }
        chain.enqueue()
    }

    fun cancel(context: Context, mealId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(mealId))
    }

    /** Removes the periodic/catch-up jobs installed by app versions before v0.12.4. */
    fun cancelObsoleteDailyWork(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork("daily_calorie_analysis")
            cancelUniqueWork("daily_calorie_analysis_catch_up")
            cancelUniqueWork("daily_calorie_analysis_initial_backfill")
        }
    }

    private fun request(mealId: String, backfill: Boolean) = OneTimeWorkRequestBuilder<MealCalorieAnalysisWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInputData(Data.Builder()
            .putString(MealCalorieAnalysisWorker.KEY_MEAL_ID, mealId)
            .putBoolean(MealCalorieAnalysisWorker.KEY_BACKFILL, backfill)
            .build())
        .build()
}
