package com.sorimpower.app.core.ai

enum class AiProviderType { OPENAI }

internal enum class AiTaskType { BODY_LOG_PROGRESS_ANALYSIS, HEALTH_CHECKUP_PAGE_SELECTION, HEALTH_CHECKUP_EXTRACTION, HEALTH_TREND_ANALYSIS, HEALTH_SCREENING_OPTION_RECOMMENDATION, AUCTION_RIGHTS_ANALYSIS }

enum class AiModelId(
    val provider: AiProviderType,
    val apiModelName: String,
) {
    OPENAI_FAST(AiProviderType.OPENAI, "gpt-5.6-luna"),
    OPENAI_SMART(AiProviderType.OPENAI, "gpt-5.6-terra"),
}

internal data class AiRequest(
    val taskType: AiTaskType,
    val userPrompt: String,
    val jsonOutput: Boolean = true,
    val images: List<AiImageAttachment> = emptyList(),
)

internal data class AiImageAttachment(val mimeType: String = "image/jpeg", val bytes: ByteArray)

internal data class AiResponse(
    val text: String,
    val provider: AiProviderType,
    val model: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
)

internal interface AiProvider {
    suspend fun generate(model: AiModelId, request: AiRequest): AiResponse
}
