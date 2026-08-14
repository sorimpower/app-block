package com.sorimpower.app.feature.phoneinsight.reminder

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.*
import androidx.core.content.ContextCompat
import androidx.work.*
import com.sorimpower.app.MainActivity
import com.sorimpower.app.feature.phoneinsight.data.*
import com.sorimpower.app.feature.phoneinsight.domain.InsightStatus
import kotlinx.coroutines.*
import java.time.*
import java.util.concurrent.TimeUnit

class MorningDigestWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result{val repo=PhoneInsightRepository(applicationContext);runCatching{repo.scanAll(false)};val values=runCatching{repo.digestRelevant()}.getOrDefault(emptyList());PhoneInsightNotifier.showDigest(applicationContext,values);repo.markNotified(values.take(8).map{it.id});return Result.success()}
}
object MorningDigestScheduler{
    private const val LEGACY_REQUEST=8301
    private const val EVENING_REQUEST=1901
    const val EXTRA_SLOT="digest_slot"
    fun scheduleAll(context:Context){cancelLegacy(context);cancelEvening(context);InsightDigestSlot.entries.forEach{scheduleSlot(context,it)}}
    fun scheduleNext(context:Context)=scheduleAll(context)
    internal fun scheduleSlot(context:Context,slot:InsightDigestSlot){val next=InsightDigestSchedule.next(ZonedDateTime.now(),slot);val intent=Intent(context,MorningDigestReceiver::class.java).putExtra(EXTRA_SLOT,slot.name);val pending=PendingIntent.getBroadcast(context,slot.requestCode,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val alarm=context.getSystemService(AlarmManager::class.java);if(Build.VERSION.SDK_INT<31||alarm.canScheduleExactAlarms())alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),pending)else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),pending)}
    private fun cancelLegacy(context:Context){val intent=Intent(context,MorningDigestReceiver::class.java);val pending=PendingIntent.getBroadcast(context,LEGACY_REQUEST,intent,PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?:return;context.getSystemService(AlarmManager::class.java).cancel(pending);pending.cancel()}
    private fun cancelEvening(context:Context){val intent=Intent(context,MorningDigestReceiver::class.java);val pending=PendingIntent.getBroadcast(context,EVENING_REQUEST,intent,PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?:return;context.getSystemService(AlarmManager::class.java).cancel(pending);pending.cancel()}
}
class MorningDigestReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){val slot=runCatching{InsightDigestSlot.valueOf(intent.getStringExtra(MorningDigestScheduler.EXTRA_SLOT).orEmpty())}.getOrDefault(InsightDigestSlot.MORNING);MorningDigestScheduler.scheduleSlot(context,slot);val date=LocalDate.now().toString();WorkManager.getInstance(context).enqueueUniqueWork("phone_insight_digest_${slot.name}_$date",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<MorningDigestWorker>().build())}}

object InsightReminderScheduler{
    const val EXTRA_ID="insight_id"
    private const val REMINDER_BEFORE_MILLIS=30*60*1_000L
    fun schedule(context:Context,values:List<PhoneInsightEntity>){values.forEach{value->val dueAt=value.dueAt?:return@forEach;if(dueAt<=System.currentTimeMillis())return@forEach;val trigger=maxOf(System.currentTimeMillis()+1_000L,dueAt-REMINDER_BEFORE_MILLIS);val intent=Intent(context,InsightDueReceiver::class.java).putExtra(EXTRA_ID,value.id);val pending=PendingIntent.getBroadcast(context,value.id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val alarm=context.getSystemService(AlarmManager::class.java);if(Build.VERSION.SDK_INT<31||alarm.canScheduleExactAlarms())alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pending)else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pending)}}
}

class InsightDueReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){val id=intent.getStringExtra(InsightReminderScheduler.EXTRA_ID)?:return;val pending=goAsync();CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{try{val value=PhoneInsightDatabase.get(context).dao().insight(id);if(value?.status in setOf(InsightStatus.ACTIVE,InsightStatus.REVIEW))PhoneInsightNotifier.showOne(context,value!!)}finally{pending.finish()}}}
}
class PhoneInsightBootReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){if(intent.action!=Intent.ACTION_BOOT_COMPLETED)return;MorningDigestScheduler.scheduleAll(context);val pending=goAsync();CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{try{InsightReminderScheduler.schedule(context,PhoneInsightDatabase.get(context).dao().activeNow())}finally{pending.finish()}}}
}

internal object PhoneInsightNotifier{
    private const val CHANNEL="phone_insight"
    fun showDigest(context:Context,values:List<PhoneInsightEntity>){if(values.isEmpty()||!allowed(context))return;val text=values.take(8).joinToString("\n"){"• ${PhoneInsightVisibility.dayLabel(it)?:""} ${it.title}"};manager(context).notify(4101,builder(context).setContentTitle("오늘·내일 챙길 항목 ${values.size}개").setContentText(values.first().title).setStyle(NotificationCompat.BigTextStyle().bigText(text)).build())}
    fun showOne(context:Context,value:PhoneInsightEntity){if(!allowed(context))return;manager(context).notify(value.id.hashCode(),builder(context).setContentTitle("30분 뒤 일정이 있어요").setContentText(value.title).setStyle(NotificationCompat.BigTextStyle().bigText(listOf(value.title,value.description).filter(String::isNotBlank).joinToString("\n"))).build())}
    fun cancel(context:Context,id:String){manager(context).cancel(id.hashCode())}
    fun clearDigest(context:Context){manager(context).cancel(4101)}
    private fun builder(context:Context)=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(com.sorimpower.app.R.drawable.ic_notification_najalal).setContentIntent(PendingIntent.getActivity(context,4101,Intent(context,MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PHONE_INSIGHT,true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
    private fun manager(context:Context)=NotificationManagerCompat.from(context).also{it.createNotificationChannel(NotificationChannel(CHANNEL,"챙김 알림",NotificationManager.IMPORTANCE_HIGH))}
    private fun allowed(context:Context)=Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
}
