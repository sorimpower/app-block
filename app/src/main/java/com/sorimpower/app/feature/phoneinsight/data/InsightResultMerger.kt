package com.sorimpower.app.feature.phoneinsight.data

import com.sorimpower.app.feature.phoneinsight.domain.*
import java.util.concurrent.TimeUnit

internal class InsightResultMerger(private val dao:PhoneInsightDao){
    suspend fun merge(values:List<PhoneInsightEntity>,now:Long=System.currentTimeMillis()):List<PhoneInsightEntity>{
        val existing=dao.recentInsights(now-TimeUnit.DAYS.toMillis(90)).toMutableList()
        val merged=values.map{incoming->
            val match=existing.map{it to InsightMergePolicy.similarity(it,incoming)}.filter{it.second>=InsightMergePolicy.MATCH_THRESHOLD}.maxByOrNull{it.second}?.first
            val result=if(match==null)incoming else InsightMergePolicy.merge(match,incoming)
            existing.removeAll{it.id==result.id};existing+=result;result
        }
        val unique=merged.associateBy{it.id}.values.toList()
        if(unique.isNotEmpty())dao.upsertInsights(unique)
        return unique
    }

}

internal object InsightMergePolicy{
    const val MATCH_THRESHOLD=.66
    fun similarity(left:PhoneInsightEntity,right:PhoneInsightEntity):Double{
        if(left.groupKey==right.groupKey)return 1.0
        if(left.type!=right.type&&!compatible(left.type,right.type))return 0.0
        if(left.amount!=null&&right.amount!=null&&left.amount!=right.amount)return 0.0
        val dueScore=when{left.dueAt==null&&right.dueAt==null->.10;left.dueAt==null||right.dueAt==null->0.0;kotlin.math.abs(left.dueAt-right.dueAt)<=TimeUnit.DAYS.toMillis(1)->.20;else->0.0}
        val titleScore=jaccard(bigrams(normalize(left.title)),bigrams(normalize(right.title)))*.70
        val amountScore=if(left.amount!=null&&left.amount==right.amount).10 else 0.0
        return titleScore+dueScore+amountScore
    }
    fun merge(old:PhoneInsightEntity,new:PhoneInsightEntity):PhoneInsightEntity{
        val terminalStatus=listOf(old.status,new.status).firstOrNull{it in setOf(InsightStatus.COMPLETED,InsightStatus.DISMISSED)}
        val importance=if(old.importance.ordinal>=new.importance.ordinal)old.importance else new.importance
        return new.copy(id=old.id,groupKey=old.groupKey,title=if(new.title.length>=old.title.length)new.title else old.title,description=if(new.description.length>=old.description.length)new.description else old.description,dueAt=new.dueAt?:old.dueAt,amount=new.amount?:old.amount,importance=importance,status=terminalStatus?:new.status,confidence=maxOf(old.confidence,new.confidence),createdAt=old.createdAt,lastNotifiedAt=old.lastNotifiedAt)
    }
    private fun compatible(a:InsightType,b:InsightType)=setOf(a,b).all{it in setOf(InsightType.TODO,InsightType.DEADLINE,InsightType.APPOINTMENT,InsightType.RENEWAL)}
    private fun normalize(value:String)=value.lowercase().replace(Regex("(모바일|상품권|교환권|사용|안내)"),"").replace(Regex("[^가-힣a-z0-9]"),"")
    private fun bigrams(value:String):Set<String>{if(value.length<2)return setOf(value);return (0 until value.length-1).map{value.substring(it,it+2)}.toSet()}
    private fun jaccard(a:Set<String>,b:Set<String>):Double{if(a.isEmpty()||b.isEmpty())return 0.0;return a.intersect(b).size.toDouble()/a.union(b).size}
}
