package com.sorimpower.app.feature.blocker.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sorimpower.app.feature.blocker.domain.BlockSchedule
import com.sorimpower.app.feature.blocker.domain.RepeatCycle
import com.sorimpower.app.feature.blocker.domain.ScheduleAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.blockerDataStore by preferencesDataStore("app_blocker")

class BlockerRepository(private val context: Context) {
    private val enabledKey = booleanPreferencesKey("blocker_enabled")
    private val packagesKey = stringSetPreferencesKey("blocked_packages")
    private val schedulesKey = stringSetPreferencesKey("block_schedules_v2")
    private val appScheduleAssignmentsKey = stringSetPreferencesKey("app_schedule_assignments_v3")
    private val blockMessageKey = stringPreferencesKey("block_message")
    private val startDestinationKey = stringPreferencesKey("start_destination")
    private val bottomNavigationOrderKey = stringPreferencesKey("bottom_navigation_order")
    private val bottomNavigationSchemaVersionKey = intPreferencesKey("bottom_navigation_schema_version")
    private val passwordHashKey = stringPreferencesKey("password_hash")
    private val passwordSaltKey = stringPreferencesKey("password_salt")
    private val legacyBypassPackageKey = stringPreferencesKey("bypass_package")
    private val legacyBypassUntilKey = longPreferencesKey("bypass_until")
    private val dailyLaunchCountsKey = stringSetPreferencesKey("daily_blocked_launch_counts_v1")

    // v0.2 single-schedule keys. They are read only for automatic migration.
    private val legacyWeekdaysKey = stringSetPreferencesKey("schedule_weekdays")
    private val legacyTimeEnabledKey = booleanPreferencesKey("schedule_time_enabled")
    private val legacyStartMinuteKey = intPreferencesKey("schedule_start_minute")
    private val legacyEndMinuteKey = intPreferencesKey("schedule_end_minute")
    private val legacyRepeatCycleKey = stringPreferencesKey("schedule_repeat_cycle")
    private val legacyAnchorEpochDayKey = longPreferencesKey("schedule_anchor_epoch_day")
    private val legacyMonthlyDateEnabledKey = booleanPreferencesKey("schedule_monthly_date_enabled")
    private val legacyMonthlyDaysKey = stringSetPreferencesKey("schedule_monthly_days")

