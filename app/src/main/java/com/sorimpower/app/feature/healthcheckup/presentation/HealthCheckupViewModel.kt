package com.sorimpower.app.feature.healthcheckup.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.healthcheckup.data.OpenAiHealthDocumentExtractor
import com.sorimpower.app.feature.healthcheckup.data.OpenAiHealthTrendAnalyzer
import com.sorimpower.app.feature.healthcheckup.data.HealthCheckupRepository
import com.sorimpower.app.feature.healthcheckup.data.HealthScreeningOptionAnalyzer
import com.sorimpower.app.feature.healthcheckup.data.HealthCheckupWithMetrics
import com.sorimpower.app.feature.healthcheckup.data.toDraft
import com.sorimpower.app.feature.healthcheckup.domain.HealthCheckupDraft
import com.sorimpower.app.feature.healthcheckup.domain.HealthMetricDraft
import com.sorimpower.app.feature.healthcheckup.domain.ImportedHealthDocument
import com.sorimpower.app.feature.healthcheckup.domain.LongTermHealthAnalysis
import com.sorimpower.app.feature.healthcheckup.domain.HealthScreeningRecommendation
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.util.UUID

class HealthCheckupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HealthCheckupRepository(application)
    private val extractor = OpenAiHealthDocumentExtractor(application)
    private val trendAnalyzer = OpenAiHealthTrendAnalyzer(application)
    private val optionAnalyzer = HealthScreeningOptionAnalyzer(application)
    val checkups = repository.checkups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _draft = MutableStateFlow<HealthCheckupDraft?>(null)
    val draft = _draft.asStateFlow()
    private val _isWorking = MutableStateFlow(false)
    val isWorking = _isWorking.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    private val _longTermAnalysis = MutableStateFlow<LongTermHealthAnalysis?>(null)
    val longTermAnalysis = _longTermAnalysis.asStateFlow()
    private val _isTrendAnalyzing = MutableStateFlow(false)
    val isTrendAnalyzing = _isTrendAnalyzing.asStateFlow()
    private val _trendErrorMessage = MutableStateFlow<String?>(null)
    val trendErrorMessage = _trendErrorMessage.asStateFlow()
    private val _screeningRecommendation = MutableStateFlow<HealthScreeningRecommendation?>(null)
    val screeningRecommendation = _screeningRecommendation.asStateFlow()
    private val _isScreeningAnalyzing = MutableStateFlow(false)
    val isScreeningAnalyzing = _isScreeningAnalyzing.asStateFlow()
    private val _screeningErrorMessage = MutableStateFlow<String?>(null)
    val screeningErrorMessage = _screeningErrorMessage.asStateFlow()
    private var temporaryDocument: ImportedHealthDocument? = null

    init {
        viewModelScope.launch { _longTermAnalysis.value = repository.getFreshLongTermAnalysis() }
    }

    fun extractDocument(
        uri: Uri,
        checkupDate: LocalDate,
        hospitalName: String,
        title: String,
        memo: String,
    ) {
        if (_isWorking.value) return
        viewModelScope.launch {
            _isWorking.value = true
            _errorMessage.value = null
            runCatching {
                repository.discardDocument(temporaryDocument)
                val document = repository.importDocument(uri)
                temporaryDocument = document
                val extraction = withTimeout(180_000L) { extractor.extract(document) }
                HealthCheckupDraft(
                    checkupId = UUID.randomUUID().toString(),
                    checkupDateEpochDay = checkupDate.toEpochDay(),
                    hospitalName = hospitalName,
                    title = title,
                    memo = memo,
                    originalFilePath = document.localPath,
                    originalFileName = document.displayName,
                    originalMimeType = document.mimeType,
                    aiSummary = extraction.summary,
                    metrics = extraction.metrics,
                )
            }.onSuccess { _draft.value = it }
                .onFailure { error ->
                    _errorMessage.value = if (error is TimeoutCancellationException) {
                        "문서 분석 시간이 초과됐어요. 파일 크기를 확인한 뒤 다시 시도해 주세요."
                    } else {
                        error.message?.takeIf(String::isNotBlank) ?: "건강검진 문서를 분석하지 못했어요."
                    }
                }
            _isWorking.value = false
        }
    }

    fun edit(checkup: HealthCheckupWithMetrics) {
        temporaryDocument = null
        _errorMessage.value = null
        _draft.value = checkup.toDraft()
    }

    fun updateDraft(value: HealthCheckupDraft) {
        _draft.value = value
    }

    fun addMetric() {
        _draft.value = _draft.value?.let { draft ->
            draft.copy(metrics = draft.metrics + HealthMetricDraft(id = UUID.randomUUID().toString()))
        }
    }

    fun updateMetric(value: HealthMetricDraft) {
        _draft.value = _draft.value?.let { draft ->
            draft.copy(metrics = draft.metrics.map { if (it.id == value.id) value else it })
        }
    }

    fun deleteMetric(id: String) {
        _draft.value = _draft.value?.let { it.copy(metrics = it.metrics.filterNot { metric -> metric.id == id }) }
    }

    fun saveDraft(onSaved: () -> Unit) {
        val value = _draft.value ?: return
        if (value.metrics.none { it.name.isNotBlank() }) {
            _errorMessage.value = "검사 항목을 한 개 이상 입력해 주세요."
            return
        }
        if (_isWorking.value) return
        viewModelScope.launch {
            _isWorking.value = true
            _errorMessage.value = null
            runCatching { repository.save(value) }
                .onSuccess {
                    temporaryDocument = null
                    _draft.value = null
                    _longTermAnalysis.value = null
                    onSaved()
                }
                .onFailure { _errorMessage.value = it.message ?: "건강검진을 저장하지 못했어요." }
            _isWorking.value = false
        }
    }

    fun cancelDraft() {
        val document = temporaryDocument
        temporaryDocument = null
        _draft.value = null
        _errorMessage.value = null
        viewModelScope.launch { repository.discardDocument(document) }
    }

    fun delete(checkup: HealthCheckupWithMetrics, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(checkup)
            _longTermAnalysis.value = null
            onDeleted()
        }
    }

    fun analyzeLongTermHealthTrend() {
        if (_isTrendAnalyzing.value) return
        val source = checkups.value
        if (source.size < 2) {
            _trendErrorMessage.value = "건강 추이를 분석하려면 서로 다른 건강검진 기록이 2개 이상 필요해요."
            return
        }
        viewModelScope.launch {
            _isTrendAnalyzing.value = true
            _trendErrorMessage.value = null
            runCatching {
                repository.invalidateLongTermAnalysis()
                withTimeout(120_000L) { trendAnalyzer.analyze(source) }
                    .also { repository.saveLongTermAnalysis(it, source) }
            }.onSuccess { _longTermAnalysis.value = it }
                .onFailure { error ->
                    _trendErrorMessage.value = if (error is TimeoutCancellationException) {
                        "건강 추이 분석 시간이 초과됐어요. 잠시 후 다시 시도해 주세요."
                    } else {
                        error.message?.takeIf(String::isNotBlank) ?: "건강 추이를 분석하지 못했어요."
                    }
                }
            _isTrendAnalyzing.value = false
        }
    }

    fun analyzeScreeningOptions(uri: Uri) {
        if (_isScreeningAnalyzing.value) return
        viewModelScope.launch {
            _isScreeningAnalyzing.value = true
            _screeningErrorMessage.value = null
            runCatching {
                val document = repository.importDocument(uri)
                try { withTimeout(120_000L) { optionAnalyzer.analyze(document, checkups.value) } }
                finally { repository.discardDocument(document) }
            }.onSuccess { _screeningRecommendation.value = it }
                .onFailure { _screeningErrorMessage.value = it.message ?: "선택검사 추천을 만들지 못했어요." }
            _isScreeningAnalyzing.value = false
        }
    }

    fun documentUri(path: String) = repository.openDocumentUri(path)
}
