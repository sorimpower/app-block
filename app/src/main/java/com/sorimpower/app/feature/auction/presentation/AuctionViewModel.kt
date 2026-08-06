package com.sorimpower.app.feature.auction.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.auction.data.AuctionRepository
import com.sorimpower.app.feature.auction.domain.AuctionItem
import com.sorimpower.app.feature.auction.domain.AuctionListFilter
import com.sorimpower.app.feature.auction.domain.AuctionSortDirection
import com.sorimpower.app.feature.auction.domain.AuctionSortField
import com.sorimpower.app.feature.auction.domain.filterAndSortAuctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuctionUiState(
    val items: List<AuctionItem> = emptyList(),
    val totalCount: Int = 0,
    val lastUpdatedAt: String? = null,
    val lastSuccessfulSyncAt: Long? = null,
    val hasCache: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshCompleted: Boolean = false,
    val errorMessage: String? = null,
    val favoriteKeys: Set<String> = emptySet(),
    val favoriteCount: Int = 0,
    val favoritesOnly: Boolean = false,
    val filter: AuctionListFilter = AuctionListFilter(),
    val sortField: AuctionSortField = AuctionSortField.AUCTION_DATE,
    val sortDirection: AuctionSortDirection = AuctionSortDirection.ASCENDING,
    val searchQuery: String = "",
)

class AuctionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuctionRepository(application)
    private val refreshing = MutableStateFlow(false)
    private val refreshCompleted = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    private val filter = MutableStateFlow(AuctionListFilter())
    private val sortField = MutableStateFlow(AuctionSortField.AUCTION_DATE)
    private val sortDirection = MutableStateFlow(AuctionSortDirection.ASCENDING)
    private val searchQuery = MutableStateFlow("")

    private val displayData = combine(repository.data, filter, sortField, sortDirection, searchQuery) { data, filter, field, direction, query ->
        val filteredItems = filterAndSortAuctions(data.items, filter, field, direction, query)
        AuctionUiState(
            items = filteredItems,
            totalCount = data.items.size,
            favoriteKeys = data.favoriteKeys,
            favoriteCount = data.items.count { it.itemKey in data.favoriteKeys },
            lastUpdatedAt = data.lastUpdatedAt,
            lastSuccessfulSyncAt = data.lastSuccessfulSyncAt,
            hasCache = data.hasCache,
            filter = filter,
            sortField = field,
            sortDirection = direction,
            searchQuery = query,
        )
    }.combine(favoritesOnly) { display, savedOnly ->
        display.copy(
            items = if (savedOnly) display.items.filter { it.itemKey in display.favoriteKeys } else display.items,
            favoritesOnly = savedOnly,
        )
    }

    val state = combine(
        displayData,
        refreshing,
        refreshCompleted,
        errorMessage,
    ) { display, isRefreshing, completed, error ->
        display.copy(
            isRefreshing = isRefreshing,
            refreshCompleted = completed,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuctionUiState())

    init { refresh() }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            errorMessage.value = null
            runCatching { repository.refresh() }
                .onFailure { error ->
                    errorMessage.value = when {
                        error.message?.contains("JSON이 아닌") == true -> "API 접근 설정을 확인해 주세요."
                        else -> "최신 경매 목록을 불러오지 못했어요."
                    }
                }
            refreshing.value = false
            refreshCompleted.value = true
        }
    }

    fun setFilter(value: AuctionListFilter) { filter.value = value }

    fun setSort(field: AuctionSortField) {
        sortField.value = field
    }

    fun setSortDirection(direction: AuctionSortDirection) { sortDirection.value = direction }

    fun setSearchQuery(query: String) { searchQuery.value = query.take(60) }

    fun setFavoritesOnly(value: Boolean) { favoritesOnly.value = value }

    fun setFavorite(itemKey: String, favorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(itemKey, favorite) }
    }
}
