package com.sorimpower.app.feature.phoneinsight.data

import com.sorimpower.app.feature.phoneinsight.domain.InsightSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class InsightUsageAllocatorTest {
    @Test fun `통합 배치 토큰은 소스 기여도에 따라 배분하고 합계를 보존한다`() {
        val result=InsightUsageAllocator.allocate(101,mapOf(InsightSourceType.SMS to 3,InsightSourceType.DOCUMENT to 1))
        assertEquals(101,result.values.sum())
        assertEquals(75,result[InsightSourceType.SMS])
        assertEquals(26,result[InsightSourceType.DOCUMENT])
    }
}
