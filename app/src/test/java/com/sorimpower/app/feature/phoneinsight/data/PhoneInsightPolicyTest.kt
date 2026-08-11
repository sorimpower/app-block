package com.sorimpower.app.feature.phoneinsight.data

import com.sorimpower.app.feature.phoneinsight.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PhoneInsightPolicyTest {
    @Test fun localFilter_removesOtpAndGenericAdsButKeepsExpiringBenefits(){
        assertFalse(InsightLocalPreprocessor.isCandidate("인증번호 123456은 3분간 유효합니다"))
        assertFalse(InsightLocalPreprocessor.isCandidate("(광고) 오늘 방문하면 특별 할인"))
        assertTrue(InsightLocalPreprocessor.isCandidate("(광고) 보유 쿠폰이 8월 15일 만료됩니다"))
        assertTrue(InsightLocalPreprocessor.isCandidate("보험료 납부 기한이 이틀 남았습니다"))
    }

    @Test fun localFilter_keepsMmsImageEvenWhenItsTextHasNoKeyword(){
        val item=RawInsightItem(InsightSourceType.SMS,"mms:1","MMS","모바일 교환권 이미지",0,"content://mms/part/1")
        assertEquals(listOf(item),InsightLocalPreprocessor.candidates(listOf(item)))
    }

    @Test fun visibility_keepsRecentHighImportanceWithoutDueDate(){
        val now=Instant.parse("2026-08-11T00:00:00Z").toEpochMilli()
        val value=entity(createdAt=now-TimeZoneDays(2),importance=InsightImportance.HIGH,dueAt=null)
        assertTrue(PhoneInsightVisibility.visible(value,now))
        assertEquals(2,PhoneInsightVisibility.priorityRank(value,now))
    }

    @Test fun comparator_ordersTodayThenImminentThenImportant(){
        val zone=ZoneId.systemDefault();val date=LocalDate.of(2026,8,11);val now=date.atTime(10,0).atZone(zone).toInstant().toEpochMilli()
        fun due(days:Long)=date.plusDays(days).atTime(8,30).atZone(zone).toInstant().toEpochMilli()
        val important=entity(createdAt=now,importance=InsightImportance.HIGH,dueAt=due(7));val imminent=entity(createdAt=now,dueAt=due(2));val today=entity(createdAt=now,dueAt=due(0))
        assertEquals(listOf(today,imminent,important),listOf(important,today,imminent).sortedWith(PhoneInsightVisibility.comparator(now)))
    }

    @Test fun everyDueTypeIncludingCoupon_isVisibleOnlyWithinTwoWeeks(){
        val zone=ZoneId.systemDefault();val today=LocalDate.of(2026,8,11);val now=today.atStartOfDay(zone).toInstant().toEpochMilli();val due=today.plusDays(14).atStartOfDay(zone).toInstant().toEpochMilli()
        assertTrue(PhoneInsightVisibility.visible(entity(createdAt=now,dueAt=due,type=InsightType.COUPON),now))
        assertTrue(PhoneInsightVisibility.visible(entity(createdAt=now,dueAt=due,type=InsightType.RENEWAL),now))
        val outsideWindow=today.plusDays(15).atStartOfDay(zone).toInstant().toEpochMilli()
        assertFalse(PhoneInsightVisibility.visible(entity(createdAt=now,dueAt=outsideWindow,type=InsightType.COUPON),now))
    }

    @Test fun digest_includesOnlyTodayAndTomorrow(){
        val zone=ZoneId.systemDefault();val today=LocalDate.of(2026,8,11);val now=today.atStartOfDay(zone).toInstant().toEpochMilli()
        fun due(days:Long)=today.plusDays(days).atTime(9,0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(PhoneInsightVisibility.digestVisible(entity(now,dueAt=due(0)),now))
        assertTrue(PhoneInsightVisibility.digestVisible(entity(now,dueAt=due(1)),now))
        assertFalse(PhoneInsightVisibility.digestVisible(entity(now,dueAt=due(2)),now))
    }

    @Test fun appointment_isVisibleWithinTwoWeekWindow(){
        val zone=ZoneId.systemDefault();val today=LocalDate.of(2026,8,11);val now=today.atStartOfDay(zone).toInstant().toEpochMilli();val due=today.plusDays(14).atTime(14,30).atZone(zone).toInstant().toEpochMilli()
        assertTrue(PhoneInsightVisibility.visible(entity(createdAt=now,dueAt=due,type=InsightType.APPOINTMENT),now))
    }

    private fun entity(createdAt:Long,importance:InsightImportance=InsightImportance.MEDIUM,dueAt:Long?=null,type:InsightType=InsightType.TODO)=PhoneInsightEntity("id-$createdAt-$dueAt-$importance","group-$createdAt-$dueAt-$importance",type,"테스트","",dueAt,null,importance,InsightStatus.ACTIVE,InsightSourceType.SMS,"source","sender",.9,createdAt,createdAt,null)
    private fun TimeZoneDays(days:Long)=Duration.ofDays(days).toMillis()
}
