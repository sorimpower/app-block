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

class ExerciseCalorieAnalysisWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val exerciseId = inputData.getString(KEY_EXERCISE_ID)?.takeIf(String::isNotBlank) ?: return Result.failure()
        return runCatching { BodyLogRepository(applicationContext).analyzeExerciseCalories(exerciseId); Result.success() }
            .getOrElse { error ->
                Log.w("ExerciseCalorieWorker", "Exercise calorie analysis failed: $exerciseId, attempt=$runAttemptCount", error)
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
    }
    companion object { const val KEY_EXERCISE_ID = "exercise_id" }
}

object ExerciseCalorieAnalysisScheduler {
    private fun workName(id: String) = "exercise_calorie_analysis_$id"
    fun enqueue(context: Context, exerciseId: String) {
        val request = OneTimeWorkRequestBuilder<ExerciseCalorieAnalysisWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(Data.Builder().putString(ExerciseCalorieAnalysisWorker.KEY_EXERCISE_ID, exerciseId).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(exerciseId), ExistingWorkPolicy.REPLACE, request)
    }
    fun cancel(context: Context, exerciseId: String) = WorkManager.getInstance(context).cancelUniqueWork(workName(exerciseId))
}
