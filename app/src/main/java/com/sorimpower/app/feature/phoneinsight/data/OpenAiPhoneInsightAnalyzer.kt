package com.sorimpower.app.feature.phoneinsight.data

import android.content.Context
import com.sorimpower.app.core.ai.*
import com.sorimpower.app.feature.phoneinsight.domain.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import java.io.File
import java.io.FileInputStream

internal class OpenAiPhoneInsightAnalyzer(private val context: Context) {
    suspend fun analyze(items: List<RawInsightItem>):PhoneInsightAnalysisOutcome {
        if (items.isEmpty()) return PhoneInsightAnalysisOutcome(emptyList(),null,0,0)
        val images=items.filter { it.sourceType!=InsightSourceType.CALL_RECORDING&&it.attachmentUri!=null }.mapNotNull { item -> item.attachmentUri?.let { uri -> loadImage(uri)?.let { AiImageAttachment(sourceId=item.sourceId,bytes=it) } } }.take(6)
        val audios=items.filter { it.sourceType==InsightSourceType.CALL_RECORDING }.mapNotNull { item -> item.attachmentUri?.let { uri -> loadAudio(item.sourceId,item.text,uri) } }.take(2)
        val response = AiModelRouter(context).generate(AiRequest(AiTaskType.PHONE_INSIGHT_BATCH, prompt(items), images=images, audios=audios))
        val json=response.text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim();val root = JSONObject(json); val array = root.optJSONArray("insights") ?: return PhoneInsightAnalysisOutcome(emptyList(),response.model,response.inputTokens?:0,response.outputTokens?:0); val now = System.currentTimeMillis();val todayStart=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val insights=buildList {
            for (i in 0 until array.length()) {
                val value = array.optJSONObject(i) ?: continue
                val sourceId = value.optString("sourceId"); val raw = items.firstOrNull { it.sourceId == sourceId } ?: continue
                if (!value.optBoolean("isActionable", false)) continue
                val confidence = value.optDouble("confidence", 0.0).coerceIn(0.0, 1.0); val due = parseDue(value.optString("dueAt"))
                val type=InsightType.entries.firstOrNull{it.name==value.optString("type") }?:InsightType.INFORMATION;val title=value.optString("title").ifBlank{"확인이 필요한 항목"};val amount=value.optLong("amount").takeIf{it>0};val groupKey=canonicalGroupKey(type,title,due,amount)
                val importance=InsightImportance.entries.firstOrNull{it.name==value.optString("importance") }?:InsightImportance.MEDIUM
                add(PhoneInsightEntity(UUID.nameUUIDFromBytes(groupKey.toByteArray()).toString(),groupKey,type,title,value.optString("description"),due,amount,importance,if(confidence<.65)InsightStatus.REVIEW else if(due!=null&&due<todayStart)InsightStatus.EXPIRED else InsightStatus.ACTIVE,raw.sourceType,sourceId,raw.sender,confidence,now,now,null))
            }
        }
        return PhoneInsightAnalysisOutcome(insights,response.model,response.inputTokens?:0,response.outputTokens?:0)
    }
    private fun prompt(items: List<RawInsightItem>) = buildString {
        appendLine("오늘은 ${LocalDate.now()}이다. 다음 휴대폰 정보 후보(문자, 앱 알림, 사진과 촬영 메타데이터, 파일·문서, 통화 녹음 전사, 캘린더, 통화 기록 메타데이터, 앱 사용 기록)를 합쳐 사용자가 실제로 챙겨야 할 항목만 추출하라. 같은 사건은 groupKey 하나로 묶고, 광고·단순 승인·이미 완료된 배송은 제외하라. 단, 14일 이내의 캘린더 예약·약속·개인 일정은 APPOINTMENT로 반드시 추출해 일정 리마인더로 보여라. 직접 행동을 대신하지 않는다.")
        appendLine("오늘 당장은 아니어도 상품권·쿠폰·포인트의 사용 또는 교환 유효기간, 결제·납부 마감, 계약·보험 갱신처럼 미리 알아야 손해를 막을 수 있는 정보는 반드시 actionable로 추출하고 정확한 type과 dueAt을 지정하라. 특히 모바일 상품권은 교환처와 남은 기간을 제목·설명에 보존하라.")
        appendLine("캘린더 정보에 시작·종료가 함께 있으면 진행 중인 일정은 시작일이 지났다는 이유로 제외하지 말고 종료 시각을 판단 기준으로 사용하라. 제목에 해지·취소·신청·납부·제출·갱신처럼 사용자의 행동이 필요한 말이 있으면 DEADLINE으로 추출하고 종료 시각을 dueAt으로 지정하라.")
        appendLine("시간이 확인되면 dueAt에 ISO-8601 날짜와 시간(예: 2026-08-20T10:00:00+09:00)을 반드시 보존하라. 시간 없이 날짜만 확인되면 YYYY-MM-DD, 기한이 없으면 빈 문자열을 사용하라.")
        appendLine("JSON 객체만 반환: {\"insights\":[{\"sourceId\":\"\",\"isActionable\":true,\"type\":\"TODO|DEADLINE|APPOINTMENT|COUPON|FINANCIAL_EVENT|DELIVERY|RENEWAL|INFORMATION\",\"title\":\"\",\"description\":\"\",\"dueAt\":\"ISO-8601 날짜·시간 또는 YYYY-MM-DD 또는 빈 문자열\",\"amount\":0,\"importance\":\"LOW|MEDIUM|HIGH\",\"confidence\":0.0,\"groupKey\":\"같은 사건을 묶는 안정적 키\"}]}")
        items.forEach { item ->
            val attachment = if (item.attachmentUri != null) " (첨부 이미지가 있으면 해당 항목과 연결해 읽어라)" else ""
            appendLine("[source=${item.sourceType.name}, sourceId=${item.sourceId}, sender=${sanitize(item.sender)}, date=${java.time.Instant.ofEpochMilli(item.occurredAt)}] ${sanitize(item.text).take(500)}$attachment")
        }
    }
    private fun canonicalGroupKey(type:InsightType,title:String,due:Long?,amount:Long?):String{val normalized=title.lowercase().replace(Regex("[^가-힣a-z0-9]"),"").take(40);val date=due?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()}.orEmpty();return listOf(type.name,normalized,date,amount?:0).joinToString("|")}
    private fun sanitize(value:String)=value.replace(Regex("(?<!\\d)01\\d[- ]?\\d{3,4}[- ]?\\d{4}(?!\\d)"),"[전화번호]").replace(Regex("(?<!\\d)\\d{6}[- ]?[1-4]\\d{6}(?!\\d)"),"[주민번호]").replace(Regex("(?<!\\d)\\d{12,}(?!\\d)"),"[민감번호]")
    private fun parseDue(value:String):Long?{if(value.isBlank())return null;return runCatching{Instant.parse(value).toEpochMilli()}.getOrNull()?:runCatching{OffsetDateTime.parse(value).toInstant().toEpochMilli()}.getOrNull()?:runCatching{LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull()?:runCatching{LocalDate.parse(value).atTime(8,30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull()}
    private fun loadImage(uri:String):ByteArray?=runCatching{
        val parsed=Uri.parse(uri);if(context.contentResolver.getType(parsed)=="application/pdf"||parsed.path?.endsWith(".pdf",true)==true) return renderPdf(uri)
        (if(parsed.scheme=="file")FileInputStream(File(requireNotNull(parsed.path)))else context.contentResolver.openInputStream(parsed))?.use { input ->
            val bitmap=BitmapFactory.decodeStream(input)?:return null
            val scaled=if(bitmap.width>1400){val h=(bitmap.height*1400.0/bitmap.width).toInt();android.graphics.Bitmap.createScaledBitmap(bitmap,1400,h,true)}else bitmap
            java.io.ByteArrayOutputStream().use { out->scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG,82,out);out.toByteArray() }
        }
    }.getOrNull()
    private fun renderPdf(uri:String):ByteArray?=runCatching{
        val parsed=Uri.parse(uri);val fd:ParcelFileDescriptor=if(parsed.scheme=="file")ParcelFileDescriptor.open(File(requireNotNull(parsed.path)),ParcelFileDescriptor.MODE_READ_ONLY)else context.contentResolver.openFileDescriptor(parsed,"r")?:return null
        fd.use { handle -> PdfRenderer(handle).use { renderer ->
            if(renderer.pageCount==0)return null
            renderer.openPage(0).use { page ->
                val scale=minOf(1f,1400f/page.width);val bitmap=android.graphics.Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),android.graphics.Bitmap.Config.ARGB_8888)
                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                java.io.ByteArrayOutputStream().use{out->bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,82,out);out.toByteArray()}
            }
        }}
    }.getOrNull()
    private fun loadAudio(sourceId:String,fileName:String,uri:String):AiAudioAttachment?=runCatching{
        val parsed=Uri.parse(uri);val type=context.contentResolver.getType(parsed)?:"audio/mp4"
        context.contentResolver.openInputStream(parsed)?.use { input ->
            val bytes=input.readNBytes(8*1024*1024+1);if(bytes.size>8*1024*1024)return null
            AiAudioAttachment(sourceId,fileName,type,bytes)
        }
    }.getOrNull()
}

internal data class PhoneInsightAnalysisOutcome(val insights:List<PhoneInsightEntity>,val model:String?,val inputTokens:Int,val outputTokens:Int)
