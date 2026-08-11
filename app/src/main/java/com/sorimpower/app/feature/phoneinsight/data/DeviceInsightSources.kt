package com.sorimpower.app.feature.phoneinsight.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.sorimpower.app.feature.phoneinsight.domain.*
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.io.File
import android.os.Environment

/** Source adapters keep originals on the device and return only bounded candidates for the common queue. */
internal class DeviceInsightSources(private val context: Context) {
    fun galleryImages(treeUris: List<String>, since: Long?): List<RawInsightItem> = treeUris.flatMap { value ->
        val root=DocumentFile.fromTreeUri(context,Uri.parse(value))?:return@flatMap emptyList()
        walk(root).filter{it.isFile&&it.type?.startsWith("image/")==true&&(since==null||it.lastModified()>=since)}.map { file ->
            val metadata=imageMetadata(file.uri)
            RawInsightItem(InsightSourceType.SCREENSHOT,"image:${file.uri}","사진·이미지",listOfNotNull(file.name?:"사진",metadata).joinToString(" · "),file.lastModified(),file.uri.toString())
        }.toList()
    }.distinctBy { it.sourceId }.sortedByDescending { it.occurredAt }.take(MAX_SOURCE_ITEMS)

    private fun imageMetadata(uri:Uri):String?=runCatching{
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif=ExifInterface(input);val parts=mutableListOf<String>()
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { parts += "촬영일 $it" }
            exif.latLong?.let { parts += "촬영위치 위도 %.5f, 경도 %.5f".format(it[0],it[1]) }
            parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
    }.getOrNull()

    fun callRecordings(treeUris:List<String>, since:Long?):List<RawInsightItem>{
        val supported=setOf("audio/mpeg","audio/mp4","audio/mp4a-latm","audio/x-m4a","audio/wav","audio/webm","video/mp4")
        return treeUris.flatMap { value -> val root=DocumentFile.fromTreeUri(context,Uri.parse(value))?:return@flatMap emptyList();walk(root).filter{it.isFile&&(it.type in supported||it.name?.substringAfterLast('.',"")?.lowercase() in setOf("mp3","mp4","m4a","wav","webm"))&&(since==null||it.lastModified()>=since)}.map{RawInsightItem(InsightSourceType.CALL_RECORDING,"audio:${it.uri}","통화 녹음",it.name?:"통화 녹음",it.lastModified(),it.uri.toString())}.toList() }.distinctBy{it.sourceId}.sortedByDescending{it.occurredAt}.take(MAX_SOURCE_ITEMS)
    }

    fun documents(treeUris:List<String>, since:Long?):List<RawInsightItem> = treeUris.flatMap { value ->
        if(value==DOWNLOADS_ROOT)return@flatMap downloadFiles(since)
        val root=DocumentFile.fromTreeUri(context,Uri.parse(value))?:return@flatMap emptyList()
        walk(root).filter{it.isFile&&(it.type?.startsWith("image/")==true||it.type=="application/pdf")&&(since==null||it.lastModified()>=since)}.map{RawInsightItem(InsightSourceType.DOCUMENT,"doc:${it.uri}","파일·문서",it.name?:"문서",it.lastModified(),it.uri.toString())}.toList()
    }.distinctBy{it.sourceId}.sortedByDescending{it.occurredAt}.take(MAX_SOURCE_ITEMS)

    private fun downloadFiles(since:Long?):List<RawInsightItem>{
        if(!Environment.isExternalStorageManager())return emptyList();val root=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return root.walkTopDown().maxDepth(4).filter{it.isFile&&it.extension.lowercase() in setOf("pdf","jpg","jpeg","png","webp")&&(since==null||it.lastModified()>=since)}.map{RawInsightItem(InsightSourceType.DOCUMENT,"doc:${it.absolutePath}","Downloads",it.name,it.lastModified(),it.toURI().toString())}.take(MAX_SOURCE_ITEMS).toList()
    }

