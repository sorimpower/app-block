package com.sorimpower.app.feature.blocker.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

interface BlockRule {
    val packageName: String
    fun appliesNow(currentTimeMillis: Long = System.currentTimeMillis()): Boolean
}

enum class RepeatCycle {
    EVERY_WEEK,
    EVERY_TWO_WEEKS,
    EVERY_MONTH;

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value } ?: EVERY_WEEK
    }
}

enum class ScheduleAction {
    BLOCK,
    ALLOW;

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value } ?: BLOCK
    }
}

data class BlockSchedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "기본 차단 조건",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val action: ScheduleAction = ScheduleAction.BLOCK,
    val weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val timeEnabled: Boolean = false,
    val startMinute: Int = 0,
    val endMinute: Int = 24 * 60 - 1,
    val repeatCycle: RepeatCycle = RepeatCycle.EVERY_WEEK,
    val anchorEpochDay: Long = LocalDate.now().toEpochDay(),
    val monthlyDateEnabled: Boolean = false,
    val monthlyDays: Set<Int> = emptySet(),
) {
    fun appliesAt(currentTimeMillis: Long): Boolean {
        if (!enabled) return false
        val zone = ZoneId.systemDefault()
        val dateTime = Instant.ofEpochMilli(currentTimeMillis).atZone(zone)
        val date = dateTime.toLocalDate()

        if (date.dayOfWeek !in weekdays) return false
        if (monthlyDateEnabled && date.dayOfMonth !in monthlyDays) return false

        val anchor = LocalDate.ofEpochDay(anchorEpochDay)
        val repeatMatches = when (repeatCycle) {
            RepeatCycle.EVERY_WEEK -> true
            RepeatCycle.EVERY_TWO_WEEKS -> {
                val weeks = ChronoUnit.WEEKS.between(
                    anchor.with(DayOfWeek.MONDAY),
                    date.with(DayOfWeek.MONDAY),
                )
                weeks >= 0 && weeks % 2L == 0L
            }
            RepeatCycle.EVERY_MONTH -> {
                val months = ChronoUnit.MONTHS.between(
                    anchor.withDayOfMonth(1),
                    date.withDayOfMonth(1),
                )
                val anchorDayInThisMonth = minOf(anchor.dayOfMonth, date.lengthOfMonth())
                months >= 0 && (monthlyDateEnabled || date.dayOfMonth == anchorDayInThisMonth)
            }
        }
        if (!repeatMatches) return false

        if (!timeEnabled) return true
        val minute = dateTime.hour * 60 + dateTime.minute
        return if (startMinute <= endMinute) {
            minute in startMinute..endMinute
        } else {
            minute >= startMinute || minute <= endMinute
        }
    }
}

data class ScheduledBlockRule(
    override val packageName: String,
    val schedule: BlockSchedule,
) : BlockRule {
    override fun appliesNow(currentTimeMillis: Long) = schedule.appliesAt(currentTimeMillis)
}
