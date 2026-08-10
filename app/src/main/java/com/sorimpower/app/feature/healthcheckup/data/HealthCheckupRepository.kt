package com.sorimpower.app.feature.healthcheckup.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.sorimpower.app.feature.healthcheckup.domain.HealthCategory
import com.sorimpower.app.feature.healthcheckup.domain.HealthCheckupDraft
import com.sorimpower.app.feature.healthcheckup.domain.HealthMetricDraft
import com.sorimpower.app.feature.healthcheckup.domain.HealthMetricStatus
import com.sorimpower.app.feature.healthcheckup.domain.ImportedHealthDocument
import com.sorimpower.app.feature.healthcheckup.domain.LongTermHealthAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class HealthCheckupRepository(private val context: Context) {
    private val dao = HealthCheckupDatabase.get(context).dao()
    val checkups: Flow<List<HealthCheckupWithMetrics>> = dao.observeCheckups()

    fun observeCheckup(id: String): Flow<HealthCheckupWithMetrics?> = dao.observeCheckup(id)

    suspend fun importDocument(uri: Uri): ImportedHealthDocument = withContext(Dispatchers.IO) {
        val metadata = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                name to if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
            }
        }
        val displayName = metadata?.first?.take(120) ?: "건강검진_${System.currentTimeMillis()}"
        val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank {
            if (displayName.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
        }
        require(mimeType == "application/pdf" || mimeType.startsWith("image/")) { "PDF 또는 이미지 파일만 등록할 수 있어요." }
        val directory = File(context.filesDir, "health_checkup_files").apply { mkdirs() }
        val extension = displayName.substringAfterLast('.', if (mimeType == "application/pdf") "pdf" else "jpg")
            .filter(Char::isLetterOrDigit).take(8).ifBlank { "bin" }
        val target = File(directory, "${UUID.randomUUID()}.$extension")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, bufferSize = 64 * 1024) }
        } ?: error("선택한 파일을 읽을 수 없어요.")
        if (copied > MAX_DOCUMENT_BYTES) {
            target.delete()
            error("문서 크기는 18MB 이하여야 해요.")
        }
        ImportedHealthDocument(target.absolutePath, displayName, mimeType, copied)
    }

    suspend fun save(draft: HealthCheckupDraft) = withContext(Dispatchers.IO) {
        val existing = dao.getCheckupEntity(draft.checkupId)
        val now = System.currentTimeMillis()
        val entity = HealthCheckupEntity(
            id = draft.checkupId,
            checkupDateEpochDay = draft.checkupDateEpochDay,
            hospitalName = draft.hospitalName.trim().take(100),
            title = draft.title.trim().take(100),
            originalFilePath = draft.originalFilePath,
            originalFileName = draft.originalFileName,
            originalMimeType = draft.originalMimeType,
            memo = draft.memo.trim().take(500),
            aiSummary = draft.aiSummary.trim().take(2_000),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            dataVersion = (existing?.dataVersion ?: 0L) + 1L,
        )
        val metrics = draft.metrics.filter { it.name.isNotBlank() }.mapIndexed { index, metric -> metric.toEntity(entity.id, index) }
        dao.replaceCheckup(entity, metrics)
    }

    suspend fun delete(checkup: HealthCheckupWithMetrics) = withContext(Dispatchers.IO) {
        dao.invalidateLongTermAnalyses()
        dao.deleteCheckup(checkup.checkup.id)
        checkup.checkup.originalFilePath.takeIf(String::isNotBlank)?.let { File(it).delete() }
    }

    suspend fun getFreshLongTermAnalysis(): LongTermHealthAnalysis? = withContext(Dispatchers.IO) {
        dao.getFreshLongTermAnalysis()?.let(::parseLongTermAnalysis)
    }

    suspend fun invalidateLongTermAnalysis() = withContext(Dispatchers.IO) { dao.invalidateLongTermAnalyses() }

    suspend fun saveLongTermAnalysis(
        analysis: LongTermHealthAnalysis,
        checkups: List<HealthCheckupWithMetrics>,
    ) = withContext(Dispatchers.IO) {
        dao.upsertAiAnalysis(
            HealthAiAnalysisEntity(
                id = LONG_TERM_ANALYSIS_ID,
                type = "LONG_TERM",
                sourceIds = checkups.joinToString(",") { it.checkup.id },
                model = OpenAiHealthTrendAnalyzer.MODEL_NAME,
                resultJson = analysis.toJson(),
                createdAt = analysis.analyzedAt,
                dataVersion = checkups.joinToString(",") { "${it.checkup.id}:${it.checkup.dataVersion}" },
                stale = false,
            ),
        )
    }

    suspend fun discardDocument(document: ImportedHealthDocument?) = withContext(Dispatchers.IO) {
        document?.localPath?.let { File(it).delete() }
    }

    fun openDocumentUri(path: String): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(path),
    )

    private fun HealthMetricDraft.toEntity(checkupId: String, index: Int) = HealthMetricEntity(
        id = id,
        checkupId = checkupId,
        category = category.name,
        name = name.trim().take(100),
        normalizedName = normalizedName.trim().take(100),
        value = value,
        stringValue = stringValue.trim().take(200),
        unit = unit.trim().take(30),
        referenceMin = referenceMin,
        referenceMax = referenceMax,
        referenceText = referenceText.trim().take(100),
        status = status.name,
        sourceText = sourceText.trim().take(300),
        sortOrder = index,
    )

    companion object {
        const val MAX_DOCUMENT_BYTES = 18L * 1024 * 1024
        const val LONG_TERM_ANALYSIS_ID = "long_term"
    }
}

fun HealthCheckupWithMetrics.toDraft() = HealthCheckupDraft(
    checkupId = checkup.id,
    checkupDateEpochDay = checkup.checkupDateEpochDay,
    hospitalName = checkup.hospitalName,
    title = checkup.title,
    memo = checkup.memo,
    originalFilePath = checkup.originalFilePath,
    originalFileName = checkup.originalFileName,
    originalMimeType = checkup.originalMimeType,
    aiSummary = checkup.aiSummary,
    metrics = metrics.sortedBy(HealthMetricEntity::sortOrder).map { metric ->
        HealthMetricDraft(
            id = metric.id,
            category = enumValues<HealthCategory>().firstOrNull { it.name == metric.category } ?: HealthCategory.OTHER,
            name = metric.name,
            normalizedName = metric.normalizedName,
            value = metric.value,
            stringValue = metric.stringValue,
            unit = metric.unit,
            referenceMin = metric.referenceMin,
            referenceMax = metric.referenceMax,
            referenceText = metric.referenceText,
            status = enumValues<HealthMetricStatus>().firstOrNull { it.name == metric.status } ?: HealthMetricStatus.UNKNOWN,
            sourceText = metric.sourceText,
        )
    },
)
