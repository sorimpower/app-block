package com.sorimpower.app.feature.bodylog.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.sorimpower.app.core.ai.AiImageAttachment
import com.sorimpower.app.core.ai.AiModelId
import com.sorimpower.app.core.ai.AiModelRouter
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import java.io.ByteArrayOutputStream
import java.io.File
import org.json.JSONObject

internal data class InBodyExtraction(val summary: String, val metricsJson: String)

internal class OpenAiInBodyAnalyzer(private val context: Context) {
    suspend fun analyze(document: ImportedInBodyFile): InBodyExtraction {
        val file = File(document.localPath)
        val images = if (document.mimeType == "application/pdf") renderPdf(file) else {
            listOf(AiImageAttachment(sourceId = document.displayName, mimeType = document.mimeType, bytes = file.readBytes()))
        }
        require(images.isNotEmpty()) { "인바디 결과지 내용을 읽을 수 없어요." }
        val response = AiModelRouter(context).generate(
            AiRequest(
                taskType = AiTaskType.BODY_LOG_INBODY_EXTRACTION,
                userPrompt = PROMPT,
                images = images,
            ),
            model = AiModelId.OPENAI_FAST,
        )
        val root = JSONObject(response.text)
        val metrics = root.optJSONObject("metrics") ?: JSONObject()
        return InBodyExtraction(
            summary = root.optString("summary").trim().take(1_000).ifBlank { "인바디 주요 수치를 추출했어요." },
            metricsJson = metrics.toString(),
        )
    }

    private fun renderPdf(file: File): List<AiImageAttachment> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                (0 until minOf(renderer.pageCount, 3)).map { index ->
                    renderer.openPage(index).use { page ->
                        val width = 1_400
                        val height = (page.height.toFloat() / page.width * width).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val output = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
                        bitmap.recycle()
                        AiImageAttachment(sourceId = "page-${index + 1}", bytes = output.toByteArray())
                    }
                }
            }
        }

    private companion object {
        val PROMPT = """
            첨부된 인바디 또는 체성분 결과지에서 측정값을 정확히 추출한다.
            보이지 않는 값은 추정하지 말고 null로 둔다. summary는 체중·골격근량·체지방량·체지방률을 중심으로 2문장 이내의 중립적인 한국어 요약으로 작성한다.
            반드시 JSON 객체만 반환한다.
            {
              "summary":"",
              "metrics":{
                "weightKg":null,
                "skeletalMuscleMassKg":null,
                "bodyFatMassKg":null,
                "bodyFatPercent":null,
                "bmi":null,
                "visceralFatLevel":null,
                "inBodyScore":null,
                "basalMetabolicRateKcal":null
              }
            }
        """.trimIndent()
    }
}
