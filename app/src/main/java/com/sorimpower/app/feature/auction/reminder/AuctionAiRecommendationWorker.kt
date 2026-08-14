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
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
        if (!refreshSucceeded) {
            return if (runAttemptCount < 2) Result.retry()
            else completeAndScheduleTomorrow(preferences)
        }
        val items = repository.getCurrentItems()
        if (items.isEmpty()) return completeAndScheduleTomorrow(preferences)
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
        return completeAndScheduleTomorrow(preferences)
    }

    private fun completeAndScheduleTomorrow(preferences: AuctionAiPreferences): Result {
        // PeriodicWork는 실제 실행 시각을 기준으로 다음 주기를 계산해 8시 예약이 밀릴 수 있다.
        // 완료 후 다음 날 목표 시각을 새로 계산하면 매일 아침 시간대로 다시 맞춰진다.
        AuctionAiRecommendationScheduler.scheduleNextAfterCompletion(applicationContext, preferences)
        return Result.success()
    }

    private companion object {
        const val MAX_DAILY_ANALYSES = 5
        const val MAX_NOTIFICATION_ITEMS = 3
    }
}

object AuctionAiRecommendationScheduler {
    private const val UNIQUE_WORK_NAME = "auction_ai_daily_recommendation_v2"
    private const val LEGACY_WORK_NAME = "auction_ai_daily_recommendation"

    /** 사용자가 켜기/시간 변경을 저장했을 때 기존 예약을 목표 시각으로 교체한다. */
    fun schedule(context: Context, preferences: AuctionAiPreferences) =
        scheduleAtNextMorning(context, preferences, ExistingWorkPolicy.REPLACE)

    /** 현재 실행 중인 작업 뒤에 다음 날 작업을 연결한다. */
    fun scheduleNextAfterCompletion(context: Context, preferences: AuctionAiPreferences) =
        scheduleAtNextMorning(context, preferences, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun scheduleAtNextMorning(
        context: Context,
        preferences: AuctionAiPreferences,
        existingWorkPolicy: ExistingWorkPolicy,
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
        val request = OneTimeWorkRequestBuilder<AuctionAiRecommendationWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, existingWorkPolicy, request)
    }

    fun restore(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val preferences = AuctionAiPreferencesRepository(context.applicationContext).current()
            // v1 PeriodicWork가 남아 있으면 KEEP 정책이 새 단발 예약을 무시한다.
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(LEGACY_WORK_NAME)
            scheduleAtNextMorning(context, preferences, ExistingWorkPolicy.KEEP)
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
                .setSmallIcon(com.sorimpower.app.R.drawable.ic_notification_najalal)
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
