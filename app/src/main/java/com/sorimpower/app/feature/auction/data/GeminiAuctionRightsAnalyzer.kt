package com.sorimpower.app.feature.auction.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
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
) {
    MANUAL(
        modelName = "gemini-3.5-flash-lite",
        promptVersion = "auction-rights-v2-flash-lite",
    ),
    DAILY_RECOMMENDATION(
        modelName = "gemini-3.6-flash",
        promptVersion = "auction-rights-v2-flash",
    ),
}

internal class GeminiAuctionRightsAnalyzer(
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
        check(FirebaseApp.getApps(context).isNotEmpty()) {
            "Firebase가 연결되지 않았습니다. google-services.json과 Firebase AI Logic 설정이 필요합니다."
        }

        val model = com.google.firebase.Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = mode.modelName,
            generationConfig = generationConfig {
                temperature = 0.1f
                responseMimeType = "application/json"
                responseSchema = RESPONSE_SCHEMA
            },
        )
        val response = model.generateContent(createPrompt(item, evidence, criteria)).text
            ?: error("Gemini가 분석 결과를 반환하지 않았습니다.")
        return validateAuctionAiAnalysis(parseAnalysis(item.itemKey, response, mode), evidence)
    }

    private fun createPrompt(
        item: AuctionItem,
        evidence: AuctionEvidenceBundle,
        criteria: AuctionAiCriteria,
    ): String = buildString {
        appendLine("당신은 대한민국 법원 부동산 경매 문서에서 사실을 추출해 예비 권리분석을 돕는 분석기다.")
        appendLine("문서에 없는 사실을 추정하지 말고, 불확실한 항목은 반드시 requiredChecks에 넣어라.")
        appendLine("등기사항전부증명서가 없으면 확정적 권리 순위나 인수 여부를 단정하지 마라.")
        appendLine("'PDF 원문은 아님'이라고 표시된 근거는 요약·메타데이터일 뿐이므로 원문에 있을 법한 내용을 추측하지 마라.")
        appendLine("riskItems에는 근거 문서에서 확인되는 위험만, requiredChecks에는 추가 확인 사항만 기록하라.")
        appendLine("suitabilityScore는 권리위험, 가격조건, 지역조건, 점유위험을 함께 반영한 0~100 적합도다.")
        appendLine("사건: ${item.caseNumber}, 물건: ${item.auctionItemNumber}, 주소: ${item.address}")
        appendLine("감정가: ${item.appraisalPrice}, 최저가: ${item.minimumPrice}, 유찰: ${item.failedCount}회")
        appendLine("사용자 조건: 최대입찰가=${criteria.maxBidPrice ?: "미설정"}, 선호지역=${criteria.preferredDistricts.joinToString()}, 최소할인율=${criteria.minimumDiscountRate ?: "미설정"}, 최대허용위험=${criteria.maximumRiskLevel}, 점유허용=${criteria.allowOccupiedProperty}, 추가요청=${criteria.extraRequest}")
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
            riskItems = json.optJSONArray("riskItems").toStringList(),
            requiredChecks = json.optJSONArray("requiredChecks").toStringList(),
            analyzedAt = System.currentTimeMillis(),
            modelName = mode.modelName,
            promptVersion = mode.promptVersion,
        )
    }

    private companion object {
        const val MAX_DOCUMENT_TEXT_LENGTH = 120_000

        val RESPONSE_SCHEMA = Schema.obj(
            mapOf(
                "riskLevel" to Schema.enumeration(AuctionRiskLevel.entries.map(Enum<*>::name)),
                "suitabilityScore" to Schema.integer(minimum = 0.0, maximum = 100.0),
                "headline" to Schema.string(),
                "summary" to Schema.string(),
                "riskItems" to Schema.array(Schema.string(), maxItems = 12),
                "requiredChecks" to Schema.array(Schema.string(), maxItems = 12),
            ),
        )
    }
}

private fun org.json.JSONArray?.toStringList(): List<String> = buildList {
    val source = this@toStringList ?: return@buildList
    for (index in 0 until source.length()) {
        source.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
    }
}
