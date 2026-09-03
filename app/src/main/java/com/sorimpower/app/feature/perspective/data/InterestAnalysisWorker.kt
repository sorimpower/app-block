package com.sorimpower.app.feature.perspective.data

import android.content.Context
import androidx.work.WorkManager

/** Interest analysis is deliberately user-initiated; this only removes legacy periodic work. */
object InterestAnalysisScheduler {
    private const val WORK_NAME = "youtube_interest_history_analysis"

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).apply {
            cancelUniqueWork(WORK_NAME)
            // Older builds used a different unique name. WorkManager always stores the worker
            // class as a tag, so cancel both forms during the one-time cleanup.
            cancelAllWorkByTag("com.sorimpower.app.feature.perspective.data.InterestAnalysisWorker")
            cancelAllWorkByTag("InterestAnalysisWorker")
        }
    }
}
