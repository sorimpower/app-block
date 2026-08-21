package com.sorimpower.app.feature.bodylog.domain

import com.sorimpower.app.feature.bodylog.data.MealWithDetails
import com.sorimpower.app.feature.bodylog.data.MealQuickTemplate
import com.sorimpower.app.feature.bodylog.data.WeightEntryEntity
import com.sorimpower.app.feature.bodylog.data.WeightGoalEntity
import com.sorimpower.app.feature.bodylog.data.MounjaroInjectionEntity
import com.sorimpower.app.feature.bodylog.data.DailyCalorieSummaryEntity
import com.sorimpower.app.feature.bodylog.data.MealCalorieEstimateEntity
import com.sorimpower.app.feature.bodylog.data.ExerciseEntryEntity
import com.sorimpower.app.feature.bodylog.data.InBodyResultEntity
import com.sorimpower.app.feature.bodylog.data.DailyHealthActivityEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class MealType(val label: String) {
    BREAKFAST("아침"), LUNCH("점심"), DINNER("저녁"), SNACK("간식");

    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: SNACK }
}

enum class ChartPeriod(val label: String) { DAY("일"), WEEK("주"), MONTH("월"), YEAR("연") }

data class ChartPoint(val label: String, val value: Double, val timestamp: Long)

data class BodyLogState(
    val weights: List<WeightEntryEntity> = emptyList(),
    val meals: List<MealWithDetails> = emptyList(),
    val activeGoal: WeightGoalEntity? = null,
    val mounjaroInjections: List<MounjaroInjectionEntity> = emptyList(),
    val weightsHidden: Boolean = false,
    val quickMealTemplates: List<MealQuickTemplate> = emptyList(),
    val dailyCalories: List<DailyCalorieSummaryEntity> = emptyList(),
    val mealCalories: List<MealCalorieEstimateEntity> = emptyList(),
    val exercises: List<ExerciseEntryEntity> = emptyList(),
    val healthActivity: List<DailyHealthActivityEntity> = emptyList(),
    val inBodyResults: List<InBodyResultEntity> = emptyList(),
    val loaded: Boolean = false,
) {
    val latestWeight get() = weights.maxByOrNull(WeightEntryEntity::measuredAt)
    val today: LocalDate get() = LocalDate.now()
    val todayMeals get() = meals.filter { it.meal.localDate() == today }.sortedBy { it.meal.eatenAt }
    val todayWeights get() = weights.filter { it.localDate() == today }.sortedBy(WeightEntryEntity::measuredAt)
    val startChange get() = latestWeight?.let { latest -> activeGoal?.let { latest.weightKg - it.startWeightKg } }
    val goalRemaining get() = latestWeight?.let { latest -> activeGoal?.let { it.targetWeightKg - latest.weightKg } }
    val latestMounjaroInjection get() = mounjaroInjections.maxByOrNull(MounjaroInjectionEntity::injectedAt)
    val nextMounjaroInjectionAt get() = latestMounjaroInjection?.takeIf(MounjaroInjectionEntity::reminderEnabled)?.let {
        it.injectedAt + MOUNJARO_INTERVAL_MILLIS * it.reminderIntervalWeeks
    }
    val sevenDayAverage: Double? get() {
        val from = today.minusDays(6)
        return weights.dailyRepresentatives().filterKeys { !it.isBefore(from) && !it.isAfter(today) }
            .values.map(WeightEntryEntity::weightKg).takeIf(List<Double>::isNotEmpty)?.average()
    }
}

const val MOUNJARO_INTERVAL_MILLIS = 7 * 24 * 60 * 60 * 1000L

fun WeightEntryEntity.localDate(): LocalDate = Instant.ofEpochMilli(measuredAt).atZone(ZoneId.systemDefault()).toLocalDate()
fun com.sorimpower.app.feature.bodylog.data.MealEntryEntity.localDate(): LocalDate =
    Instant.ofEpochMilli(eatenAt).atZone(ZoneId.systemDefault()).toLocalDate()
fun MounjaroInjectionEntity.localDate(): LocalDate =
    Instant.ofEpochMilli(injectedAt).atZone(ZoneId.systemDefault()).toLocalDate()
fun ExerciseEntryEntity.localDate(): LocalDate =
    Instant.ofEpochMilli(exercisedAt).atZone(ZoneId.systemDefault()).toLocalDate()
fun InBodyResultEntity.localDate(): LocalDate =
    Instant.ofEpochMilli(measuredAt).atZone(ZoneId.systemDefault()).toLocalDate()

fun List<WeightEntryEntity>.dailyRepresentatives(): Map<LocalDate, WeightEntryEntity> =
    groupBy(WeightEntryEntity::localDate).mapValues { (_, entries) -> entries.maxBy(WeightEntryEntity::measuredAt) }

fun List<MealWithDetails>.groupsByDate(selectedDate: LocalDate? = null): List<Pair<LocalDate, List<MealWithDetails>>> =
    asSequence()
        .filter { selectedDate == null || it.meal.localDate() == selectedDate }
        .groupBy { it.meal.localDate() }
        .entries
        .sortedByDescending { it.key }
        .map { it.key to it.value.sortedByDescending { meal -> meal.meal.eatenAt } }