    val state: Flow<BlockerState> = context.blockerDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::toState)

    private fun toState(preferences: Preferences): BlockerState {
        val schedules = preferences[schedulesKey]
            ?.mapNotNull(::decodeSchedule)
            ?.sortedBy(BlockSchedule::createdAt)
            ?: listOf(legacySchedule(preferences))
        val blockedPackages = preferences[packagesKey] ?: emptySet()
        val assignments = preferences[appScheduleAssignmentsKey]
            ?.mapNotNull(::decodeAssignment)
            ?.toMap()
            ?: blockedPackages.associateWith { schedules.map(BlockSchedule::id).toSet() }
        return BlockerState(
            loaded = true,
            enabled = preferences[enabledKey] ?: false,
            blockedPackages = blockedPackages,
            schedules = schedules,
            appScheduleIds = assignments,
            blockMessage = preferences[blockMessageKey] ?: DEFAULT_BLOCK_MESSAGE,
            startDestination = StartDestination.from(preferences[startDestinationKey]),
            bottomNavigationOrder = BottomNavigationTab.from(preferences[bottomNavigationOrderKey]),
            hasPassword = !preferences[passwordHashKey].isNullOrBlank(),
            oneTimeBypassPackage = preferences[legacyBypassPackageKey],
        )
    }

    private fun legacySchedule(preferences: Preferences): BlockSchedule {
        val weekdays = preferences[legacyWeekdaysKey]
            ?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.takeIf(Set<DayOfWeek>::isNotEmpty)
            ?: DayOfWeek.entries.toSet()
        val monthlyDays = preferences[legacyMonthlyDaysKey]
            ?.mapNotNull(String::toIntOrNull)
            ?.filter { it in 1..31 }
            ?.toSet()
            .orEmpty()
        return BlockSchedule(
            id = LEGACY_SCHEDULE_ID,
            name = "기본 차단 조건",
            createdAt = 0L,
            weekdays = weekdays,
            timeEnabled = preferences[legacyTimeEnabledKey] ?: false,
            startMinute = preferences[legacyStartMinuteKey] ?: 0,
            endMinute = preferences[legacyEndMinuteKey] ?: 1439,
            repeatCycle = RepeatCycle.from(preferences[legacyRepeatCycleKey]),
            anchorEpochDay = preferences[legacyAnchorEpochDayKey] ?: LocalDate.now().toEpochDay(),
            monthlyDateEnabled = preferences[legacyMonthlyDateEnabledKey] ?: false,
            monthlyDays = monthlyDays,
        )
    }

    suspend fun setEnabled(enabled: Boolean) = context.blockerDataStore.edit { it[enabledKey] = enabled }

    suspend fun setBlocked(packageName: String, blocked: Boolean) = context.blockerDataStore.edit { preferences ->
        val packages = (preferences[packagesKey] ?: emptySet()).toMutableSet()
        val schedules = currentSchedules(preferences)
        val assignments = currentAssignments(preferences, packages, schedules).toMutableMap()
        if (blocked) {
            packages += packageName
            assignments[packageName] = schedules.map(BlockSchedule::id).toSet()
        } else {
            packages -= packageName
            assignments.remove(packageName)
        }
        preferences[packagesKey] = packages
        preferences[appScheduleAssignmentsKey] = assignments.map(::encodeAssignment).toSet()
    }

    suspend fun upsertSchedule(schedule: BlockSchedule) = context.blockerDataStore.edit { preferences ->
        val current = currentSchedules(preferences)
        materializeAssignments(preferences, current)
        val schedules = current.associateBy(BlockSchedule::id).toMutableMap()
        schedules[schedule.id] = schedule.normalized().copy(enabled = true)
        preferences[schedulesKey] = schedules.values.map(::encodeSchedule).toSet()
    }

    suspend fun deleteSchedule(id: String) = context.blockerDataStore.edit { preferences ->
        val current = currentSchedules(preferences)
        val assignments = currentAssignments(
            preferences,
            preferences[packagesKey] ?: emptySet(),
            current,
        ).mapValues { (_, ids) -> ids - id }
        val schedules = current.filterNot { it.id == id }
        preferences[schedulesKey] = schedules.map(::encodeSchedule).toSet()
        preferences[appScheduleAssignmentsKey] = assignments.map(::encodeAssignment).toSet()
    }

    suspend fun setAppSchedules(packageName: String, scheduleIds: Set<String>) =
        context.blockerDataStore.edit { preferences ->
            val packages = preferences[packagesKey] ?: emptySet()
            val schedules = currentSchedules(preferences)
            val validIds = schedules.map(BlockSchedule::id).toSet()
            val assignments = currentAssignments(preferences, packages, schedules).toMutableMap()
            assignments[packageName] = scheduleIds.intersect(validIds)
            preferences[appScheduleAssignmentsKey] = assignments.map(::encodeAssignment).toSet()
        }

    suspend fun setBlockMessage(message: String) = context.blockerDataStore.edit {
        it[blockMessageKey] = message.trim().take(120).ifBlank { DEFAULT_BLOCK_MESSAGE }
    }

    suspend fun setStartDestination(destination: StartDestination) = context.blockerDataStore.edit {
        it[startDestinationKey] = destination.name
    }

    suspend fun setBottomNavigationOrder(order: List<BottomNavigationTab>) = context.blockerDataStore.edit {
        it[bottomNavigationOrderKey] = BottomNavigationTab.normalize(order).joinToString(",") { tab -> tab.name }
    }

    /** 기존 설치에도 새 세금 탭을 한 번만 추가하고, 이후 사용자의 탭 숨김 설정은 존중한다. */
    suspend fun migrateBottomNavigation() = context.blockerDataStore.edit { preferences ->
        if ((preferences[bottomNavigationSchemaVersionKey] ?: 0) >= BOTTOM_NAVIGATION_SCHEMA_VERSION) return@edit
        val tabs = BottomNavigationTab.from(preferences[bottomNavigationOrderKey]).toMutableList()
        if (BottomNavigationTab.PROPERTY_TAX !in tabs) {
            val moreIndex = tabs.indexOf(BottomNavigationTab.MORE).let { if (it < 0) tabs.size else it }
            tabs.add(moreIndex, BottomNavigationTab.PROPERTY_TAX)
            preferences[bottomNavigationOrderKey] = BottomNavigationTab.normalize(tabs).joinToString(",") { it.name }
        }
        preferences[bottomNavigationSchemaVersionKey] = BOTTOM_NAVIGATION_SCHEMA_VERSION
    }

    suspend fun setPassword(password: String) {
        require(password.length in 4..12)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = withContext(Dispatchers.Default) { derivePassword(password, salt) }
        context.blockerDataStore.edit {
            it[passwordSaltKey] = Base64.encodeToString(salt, Base64.NO_WRAP)
            it[passwordHashKey] = Base64.encodeToString(hash, Base64.NO_WRAP)
        }
    }

    suspend fun clearPassword() = context.blockerDataStore.edit {
        it.remove(passwordSaltKey)
        it.remove(passwordHashKey)
        it.remove(legacyBypassPackageKey)
        it.remove(legacyBypassUntilKey)
    }

    suspend fun verifyPassword(password: String): Boolean {
        val preferences = context.blockerDataStore.data.first()
        val expected = preferences[passwordHashKey]?.let(::decodeBase64) ?: return false
        val salt = preferences[passwordSaltKey]?.let(::decodeBase64) ?: return false
        val actual = withContext(Dispatchers.Default) { derivePassword(password, salt) }
        return MessageDigest.isEqual(expected, actual)
    }

    suspend fun allowNextLaunch(packageName: String) {
        context.blockerDataStore.edit {
            it[legacyBypassPackageKey] = packageName
            it.remove(legacyBypassUntilKey)
        }
    }

    suspend fun consumeNextLaunch(packageName: String) {
        context.blockerDataStore.edit {
            if (it[legacyBypassPackageKey] == packageName) {
                it.remove(legacyBypassPackageKey)
                it.remove(legacyBypassUntilKey)
            }
        }
    }

    suspend fun recordBlockedLaunch(packageName: String, now: Long = System.currentTimeMillis()): Int {
        val epochDay = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        var updatedCount = 1
        context.blockerDataStore.edit { preferences ->
            val todayCounts = preferences[dailyLaunchCountsKey]
                ?.mapNotNull(::decodeDailyCount)
                ?.filter { it.epochDay == epochDay }
                ?.associateBy(DailyLaunchCount::packageName)
                ?.toMutableMap()
                ?: mutableMapOf()
            updatedCount = (todayCounts[packageName]?.count ?: 0) + 1
            todayCounts[packageName] = DailyLaunchCount(packageName, epochDay, updatedCount)
            preferences[dailyLaunchCountsKey] = todayCounts.values.map(::encodeDailyCount).toSet()
        }
        return updatedCount
    }

    private fun currentSchedules(preferences: Preferences): List<BlockSchedule> =
        preferences[schedulesKey]?.mapNotNull(::decodeSchedule) ?: listOf(legacySchedule(preferences))

    private fun currentAssignments(
        preferences: Preferences,
        blockedPackages: Set<String>,
        schedules: List<BlockSchedule>,
    ): Map<String, Set<String>> = preferences[appScheduleAssignmentsKey]
        ?.mapNotNull(::decodeAssignment)
        ?.toMap()
        ?: blockedPackages.associateWith { schedules.map(BlockSchedule::id).toSet() }

    private fun materializeAssignments(preferences: androidx.datastore.preferences.core.MutablePreferences, schedules: List<BlockSchedule>) {
        if (preferences[appScheduleAssignmentsKey] != null) return
        val blockedPackages = preferences[packagesKey] ?: emptySet()
        val scheduleIds = schedules.map(BlockSchedule::id).toSet()
        preferences[appScheduleAssignmentsKey] = blockedPackages
            .associateWith { scheduleIds }
            .map(::encodeAssignment)
            .toSet()
    }

    private fun encodeSchedule(schedule: BlockSchedule): String = listOf(
        schedule.id,
        Base64.encodeToString(schedule.name.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP),
        schedule.enabled.toString(),
        schedule.createdAt.toString(),
        schedule.action.name,
        schedule.weekdays.joinToString(",", transform = DayOfWeek::name),
        schedule.timeEnabled.toString(),
        schedule.startMinute.toString(),
        schedule.endMinute.toString(),
        schedule.repeatCycle.name,
        schedule.anchorEpochDay.toString(),
        schedule.monthlyDateEnabled.toString(),
        schedule.monthlyDays.joinToString(","),
    ).joinToString("|")

    private fun decodeSchedule(raw: String): BlockSchedule? = runCatching {
        val parts = raw.split('|')
        require(parts.size == 12 || parts.size == 13)
        val hasAction = parts.size == 13
        val offset = if (hasAction) 1 else 0
        BlockSchedule(
            id = parts[0],
            name = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP)),
            enabled = parts[2].toBooleanStrict(),
            createdAt = parts[3].toLong(),
            action = if (hasAction) ScheduleAction.from(parts[4]) else ScheduleAction.BLOCK,
            weekdays = parts[4 + offset].split(',').mapNotNull { value ->
                runCatching { DayOfWeek.valueOf(value) }.getOrNull()
            }.toSet().ifEmpty { DayOfWeek.entries.toSet() },
            timeEnabled = parts[5 + offset].toBooleanStrict(),
            startMinute = parts[6 + offset].toInt(),
            endMinute = parts[7 + offset].toInt(),
            repeatCycle = RepeatCycle.from(parts[8 + offset]),
            anchorEpochDay = parts[9 + offset].toLong(),
            monthlyDateEnabled = parts[10 + offset].toBooleanStrict(),
            monthlyDays = parts[11 + offset].split(',').mapNotNull(String::toIntOrNull).toSet(),
        ).normalized()
    }.getOrNull()

    private fun encodeAssignment(entry: Map.Entry<String, Set<String>>): String {
        val packageName = Base64.encodeToString(entry.key.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        return "$packageName|${entry.value.joinToString(",")}" 
    }

    private fun decodeAssignment(raw: String): Pair<String, Set<String>>? = runCatching {
        val parts = raw.split('|', limit = 2)
        require(parts.size == 2)
        val packageName = String(Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_WRAP))
        packageName to parts[1].split(',').filter(String::isNotBlank).toSet()
    }.getOrNull()

    private fun encodeDailyCount(value: DailyLaunchCount): String {
        val packageName = Base64.encodeToString(value.packageName.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        return "$packageName|${value.epochDay}|${value.count}"
    }

    private fun decodeDailyCount(raw: String): DailyLaunchCount? = runCatching {
        val parts = raw.split('|')
        require(parts.size == 3)
        DailyLaunchCount(
            packageName = String(Base64.decode(parts[0], Base64.URL_SAFE or Base64.NO_WRAP)),
            epochDay = parts[1].toLong(),
            count = parts[2].toInt().coerceAtLeast(0),
        )
    }.getOrNull()

    private fun BlockSchedule.normalized() = copy(
        name = name.trim().take(30).ifBlank { "차단 조건" },
        weekdays = weekdays.ifEmpty { DayOfWeek.entries.toSet() },
        startMinute = startMinute.coerceIn(0, 1439),
        endMinute = endMinute.coerceIn(0, 1439),
        monthlyDays = monthlyDays.filter { it in 1..31 }.toSet(),
    )

    private fun derivePassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeBase64(value: String) = Base64.decode(value, Base64.NO_WRAP)

    companion object {
        const val DEFAULT_BLOCK_MESSAGE = "지금은 잠시 멈춰갈 시간이에요."
        private const val LEGACY_SCHEDULE_ID = "legacy-default"
        private const val BOTTOM_NAVIGATION_SCHEMA_VERSION = 1
    }
}