    fun contacts():Map<String,String> = runCatching {
        val result=mutableMapOf<String,String>();val p=arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,p,null,null,null)?.use { c -> while(c.moveToNext()){val number=normalize(c.getString(0));val name=c.getString(1).orEmpty();if(number.isNotBlank()&&name.isNotBlank())result[number]=name} };result
    }.getOrDefault(emptyMap())

    fun calendar(since:Long?):List<RawInsightItem> = runCatching {
        val now=System.currentTimeMillis();val start=since?:now-TimeUnit.DAYS.toMillis(1);val end=now+TimeUnit.DAYS.toMillis(14)
        // Instances expands repeating calendar events into their actual dates (for example, this Sunday's recurring lottery purchase).
        val uri=CalendarContract.Instances.CONTENT_URI.buildUpon().appendPath(start.toString()).appendPath(end.toString()).build()
        val p=arrayOf(CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.TITLE,CalendarContract.Instances.BEGIN,CalendarContract.Instances.END,CalendarContract.Instances.EVENT_LOCATION,CalendarContract.Instances.DESCRIPTION)
        context.contentResolver.query(uri,p,null,null,"${CalendarContract.Instances.BEGIN} ASC")?.use { c -> buildList { while(c.moveToNext()){val eventId=c.getLong(0);val title=c.getString(1).orEmpty();val startedAt=c.getLong(2);val endsAt=c.getLong(3);val location=c.getString(4).orEmpty();val description=c.getString(5).orEmpty();val ongoing=startedAt<now&&endsAt>now;val deadline=if(ongoing)endsAt else startedAt;val sourceId="calendar:instance-v5:$eventId:$startedAt:$endsAt";add(RawInsightItem(InsightSourceType.CALENDAR,sourceId,"캘린더",listOf(title,"시작 ${Instant.ofEpochMilli(startedAt)}","종료 ${Instant.ofEpochMilli(endsAt)}",location,description).filter(String::isNotBlank).joinToString(" · "),deadline))} } }?: emptyList()
    }.getOrDefault(emptyList()).take(MAX_SOURCE_ITEMS)

    fun callLogs(since:Long?, names:Map<String,String>):List<RawInsightItem> = runCatching {
        val p=arrayOf(CallLog.Calls._ID,CallLog.Calls.NUMBER,CallLog.Calls.DATE,CallLog.Calls.DURATION,CallLog.Calls.TYPE,CallLog.Calls.CACHED_NAME)
        val selection=since?.let{"${CallLog.Calls.DATE} >= ?"};val args=since?.let{arrayOf(it.toString())}
        context.contentResolver.query(CallLog.Calls.CONTENT_URI,p,selection,args,"${CallLog.Calls.DATE} DESC")?.use { c -> buildList { var count=0;while(c.moveToNext()&&count<MAX_SOURCE_ITEMS){val id=c.getLong(0);val number=c.getString(1).orEmpty();val date=c.getLong(2);val duration=c.getLong(3);val type=c.getInt(4);val providerName=c.getString(5).orEmpty();val name=providerName.ifBlank{resolveName(number,names)};val direction=when(type){CallLog.Calls.MISSED_TYPE->"부재중";CallLog.Calls.INCOMING_TYPE->"수신";CallLog.Calls.OUTGOING_TYPE->"발신";CallLog.Calls.REJECTED_TYPE->"거절";else->"기타"};add(RawInsightItem(InsightSourceType.CALL_LOG,"call:$id",name.ifBlank{number},"$direction 통화 · ${duration}초 · ${Instant.ofEpochMilli(date)}",date));count++} } }?: emptyList()
    }.getOrDefault(emptyList())

    fun usage(selected:Set<String>, since:Long=System.currentTimeMillis()-TimeUnit.DAYS.toMillis(1)):List<RawInsightItem>{
        if(selected.isEmpty())return emptyList();val values=context.getSystemService(UsageStatsManager::class.java).queryAndAggregateUsageStats(since,System.currentTimeMillis())
        return values.filter{(pkg,s)->pkg in selected&&s.totalTimeInForeground>=TimeUnit.MINUTES.toMillis(5)}.map{(pkg,s)->val label=runCatching{context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg,0)).toString()}.getOrDefault(pkg);RawInsightItem(InsightSourceType.APP_USAGE,"usage:$pkg:${System.currentTimeMillis()/TimeUnit.DAYS.toMillis(1)}",label,"$label 사용 ${TimeUnit.MILLISECONDS.toMinutes(s.totalTimeInForeground)}분",System.currentTimeMillis())}
    }
    private fun normalize(value:String)=value.filter(Char::isDigit).takeLast(10)
    private fun resolveName(number:String,names:Map<String,String>):String{val normalized=normalize(number);return names[normalized]?:names.entries.firstOrNull{it.key.endsWith(normalized)||normalized.endsWith(it.key)}?.value.orEmpty()}
    private fun walk(root:DocumentFile,maxDepth:Int=4):Sequence<DocumentFile> = sequence{val pending=java.util.ArrayDeque<Pair<DocumentFile,Int>>();pending.add(root to 0);while(pending.isNotEmpty()){val(node,depth)=pending.removeFirst();for(child in node.listFiles()){if(child.isDirectory&&depth<maxDepth)pending.add(child to depth+1)else if(child.isFile)yield(child)}}}
    companion object{const val DOWNLOADS_ROOT="downloads://root";private const val MAX_SOURCE_ITEMS=5_000}
}
