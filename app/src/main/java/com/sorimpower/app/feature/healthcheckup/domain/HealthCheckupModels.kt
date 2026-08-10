package com.sorimpower.app.feature.healthcheckup.domain

enum class HealthCategory(val label: String) {
    BODY("신체"),
    BLOOD_PRESSURE("혈압"),
    METABOLIC("혈당·대사"),
    CARDIOVASCULAR("지질·심혈관"),
    LIVER("간"),
    KIDNEY("신장"),
    BLOOD("혈액"),
    DIGESTIVE("소화기"),
    THYROID("갑상선"),
    LUNG("폐"),
    CANCER_SCREENING("암검진"),
    URINE("소변"),
    OTHER("기타"),
}

enum class HealthMetricStatus(val label: String) {
    NORMAL("정상"), LOW("낮음"), HIGH("높음"), WARNING("확인 필요"), UNKNOWN("미확인"),
}

data class HealthMetricDraft(
    val id: String,
    val category: HealthCategory = HealthCategory.OTHER,
    val name: String = "",
    val normalizedName: String = "",
    val value: Double? = null,
    val stringValue: String = "",
    val unit: String = "",
    val referenceMin: Double? = null,
    val referenceMax: Double? = null,
    val referenceText: String = "",
    val status: HealthMetricStatus = HealthMetricStatus.UNKNOWN,
    val sourceText: String = "",
)

data class HealthCheckupDraft(
    val checkupId: String,
    val checkupDateEpochDay: Long,
    val hospitalName: String = "",
    val title: String = "",
    val memo: String = "",
    val originalFilePath: String = "",
    val originalFileName: String = "",
    val originalMimeType: String = "",
    val aiSummary: String = "",
    val metrics: List<HealthMetricDraft> = emptyList(),
)

data class ImportedHealthDocument(
    val localPath: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
)

data class HealthDocumentExtraction(
    val summary: String,
    val metrics: List<HealthMetricDraft>,
)

data class LongTermHealthAnalysis(
    val summary: String,
    val positiveChanges: List<String>,
    val attentionChanges: List<String>,
    val stableAreas: List<String>,
    val recommendations: List<String>,
    val missingInformation: List<String>,
    val medicalConsultationSuggested: Boolean,
    val analyzedAt: Long = System.currentTimeMillis(),
)

data class HealthScreeningOptionRecommendation(
    val name: String,
    val priority: String,
    val reason: String,
    val clinicalNote: String,
)

data class HealthScreeningRecommendation(
    val summary: String,
    val recommendations: List<HealthScreeningOptionRecommendation>,
    val caution: String,
)
