package com.sorimpower.app.feature.phoneinsight.data

import com.sorimpower.app.feature.phoneinsight.domain.*
import org.junit.Assert.*
import org.junit.Test

class InsightSourceSettingsPolicyTest {
    @Test fun `앱 알림 선택이 비어 있으면 전체 앱을 기본 허용한다`() {
        assertTrue(InsightAppSelectionPolicy.includes(emptySet(),"com.example.any"))
        assertTrue(InsightAppSelectionPolicy.includes(setOf("com.example.allowed"),"com.example.allowed"))
        assertFalse(InsightAppSelectionPolicy.includes(setOf("com.example.allowed"),"com.example.other"))
    }
}
