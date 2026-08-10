package com.sorimpower.app.feature.auction.data

import android.content.Context
import com.sorimpower.app.core.ai.AiModelId
import com.sorimpower.app.core.ai.AiModelRouter
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import com.sorimpower.app.feature.auction.domain.AuctionAiAnalysis
import com.sorimpower.app.feature.auction.domain.AuctionAiCriteria
import com.sorimpower.app.feature.auction.domain.AuctionAnalysisStatus
import com.sorimpower.app.feature.auction.domain.AuctionDocumentType
import com.sorimpower.app.feature.auction.domain.AuctionEvidenceBundle
import com.sorimpower.app.feature.auction.domain.AuctionItem
import com.sorimpower.app.feature.auction.domain.AuctionRiskLevel
import com.sorimpower.app.feature.auction.domain.validateAuctionAiAnalysis
import org.json.JSONObject

internal interface AuctionRightsAnalyzer {
    suspend fun analyze(
        item: AuctionItem,
        evidence: AuctionEvidenceBundle,
        criteria: AuctionAiCriteria,
        mode: AuctionAiAnalysisMode,
    ): AuctionAiAnalysis
}

enum class AuctionAiAnalysisMode(
    val modelName: String,
    val promptVersion: String,
    val aiModel: AiModelId,
) {
    MANUAL(
        modelName = "gpt-5.6-luna",
        promptVersion = "auction-rights-v3-luna",
        aiModel = AiModelId.OPENAI_FAST,
    ),
    DAILY_RECOMMENDATION(
        modelName = "gpt-5.6-luna",
        promptVersion = "auction-rights-v3-luna-daily",
        aiModel = AiModelId.OPENAI_FAST,
    ),
    MANUAL_TERRA(
        modelName = "gpt-5.6-terra",
        promptVersion = "auction-rights-v3-terra",
        aiModel = AiModelId.OPENAI_SMART,
    ),
}

internal class OpenAiAuctionRightsAnalyzer(
    private val context: Context,
) : AuctionRightsAnalyzer {
    override suspend fun analyze(
        item: AuctionItem,
        evidence: AuctionEvidenceBundle,
        criteria: AuctionAiCriteria,
        mode: AuctionAiAnalysisMode,
    ): AuctionAiAnalysis {
        if (!evidence.hasCourtEvidence) {
            return validateAuctionAiAnalysis(
                AuctionAiAnalysis(
                    itemKey = item.itemKey,
                    status = AuctionAnalysisStatus.WAITING_FOR_DOCUMENTS,
                    headline = "법원 문서가 더 필요해요",
                    requiredChecks = evidence.missingCourtDocuments.map { "${it.label} 확보" },
                ),
                evidence,
            )
        }
        val response = AiModelRouter(context).generate(
            AiRequest(AiTaskType.AUCTION_RIGHTS_ANALYSIS, createPrompt(item, evidence, criteria)),
            mode.aiModel,
        ).text
        return validateAuctionAiAnalysis(parseAnalysis(item.itemKey, response, mode), evidence)
    }

    private fun createPrompt(
        item: AuctionItem,
        evidence: AuctionEvidenceBundle,
        criteria: AuctionAiCriteria,
    ): String = buildString {
        appendLine("당신은 대한민국 법원 부동산 경매 문서에서 사실을 추출해 예비 권리분석을 돕는 분석기다.")
        appendLine("반드시 유효한 JSON 객체만 반환한다. 키는 riskLevel, suitabilityScore, headline, summary, riskItems, requiredChecks를 사용한다.")
        appendLine("문서에 없는 사실을 추정하지 말고, 불확실한 항목은 반드시 requiredChecks에 넣어라.")
        appendLine("등기사항전부증명서가 없으면 확정적 권리 순위나 인수 여부를 단정하지 마라.")
        appendLine("'PDF 원문은 아님'이라고 표시된 근거는 요약·메타데이터일 뿐이므로 원문에 있을 법한 내용을 추측하지 마라.")
        appendLine("riskItems에는 근거 문서에서 확인되는 위험만, requiredChecks에는 추가 확인 사항만 기록하라.")
        appendLine("suitabilityScore는 권리위험, 가격조건, 지역조건, 점유위험을 함께 반영한 0~100 적합도다.")
        appendLine("사건: ${item.caseNumber}, 물건: ${item.auctionItemNumber}, 주소: ${item.address}")
        appendLine("감정가: ${item.appraisalPrice}, 최저가: ${item.minimumPrice}, 유찰: ${item.failedCount}회")
        appendLine("사용자 조건: 최저매각가격 하한=${criteria.minimumBidPrice ?: "미설정"}, 상한=${criteria.maxBidPrice ?: "미설정"}, 최소적합도=${criteria.minimumSuitabilityScore}점, 선호지역=${criteria.preferredDistricts.joinToString()}, 최소할인율=${criteria.minimumDiscountRate ?: "미설정"}, 최대허용위험=${criteria.maximumRiskLevel}, 점유허용=${criteria.allowOccupiedProperty}, 추가요청=${criteria.extraRequest}")
        evidence.documents.forEach { document ->
            appendLine("\n[${document.type.label}] sourceId=${document.sourceId}")
            appendLine(document.text.take(MAX_DOCUMENT_TEXT_LENGTH))
        }
    }

    private fun parseAnalysis(
        itemKey: String,
        response: String,
        mode: AuctionAiAnalysisMode,
    ): AuctionAiAnalysis {
        val json = JSONObject(response)
        return AuctionAiAnalysis(
            itemKey = itemKey,
            status = AuctionAnalysisStatus.PRELIMINARY,
            riskLevel = runCatching { AuctionRiskLevel.valueOf(json.getString("riskLevel")) }
                .getOrDefault(AuctionRiskLevel.UNKNOWN),
            suitabilityScore = json.optInt("suitabilityScore", 0),
            headline = json.optString("headline"),
            summary = json.optString("summary"),
            riskItems = json.optJSONArray("riskItems").toDisplayList(),
            requiredChecks = json.optJSONArray("requiredChecks").toDisplayList(),
            analyzedAt = System.currentTimeMillis(),
            modelName = mode.modelName,
            promptVersion = mode.promptVersion,
        )
    }

    private companion object {
        const val MAX_DOCUMENT_TEXT_LENGTH = 120_000

    }
}

private fun org.json.JSONArray?.toDisplayList(): List<String> = buildList {
    val source = this@toDisplayList ?: return@buildList
    for (index in 0 until source.length()) {
        when (val value = source.opt(index)) {
            is JSONObject -> listOf("summary", "detail", "reason", "risk", "action", "category")
                .map { value.optString(it).trim() }
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(" · ")
                .takeIf(String::isNotBlank)
                ?.let(::add)
            else -> value?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(::add)
        }
    }
}
