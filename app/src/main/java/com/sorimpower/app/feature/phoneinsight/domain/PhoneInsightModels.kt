package com.sorimpower.app.feature.phoneinsight.domain

enum class InsightSourceType(val label: String, val description: String) {
    SMS("문자", "쿠폰, 예약, 납부, 만기 등의 중요 정보를 찾습니다."),
    NOTIFICATION("앱 알림", "은행, 쇼핑, 병원, 택배 등의 알림을 분석합니다."),
    SCREENSHOT("사진·이미지", "갤러리의 영수증, 쿠폰, 티켓, 예약 이미지를 확인합니다."),
    DOCUMENT("파일·문서", "선택한 폴더의 계약, 보험, 예약 관련 문서를 확인합니다."),
    CALL_RECORDING("통화 녹음", "선택한 폴더의 새 통화 녹음을 글로 변환해 약속과 할 일을 찾습니다."),
    CALENDAR("캘린더", "예정된 일정과 다른 데이터의 약속·기한을 함께 확인합니다."),
    CONTACTS("연락처", "전화번호를 이름과 연결해 문자와 통화 분석 정확도를 높입니다."),
    CALL_LOG("통화 기록", "부재중 전화와 통화 시각·길이 같은 메타데이터를 확인합니다."),
    APP_USAGE("앱 사용 기록", "선택한 앱의 의미 있는 사용 패턴 변화를 확인합니다."),
}
enum class InsightType(val label: String) { TODO("해야 할 일"), DEADLINE("기한"), APPOINTMENT("예약"), COUPON("쿠폰"), FINANCIAL_EVENT("금융"), DELIVERY("배송"), RENEWAL("갱신"), INFORMATION("안내") }
enum class InsightImportance { LOW, MEDIUM, HIGH }
enum class InsightStatus { ACTIVE, REVIEW, COMPLETED, DISMISSED, EXPIRED }
enum class InsightCandidateState { PENDING, PROCESSING, FAILED }
enum class InsightRunStatus { RUNNING, SUCCESS, FAILED, SKIPPED }
enum class SmsScanRange(val label: String, val days: Long?) { ONE_MONTH("최근 1개월", 30), THREE_MONTHS("최근 3개월", 90), SIX_MONTHS("최근 6개월", 180), ONE_YEAR("최근 1년", 365), ALL("전체", null) }
enum class InsightAnalysisFrequency(val label: String, val days: Long?) { DAILY("매일", 1), EVERY_THREE_DAYS("3일마다", 3), MANUAL("수동만", null) }

data class InsightSourceSettings(
    val accessUris: Set<String> = emptySet(),
    val selectedPackages: Set<String> = emptySet(),
)

object InsightAppSelectionPolicy {
    /** An empty selection intentionally means every app; turning the source off means no apps. */
    fun includes(selectedPackages:Set<String>,packageName:String)=selectedPackages.isEmpty()||packageName in selectedPackages
}

data class RawInsightItem(val sourceType: InsightSourceType = InsightSourceType.SMS, val sourceId: String, val sender: String, val text: String, val occurredAt: Long, val attachmentUri: String? = null)
data class SmsScanEstimate(val range: SmsScanRange, val totalMessages: Int, val candidateMessages: Int, val estimatedAiCalls: Int)
data class SourceScanEstimate(val sourceType: InsightSourceType, val range: SmsScanRange, val totalItems: Int, val candidateItems: Int, val estimatedAiCalls: Int)
data class CombinedSourceEstimate(val sourceType: InsightSourceType, val totalItems: Int, val candidateItems: Int)
data class CombinedScanEstimate(val sources: List<CombinedSourceEstimate>, val totalItems: Int, val candidateItems: Int, val estimatedAiCalls: Int)
