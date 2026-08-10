package com.sorimpower.app.feature.auction.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.auction.data.AuctionRepository
import com.sorimpower.app.feature.auction.data.AuctionAiPreferencesRepository
import com.sorimpower.app.feature.auction.data.AuctionAiAnalysisMode
import com.sorimpower.app.feature.auction.domain.AuctionItem
import com.sorimpower.app.feature.auction.domain.AuctionAiAnalysis
import com.sorimpower.app.feature.auction.domain.AuctionAiPreferences
import com.sorimpower.app.feature.auction.domain.AuctionListFilter
import com.sorimpower.app.feature.auction.domain.AuctionSortDirection
import com.sorimpower.app.feature.auction.domain.AuctionSortField
import com.sorimpower.app.feature.auction.domain.filterAndSortAuctions
import com.sorimpower.app.feature.auction.domain.favoriteAuctionItems
import com.sorimpower.app.feature.auction.reminder.AuctionAiRecommendationScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AuctionListMode { ACTIVE, FAVORITES, ANALYZED, REMOVED }

private data class AuctionDisplayOptions(
    val filter: AuctionListFilter,
    val sortField: AuctionSortField,
    val sortDirection: AuctionSortDirection,
    val searchQuery: String,
    val listMode: AuctionListMode,
)

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
    val aiAnalyses: Map<String, AuctionAiAnalysis> = emptyMap(),
    val aiPreferences: AuctionAiPreferences = AuctionAiPreferences(),
    val favoriteCount: Int = 0,
    val analysisCount: Int = 0,
    val removedCount: Int = 0,
    val listMode: AuctionListMode = AuctionListMode.ACTIVE,
    val filter: AuctionListFilter = AuctionListFilter(),
    val sortField: AuctionSortField = AuctionSortField.AUCTION_DATE,
    val sortDirection: AuctionSortDirection = AuctionSortDirection.ASCENDING,
    val searchQuery: String = "",
)

class AuctionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuctionRepository(application)
    private val aiPreferencesRepository = AuctionAiPreferencesRepository(application)
    private val refreshing = MutableStateFlow(false)
    private val refreshCompleted = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val listMode = MutableStateFlow(AuctionListMode.ACTIVE)
    private val filter = MutableStateFlow(AuctionListFilter())
    private val sortField = MutableStateFlow(AuctionSortField.AUCTION_DATE)
    private val sortDirection = MutableStateFlow(AuctionSortDirection.ASCENDING)
    private val searchQuery = MutableStateFlow("")
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch { repository.recoverStaleAiAnalyses() }
    }

    private val displayOptions = combine(filter, sortField, sortDirection, searchQuery, listMode) { currentFilter, field, direction, query, mode ->
        AuctionDisplayOptions(currentFilter, field, direction, query, mode)
    }

    private val displayData = combine(repository.data, displayOptions) { data, options ->
        val favoriteItems = favoriteAuctionItems(data.items, data.historyItems, data.favoriteKeys)
        val analyzedItems = (data.items + data.historyItems)
            .distinctBy(AuctionItem::itemKey)
            .filter { it.itemKey in data.aiAnalyses }
        val sourceItems = when (options.listMode) {
            AuctionListMode.ACTIVE -> data.items
            AuctionListMode.FAVORITES -> favoriteItems
            AuctionListMode.ANALYZED -> analyzedItems
            AuctionListMode.REMOVED -> data.historyItems
        }
        val filteredItems = filterAndSortAuctions(
            sourceItems,
            options.filter,
            options.sortField,
            options.sortDirection,
            options.searchQuery,
        )
        AuctionUiState(
            items = filteredItems,
            totalCount = data.items.size,
            favoriteKeys = data.favoriteKeys,
            aiAnalyses = data.aiAnalyses,
            favoriteCount = favoriteItems.size,
            analysisCount = analyzedItems.size,
            removedCount = data.historyItems.size,
            listMode = options.listMode,
            lastUpdatedAt = if (options.listMode == AuctionListMode.REMOVED) data.historyLastUpdatedAt else data.lastUpdatedAt,
            lastSuccessfulSyncAt = data.lastSuccessfulSyncAt,
            hasCache = data.hasCache,
            filter = options.filter,
            sortField = options.sortField,
            sortDirection = options.sortDirection,
            searchQuery = options.searchQuery,
        )
    }

    val state = combine(
        displayData,
        refreshing,
        refreshCompleted,
        errorMessage,
        aiPreferencesRepository.preferences,
    ) { display, isRefreshing, completed, error, aiPreferences ->
        display.copy(
            isRefreshing = isRefreshing,
            refreshCompleted = completed,
            errorMessage = error,
            aiPreferences = aiPreferences,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuctionUiState())

    fun refreshIfNeeded() = launchRefresh(force = false)

    fun refresh() = launchRefresh(force = true)

    private fun launchRefresh(force: Boolean) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            if (!force && !repository.needsAutomaticRefresh()) return@launch
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

    fun setListMode(value: AuctionListMode) {
        listMode.value = value
        if (value == AuctionListMode.REMOVED) {
            sortField.value = AuctionSortField.REMOVED_AT
            sortDirection.value = AuctionSortDirection.DESCENDING
        } else if (sortField.value == AuctionSortField.REMOVED_AT) {
            sortField.value = AuctionSortField.AUCTION_DATE
            sortDirection.value = AuctionSortDirection.ASCENDING
        }
    }

    fun showAiAnalyses() = setListMode(AuctionListMode.ANALYZED)

    fun setFavorite(itemKey: String, favorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(itemKey, favorite) }
    }

    fun deleteHistoryItem(itemKey: String) {
        viewModelScope.launch { repository.deleteHistoryItem(itemKey) }
    }

    fun analyzeRights(item: AuctionItem, useTerra: Boolean = false) {
        viewModelScope.launch {
            repository.analyzeRights(item, aiPreferencesRepository.current().toCriteria(), if (useTerra) AuctionAiAnalysisMode.MANUAL_TERRA else AuctionAiAnalysisMode.MANUAL)
        }
    }

    fun saveAiPreferences(value: AuctionAiPreferences) {
        viewModelScope.launch {
            aiPreferencesRepository.save(value)
            AuctionAiRecommendationScheduler.schedule(getApplication(), value)
        }
    }

}
