package com.sorimpower.app.feature.auction.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sorimpower.app.feature.auction.domain.AuctionSortDirection
import com.sorimpower.app.feature.auction.domain.AuctionSortField
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.auctionSortDataStore by preferencesDataStore("auction_sort_settings")

data class AuctionSortPreference(
    val field: AuctionSortField = AuctionSortField.AUCTION_DATE,
    val direction: AuctionSortDirection = AuctionSortDirection.ASCENDING,
)

/** 진행·관심·AI 목록에서 선택한 정렬을 앱 재시작 뒤에도 유지한다. */
class AuctionSortPreferencesRepository(private val context: Context) {
    val preference: Flow<AuctionSortPreference> = context.auctionSortDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values ->
            AuctionSortPreference(
                field = values[SORT_FIELD]
                    ?.let { name -> AuctionSortField.entries.firstOrNull { it.name == name } }
                    ?.takeUnless { it == AuctionSortField.REMOVED_AT }
                    ?: AuctionSortField.AUCTION_DATE,
                direction = values[SORT_DIRECTION]
                    ?.let { name -> AuctionSortDirection.entries.firstOrNull { it.name == name } }
                    ?: AuctionSortDirection.ASCENDING,
            )
        }

    suspend fun save(value: AuctionSortPreference) {
        context.auctionSortDataStore.edit { values ->
            values[SORT_FIELD] = value.field.name
            values[SORT_DIRECTION] = value.direction.name
        }
    }

    private companion object {
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_DIRECTION = stringPreferencesKey("sort_direction")
    }
}