private data class DailyLaunchCount(val packageName: String, val epochDay: Long, val count: Int)

enum class StartDestination {
    HOME,
    APP_BLOCKER,
    BODY_LOG,
    REAL_ESTATE_AUCTION,
    PHONE_INSIGHT,
    PROPERTY_TAX,
    MORE;

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value } ?: HOME
    }
}

enum class BottomNavigationTab(val label: String) {
    HOME("홈"), PHONE_INSIGHT("챙김"), BLOCKER("차단"), BODY_LOG("기록"), AUCTION("경매"), PROPERTY_TAX("세금"), MORE("더보기");

    companion object {
        val defaultOrder = entries.toList()

        fun from(value: String?): List<BottomNavigationTab> {
            if (value.isNullOrBlank()) return defaultOrder
            return normalize(value.split(",").mapNotNull { name -> entries.firstOrNull { it.name == name } })
        }

        fun normalize(order: List<BottomNavigationTab>): List<BottomNavigationTab> {
            val selected = order.distinct().toMutableList()
            if (MORE !in selected) selected += MORE
            return selected
        }
    }
}

data class BlockerState(
    val loaded: Boolean = false,
    val enabled: Boolean = false,
    val blockedPackages: Set<String> = emptySet(),
    val schedules: List<BlockSchedule> = emptyList(),
    val appScheduleIds: Map<String, Set<String>> = emptyMap(),
    val blockMessage: String = BlockerRepository.DEFAULT_BLOCK_MESSAGE,
    val startDestination: StartDestination = StartDestination.HOME,
    val bottomNavigationOrder: List<BottomNavigationTab> = BottomNavigationTab.defaultOrder,
    val hasPassword: Boolean = false,
    val oneTimeBypassPackage: String? = null,
) {
    val activeScheduleCount get() = schedules.size

    fun shouldBlock(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        if (!enabled || packageName !in blockedPackages) return false
        val assignedIds = appScheduleIds[packageName].orEmpty()
        val applicable = schedules.filter { it.id in assignedIds && it.copy(enabled = true).appliesAt(now) }
        if (applicable.any { it.action == ScheduleAction.ALLOW }) return false
        return applicable.any { it.action == ScheduleAction.BLOCK }
    }
}
