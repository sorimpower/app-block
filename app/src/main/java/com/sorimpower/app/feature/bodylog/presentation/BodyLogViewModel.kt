package com.sorimpower.app.feature.bodylog.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.bodylog.data.BodyLogRepository
import com.sorimpower.app.feature.bodylog.data.OpenAiBodyLogAnalyzer
import com.sorimpower.app.feature.bodylog.data.MealItemInput
import com.sorimpower.app.feature.bodylog.data.MealWithDetails
import com.sorimpower.app.feature.bodylog.data.MealQuickTemplate
import com.sorimpower.app.feature.bodylog.data.MounjaroInjectionEntity
import com.sorimpower.app.feature.bodylog.data.WeightEntryEntity
import com.sorimpower.app.feature.bodylog.domain.BodyLogState
import com.sorimpower.app.feature.bodylog.domain.BodyLogAiAnalysis
import com.sorimpower.app.feature.bodylog.reminder.MounjaroReminder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.LocalDate

class BodyLogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BodyLogRepository(application)
    private val aiAnalyzer = OpenAiBodyLogAnalyzer(application)
    private val _aiAnalysis = MutableStateFlow<BodyLogAiAnalysis?>(null)
    val aiAnalysis = _aiAnalysis.asStateFlow()
    private val _isAiAnalyzing = MutableStateFlow(false)
    val isAiAnalyzing = _isAiAnalyzing.asStateFlow()
    private val _aiAnalysisError = MutableStateFlow<String?>(null)
    val aiAnalysisError = _aiAnalysisError.asStateFlow()
    val state = repository.data.map {
        BodyLogState(it.weights, it.meals, it.goal, it.mounjaroInjections, it.weightsHidden, it.quickMealTemplates, loaded = true)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyLogState())

    fun setWeightsHidden(hidden: Boolean) = viewModelScope.launch { repository.setWeightsHidden(hidden) }

    fun analyzeWeightProgress() {
        if (_isAiAnalyzing.value) return
        viewModelScope.launch {
            _isAiAnalyzing.value = true
            _aiAnalysisError.value = null
            runCatching {
                withTimeout(90_000L) { aiAnalyzer.analyze(state.value) }
            }.onSuccess { analysis ->
                _aiAnalysis.value = analysis
            }.onFailure { error ->
                _aiAnalysisError.value = if (error is TimeoutCancellationException) {
                    "AI 응답 시간이 지나 분석을 중단했어요. 잠시 후 다시 시도해 주세요."
                } else {
                    error.message?.takeIf(String::isNotBlank) ?: "AI 분석을 완료하지 못했어요."
                }
            }
            _isAiAnalyzing.value = false
        }
    }

    fun saveWeight(
        id: String? = null,
        weightKg: Double,
        measuredAt: Long = System.currentTimeMillis(),
        bodyFatPercent: Double? = null,
        condition: String? = null,
        note: String? = null,
    ) = viewModelScope.launch {
        repository.saveWeight(id, weightKg, measuredAt, bodyFatPercent, condition, note)
    }

    fun deleteWeight(entry: WeightEntryEntity) = viewModelScope.launch { repository.deleteWeight(entry) }

    fun saveGoal(startWeightKg: Double, targetWeightKg: Double, targetDate: LocalDate? = null) = viewModelScope.launch {
        repository.saveGoal(startWeightKg, targetWeightKg, targetDate)
    }

    fun saveMounjaroInjection(
        existing: MounjaroInjectionEntity? = null,
        injectedAt: Long,
        doseMg: Double,
        sideEffects: Set<String>,
        note: String?,
        reminderEnabled: Boolean,
        reminderIntervalWeeks: Int,
        onSaved: () -> Unit,
    ) = viewModelScope.launch {
        repository.saveMounjaroInjection(existing, injectedAt, doseMg, sideEffects, note, reminderEnabled, reminderIntervalWeeks)
        MounjaroReminder.schedule(getApplication(), injectedAt, reminderIntervalWeeks, reminderEnabled)
        onSaved()
    }

    fun updateMounjaroReminder(injection: com.sorimpower.app.feature.bodylog.data.MounjaroInjectionEntity, enabled: Boolean, intervalWeeks: Int) = viewModelScope.launch {
        repository.updateMounjaroReminder(injection, enabled, intervalWeeks)
        MounjaroReminder.schedule(getApplication(), injection.injectedAt, intervalWeeks, enabled)
    }

    fun deleteMounjaroInjection(injection: MounjaroInjectionEntity) = viewModelScope.launch {
        repository.deleteMounjaroInjection(injection)
        MounjaroReminder.schedule(getApplication(), injection.injectedAt, injection.reminderIntervalWeeks, enabled = false)
        repository.latestMounjaroInjection()?.let { latest ->
            MounjaroReminder.schedule(getApplication(), latest.injectedAt, latest.reminderIntervalWeeks, latest.reminderEnabled)
        }
    }

    fun saveMeal(
        existing: MealWithDetails? = null,
        eatenAt: Long = System.currentTimeMillis(),
        mealType: String,
        items: List<MealItemInput>,
        note: String?,
        tags: Set<String>,
        photoUris: List<Uri>,
        retainedPhotoIds: Set<String> = emptySet(),
        onSaved: () -> Unit,
    ) = viewModelScope.launch {
        repository.saveMeal(existing, mealType, eatenAt, items, note, tags, photoUris, retainedPhotoIds)
        onSaved()
    }

    fun deleteMeal(meal: MealWithDetails) = viewModelScope.launch { repository.deleteMeal(meal) }
    fun saveQuickMealTemplate(mealType: String, items: List<String>, note: String?, tags: Set<String>, onSaved: () -> Unit) = viewModelScope.launch {
        repository.saveQuickMealTemplate(mealType, items, note, tags)
        onSaved()
    }
    fun deleteQuickMealTemplate(template: MealQuickTemplate) = viewModelScope.launch { repository.deleteQuickMealTemplate(template.id) }
    fun createCameraUri(): Pair<Uri, File> = repository.createCameraUri()

}
