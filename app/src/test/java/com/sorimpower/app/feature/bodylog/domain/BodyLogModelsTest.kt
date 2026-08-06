package com.sorimpower.app.feature.bodylog.domain

import com.sorimpower.app.feature.bodylog.data.WeightEntryEntity
import com.sorimpower.app.feature.bodylog.data.MealEntryEntity
import com.sorimpower.app.feature.bodylog.data.MealWithDetails
import com.sorimpower.app.feature.bodylog.data.WeightGoalEntity
import com.sorimpower.app.feature.bodylog.data.MounjaroInjectionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BodyLogModelsTest {
    @Test
    fun `next mounjaro record date is seven days after the latest injection`() {
        val injectedAt = LocalDate.of(2026, 8, 1).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val state = BodyLogState(mounjaroInjections = listOf(mounjaroInjection("latest", injectedAt, 5.0)))

        assertEquals(injectedAt + MOUNJARO_INTERVAL_MILLIS, state.nextMounjaroInjectionAt)
    }

    @Test
    fun `mounjaro reminder interval follows the selected number of weeks`() {
        val injectedAt = LocalDate.of(2026, 8, 1).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val state = BodyLogState(mounjaroInjections = listOf(mounjaroInjection("latest", injectedAt, 5.0, intervalWeeks = 3)))

        assertEquals(injectedAt + MOUNJARO_INTERVAL_MILLIS * 3, state.nextMounjaroInjectionAt)
    }

    @Test
    fun `disabled mounjaro reminder has no next reminder date`() {
        val injectedAt = LocalDate.of(2026, 8, 1).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val state = BodyLogState(mounjaroInjections = listOf(mounjaroInjection("latest", injectedAt, 5.0, reminderEnabled = false)))

        assertNull(state.nextMounjaroInjectionAt)
    }

    @Test
    fun `goal remaining is negative when weight needs to decrease`() {
        val today = LocalDate.now()
        val state = BodyLogState(
            weights = listOf(weight("current", 86.1, today, 8)),
            activeGoal = goal(start = 90.0, target = 70.0),
        )

        assertEquals(-16.1, state.goalRemaining ?: 0.0, 0.001)
    }

    @Test
    fun `goal remaining is positive when weight needs to increase`() {
        val today = LocalDate.now()
        val state = BodyLogState(
            weights = listOf(weight("current", 70.0, today, 8)),
            activeGoal = goal(start = 68.0, target = 75.0),
        )

        assertEquals(5.0, state.goalRemaining ?: 0.0, 0.001)
    }

    @Test
    fun `daily representative is the latest measurement of each day`() {
        val day = LocalDate.of(2026, 8, 5)
        val morning = weight("morning", 72.4, day, 7)
        val evening = weight("evening", 73.0, day, 21)

        val representative = listOf(evening, morning).dailyRepresentatives()[day]

        assertEquals("evening", representative?.id)
        assertEquals(73.0, representative?.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun `records from different days remain independent`() {
        val firstDay = LocalDate.of(2026, 8, 4)
        val secondDay = LocalDate.of(2026, 8, 5)

        val representatives = listOf(
            weight("one", 73.2, firstDay, 8),
            weight("two", 72.8, secondDay, 8),
        ).dailyRepresentatives()

        assertEquals(2, representatives.size)
        assertEquals(73.2, representatives[firstDay]?.weightKg ?: 0.0, 0.001)
        assertEquals(72.8, representatives[secondDay]?.weightKg ?: 0.0, 0.001)
    }

    @Test
    fun `meal keeps the date selected by the user`() {
        val selectedDate = LocalDate.of(2026, 7, 31)
        val eatenAt = selectedDate.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val meal = MealEntryEntity("meal", "LUNCH", eatenAt, 0, null, "", eatenAt, eatenAt)

        assertEquals(selectedDate, meal.localDate())
    }

    @Test
    fun `meal groups can be filtered to a calendar date`() {
        val firstDate = LocalDate.of(2026, 8, 4)
        val selectedDate = LocalDate.of(2026, 8, 5)
        val meals = listOf(meal("old", firstDate), meal("lunch", selectedDate), meal("dinner", selectedDate))

        val groups = meals.groupsByDate(selectedDate)

        assertEquals(1, groups.size)
        assertEquals(selectedDate, groups.single().first)
        assertEquals(listOf("dinner", "lunch"), groups.single().second.map { it.meal.id })
    }

    private fun weight(id: String, value: Double, date: LocalDate, hour: Int): WeightEntryEntity {
        val measuredAt = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return WeightEntryEntity(id, value, measuredAt, 0, null, null, null, measuredAt, measuredAt)
    }

    private fun meal(id: String, date: LocalDate): MealWithDetails {
        val hour = if (id == "dinner") 19 else 12
        val eatenAt = date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return MealWithDetails(MealEntryEntity(id, "LUNCH", eatenAt, 0, null, "", eatenAt, eatenAt), emptyList(), emptyList())
    }

    private fun goal(start: Double, target: Double) = WeightGoalEntity(
        id = "goal",
        startWeightKg = start,
        targetWeightKg = target,
        startedOnEpochDay = LocalDate.now().toEpochDay(),
        targetDateEpochDay = null,
        status = "ACTIVE",
    )

    private fun mounjaroInjection(
        id: String,
        injectedAt: Long,
        doseMg: Double,
        intervalWeeks: Int = 1,
        reminderEnabled: Boolean = true,
    ) = MounjaroInjectionEntity(
        id = id,
        injectedAt = injectedAt,
        doseMg = doseMg,
        sideEffects = "",
        note = null,
        reminderEnabled = reminderEnabled,
        reminderIntervalWeeks = intervalWeeks,
        createdAt = injectedAt,
        updatedAt = injectedAt,
    )
}
