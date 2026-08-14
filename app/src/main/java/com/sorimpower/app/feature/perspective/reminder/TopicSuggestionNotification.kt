package com.sorimpower.app.feature.perspective.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sorimpower.app.MainActivity
import com.sorimpower.app.feature.perspective.data.PerspectiveRepository
import com.sorimpower.app.feature.perspective.data.PerspectiveTopicEntity
import com.sorimpower.app.feature.perspective.data.TopicSuggestionEntity
import com.sorimpower.app.feature.perspective.data.WatchedVideoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

object TopicSuggestionNotifier {
    private const val CHANNEL_ID = "perspective_topic_suggestions"
    private const val EXPLORE_CHANNEL_ID = "perspective_explore_suggestions"

    fun show(context: Context, suggestion: TopicSuggestionEntity, video: WatchedVideoEntity) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notificationId = notificationId(suggestion.videoId)
        val openIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_PERSPECTIVE_TOPICS)
                .putExtra(MainActivity.EXTRA_OPEN_PERSPECTIVE_TOPICS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val acceptIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            Intent(context, TopicSuggestionActionReceiver::class.java)
                .setAction(TopicSuggestionActionReceiver.ACTION_ACCEPT)
                .putExtra(TopicSuggestionActionReceiver.EXTRA_VIDEO_ID, suggestion.videoId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            Intent(context, TopicSuggestionActionReceiver::class.java)
                .setAction(TopicSuggestionActionReceiver.ACTION_DISMISS)
                .putExtra(TopicSuggestionActionReceiver.EXTRA_VIDEO_ID, suggestion.videoId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager(context).notify(
            notificationId,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.sorimpower.app.R.drawable.ic_notification_najalal)
                .setContentTitle("‘${suggestion.proposedName}’ 주제를 등록할까요?")
                .setContentText(video.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${video.title}\n${suggestion.description}".trim()))
                .setContentIntent(openIntent)
                .addAction(0, "주제 등록", acceptIntent)
                .addAction(0, "건너뛰기", dismissIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    fun cancel(context: Context, videoId: String) = NotificationManagerCompat.from(context).cancel(notificationId(videoId))

    /** 이미 승인한 주제로 분류된 영상은 재등록을 요구하지 않고 바로 분석을 제안한다. */
    fun showExplore(context: Context, topic: PerspectiveTopicEntity, video: WatchedVideoEntity): Boolean {
        if (!canNotify(context) || !claimExploreDailySlot(context)) return false
        val notificationId = 56_000 + (video.id.hashCode() and 0x0FFF)
        val openIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_PERSPECTIVE_RECORDS)
                .putExtra(MainActivity.EXTRA_OPEN_PERSPECTIVE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).also {
            it.createNotificationChannel(NotificationChannel(EXPLORE_CHANNEL_ID, "유튜브 분석 제안", NotificationManager.IMPORTANCE_DEFAULT))
            it.notify(
                notificationId,
                NotificationCompat.Builder(context, EXPLORE_CHANNEL_ID)
                    .setSmallIcon(com.sorimpower.app.R.drawable.ic_notification_najalal)
                    .setContentTitle("‘${topic.name}’ 영상이에요")
                    .setContentText("다른 관점 4개를 찾아볼까요?")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("${video.title}\n다른 관점 4개를 찾아볼까요?"))
                    .setContentIntent(openIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build(),
            )
        }
        return true
    }

    private fun manager(context: Context): NotificationManagerCompat = NotificationManagerCompat.from(context).also {
        it.createNotificationChannel(NotificationChannel(CHANNEL_ID, "유튜브 분석 주제 제안", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** 기존 주제의 다른 관점 제안은 영상을 끝낸 뒤 하루 두 번까지만 보낸다. */
    private fun claimExploreDailySlot(context: Context): Boolean {
        val preferences = context.getSharedPreferences("perspective_explore_notifications", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val savedDay = preferences.getString("day", null)
        val count = if (savedDay == today) preferences.getInt("count", 0) else 0
        if (count >= MAX_EXPLORE_NOTIFICATIONS_PER_DAY) return false
        preferences.edit().putString("day", today).putInt("count", count + 1).apply()
        return true
    }

    private fun notificationId(videoId: String): Int = 52_000 + (videoId.hashCode() and 0x0FFF)
    private const val MAX_EXPLORE_NOTIFICATIONS_PER_DAY = 2
}

class TopicSuggestionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = PerspectiveRepository(context)
                when (intent.action) {
                    ACTION_ACCEPT -> repository.acceptTopicSuggestion(videoId)
                    ACTION_DISMISS -> repository.dismissTopicSuggestion(videoId)
                }
            } finally {
                TopicSuggestionNotifier.cancel(context, videoId)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.sorimpower.app.perspective.ACCEPT_TOPIC"
        const val ACTION_DISMISS = "com.sorimpower.app.perspective.DISMISS_TOPIC"
        const val EXTRA_VIDEO_ID = "video_id"
    }
}
