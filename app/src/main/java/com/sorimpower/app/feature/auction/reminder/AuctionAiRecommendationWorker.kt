package com.sorimpower.app.feature.auction.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sorimpower.app.MainActivity
import com.sorimpower.app.feature.auction.data.AuctionAiAnalysisMode
import com.sorimpower.app.feature.auction.data.AuctionAiPreferencesRepository
import com.sorimpower.app.feature.auction.data.AuctionRepository
import com.sorimpower.app.feature.auction.domain.AuctionAiAnalysis
import com.sorimpower.app.feature.auction.domain.AuctionAiPreferences
import com.sorimpower.app.feature.auction.domain.AuctionItem
import com.sorimpower.app.feature.auction.domain.auctionDiscountRate
import com.sorimpower.app.feature.auction.domain.displayTitle
import com.sorimpower.app.feature.auction.domain.formatAuctionPrice
import com.sorimpower.app.feature.auction.domain.isAuctionNewToday
import com.sorimpower.app.feature.auction.domain.isPreliminaryRecommendationEligible
import com.sorimpower.app.feature.auction.domain.matchesAiPreferences
import com.sorimpower.app.feature.auction.domain.parseAuctionDate
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AuctionAiRecommendationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val preferences = AuctionAiPreferencesRepository(applicationContext).current()
        if (!preferences.dailyRecommendationEnabled) return Result.success()

        val repository = AuctionRepository(applicationContext)
        val refreshSucceeded = runCatching { repository.refresh() }.isSuccess
        if (!refreshSucceeded) return if (runAttemptCount < 2) Result.retry() else Result.success()
        val items = repository.getCurrentItems()
        if (items.isEmpty()) return Result.success()
        val analyzedKeys = repository.getAiAnalysisKeys()

        val criteria = preferences.toCriteria()
        val candidates = items.asSequence()
            .filter { isAuctionNewToday(it.isNew, it.firstSeenAt) }
            .filter { it.itemKey !in analyzedKeys }
            .filter { it.matchesAiPreferences(preferences) }
            .sortedWith(
                compareByDescending<AuctionItem> { it.isNew }
                    .thenByDescending(AuctionItem::auctionDiscountRate)
                    .thenBy { parseAuctionDate(it.auctionDate) },
            )
            .take(MAX_DAILY_ANALYSES)
            .toList()

        val recommendations = buildList {
            for (item in candidates) {
                val analysis = repository.analyzeRights(
                    item = item,
                    criteria = criteria,
                    mode = AuctionAiAnalysisMode.DAILY_RECOMMENDATION,
                )
                if (analysis.isPreliminaryRecommendationEligible(criteria)) add(item to analysis)
            }
        }.sortedByDescending { (_, analysis) -> analysis.suitabilityScore }

        if (recommendations.isNotEmpty()) {
            AuctionAiRecommendationNotifier.show(applicationContext, recommendations.take(MAX_NOTIFICATION_ITEMS))
        }
        return Result.success()
    }

    private companion object {
        const val MAX_DAILY_ANALYSES = 5
        const val MAX_NOTIFICATION_ITEMS = 3
    }
}

object AuctionAiRecommendationScheduler {
    private const val UNIQUE_WORK_NAME = "auction_ai_daily_recommendation"

    fun schedule(
        context: Context,
        preferences: AuctionAiPreferences,
        existingWorkPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
    ) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!preferences.dailyRecommendationEnabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val now = ZonedDateTime.now()
        var nextRun = now.toLocalDate().atTime(preferences.notificationHour, 0).atZone(now.zone)
        if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
        val initialDelay = Duration.between(now, nextRun).toMillis().coerceAtLeast(0L)
        val request = PeriodicWorkRequestBuilder<AuctionAiRecommendationWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, existingWorkPolicy, request)
    }

    fun restore(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val preferences = AuctionAiPreferencesRepository(context.applicationContext).current()
            schedule(context, preferences, ExistingPeriodicWorkPolicy.KEEP)
        }
    }

}

private object AuctionAiRecommendationNotifier {
    private const val CHANNEL_ID = "auction_ai_recommendations"
    private const val NOTIFICATION_ID = 3101
    private const val REQUEST_CODE = 3101

    fun show(context: Context, recommendations: List<Pair<AuctionItem, AuctionAiAnalysis>>) {
        val manager = notificationManager(context) ?: return
        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_AUCTION_ANALYSES, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val lines = recommendations.map { (item, analysis) ->
            "${item.displayTitle()} · ${analysis.suitabilityScore}점 · 최저 ${formatAuctionPrice(item.minimumPrice)}"
        }
        val title = "AI 예비 추천 경매 ${recommendations.size}건"
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(lines.first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n") + "\n입찰 전 등기와 법원 원문을 확인하세요."))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    private fun notificationManager(context: Context): NotificationManagerCompat? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return null
        return NotificationManagerCompat.from(context).also { manager ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "경매 AI 추천", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "매일 아침 AI가 선별한 법원 경매 사건을 알려줍니다."
                    },
                )
            }
        }
    }
}
