package com.sorimpower.app.feature.phoneinsight.data

import android.content.Context
import com.sorimpower.app.feature.phoneinsight.domain.*
import com.sorimpower.app.feature.phoneinsight.reminder.InsightReminderScheduler
import java.util.concurrent.TimeUnit

internal data class InsightSourceUsage(val aiCalls:Int=0,val model:String?=null,val inputTokens:Int=0,val outputTokens:Int=0,val estimatedCostMicros:Long?=null)
internal data class InsightProcessSummary(val insights:Int=0,val aiCalls:Int=0,val model:String?=null,val inputTokens:Int=0,val outputTokens:Int=0,val estimatedCostMicros:Long?=null,val sourceUsage:Map<InsightSourceType,InsightSourceUsage> = emptyMap())
internal class InsightQueueException(val sources:Set<InsightSourceType>,val partial:InsightProcessSummary,cause:Throwable):Exception(cause.message,cause)

internal class InsightQueueProcessor(context:Context,private val dao:PhoneInsightDao){
    private val analyzer=OpenAiPhoneInsightAnalyzer(context)
    private val merger=InsightResultMerger(dao)
    private val appContext=context.applicationContext

    suspend fun process():InsightProcessSummary{
        dao.releaseStaleClaims(System.currentTimeMillis()-TimeUnit.MINUTES.toMillis(30));var summary=InsightProcessSummary()
        while(true){
            val batch=dao.claimCandidates();if(batch.isEmpty())break
            val raw=batch.map{RawInsightItem(it.sourceType,it.sourceId,it.senderOrApp,it.text,it.occurredAt,it.attachmentUri)}
            try{
                val outcome=analyzer.analyze(raw);val merged=merger.merge(outcome.insights)
                if(merged.isNotEmpty())InsightReminderScheduler.schedule(appContext,merged)
                val inputAllocation=InsightUsageAllocator.allocate(outcome.inputTokens,batch.groupingBy{it.sourceType}.fold(0){total,item->total+item.text.length.coerceAtLeast(1)})
                val outputWeights=outcome.insights.groupingBy{it.sourceType}.eachCount().ifEmpty{batch.groupingBy{it.sourceType}.eachCount()}
                val outputAllocation=InsightUsageAllocator.allocate(outcome.outputTokens,outputWeights)
                val sourceUsage=summary.sourceUsage.toMutableMap();batch.map{it.sourceType}.distinct().forEach{type->val previous=sourceUsage[type]?:InsightSourceUsage();sourceUsage[type]=previous.copy(aiCalls=previous.aiCalls+1,model=outcome.model?:previous.model,inputTokens=previous.inputTokens+(inputAllocation[type]?:0),outputTokens=previous.outputTokens+(outputAllocation[type]?:0))}
                summary=summary.copy(insights=summary.insights+merged.size,aiCalls=summary.aiCalls+1,model=outcome.model?:summary.model,inputTokens=summary.inputTokens+outcome.inputTokens,outputTokens=summary.outputTokens+outcome.outputTokens,sourceUsage=sourceUsage)
                val now=System.currentTimeMillis();dao.markProcessed(batch.map{ProcessedInsightSourceEntity(it.sourceType,it.sourceId,it.text.hashCode(),now)});dao.deleteCandidates(batch.map{it.id})
            }catch(t:Throwable){dao.failCandidates(batch.map{it.id},t.message?.take(500)?:"AI 분석 실패",System.currentTimeMillis());throw InsightQueueException(batch.mapTo(linkedSetOf()){it.sourceType},summary,t)}
        }
        return summary
    }
}

internal object InsightUsageAllocator {
    fun <T> allocate(total:Int,weights:Map<T,Int>):Map<T,Int>{
        if(total<=0||weights.isEmpty())return weights.keys.associateWith{0}
        val positive=weights.mapValues{it.value.coerceAtLeast(1)};val weightTotal=positive.values.sum();var remaining=total
        return positive.entries.mapIndexed{index,(key,weight)->val value=if(index==positive.size-1)remaining else (total.toLong()*weight/weightTotal).toInt().coerceAtMost(remaining);remaining-=value;key to value}.toMap()
    }
}
