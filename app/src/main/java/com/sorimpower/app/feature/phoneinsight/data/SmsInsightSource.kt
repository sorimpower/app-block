package com.sorimpower.app.feature.phoneinsight.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.net.Uri
import androidx.core.content.ContextCompat
import com.sorimpower.app.feature.phoneinsight.domain.*

internal class SmsInsightSource(private val context: Context) {
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    fun load(since: Long?): List<RawInsightItem> {
        check(hasPermission()) { "문자 접근 권한이 필요합니다." }
        return (loadSms(since)+loadMms(since)).sortedByDescending{it.occurredAt}
    }

    private fun loadSms(since:Long?):List<RawInsightItem>{
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val selection = since?.let { "${Telephony.Sms.DATE} >= ?" }
        val args = since?.let { arrayOf(it.toString()) }
        return buildList {
            context.contentResolver.query(Telephony.Sms.CONTENT_URI, projection, selection, args, "${Telephony.Sms.DATE} DESC")?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(Telephony.Sms._ID); val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS); val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY); val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext()) add(RawInsightItem(InsightSourceType.SMS, cursor.getLong(id).toString(), cursor.getString(address).orEmpty(), cursor.getString(body).orEmpty(), cursor.getLong(date)))
            }
        }
    }

    /** MMS/LMS is stored separately from the SMS table. Read its text and first image as one item. */
    private fun loadMms(since:Long?):List<RawInsightItem>{
        val messages=linkedMapOf<Long,MmsMessage>()
        val selection=since?.let{"date >= ?"};val args=since?.let{arrayOf((it/1_000L).toString())}
        context.contentResolver.query(Telephony.Mms.CONTENT_URI,arrayOf("_id","date","sub"),selection,args,"date DESC")?.use{cursor->
            val idIndex=cursor.getColumnIndexOrThrow("_id");val dateIndex=cursor.getColumnIndexOrThrow("date");val subjectIndex=cursor.getColumnIndex("sub")
            while(cursor.moveToNext()){val id=cursor.getLong(idIndex);messages[id]=MmsMessage(cursor.getLong(dateIndex)*1_000L,if(subjectIndex>=0)cursor.getString(subjectIndex).orEmpty() else "")}
        }
        messages.keys.chunked(200).forEach{ids->
            if(ids.isEmpty())return@forEach
            val where="mid IN (${ids.joinToString(",")}) AND (ct='text/plain' OR ct LIKE 'image/%')"
            context.contentResolver.query(Uri.parse("content://mms/part"),arrayOf("_id","mid","ct","text","_data"),where,null,null)?.use{cursor->
                val idIndex=cursor.getColumnIndexOrThrow("_id");val midIndex=cursor.getColumnIndexOrThrow("mid");val typeIndex=cursor.getColumnIndexOrThrow("ct");val textIndex=cursor.getColumnIndex("text");val dataIndex=cursor.getColumnIndex("_data")
                while(cursor.moveToNext()){
                    val partId=cursor.getLong(idIndex);val message=messages[cursor.getLong(midIndex)]?:continue;val type=cursor.getString(typeIndex).orEmpty();val partUri=Uri.parse("content://mms/part/$partId")
                    if(type=="text/plain"){
                        val inline=if(textIndex>=0)cursor.getString(textIndex).orEmpty() else "";val stored=if(inline.isBlank()&&dataIndex>=0&&!cursor.getString(dataIndex).isNullOrBlank())runCatching{context.contentResolver.openInputStream(partUri)?.bufferedReader()?.use{it.readText()}.orEmpty()}.getOrDefault("") else ""
                        (inline.ifBlank{stored}).takeIf(String::isNotBlank)?.let{message.texts+=it}
                    }else if(type.startsWith("image/")&&message.imageUri==null)message.imageUri=partUri.toString()
                }
            }
        }
        return messages.mapNotNull{(id,value)->val text=(listOf(value.subject)+value.texts).filter(String::isNotBlank).joinToString("\n");if(text.isBlank()&&value.imageUri==null)null else RawInsightItem(InsightSourceType.SMS,"mms:$id","MMS",text.ifBlank{"MMS 이미지 첨부"},value.date,value.imageUri)}
    }

    private data class MmsMessage(val date:Long,val subject:String,val texts:MutableList<String> = mutableListOf(),var imageUri:String?=null)
}

internal object InsightLocalPreprocessor {
    private val keywords = listOf("만료", "유효기간", "소멸", "예약", "방문", "검진", "만기", "갱신", "납부", "결제예정", "결제 예정", "쿠폰", "상품권", "포인트", "택배", "배송", "수령", "계약", "보험", "대출", "세금", "환불", "반품", "기한", "마감", "출발", "도착", "부재중", "미납", "자동이체")
    private val promotionalUseful=listOf("쿠폰","상품권","포인트","만료","소멸","유효기간")
    private val otp = Regex("(인증번호|verification|otp).{0,20}[0-9]{4,8}", RegexOption.IGNORE_CASE)
    fun isCandidate(textValue:String):Boolean{val text=textValue.lowercase();if(otp.containsMatchIn(text))return false;if(keywords.none(text::contains))return false;if((text.contains("(광고)")||text.contains("광고입니다"))&&promotionalUseful.none(text::contains))return false;return true}
    fun candidates(items: List<RawInsightItem>) = items.filter { it.attachmentUri!=null||isCandidate(it.text) }.distinctBy { it.sourceId }
}
