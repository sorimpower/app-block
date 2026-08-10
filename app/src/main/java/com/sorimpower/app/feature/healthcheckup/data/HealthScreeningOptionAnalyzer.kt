package com.sorimpower.app.feature.healthcheckup.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.sorimpower.app.core.ai.AiImageAttachment
import com.sorimpower.app.core.ai.AiModelRouter
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import com.sorimpower.app.feature.healthcheckup.domain.HealthScreeningOptionRecommendation
import com.sorimpower.app.feature.healthcheckup.domain.HealthScreeningRecommendation
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

internal class HealthScreeningOptionAnalyzer(private val context: Context) {
    suspend fun analyze(document: com.sorimpower.app.feature.healthcheckup.domain.ImportedHealthDocument, checkups: List<HealthCheckupWithMetrics>): HealthScreeningRecommendation {
        require(document.mimeType == "application/pdf") { "유료 선택검사 안내 PDF를 선택해 주세요." }
        val response = AiModelRouter(context).generate(
            AiRequest(
                taskType = AiTaskType.HEALTH_SCREENING_OPTION_RECOMMENDATION,
                userPrompt = prompt(checkups),
                images = renderPdfPages(File(document.localPath)),
            ),
        )
        val json = JSONObject(response.text)
        val items = json.optJSONArray("recommendations")
        return HealthScreeningRecommendation(
            summary = json.optString("summary").ifBlank { "선택검사 안내와 기존 건강기록을 함께 검토했어요." },
            recommendations = buildList {
                if (items != null) for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isNotBlank()) add(HealthScreeningOptionRecommendation(name, item.optString("priority", "검토"), item.optString("reason"), item.optString("clinicalNote")))
                }
            },
            caution = json.optString("caution").ifBlank { "최종 선택은 검사기관의 설명과 개인·가족력, 담당 의료진 상담을 함께 고려해 결정하세요." },
        )
    }

    private fun renderPdfPages(file: File): List<AiImageAttachment> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                (0 until minOf(renderer.pageCount, 12)).map { index ->
                    renderer.openPage(index).use { page ->
                        val width = 1200
                        val height = (page.height.toFloat() / page.width * width).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
                        bitmap.recycle()
                        AiImageAttachment(bytes = bytes)
                    }
                }
            }
        }

    private fun prompt(checkups: List<HealthCheckupWithMetrics>) = buildString {
        appendLine("첨부 이미지는 다음 건강검진에서 선택 가능한 유료 검사 항목 안내다. 이미지에 실제로 적힌 항목만 추천하라.")
        appendLine("사용자의 과거 검진 기록을 근거로 최대 3개만 우선순위로 추천한다. 진단·처방·확정적 표현은 금지하며, 개인·가족력·증상이 없어서 판단할 수 없는 점은 caution에 밝힌다.")
        appendLine("JSON만 반환: {summary, recommendations:[{name,priority,reason,clinicalNote}], caution}. priority는 '우선 검토', '선택 고려' 중 하나다.")
        appendLine("과거 검진 기록:")
        checkups.sortedBy { it.checkup.checkupDateEpochDay }.forEach { checkup ->
            appendLine("[${LocalDate.ofEpochDay(checkup.checkup.checkupDateEpochDay)}]")
            checkup.metrics.forEach { metric -> appendLine("- ${metric.normalizedName.ifBlank { metric.name }}=${metric.value ?: metric.stringValue} ${metric.unit} (${metric.status})") }
        }
    }
}
