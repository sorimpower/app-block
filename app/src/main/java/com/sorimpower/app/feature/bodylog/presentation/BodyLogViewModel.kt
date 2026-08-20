package com.sorimpower.app.feature.bodylog.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.bodylog.data.BodyLogRepository
import com.sorimpower.app.feature.bodylog.data.OpenAiBodyLogAnalyzer
import com.sorimpower.app.feature.bodylog.data.OpenAiInBodyAnalyzer
import com.sorimpower.app.feature.bodylog.data.ExerciseEntryEntity
import com.sorimpower.app.feature.bodylog.data.InBodyResultEntity
import com.sorimpower.app.feature.bodylog.data.MealItemInput
import com.sorimpower.app.feature.bodylog.data.MealWithDetails
import com.sorimpower.app.feature.bodylog.data.MealQuickTemplate
import com.sorimpower.app.feature.bodylog.data.MounjaroInjectionEntity
import com.sorimpower.app.feature.bodylog.data.WeightEntryEntity
import com.sorimpower.app.feature.bodylog.domain.BodyLogState
import com.sorimpower.app.feature.bodylog.domain.BodyLogAiAnalysis
import com.sorimpower.app.feature.bodylog.reminder.MounjaroReminder
import com.sorimpower.app.feature.bodylog.reminder.MealCalorieAnalysisScheduler
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
import java.util.UUID

class BodyLogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BodyLogRepository(application)
    private val aiAnalyzer = OpenAiBodyLogAnalyzer(application)
    private val inBodyAnalyzer = OpenAiInBodyAnalyzer(application)
    private val _aiAnalysis = MutableStateFlow<BodyLogAiAnalysis?>(null)
    val aiAnalysis = _aiAnalysis.asStateFlow()
    private val _isAiAnalyzing = MutableStateFlow(false)
    val isAiAnalyzing = _isAiAnalyzing.asStateFlow()
    private val _aiAnalysisError = MutableStateFlow<String?>(null)
    val aiAnalysisError = _aiAnalysisError.asStateFlow()
    private val _isInBodyAnalyzing = MutableStateFlow(false)
    val isInBodyAnalyzing = _isInBodyAnalyzing.asStateFlow()
    private val _inBodyError = MutableStateFlow<String?>(null)
    val inBodyError = _inBodyError.asStateFlow()
    val state = repository.data.map {
        BodyLogState(
            weights = it.weights,
            meals = it.meals,
            activeGoal = it.goal,
            mounjaroInjections = it.mounjaroInjections,
            weightsHidden = it.weightsHidden,
            quickMealTemplates = it.quickMealTemplates,
            dailyCalories = it.dailyCalories,
            mealCalories = it.mealCalories,
            exercises = it.exercises,
            inBodyResults = it.inBodyResults,
            loaded = true,
        )
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

    fun saveExercise(
        existing: ExerciseEntryEntity? = null,
        exercisedAt: Long,
        exerciseType: String,
        durationMinutes: Int,
        intensity: String,
        caloriesBurned: Int?,
        note: String?,
        onSaved: () -> Unit,
    ) = viewModelScope.launch {
        repository.saveExercise(existing, exercisedAt, exerciseType, durationMinutes, intensity, caloriesBurned, note)
        onSaved()
    }

    fun deleteExercise(exercise: ExerciseEntryEntity) = viewModelScope.launch { repository.deleteExercise(exercise) }

    fun importAndAnalyzeInBody(uri: Uri, measuredAt: Long) {
        if (_isInBodyAnalyzing.value) return
        viewModelScope.launch {
            _isInBodyAnalyzing.value = true
            _inBodyError.value = null
            var pending: InBodyResultEntity? = null
            runCatching {
                val document = repository.importInBodyFile(uri)
                val now = System.currentTimeMillis()
                pending = InBodyResultEntity(
                    id = UUID.randomUUID().toString(),
                    measuredAt = measuredAt,
                    originalFilePath = document.localPath,
                    originalFileName = document.displayName,
                    originalMimeType = document.mimeType,
                    metricsJson = "{}",
                    aiSummary = "인바디 결과를 분석하고 있어요.",
                    analysisStatus = "ANALYZING",
                    errorMessage = null,
                    createdAt = now,
                    updatedAt = now,
                )
                repository.saveInBodyResult(pending!!)
                val extraction = withTimeout(120_000L) { inBodyAnalyzer.analyze(document) }
                repository.saveInBodyResult(
                    pending!!.copy(
                        metricsJson = extraction.metricsJson,
                        aiSummary = extraction.summary,
                        analysisStatus = "COMPLETE",
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }.onFailure { error ->
                val message = if (error is TimeoutCancellationException) {
                    "인바디 분석 시간이 초과됐어요. 다시 시도해 주세요."
                } else error.message?.takeIf(String::isNotBlank) ?: "인바디 결과를 분석하지 못했어요."
                pending?.let {
                    repository.saveInBodyResult(
                        it.copy(aiSummary = "인바디 분석 실패", analysisStatus = "FAILED", errorMessage = message, updatedAt = System.currentTimeMillis()),
                    )
                }
                _inBodyError.value = message
            }
            _isInBodyAnalyzing.value = false
        }
    }

    fun deleteInBodyResult(result: InBodyResultEntity) = viewModelScope.launch { repository.deleteInBodyResult(result) }

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
        val result = repository.saveMeal(existing, mealType, eatenAt, items, note, tags, photoUris, retainedPhotoIds)
        onSaved()
        result.calorieAnalysisMealIds.forEach { MealCalorieAnalysisScheduler.enqueue(getApplication(), it) }
    }

    fun deleteMeal(meal: MealWithDetails) = viewModelScope.launch {
        MealCalorieAnalysisScheduler.cancel(getApplication(), meal.meal.id)
        repository.deleteMeal(meal)
    }
    fun saveQuickMealTemplate(mealType: String, items: List<String>, note: String?, tags: Set<String>, onSaved: () -> Unit) = viewModelScope.launch {
        repository.saveQuickMealTemplate(mealType, items, note, tags)
        onSaved()
    }
    fun deleteQuickMealTemplate(template: MealQuickTemplate) = viewModelScope.launch { repository.deleteQuickMealTemplate(template.id) }
    fun createCameraUri(): Pair<Uri, File> = repository.createCameraUri()

}
