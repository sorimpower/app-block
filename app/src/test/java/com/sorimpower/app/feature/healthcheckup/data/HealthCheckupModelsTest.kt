package com.sorimpower.app.feature.healthcheckup.data

import com.sorimpower.app.feature.healthcheckup.domain.HealthCategory
import com.sorimpower.app.feature.healthcheckup.domain.HealthMetricStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthCheckupModelsTest {
    @Test
    fun `stored checkup and metrics are restored as editable draft`() {
        val stored = HealthCheckupWithMetrics(
            checkup = HealthCheckupEntity(
                id = "checkup-1",
                checkupDateEpochDay = 20_000,
                hospitalName = "검진센터",
                title = "2024 건강검진",
                originalFilePath = "/tmp/checkup.pdf",
                originalFileName = "checkup.pdf",
                originalMimeType = "application/pdf",
                memo = "추적 관찰",
                aiSummary = "요약",
                createdAt = 1,
                updatedAt = 2,
                dataVersion = 3,
            ),
            metrics = listOf(
                HealthMetricEntity(
                    id = "metric-1",
                    checkupId = "checkup-1",
                    category = HealthCategory.LIVER.name,
                    name = "ALT",
                    normalizedName = "ALT",
                    value = 42.0,
                    stringValue = "",
                    unit = "U/L",
                    referenceMin = 0.0,
                    referenceMax = 40.0,
                    referenceText = "0~40",
                    status = HealthMetricStatus.HIGH.name,
                    sourceText = "ALT 42 U/L",
                    sortOrder = 0,
                ),
            ),
        )

        val draft = stored.toDraft()

        assertEquals("checkup-1", draft.checkupId)
        assertEquals("검진센터", draft.hospitalName)
        assertEquals(1, draft.metrics.size)
        assertEquals(HealthCategory.LIVER, draft.metrics.single().category)
        assertEquals(42.0, draft.metrics.single().value!!, 0.001)
        assertEquals(HealthMetricStatus.HIGH, draft.metrics.single().status)
    }
}
