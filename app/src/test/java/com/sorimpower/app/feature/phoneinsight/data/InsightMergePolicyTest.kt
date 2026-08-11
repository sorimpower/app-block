package com.sorimpower.app.feature.phoneinsight.data

import com.sorimpower.app.feature.phoneinsight.domain.*
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class InsightMergePolicyTest {
    @Test fun `서로 다른 AI 배치의 같은 일정은 유사 항목으로 병합한다`() {
        val old = entity(id="old", groupKey="batch-1", title="강남병원 건강검진 예약", dueAt=day(3))
        val fresh = entity(id="fresh", groupKey="batch-2", title="강남병원 건강검진 예약 안내", dueAt=day(3)+TimeUnit.HOURS.toMillis(2))

        assertTrue(InsightMergePolicy.similarity(old, fresh) >= InsightMergePolicy.MATCH_THRESHOLD)
        val merged = InsightMergePolicy.merge(old, fresh)
        assertEquals("old", merged.id)
        assertEquals("batch-1", merged.groupKey)
        assertEquals(old.createdAt, merged.createdAt)
    }

    @Test fun `금액이 서로 다른 항목은 제목이 같아도 병합하지 않는다`() {
        val left = entity(groupKey="a", amount=10_000L)
        val right = entity(groupKey="b", amount=20_000L)
        assertEquals(0.0, InsightMergePolicy.similarity(left, right), 0.0)
    }

    @Test fun `완료하거나 필요없음 처리한 상태는 새 분석이 되돌리지 않는다`() {
        val old = entity(status=InsightStatus.DISMISSED, importance=InsightImportance.LOW)
        val fresh = entity(id="new", groupKey="new", status=InsightStatus.ACTIVE, importance=InsightImportance.HIGH)
        val merged = InsightMergePolicy.merge(old, fresh)
        assertEquals(InsightStatus.DISMISSED, merged.status)
        assertEquals(InsightImportance.HIGH, merged.importance)
    }

    @Test fun `표현이 다른 같은 이마트 상품권은 하나로 판단한다`() {
        val left=entity(groupKey="download-1",title="이마트·신세계 모바일 교환권 10,000원 사용",dueAt=day(15),amount=10_000L)
        val right=entity(id="mms",groupKey="mms-1",title="이마트 신세계상품권 모바일교환권 사용",dueAt=day(15),amount=10_000L)
        assertTrue(InsightMergePolicy.similarity(left,right)>=InsightMergePolicy.MATCH_THRESHOLD)
    }

    private fun day(value:Long)=1_800_000_000_000L+TimeUnit.DAYS.toMillis(value)
    private fun entity(
        id:String="old",
        groupKey:String="group",
        title:String="자동차 보험료 납부 안내",
        dueAt:Long?=day(2),
        amount:Long?=null,
        importance:InsightImportance=InsightImportance.MEDIUM,
        status:InsightStatus=InsightStatus.ACTIVE,
    )=PhoneInsightEntity(id,groupKey,InsightType.DEADLINE,title,"기한을 확인하세요",dueAt,amount,importance,status,InsightSourceType.SMS,"source","sender",.9,1_700_000_000_000L,1_700_000_000_000L,null)
}
