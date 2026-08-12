package com.sorimpower.app.core.ai

import android.content.Context

internal class AiModelRouter(context: Context) {
    @Suppress("UNUSED_PARAMETER")
    private val appContext = context
    private val openAi = OpenAiProvider()

    /** AI 분석은 현재 GPT-5.6 Luna를 기본 모델로 사용한다. */
    suspend fun generate(request: AiRequest, model: AiModelId = AiModelId.OPENAI_FAST): AiResponse = when (request.taskType) {
        AiTaskType.BODY_LOG_PROGRESS_ANALYSIS,
        AiTaskType.HEALTH_CHECKUP_PAGE_SELECTION,
        AiTaskType.HEALTH_CHECKUP_EXTRACTION,
        AiTaskType.HEALTH_TREND_ANALYSIS,
        AiTaskType.HEALTH_SCREENING_OPTION_RECOMMENDATION,
        AiTaskType.AUCTION_RIGHTS_ANALYSIS,
        AiTaskType.PHONE_INSIGHT_BATCH,
        AiTaskType.PROPERTY_TAX_DEEP_ANALYSIS,
        AiTaskType.PROPERTY_TAX_RULE_CHANGE_ANALYSIS,
        AiTaskType.PROPERTY_TAX_SCENARIO_COMPARISON,
        -> openAi.generate(model, request)
    }
}
