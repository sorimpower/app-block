package com.sorimpower.app.feature.phoneinsight.data

import com.sorimpower.app.feature.phoneinsight.domain.*
import java.time.*
import java.time.temporal.ChronoUnit

/** Keeps the in-app list focused on the next two weeks, regardless of AI-classified type. */
internal object PhoneInsightVisibility {
    private const val LIST_WINDOW_DAYS=14L
    fun visible(value:PhoneInsightEntity,now:Long=System.currentTimeMillis()):Boolean{
        val due=value.dueAt;if(due==null){val age=ChronoUnit.DAYS.between(Instant.ofEpochMilli(value.createdAt).atZone(ZoneId.systemDefault()).toLocalDate(),Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate());return value.importance==InsightImportance.HIGH&&age<=LIST_WINDOW_DAYS};val zone=ZoneId.systemDefault();val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();val dueDate=Instant.ofEpochMilli(due).atZone(zone).toLocalDate();val days=ChronoUnit.DAYS.between(today,dueDate)
        if(days<0)return false
        return days<=LIST_WINDOW_DAYS
    }
    /** Morning/evening digest intentionally excludes later items; they remain in the in-app two-week list. */
    fun digestVisible(value:PhoneInsightEntity,now:Long=System.currentTimeMillis()):Boolean=visible(value,now)&&(daysUntil(value,now) in 0L..1L)
    fun dayLabel(value:PhoneInsightEntity,now:Long=System.currentTimeMillis()):String?{
        val days=daysUntil(value,now)?:return null;return when{days==0L->"오늘";days>0->"D-$days";else->null}
    }
    fun daysUntil(value:PhoneInsightEntity,now:Long=System.currentTimeMillis()):Long?{val due=value.dueAt?:return null;val zone=ZoneId.systemDefault();return ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),Instant.ofEpochMilli(due).atZone(zone).toLocalDate())}
    /** Today first, then imminent items, then AI-classified high importance, followed by date. */
    fun priorityRank(value:PhoneInsightEntity,now:Long=System.currentTimeMillis()):Int{val days=daysUntil(value,now);return when{days==0L->0;days!=null&&days<=2->1;value.importance==InsightImportance.HIGH->2;else->3}}
    fun comparator(now:Long=System.currentTimeMillis())=compareBy<PhoneInsightEntity>{priorityRank(it,now)}.thenBy{it.dueAt?:Long.MAX_VALUE}.thenByDescending{it.importance==InsightImportance.HIGH}
}
