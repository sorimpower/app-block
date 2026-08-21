package com.sorimpower.app.feature.bodylog.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectActivityReader(private val context: Context) {
    companion object {
        private const val DEFAULT_WEIGHT_KG = 65.0
        private const val WALKING_KCAL_PER_STEP_PER_KG = 0.00057
        val requiredPermissions = setOf(
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_DISTANCE",
            "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
            "android.permission.health.READ_EXERCISE",
        )
    }

    suspend fun syncRecentDays(weightKg: Double?): List<DailyHealthActivityEntity> {
        require(HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) { "Health Connect를 사용할 수 없어요. 앱을 설치하거나 업데이트해 주세요." }
        val client = HealthConnectClient.getOrCreate(context)
        require(client.permissionController.getGrantedPermissions().containsAll(requiredPermissions)) { "Health Connect에서 활동 데이터 읽기를 허용해 주세요." }
        val zone = ZoneId.systemDefault()
        return (0L..29L).mapNotNull { offset ->
            val date = LocalDate.now(zone).minusDays(offset)
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val data = client.aggregate(AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    DistanceRecord.DISTANCE_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                ),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ))
            val steps = data[StepsRecord.COUNT_TOTAL] ?: 0L
            val distance = data[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
            val measuredActiveCalories = data[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
            // 보행 속도·경사를 알 수 없어, 활동 칼로리 미제공 시에만 체중 보정 걸음 추정치를 사용한다.
            val activeCaloriesEstimated = measuredActiveCalories <= 0.0 && steps > 0L
            val activeCalories = if (activeCaloriesEstimated) steps * (weightKg ?: DEFAULT_WEIGHT_KG) * WALKING_KCAL_PER_STEP_PER_KG else measuredActiveCalories
            val exerciseMinutes = (data[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes() ?: 0L)
            if (steps == 0L && distance == 0.0 && activeCalories == 0.0 && exerciseMinutes == 0L) null else DailyHealthActivityEntity(
                dateEpochDay = date.toEpochDay(), steps = steps, distanceMeters = distance, activeCalories = activeCalories,
                activeCaloriesEstimated = activeCaloriesEstimated, exerciseMinutes = exerciseMinutes, updatedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun hasPermissions(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE &&
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(requiredPermissions)

}
