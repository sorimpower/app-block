package com.sorimpower.app

import android.app.Application
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.sorimpower.app.feature.auction.reminder.AuctionAiRecommendationScheduler
import com.sorimpower.app.feature.bodylog.data.BodyLogRepository
import com.sorimpower.app.feature.bodylog.reminder.MealCalorieAnalysisScheduler
import com.sorimpower.app.feature.phoneinsight.service.PhoneInsightNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SorimPowerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationListenerService.requestRebind(ComponentName(this, PhoneInsightNotificationListenerService::class.java))
        AuctionAiRecommendationScheduler.restore(this)
        MealCalorieAnalysisScheduler.cancelObsoleteDailyWork(this)
        FirebaseApp.initializeApp(this) ?: return
        FirebaseAppCheck.getInstance().installSorimPowerProvider()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val missingMealIds = BodyLogRepository(this@SorimPowerApplication).mealIdsWithoutCalorieEstimate()
            MealCalorieAnalysisScheduler.enqueueBackfill(this@SorimPowerApplication, missingMealIds)
        }
    }
}
