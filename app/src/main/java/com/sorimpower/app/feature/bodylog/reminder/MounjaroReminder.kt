package com.sorimpower.app.feature.bodylog.reminder

import android.Manifest
import android.app.AlarmManager
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
import com.sorimpower.app.feature.bodylog.data.BodyLogDatabase
import com.sorimpower.app.feature.bodylog.domain.MOUNJARO_INTERVAL_MILLIS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object MounjaroReminder {
    private const val CHANNEL_ID = "mounjaro_reminders"
    private const val NOTIFICATION_ID = 2001
    private const val REQUEST_CODE = 2001
    private const val EXTRA_SCHEDULED_AT = "scheduled_at"
    private const val EXTRA_INTERVAL_WEEKS = "interval_weeks"

    fun schedule(context: Context, baseAt: Long, intervalWeeks: Int, enabled: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(reminderPendingIntent(context, baseAt, intervalWeeks))
        if (!enabled) return

        val interval = MOUNJARO_INTERVAL_MILLIS * intervalWeeks.coerceIn(1, 4)
        var scheduledAt = baseAt + interval
        while (scheduledAt <= System.currentTimeMillis()) scheduledAt += interval
        scheduleAt(context, scheduledAt, intervalWeeks)
    }

    fun showNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID,
                "마운자로 알림",
                NotificationManager.IMPORTANCE_DEFAULT,
            ))
        }
        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("마운자로 기록 알림")
                .setContentText("투여 기록과 의료진의 안내를 확인해 주세요.")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun scheduleAt(context: Context, scheduledAt: Long, intervalWeeks: Int) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            scheduledAt,
            reminderPendingIntent(context, scheduledAt, intervalWeeks),
        )
    }

    private fun reminderPendingIntent(context: Context, scheduledAt: Long, intervalWeeks: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, MounjaroReminderReceiver::class.java)
            .putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
            .putExtra(EXTRA_INTERVAL_WEEKS, intervalWeeks.coerceIn(1, 4)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun handleReminder(context: Context, intent: Intent) {
        val intervalWeeks = intent.getIntExtra(EXTRA_INTERVAL_WEEKS, 1).coerceIn(1, 4)
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, System.currentTimeMillis())
        showNotification(context)
        schedule(context, scheduledAt, intervalWeeks, enabled = true)
    }
}

class MounjaroReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MounjaroReminder.handleReminder(context, intent)
    }
}

class MounjaroBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                BodyLogDatabase.get(context).dao().latestMounjaroInjection()?.let { injection ->
                    MounjaroReminder.schedule(context, injection.injectedAt, injection.reminderIntervalWeeks, injection.reminderEnabled)
                }
            }
            pendingResult.finish()
        }
    }
}
