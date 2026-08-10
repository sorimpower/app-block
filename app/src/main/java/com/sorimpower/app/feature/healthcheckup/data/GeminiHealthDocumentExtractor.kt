package com.sorimpower.app.feature.healthcheckup.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.sorimpower.app.core.ai.AiImageAttachment
import com.sorimpower.app.core.ai.AiModelRouter
import com.sorimpower.app.core.ai.AiRequest
import com.sorimpower.app.core.ai.AiTaskType
import com.sorimpower.app.feature.healthcheckup.domain.HealthCategory
import com.sorimpower.app.feature.healthcheckup.domain.HealthDocumentExtraction
import com.sorimpower.app.feature.healthcheckup.domain.HealthMetricDraft
import com.sorimpower.app.feature.healthcheckup.domain.HealthMetricStatus
import com.sorimpower.app.feature.healthcheckup.domain.ImportedHealthDocument
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal class OpenAiHealthDocumentExtractor(private val context: Context) {
    suspend fun extract(document: ImportedHealthDocument): HealthDocumentExtraction {
        val file = File(document.localPath)
        val images = if (document.mimeType == "application/pdf") preparePdfForExtraction(file) else listOf(AiImageAttachment(document.mimeType, file.readBytes()))
        require(images.isNotEmpty()) { "문서 내용이 비어 있어요." }
        val response = AiModelRouter(context).generate(
            AiRequest(
                taskType = AiTaskType.HEALTH_CHECKUP_EXTRACTION,
                userPrompt = EXTRACTION_PROMPT,
                images = images,
            ),
        )
        return parse(response.text)
    }

    private suspend fun preparePdfForExtraction(file: File): List<AiImageAttachment> {
        val pageCount = pdfPageCount(file)
        if (pageCount <= DIRECT_EXTRACTION_PAGE_LIMIT) {
            return renderPdfPages(file, (0 until pageCount).toList(), PDF_RENDER_WIDTH, PDF_RENDER_QUALITY)
        }

        val previewPageCount = minOf(pageCount, MAX_PAGE_SELECTION_PAGES)
        val previews = renderPdfPages(
            file = file,
            pageIndexes = (0 until previewPageCount).toList(),
            width = PAGE_SELECTION_RENDER_WIDTH,
            quality = PAGE_SELECTION_RENDER_QUALITY,
        )
        val selectedIndexes = selectResultPageIndexes(previews, pageCount)
        val condensedPdf = createCondensedPdf(file, selectedIndexes)
        return try {
            renderPdfPages(
                file = condensedPdf,
                pageIndexes = (0 until selectedIndexes.size).toList(),
                width = PDF_RENDER_WIDTH,
                quality = PDF_RENDER_QUALITY,
            )
        } finally {
            condensedPdf.delete()
        }
    }

    private suspend fun selectResultPageIndexes(previews: List<AiImageAttachment>, totalPageCount: Int): List<Int> {
        val response = AiModelRouter(context).generate(
            AiRequest(
                taskType = AiTaskType.HEALTH_CHECKUP_PAGE_SELECTION,
                userPrompt = PAGE_SELECTION_PROMPT.replace("{{pageCount}}", totalPageCount.toString()),
                images = previews,
            ),
        )
        val selected = runCatching {
            val pages = JSONObject(response.text).optJSONArray("resultPageNumbers")
            buildList {
                if (pages != null) for (index in 0 until pages.length()) {
                    pages.optInt(index, 0).takeIf { it in 1..totalPageCount }?.let { add(it - 1) }
                }
            }
        }.getOrDefault(emptyList())
        return selected.distinct().sorted().take(MAX_RESULT_PAGE_COUNT)
            .ifEmpty { (0 until DIRECT_EXTRACTION_PAGE_LIMIT).toList() }
    }

    private fun pdfPageCount(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { it.pageCount }
        }

    private fun createCondensedPdf(source: File, pageIndexes: List<Int>): File {
        val output = File(context.cacheDir, "health-checkup-result-pages-${UUID.randomUUID()}.pdf")
        val document = PdfDocument()
        try {
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    pageIndexes.forEachIndexed { outputIndex, sourceIndex ->
                        renderer.openPage(sourceIndex).use { sourcePage ->
                            val width = CONDENSED_PDF_WIDTH
                            val height = (sourcePage.height.toFloat() / sourcePage.width * width).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            sourcePage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, outputIndex + 1).create())
                            page.canvas.drawColor(Color.WHITE)
                            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                            document.finishPage(page)
                            bitmap.recycle()
                        }
                    }
                }
            }
            FileOutputStream(output).use(document::writeTo)
            return output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            document.close()
        }
    }

    private fun renderPdfPages(
        file: File,
        pageIndexes: List<Int>,
        width: Int,
        quality: Int,
    ): List<AiImageAttachment> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                pageIndexes.filter { it in 0 until renderer.pageCount }.map { index ->
                    renderer.openPage(index).use { page ->
                        val height = (page.height.toFloat() / page.width * width).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val output = java.io.ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                        bitmap.recycle()
                        AiImageAttachment(bytes = output.toByteArray())
                    }
                }
            }
        }

    private fun parse(response: String): HealthDocumentExtraction {
        val root = JSONObject(response)
        val array = root.optJSONArray("metrics")
        val metrics = buildList {
            if (array != null) for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.firstNonBlank("name", "testName", "metricName", "검사명", "검사항목")
                // A column heading is not a test name. Do not show invented placeholder rows
                // to the user when the model cannot read a table cell clearly.
                if (name.isBlank() || name.isUnidentifiedMetricName()) continue
                add(
                    HealthMetricDraft(
                        id = UUID.randomUUID().toString(),
                        category = item.optEnum("category", HealthCategory.OTHER),
                        name = name,
                        normalizedName = item.firstNonBlank("normalizedName", "standardName", "표준검사명").ifBlank { name },
                        value = item.optNullableDouble("value"),
                        stringValue = item.optString("stringValue").trim(),
                        unit = item.optString("unit").trim(),
                        referenceMin = item.optNullableDouble("referenceMin"),
                        referenceMax = item.optNullableDouble("referenceMax"),
                        referenceText = item.optString("referenceText").trim(),
                        status = item.optEnum("status", HealthMetricStatus.UNKNOWN),
                        sourceText = item.optString("sourceText").trim(),
                    ),
                )
            }
        }
        return HealthDocumentExtraction(root.optString("summary").trim(), metrics)
    }

    private companion object {
        const val DIRECT_EXTRACTION_PAGE_LIMIT = 12
        const val MAX_PAGE_SELECTION_PAGES = 80
        const val MAX_RESULT_PAGE_COUNT = 16
        const val PAGE_SELECTION_RENDER_WIDTH = 480
        const val PAGE_SELECTION_RENDER_QUALITY = 55
        const val PDF_RENDER_WIDTH = 1200
        const val PDF_RENDER_QUALITY = 80
        const val CONDENSED_PDF_WIDTH = 1600
        val PAGE_SELECTION_PROMPT = """
            이 이미지는 한 건강검진 PDF의 페이지를 순서대로 축소한 것이다. 첫 번째 이미지는 1페이지, 두 번째 이미지는 2페이지다.
            전체 문서는 {{pageCount}}페이지다. 숫자 검사 결과가 들어 있는 표 페이지를 모두 찾는다.
            혈액·소변·혈압·신체계측·혈당·지질·간기능·신장기능 등 실제 검사명과 수치, 단위 또는 참고범위가 함께 있는 페이지를 선택하라.
            표지, 문진표, 안내문, 판정 요약, 영상 검사 설명, 생활습관 안내처럼 개별 검사 수치 표가 없는 페이지는 제외한다.
            반드시 JSON 객체만 반환하고, 형식은 {"resultPageNumbers":[1,2]}로 고정한다. 페이지 번호는 1부터 시작한다.
        """.trimIndent()
        val EXTRACTION_PROMPT = """
            이 문서는 사용자의 건강검진 결과표다. 문서에 실제로 존재하는 검사 결과만 빠짐없이 구조화하라.
            표의 열 제목(예: 검사명, 검사항목, 결과, 참고치)은 절대 metrics 항목으로 만들지 마라.
            name에는 반드시 결과 행에 인쇄된 실제 검사명을 원문 그대로 넣어라. "검사항목명 미식별", "미식별", "알 수 없음", "N/A" 같은 임의의 대체 이름은 절대 쓰지 마라.
            실제 검사명을 읽을 수 없는 행은 추측하거나 대체 항목을 만들지 말고 metrics에서 완전히 제외하라.
            반드시 JSON 객체만 반환하고, 최상위 키는 summary와 metrics로 고정하라.
            metrics의 각 객체는 category, name, normalizedName, value, stringValue, unit, referenceMin, referenceMax, referenceText, status, sourceText 키를 사용하라.
            category는 BODY, BLOOD_PRESSURE, METABOLIC, CARDIOVASCULAR, LIVER, KIDNEY, BLOOD, DIGESTIVE, THYROID, LUNG, CANCER_SCREENING, URINE, OTHER 중 하나로 쓴다.
            status는 NORMAL, LOW, HIGH, WARNING, UNKNOWN 중 하나로 쓴다.
            숫자는 value에, 양성·음성·정상 같은 비수치 결과는 stringValue에 넣어라.
            검사명은 name에 원문대로 쓰고 normalizedName에는 일반적으로 통용되는 짧은 이름을 쓰되 확실하지 않으면 name과 같게 하라.
            category는 제공된 enum 중 가장 적절한 값을 선택하라. 참고범위와 단위는 문서에 있을 때만 넣고, 없으면 null 또는 빈 문자열로 둬라.
            status는 문서가 명시하거나 참고범위로 확실히 판단되는 경우만 NORMAL, LOW, HIGH, WARNING을 사용하고 나머지는 UNKNOWN으로 둬라.
            sourceText에는 해당 결과가 나온 원문을 짧게 보존하라. 수치를 추정하거나 진단하지 마라.
            summary는 문서에서 확인되는 구성과 검수 시 특히 숫자를 확인할 항목을 3문장 이내로 설명하라.
        """.trimIndent()
    }
}

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf(Double::isFinite)

private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == optString(key) } ?: default

private fun JSONObject.firstNonBlank(vararg keys: String): String =
    keys.asSequence().map { optString(it).trim() }.firstOrNull(String::isNotBlank).orEmpty()

private fun String.isUnidentifiedMetricName(): Boolean {
    val compact = replace(" ", "").lowercase()
    return compact in setOf("검사항목명미식별", "미식별", "알수없음", "n/a", "na", "unknown")
}
