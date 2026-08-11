package com.sorimpower.app.feature.phoneinsight.data

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sorimpower.app.feature.phoneinsight.domain.*
import java.util.concurrent.TimeUnit

internal class InsightSourceCollector(private val context:Context){
    private val sms=SmsInsightSource(context)
    private val device=DeviceInsightSources(context)

    fun contacts()=device.contacts()
    fun smsHasPermission()=sms.hasPermission()

    fun collect(type:InsightSourceType,config:InsightSourceConfigEntity,since:Long?,contactNames:Map<String,String>):List<RawInsightItem> = when(type){
        InsightSourceType.SMS->sms.load(since).map{it.copy(sender=resolveContact(it.sender,contactNames))}
        InsightSourceType.SCREENSHOT->device.galleryImages(config.settings.accessUris.toList(),since)
        InsightSourceType.DOCUMENT->device.documents(config.settings.accessUris.toList(),since)
        InsightSourceType.CALL_RECORDING->device.callRecordings(config.settings.accessUris.toList(),since)
        InsightSourceType.CALENDAR->device.calendar(since)
        InsightSourceType.CONTACTS->emptyList()
        InsightSourceType.CALL_LOG->device.callLogs(since,contactNames)
        InsightSourceType.APP_USAGE->device.usage(config.settings.selectedPackages,since?:System.currentTimeMillis()-TimeUnit.DAYS.toMillis(1))
        InsightSourceType.NOTIFICATION->emptyList()
    }

    fun candidates(type:InsightSourceType,items:List<RawInsightItem>):List<RawInsightItem> = when(type){
        InsightSourceType.SMS,InsightSourceType.NOTIFICATION->InsightLocalPreprocessor.candidates(items)
        InsightSourceType.CALL_LOG->items.filter{it.text.startsWith("부재중")}
        InsightSourceType.CONTACTS->emptyList()
        else->items
    }

    fun isReady(type:InsightSourceType,settings:InsightSourceSettings):Boolean=when(type){
        InsightSourceType.SMS->sms.hasPermission()
        InsightSourceType.NOTIFICATION->context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        InsightSourceType.SCREENSHOT,InsightSourceType.CALL_RECORDING->hasFolderAccess(settings.accessUris)
        InsightSourceType.DOCUMENT->{val folders=settings.accessUris.filter{it!=DeviceInsightSources.DOWNLOADS_ROOT}.toSet();settings.accessUris.isNotEmpty()&&settings.accessUris.all{it!=DeviceInsightSources.DOWNLOADS_ROOT||Environment.isExternalStorageManager()}&&(folders.isEmpty()||hasFolderAccess(folders))}
        InsightSourceType.CALENDAR->hasPermission(Manifest.permission.READ_CALENDAR)
        InsightSourceType.CONTACTS->hasPermission(Manifest.permission.READ_CONTACTS)
        InsightSourceType.CALL_LOG->hasPermission(Manifest.permission.READ_CALL_LOG)
        InsightSourceType.APP_USAGE->{val ops=context.getSystemService(AppOpsManager::class.java);ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName)==AppOpsManager.MODE_ALLOWED}
    }

    private fun hasPermission(value:String)=ContextCompat.checkSelfPermission(context,value)==PackageManager.PERMISSION_GRANTED
    private fun hasFolderAccess(values:Set<String>):Boolean{if(values.isEmpty())return false;val granted=context.contentResolver.persistedUriPermissions.filter{it.isReadPermission}.map{it.uri.toString()}.toSet();return values.all{it in granted}}
    private fun resolveContact(sender:String,names:Map<String,String>):String{val normalized=sender.filter(Char::isDigit).takeLast(10);return names[normalized]?:names.entries.firstOrNull{it.key.endsWith(normalized)||normalized.endsWith(it.key)}?.value?:sender}
}
