package com.sorimpower.app.feature.auction.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sorimpower.app.feature.auction.domain.AuctionAiPreferences
import com.sorimpower.app.feature.auction.domain.AuctionRiskLevel
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.auctionAiDataStore by preferencesDataStore("auction_ai_settings")

class AuctionAiPreferencesRepository(private val context: Context) {
    val preferences: Flow<AuctionAiPreferences> = context.auctionAiDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values ->
            AuctionAiPreferences(
                dailyRecommendationEnabled = values[ENABLED] ?: false,
                maxBidPrice = values[MAX_BID_PRICE]?.takeIf { it > 0L },
                preferredDistricts = values[PREFERRED_DISTRICTS].orEmpty(),
                minimumDiscountRate = values[MINIMUM_DISCOUNT_RATE]?.takeIf { it >= 0.0 },
                maximumRiskLevel = values[MAXIMUM_RISK_LEVEL]
                    ?.let { name -> AuctionRiskLevel.entries.firstOrNull { it.name == name } }
                    ?: AuctionRiskLevel.MEDIUM,
                allowOccupiedProperty = values[ALLOW_OCCUPIED] ?: false,
                extraRequest = values[EXTRA_REQUEST].orEmpty(),
                notificationHour = (values[NOTIFICATION_HOUR] ?: 8).coerceIn(6, 10),
            )
        }

    suspend fun current(): AuctionAiPreferences = preferences.first()

    suspend fun save(value: AuctionAiPreferences) {
        context.auctionAiDataStore.edit { values ->
            values[ENABLED] = value.dailyRecommendationEnabled
            value.maxBidPrice?.takeIf { it > 0L }?.let { values[MAX_BID_PRICE] = it } ?: values.remove(MAX_BID_PRICE)
            values[PREFERRED_DISTRICTS] = value.preferredDistricts
            value.minimumDiscountRate?.takeIf { it >= 0.0 }?.let { values[MINIMUM_DISCOUNT_RATE] = it }
                ?: values.remove(MINIMUM_DISCOUNT_RATE)
            values[MAXIMUM_RISK_LEVEL] = value.maximumRiskLevel.name
            values[ALLOW_OCCUPIED] = value.allowOccupiedProperty
            values[EXTRA_REQUEST] = value.extraRequest.trim().take(500)
            values[NOTIFICATION_HOUR] = value.notificationHour.coerceIn(6, 10)
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("daily_recommendation_enabled")
        val MAX_BID_PRICE = longPreferencesKey("max_bid_price")
        val PREFERRED_DISTRICTS = stringSetPreferencesKey("preferred_districts")
        val MINIMUM_DISCOUNT_RATE = doublePreferencesKey("minimum_discount_rate")
        val MAXIMUM_RISK_LEVEL = stringPreferencesKey("maximum_risk_level")
        val ALLOW_OCCUPIED = booleanPreferencesKey("allow_occupied_property")
        val EXTRA_REQUEST = stringPreferencesKey("extra_request")
        val NOTIFICATION_HOUR = intPreferencesKey("notification_hour")
    }
}
